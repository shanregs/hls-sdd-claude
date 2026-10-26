package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.internal.OverdueFollowUps;
import com.hls.tasks.internal.DeadlineJob;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 022 US6: an overdue monthly due makes one follow-up task, escalates through spec 025, and closes when settled. */
class OverdueFollowUpTest extends ReceivablesTestBase {

    @Autowired
    private OverdueFollowUps job;

    @Autowired
    private DeadlineJob taskDeadlines;

    private UUID overdueDue(String admin, Fixture f, int daysPastDue) {
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        makeOverdue(due, daysPastDue);
        return due;
    }

    private int tasksFor(UUID due) {
        return jdbc.queryForObject(
                "select count(*) from task where source = 'SCHOOL_DUE_OVERDUE' and source_key = ?", Integer.class, due.toString());
    }

    private Map<String, Object> taskRow(UUID due) {
        return jdbc.queryForMap("select * from task where source = 'SCHOOL_DUE_OVERDUE' and source_key = ?", due.toString());
    }

    @Test
    void anOverdueDueCreatesOneTaskForTheZoneManagerAndRepeatedRunsAddNone() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 1);

        job.runNow();
        job.runNow();

        assertThat(tasksFor(due)).isEqualTo(1);
        Map<String, Object> task = taskRow(due);
        assertThat(task.get("assignee_user_id").toString()).isEqualTo(f.manager().signed().userId().toString());
        assertThat(task.get("linked_type")).isEqualTo("MONTHLY_DUE");
        assertThat(task.get("linked_id").toString()).isEqualTo(due.toString());
        assertThat(((Number) task.get("escalate_after_days")).intValue()).isEqualTo(6);
        assertThat(task.get("due_on").toString()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        Integer rows = jdbc.queryForObject("select count(*) from overdue_follow_up where due_id = ?", Integer.class, due);
        assertThat(rows).isEqualTo(1);
        // the Zone Manager sees it among their tasks
        Resp mine = get("/api/v1/tasks?view=all&size=100", f.manager().token());
        assertThat(mine.body()).contains(due.toString());
    }

    @Test
    void aDueThatIsNotYetOverdueGetsNoTask() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());

        job.runNow();

        assertThat(tasksFor(due)).isZero();
    }

    @Test
    void payingTheDueInFullCompletesItsTask() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 3);
        job.runNow();
        assertThat(taskRow(due).get("status")).isEqualTo("ASSIGNED");

        assertThat(collect(f.manager().token(), collectionBody(f.schoolId(), "15000")).status()).isEqualTo(201);

        assertThat(taskRow(due).get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select closed_on is not null from overdue_follow_up where due_id = ?", Boolean.class, due)).isTrue();
        job.runNow();
        assertThat(tasksFor(due)).isEqualTo(1);
    }

    @Test
    void aCreditNoteThatClearsTheDueCompletesItsTask() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 3);
        job.runNow();

        assertThat(post(BILLING + "/dues/" + due + "/adjustments", admin, Map.of("amount", "15000", "reason", "Agreed waiver of the month")).status())
                .isEqualTo(201);

        assertThat(taskRow(due).get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void cancellingTheDueCancelsItsTask() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 3);
        job.runNow();

        assertThat(post(BILLING + "/dues/" + due + "/cancel", admin, Map.of("reason", "Raised in error")).status()).isEqualTo(200);

        assertThat(taskRow(due).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void theDirectorIsToldByTheTaskEscalationOnTheEscalationDayAndOnlyOnce() {
        String admin = signInAs(Role.ADMIN).token();
        Signed directorUser = director();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 1);
        job.runNow();
        UUID task = (UUID) taskRow(due).get("id");
        // six days later the task is past its escalation days (due date + 7 overall)
        jdbc.update("update task set due_on = current_date - 6 where id = ?", task);

        taskDeadlines.runNow();
        taskDeadlines.runNow();

        Integer escalated = jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = 'TASK_ESCALATED' and link = ?",
                Integer.class, directorUser.userId(), "/operations/tasks/" + task);
        // every active Director is told; the one we made is among them
        assertThat(escalated).isEqualTo(1);
    }

    @Test
    void aSchoolWithoutAZoneManagerNotifiesDirectorAndAdminOnceAndMakesNoTask() {
        String admin = signInAs(Role.ADMIN).token();
        Signed adminUser = signInAs(Role.ADMIN);
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID due = raiseOne(admin, thisMonth(), f.schoolId());
        makeOverdue(due, 2);
        // the School loses its Zone Manager
        jdbc.update("update school_manager_assignment set ends_on = current_date - 1 where school_id = ? and ends_on is null", f.schoolId());
        job.runNow();
        job.runNow();

        assertThat(tasksFor(due)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and type = 'SCHOOL_DUE_UNASSIGNED' and link like ?",
                        Integer.class, adminUser.userId(), "%" + f.schoolId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select task_id is null from overdue_follow_up where due_id = ?", Boolean.class, due)).isTrue();
    }

    @Test
    void aNewZoneManagerTakesOverTheOpenTask() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");
        UUID due = overdueDue(admin, f, 2);
        job.runNow();
        ManagerCtx replacement = newManager(admin, f.zoneId());
        assignSchoolManager(admin, f.schoolId(), replacement.managerId());

        job.runNow();

        assertThat(taskRow(due).get("assignee_user_id").toString()).isEqualTo(replacement.signed().userId().toString());
        assertThat(List.of("ASSIGNED")).contains((String) taskRow(due).get("status"));
    }
}
