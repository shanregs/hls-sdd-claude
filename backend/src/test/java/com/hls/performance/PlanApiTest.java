package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.PerformanceTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 026 US2: planned observations become one Observation task and one notification for the Teacher's Manager. */
class PlanApiTest extends PerformanceTestBase {

    @Test
    void planningCreatesOneTaskAndOneNotificationHoweverOftenItIsRepeated() {
        Setup s = setup();
        Resp first = plan(s.director(), s.teacherId(), today().plusDays(3), "MANAGEMENT_PLAN");
        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(first.map().get("created")).isEqualTo(true);
        assertThat(first.map().get("reasonLabel")).isEqualTo("Management plan");
        assertThat(first.map().get("managerUserId")).isEqualTo(s.managerUserId().toString());

        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        Map<String, Object> task = jdbc.queryForMap(
                "select assignee_user_id, due_on, status, source from task where type = 'OBSERVATION' and linked_id = ?", s.teacherId());
        assertThat(task.get("assignee_user_id")).isEqualTo(s.managerUserId());
        assertThat(task.get("due_on").toString()).isEqualTo(today().plusDays(3).toString());
        assertThat(task.get("source")).isEqualTo("OBSERVATION_PLAN");
        assertThat(notificationCount(s.managerUserId(), "OBSERVATION_PLANNED")).isEqualTo(1);

        // again: the open plan is changed, nothing new is created
        Resp again = plan(s.admin(), s.teacherId(), today().plusDays(6), "SCHOOL_FEEDBACK");
        assertThat(again.status()).as(again.body()).isEqualTo(200);
        assertThat(again.map().get("created")).isEqualTo(false);
        assertThat(again.map().get("id")).isEqualTo(first.map().get("id"));
        assertThat(again.map().get("plannedOn")).isEqualTo(today().plusDays(6).toString());
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select due_on from task where type = 'OBSERVATION' and linked_id = ?", java.sql.Date.class, s.teacherId())
                        .toLocalDate())
                .isEqualTo(today().plusDays(6));
        assertThat(notificationCount(s.managerUserId(), "OBSERVATION_PLANNED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from observation_plan where teacher_id = ?", Integer.class, s.teacherId())).isEqualTo(1);
    }

    @Test
    void aManagerPlansForTheirOwnTeacherAndNoOneElses() {
        Setup s = setup();
        Setup other = setup();
        assertThat(plan(s.managerToken(), s.teacherId(), today().plusDays(1), "FOLLOW_UP").status()).isEqualTo(201);
        assertThat(plan(other.managerToken(), anotherTeacher(s), today().plusDays(1), "FOLLOW_UP").status()).isEqualTo(404);
        Resp list = get(PERF + "/plans", s.managerToken());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(s.teacherId().toString());
        assertThat(get(PERF + "/plans", other.managerToken()).body()).doesNotContain(s.teacherId().toString());
    }

    @Test
    void aPlanIsRefusedWithTheReasonWhenTheTeacherCannotBeObserved() {
        Setup s = setup();
        assertThat(plan(s.director(), s.teacherId(), today().minusDays(1), "MANAGEMENT_PLAN").status()).isEqualTo(400);
        Map<String, Object> noText = new HashMap<>();
        noText.put("teacherId", s.teacherId());
        noText.put("plannedOn", today().plusDays(1).toString());
        noText.put("reason", "OTHER");
        assertThat(post(PERF + "/plans", s.director(), noText).status()).isEqualTo(400);
        noText.put("reasonText", "Parents asked for it");
        assertThat(post(PERF + "/plans", s.director(), noText).status()).isEqualTo(201);

        UUID onLeave = anotherTeacher(s);
        assertThat(post("/api/v1/teachers/" + onLeave + "/status", s.admin(), Map.of("status", "ON_LEAVE", "effectiveOn", today().toString())).status())
                .isEqualTo(200);
        Resp refused = plan(s.director(), onLeave, today().plusDays(1), "MANAGEMENT_PLAN");
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).containsIgnoringCase("on leave");

        UUID unplaced = teacher(s.admin());
        assertThat(plan(s.director(), unplaced, today().plusDays(1), "MANAGEMENT_PLAN").status()).isEqualTo(400);

        // a School with no Manager has nobody to give the observation to
        UUID[] where = schoolInNewZone(s.admin());
        UUID orphan = teacher(s.admin());
        placeTeacher(s.admin(), orphan, where[2]);
        Resp noManager = plan(s.director(), orphan, today().plusDays(1), "MANAGEMENT_PLAN");
        assertThat(noManager.status()).isEqualTo(400);
        assertThat(noManager.body()).containsIgnoringCase("Manager");
        assertThat(jdbc.queryForObject("select count(*) from observation_plan where teacher_id = ?", Integer.class, orphan)).isZero();
    }

    @Test
    void submittingByTheManagerCompletesTheTaskButAnAdminObservationDoesNot() {
        Setup s = setup();
        plan(s.director(), s.teacherId(), today().plusDays(2), "MANAGEMENT_PLAN");

        // Admin observes: no task is completed
        observe(s.admin(), s.teacherId(), "GOOD", 4);
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from observation_plan where teacher_id = ?", String.class, s.teacherId())).isEqualTo("OPEN");

        // the Manager observes: the task is completed with the observation as its note
        Map<String, Object> done = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        assertThat(openObservationTasks(s.teacherId())).isZero();
        Map<String, Object> task = jdbc.queryForMap("select status, completion_note from task where type = 'OBSERVATION' and linked_id = ?", s.teacherId());
        assertThat(task.get("status")).isEqualTo("COMPLETED");
        assertThat(task.get("completion_note").toString()).contains("Observation submitted");
        Map<String, Object> plan = jdbc.queryForMap("select status, completed_by_observation_root_id from observation_plan where teacher_id = ?", s.teacherId());
        assertThat(plan.get("status")).isEqualTo("DONE");
        assertThat(plan.get("completed_by_observation_root_id").toString()).isEqualTo(done.get("rootId"));

        // and a new plan can be made afterwards
        assertThat(plan(s.director(), s.teacherId(), today().plusDays(30), "MANAGEMENT_PLAN").status()).isEqualTo(201);
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
    }

    @Test
    void aPlanCanBeChangedAndCancelled() {
        Setup s = setup();
        UUID plan = UUID.fromString((String) plan(s.director(), s.teacherId(), today().plusDays(4), "MANAGEMENT_PLAN").map().get("id"));
        Object version = get(PERF + "/plans/" + plan, s.director()).map().get("version");
        Resp changed = put(PERF + "/plans/" + plan, s.director(), Map.of("plannedOn", today().plusDays(9).toString(), "version", version));
        assertThat(changed.status()).as(changed.body()).isEqualTo(200);
        assertThat(changed.map().get("plannedOn")).isEqualTo(today().plusDays(9).toString());

        assertThat(delete(PERF + "/plans/" + plan + "?reason=No+longer+needed", s.managerToken()).status()).isEqualTo(204);
        assertThat(openObservationTasks(s.teacherId())).isZero();
        assertThat(jdbc.queryForObject("select status from task where type = 'OBSERVATION' and linked_id = ?", String.class, s.teacherId()))
                .isEqualTo("CANCELLED");
        assertThat(delete(PERF + "/plans/" + plan + "?reason=again", s.managerToken()).status()).isEqualTo(409);
    }

    @Test
    void theOverdueListShowsDaysOverdueAndTheLastResult() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        plan(s.director(), s.teacherId(), today().plusDays(1), "FOLLOW_UP");
        jdbc.update("update observation_plan set planned_on = ? where teacher_id = ? and status = 'OPEN'", today().minusDays(3), s.teacherId());
        Resp overdue = get(PERF + "/plans?view=overdue&teacherId=" + s.teacherId(), s.director());
        assertThat(overdue.status()).isEqualTo(200);
        assertThat(overdue.body()).contains("\"overdue\":true").contains("\"daysOverdue\":3").contains("\"lastResult\":\"NEEDS_IMPROVEMENT\"");
        assertThat(get(PERF + "/plans?view=planned&teacherId=" + s.teacherId(), s.director()).body()).contains(s.teacherId().toString());
    }
}
