package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.performance.internal.ManagerReconcileJob;
import com.hls.support.PerformanceTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 026 FR-017 and FR-024: tasks follow the Teacher to a new Manager, and an exited Teacher's open work is closed. */
class ManagerReconcileJobTest extends PerformanceTestBase {

    @Autowired
    ManagerReconcileJob job;

    @Test
    void theOpenTaskFollowsTheTeacherToTheNewSchoolsManager() {
        Setup s = setup();
        plan(s.director(), s.teacherId(), today().plusDays(5), "MANAGEMENT_PLAN");
        assertThat(jdbc.queryForObject("select assignee_user_id from task where type = 'OBSERVATION' and linked_id = ?", UUID.class, s.teacherId()))
                .isEqualTo(s.managerUserId());

        // the Teacher moves to a School run by another Manager
        UUID[] where = schoolInNewZone(s.admin());
        ManagerCtx second = newManager(s.admin(), where[0]);
        assignSchoolManager(s.admin(), where[2], second.managerId());
        placeTeacher(s.admin(), s.teacherId(), where[2]);

        job.runNow();
        assertThat(jdbc.queryForObject("select assignee_user_id from task where type = 'OBSERVATION' and linked_id = ?", UUID.class, s.teacherId()))
                .isEqualTo(second.signed().userId());
        assertThat(jdbc.queryForObject("select school_id from observation_plan where teacher_id = ? and status = 'OPEN'", UUID.class, s.teacherId()))
                .isEqualTo(where[2]);
        assertThat(notificationCount(second.signed().userId(), "TASK_ASSIGNED")).isEqualTo(1);

        // the second run changes nothing
        job.runNow();
        assertThat(notificationCount(second.signed().userId(), "TASK_ASSIGNED")).isEqualTo(1);
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
    }

    @Test
    void anExitedTeachersIssueIsResolvedAsExitedAndTheirPlanIsCancelled() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        plan(s.director(), s.teacherId(), today().plusDays(5), "MANAGEMENT_PLAN");
        assertThat(issueStatusOf(s.teacherId())).isEqualTo("OPEN");

        assertThat(post("/api/v1/teachers/" + s.teacherId() + "/status", s.admin(), Map.of("status", "EXITED", "effectiveOn", today().toString())).status())
                .isEqualTo(200);
        ManagerReconcileJob.Result first = job.runNow();
        assertThat(first.issuesClosed()).isGreaterThanOrEqualTo(1);

        Map<String, Object> issue = jdbc.queryForMap("select status, resolution from performance_issue where teacher_id = ?", s.teacherId());
        assertThat(issue.get("status")).isEqualTo("RESOLVED");
        assertThat(issue.get("resolution")).isEqualTo("EXITED");
        assertThat(jdbc.queryForObject("select status from observation_plan where teacher_id = ?", String.class, s.teacherId())).isEqualTo("CANCELLED");
        assertThat(openObservationTasks(s.teacherId())).isZero();
        assertThat(job.runNow().issuesClosed()).isZero();
    }
}
