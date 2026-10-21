package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.designation.api.BusinessDate;
import com.hls.support.TaskTestBase;
import com.hls.tasks.api.TaskActivities;
import com.hls.tasks.api.TaskActivities.ActivityEntry;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T018: the second feed method of tasks. */
class TaskActivitiesFeedTest extends TaskTestBase {

    @Autowired
    TaskActivities feed;

    @Autowired
    BusinessDate businessDate;

    private ActivityEntry entry(UUID task) {
        return feed.activitiesBetween(today().minusDays(10), today().plusDays(40), null).stream()
                .filter(e -> e.id().equals(task))
                .findFirst()
                .orElse(null);
    }

    private Resp move(String admin, UUID task, UUID assignee, LocalDate due) {
        Map<String, Object> body = new HashMap<>();
        body.put("assigneeUserId", assignee);
        body.put("dueOn", due.toString());
        body.put("title", "Visit moved");
        body.put("version", get(BASE + "/" + task, admin).map().get("version"));
        return put(BASE + "/" + task, admin, body);
    }

    @Test
    void cancelledTasksAreReturnedAndCarryTheirStatusAndNoText() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID open = newTask(admin, m.signed().userId(), today().plusDays(3));
        UUID cancelled = newTask(admin, m.signed().userId(), today().plusDays(4));
        act(admin, cancelled, "cancel", Map.of("reason", "not needed"));

        assertThat(entry(open).status()).isEqualTo("ASSIGNED");
        assertThat(entry(open).kind()).isEqualTo("SCHOOL_VISIT");
        assertThat(entry(open).ownerUserId()).isEqualTo(m.signed().userId());
        assertThat(entry(cancelled).status()).isEqualTo("CANCELLED");
        assertThat(ActivityEntry.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("title", "description", "note", "evidence", "completionNote");
    }

    @Test
    void completedOnIsSetForCompletedAndVerifiedAndNullAfterBeingSentBack() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID task = newTask(admin, m.signed().userId(), today().plusDays(2));
        startTask(m.token(), task);
        assertThat(completeTask(m.token(), task).status()).isEqualTo(200);
        assertThat(entry(task).status()).isEqualTo("COMPLETED");
        assertThat(entry(task).completedOn()).isEqualTo(businessDate.today());

        assertThat(act(admin, task, "verify", null).status()).isEqualTo(200);
        assertThat(entry(task).status()).isEqualTo("VERIFIED");
        assertThat(entry(task).completedOn()).isEqualTo(businessDate.today());

        UUID back = newTask(admin, m.signed().userId(), today().plusDays(2));
        startTask(m.token(), back);
        completeTask(m.token(), back);
        assertThat(act(admin, back, "send-back", Map.of("reason", "Add the photo")).status()).isEqualTo(200);
        assertThat(entry(back).status()).isEqualTo("IN_PROGRESS");
        assertThat(entry(back).completedOn()).isNull();
    }

    @Test
    void originalDateIsTheFirstDueDateAfterOneAndTwoChanges() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID task = newTask(admin, m.signed().userId(), today().plusDays(2));
        assertThat(entry(task).originalDate()).isNull();

        assertThat(move(admin, task, m.signed().userId(), today().plusDays(5)).status()).isEqualTo(200);
        assertThat(entry(task).originalDate()).isEqualTo(today().plusDays(2));

        assertThat(move(admin, task, m.signed().userId(), today().plusDays(9)).status()).isEqualTo(200);
        assertThat(entry(task).originalDate()).isEqualTo(today().plusDays(2));
        assertThat(entry(task).date()).isEqualTo(today().plusDays(9));
    }

    @Test
    void anUnparsableHistoryRowIsIgnored() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx m = manager(admin);
        UUID task = newTask(admin, m.signed().userId(), today().plusDays(2));
        jdbc.update(
                "insert into task_history (id, task_id, event, actor_user_id, at, before_value, after_value) values (?, ?, 'DUE_CHANGED', ?, now(), 'not a date', 'x')",
                UUID.randomUUID(), task, m.signed().userId());

        assertThat(entry(task)).isNotNull();
        assertThat(entry(task).originalDate()).isNull();
    }

    @Test
    void anOwnerFilterReturnsOnlyThatManagersTasks() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx a = manager(admin);
        ManagerCtx b = manager(admin);
        UUID mine = newTask(admin, a.signed().userId(), today().plusDays(2));
        UUID theirs = newTask(admin, b.signed().userId(), today().plusDays(2));

        var found = feed.activitiesBetween(today(), today().plusDays(5), a.signed().userId());

        assertThat(found).extracting(ActivityEntry::id).contains(mine).doesNotContain(theirs);
    }
}
