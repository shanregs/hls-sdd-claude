package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import com.hls.tasks.internal.DeadlineJob;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 025 US3: overdue needs a reason, the Manager is told once, the Director hears when it escalates, once. */
class DeadlineJobTest extends TaskTestBase {

    @Autowired
    DeadlineJob job;

    @Test
    void theManagerIsToldOncePerDueDateHoweverOftenTheCheckRuns() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        moveDueDate(task, today().minusDays(1));

        job.runNow();
        job.runNow();
        assertThat(notificationCount(manager.signed().userId(), "TASK_OVERDUE")).isEqualTo(1);

        // a new due date that passes again is a new notice
        moveDueDate(task, today().plusDays(1));
        job.runNow();
        moveDueDate(task, today().minusDays(1));
        jdbc.update("update task set due_on = ? where id = ?", today().minusDays(2), task);
        job.runNow();
        assertThat(notificationCount(manager.signed().userId(), "TASK_OVERDUE")).isEqualTo(2);
    }

    @Test
    void anOpenTaskPastItsEscalationDaysGoesToEveryDirectorOnce() {
        String admin = signInAs(Role.ADMIN).token();
        Signed director = signInAs(Role.DIRECTOR);
        ManagerCtx manager = manager(admin);
        Map<String, Object> body = taskBody(manager.signed().userId(), today().plusDays(1));
        body.put("escalateAfterDays", 2);
        Resp created = post(BASE, admin, body);
        UUID task = UUID.fromString((String) ((Map<?, ?>) created.map().get("task")).get("id"));

        moveDueDate(task, today().minusDays(1));
        job.runNow();
        assertThat(notificationCountForTask(director.userId(), "TASK_ESCALATED", task)).isZero();

        moveDueDate(task, today().minusDays(2));
        DeadlineJob.Result result = job.runNow();
        assertThat(result.escalations()).isGreaterThanOrEqualTo(1);
        assertThat(notificationCountForTask(director.userId(), "TASK_ESCALATED", task)).isEqualTo(1);
        job.runNow();
        assertThat(notificationCountForTask(director.userId(), "TASK_ESCALATED", task)).isEqualTo(1);
        // Admin is not told by default, and the Manager is not told it was escalated
        assertThat(notificationCount(manager.signed().userId(), "TASK_ESCALATED")).isZero();

        Map<?, ?> row = (Map<?, ?>) get(BASE + "/" + task, admin).map().get("task");
        assertThat(row.get("escalated")).isEqualTo(true);
        String message = jdbc.queryForObject(
                "select message from notification where recipient_user_id = ? and link like ? and type = 'TASK_ESCALATED'", String.class, director.userId(), "%" + task);
        assertThat(message).contains("2 days overdue");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) get(BASE + "/" + task, admin).map().get("history");
        assertThat(history).extracting(h -> h.get("event")).contains("OVERDUE_NOTICE", "ESCALATED");
    }

    @Test
    void completedAndCancelledTasksAreLeftAlone() {
        String admin = signInAs(Role.ADMIN).token();
        Signed director = signInAs(Role.DIRECTOR);
        ManagerCtx manager = manager(admin);
        UUID done = newTask(admin, manager.signed().userId(), today().plusDays(1));
        startTask(manager.token(), done);
        completeTask(manager.token(), done);
        UUID cancelled = newTask(admin, manager.signed().userId(), today().plusDays(1));
        act(admin, cancelled, "cancel", Map.of("reason", "not needed"));
        moveDueDate(done, today().minusDays(10));
        moveDueDate(cancelled, today().minusDays(10));

        job.runNow();
        assertThat(notificationCount(manager.signed().userId(), "TASK_OVERDUE")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from task where id in (?, ?) and escalated_at is not null", Integer.class, done, cancelled))
                .isZero();
        assertThat(notificationCountForTask(director.userId(), "TASK_ESCALATED", done) + notificationCountForTask(director.userId(), "TASK_ESCALATED", cancelled)).isZero();
    }

    @Test
    void overdueNeedsAReasonAndAFollowUpDateCreatesALinkedTask() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        startTask(manager.token(), task);
        // not overdue yet: a reason makes no sense
        assertThat(act(manager.token(), task, "overdue-reason", Map.of("reason", "Principal was away")).status()).isEqualTo(409);

        moveDueDate(task, today().minusDays(1));
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(400);
        assertThat(act(manager.token(), task, "overdue-reason", Map.of("reason", "abc")).status()).isEqualTo(400);
        assertThat(act(manager.token(), task, "overdue-reason", Map.of("reason", "Principal was away", "followUpOn", today().minusDays(1).toString()))
                        .status())
                .isEqualTo(400);

        Resp ok = act(manager.token(), task, "overdue-reason", Map.of("reason", "Principal was away", "followUpOn", today().plusDays(3).toString()));
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<String> followUps = (List<String>) ok.map().get("followUpTaskIds");
        assertThat(followUps).hasSize(1);
        Resp followUp = get(BASE + "/" + followUps.get(0), manager.token());
        assertThat(followUp.map().get("parentTaskId")).isEqualTo(task.toString());
        Map<?, ?> followRow = (Map<?, ?>) followUp.map().get("task");
        assertThat(followRow.get("type")).isEqualTo("FOLLOW_UP");
        assertThat(followRow.get("dueOn")).isEqualTo(today().plusDays(3).toString());

        // the original stays overdue until completed, and can now be completed
        assertThat(((Map<?, ?>) get(BASE + "/" + task, admin).map().get("task")).get("overdue")).isEqualTo(true);
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(200);
        assertThat(get(BASE + "/" + task, admin).map().get("overdueReason")).isEqualTo("Principal was away");
    }
}
