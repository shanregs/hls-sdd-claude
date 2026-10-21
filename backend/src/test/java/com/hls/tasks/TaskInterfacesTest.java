package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.FollowUpScheduled;
import com.hls.school.api.InvalidInputException;
import com.hls.support.TaskTestBase;
import com.hls.tasks.api.TaskActivities;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/** Spec 025 US4 and US5: the shared creation interface (C2), the 023 listener, and tasks as planned activities (C1). */
class TaskInterfacesTest extends TaskTestBase {

    @Autowired
    TaskCreation creation;

    @Autowired
    TaskActivities activities;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    private FollowUpRequest request(UUID assignee, String key, java.time.LocalDate due) {
        return new FollowUpRequest(
                TaskType.FOLLOW_UP, "Chase the overdue invoice", "Invoice is 10 days late", assignee, due, 5, "INVOICE", UUID.randomUUID(),
                "INV-1", "Green Valley School", "SCHOOL_BILLING", key, null);
    }

    @Test
    void createsOnceForOneSourceKeyWhileTheTaskIsLive() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        String key = "invoice-" + UUID.randomUUID();

        var first = creation.createFollowUp(request(manager.signed().userId(), key, today().plusDays(2)));
        var second = creation.createFollowUp(request(manager.signed().userId(), key, today().plusDays(2)));
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("select count(*) from task where source_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(notificationCount(manager.signed().userId(), "TASK_ASSIGNED")).isEqualTo(1);

        Map<?, ?> task = (Map<?, ?>) get(BASE + "/" + first.id(), manager.token()).map().get("task");
        assertThat(task.get("linkedType")).isEqualTo("INVOICE");
        assertThat(task.get("linkedLabel")).isEqualTo("INV-1");
        assertThat(task.get("place")).isEqualTo("Green Valley School");

        // once verified the key is free again
        startTask(manager.token(), first.id());
        completeTask(manager.token(), first.id());
        act(admin, first.id(), "verify", null);
        var third = creation.createFollowUp(request(manager.signed().userId(), key, today().plusDays(2)));
        assertThat(third.created()).isTrue();
        assertThat(third.id()).isNotEqualTo(first.id());
    }

    @Test
    void refusesAnAssigneeWhoIsNotAnActiveManagerAndBadInput() {
        UUID teacher = signInAs(Role.TEACHER).userId();
        assertThatThrownBy(() -> creation.createFollowUp(request(teacher, "k1", today())))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> creation.createFollowUp(request(UUID.randomUUID(), "k2", today())))
                .isInstanceOf(InvalidInputException.class);
        String admin = signInAs(Role.ADMIN).token();
        UUID manager = manager(admin).signed().userId();
        assertThatThrownBy(() -> creation.createFollowUp(new FollowUpRequest(
                        TaskType.FOLLOW_UP, "Ok title", null, manager, today(), null, null, null, null, null, " ", null, null)))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> creation.createFollowUp(request(manager, "k3", null))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void acceptsAPastDueDateFromAModuleThatIsAlreadyLate() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        var ref = creation.createFollowUp(request(manager.signed().userId(), "late-" + UUID.randomUUID(), today().minusDays(3)));
        Map<?, ?> task = (Map<?, ?>) get(BASE + "/" + ref.id(), manager.token()).map().get("task");
        assertThat(task.get("overdue")).isEqualTo(true);
    }

    @Test
    void aFollowUpDateFromMarketingBecomesAFollowUpTaskForTheOwnerOncePerActivity() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx owner = manager(admin);
        UUID activity = UUID.randomUUID();
        UUID prospect = UUID.randomUUID();
        var event = new FollowUpScheduled(activity, prospect, owner.signed().userId(), today().plusDays(7), "Call the Principal back");

        tx.executeWithoutResult(s -> publisher.publishEvent(event));
        tx.executeWithoutResult(s -> publisher.publishEvent(event));

        Resp list = get(BASE + "?view=upcoming", owner.token());
        assertThat(content(list)).hasSize(1);
        Map<String, Object> row = content(list).get(0);
        assertThat(row.get("type")).isEqualTo("FOLLOW_UP");
        assertThat(row.get("title")).isEqualTo("Call the Principal back");
        assertThat(row.get("linkedType")).isEqualTo("PROSPECT");
        assertThat(row.get("linkedId")).isEqualTo(prospect.toString());
        assertThat(row.get("dueOn")).isEqualTo(today().plusDays(7).toString());
    }

    @Test
    void anOwnerWhoIsNotAManagerDoesNotStopTheVisit() {
        UUID notManager = signInAs(Role.ADMIN).userId();
        var event = new FollowUpScheduled(UUID.randomUUID(), UUID.randomUUID(), notManager, today().plusDays(7), "Call back");
        tx.executeWithoutResult(s -> publisher.publishEvent(event));
        assertThat(jdbc.queryForObject("select count(*) from task where assignee_user_id = ?", Integer.class, notManager)).isZero();
    }

    @Test
    void plannedActivitiesFilterByRangeOwnerAndExcludeCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx a = manager(admin);
        ManagerCtx b = manager(admin);
        UUID inRange = newTask(admin, a.signed().userId(), today().plusDays(2));
        UUID outOfRange = newTask(admin, a.signed().userId(), today().plusDays(20));
        UUID cancelled = newTask(admin, a.signed().userId(), today().plusDays(3));
        act(admin, cancelled, "cancel", Map.of("reason", "not needed"));
        UUID others = newTask(admin, b.signed().userId(), today().plusDays(2));

        var mine = activities.plannedBetween(today(), today().plusDays(5), a.signed().userId());
        assertThat(mine).extracting(TaskActivities.PlannedActivity::id).containsExactly(inRange);
        var first = mine.get(0);
        assertThat(first.kind()).isEqualTo("SCHOOL_VISIT");
        assertThat(first.ownerUserId()).isEqualTo(a.signed().userId());
        assertThat(first.date()).isEqualTo(today().plusDays(2));
        assertThat(first.status()).isEqualTo("ASSIGNED");

        var everyone = activities.plannedBetween(today(), today().plusDays(5), null);
        assertThat(everyone).extracting(TaskActivities.PlannedActivity::id).contains(inRange, others).doesNotContain(outOfRange, cancelled);
    }

    // ------------------------------------------------------------------ amendment for spec 026: by source key

    @Test
    void reschedulesCompletesAndCancelsTheOpenTaskBySourceKey() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        String key = "plan-" + UUID.randomUUID();
        var task = creation.createFollowUp(request(manager.signed().userId(), key, today().plusDays(2)));

        assertThat(creation.reschedule("SCHOOL_BILLING", key, today().plusDays(9), null, null)).isTrue();
        assertThat(jdbc.queryForObject("select due_on from task where id = ?", java.sql.Date.class, task.id()).toLocalDate())
                .isEqualTo(today().plusDays(9));
        assertThat(jdbc.queryForObject("select count(*) from task_history where task_id = ? and event = 'DUE_CHANGED'", Integer.class, task.id()))
                .isEqualTo(1);

        assertThat(creation.complete("SCHOOL_BILLING", key, "Observation submitted by the Manager", manager.signed().userId())).isTrue();
        assertThat(statusOf(admin, task.id())).isEqualTo("COMPLETED");
        // a completed task is no longer open for these operations
        assertThat(creation.reschedule("SCHOOL_BILLING", key, today().plusDays(10), null, null)).isFalse();
        assertThat(creation.cancel("SCHOOL_BILLING", key, "no longer needed")).isFalse();

        String other = "plan-" + UUID.randomUUID();
        var second = creation.createFollowUp(request(manager.signed().userId(), other, today().plusDays(2)));
        assertThat(creation.cancel("SCHOOL_BILLING", other, "Teacher exited")).isTrue();
        assertThat(statusOf(admin, second.id())).isEqualTo("CANCELLED");
    }

    @Test
    void anUnknownKeyChangesNothing() {
        assertThat(creation.reschedule("SCHOOL_BILLING", "missing-" + UUID.randomUUID(), today(), null, null)).isFalse();
        assertThat(creation.complete("SCHOOL_BILLING", "missing-" + UUID.randomUUID(), "A long enough note", null)).isFalse();
        assertThat(creation.cancel("SCHOOL_BILLING", "missing-" + UUID.randomUUID(), "reason")).isFalse();
        assertThat(creation.reassign("SCHOOL_BILLING", "missing-" + UUID.randomUUID(), UUID.randomUUID())).isFalse();
    }

    @Test
    void reassignsToAnotherActiveManagerAndTellsThem() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx first = manager(admin);
        ManagerCtx second = manager(admin);
        String key = "plan-" + UUID.randomUUID();
        var task = creation.createFollowUp(request(first.signed().userId(), key, today().plusDays(2)));
        startTask(first.token(), task.id());

        assertThat(creation.reassign("SCHOOL_BILLING", key, second.signed().userId())).isTrue();
        assertThat(jdbc.queryForObject("select assignee_user_id from task where id = ?", UUID.class, task.id()))
                .isEqualTo(second.signed().userId());
        assertThat(statusOf(admin, task.id())).isEqualTo("ASSIGNED");
        assertThat(notificationCountForTask(second.signed().userId(), "TASK_ASSIGNED", task.id())).isEqualTo(1);

        UUID teacher = signInAs(Role.TEACHER).userId();
        assertThatThrownBy(() -> creation.reassign("SCHOOL_BILLING", key, teacher)).isInstanceOf(InvalidInputException.class);
    }
}
