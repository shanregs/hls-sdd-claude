package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.PerformanceTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 026 US3: Principal feedback is recorded apart from ratings, can open an issue or a plan, and can be withdrawn. */
class FeedbackApiTest extends PerformanceTestBase {

    private Map<String, Object> feedbackBody(UUID teacher, String category, boolean actionRequired) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherId", teacher);
        body.put("principalName", "Mrs Rao");
        body.put("givenOn", today().toString());
        body.put("category", category);
        body.put("comments", "Parents have complained about the homework being left unchecked");
        body.put("actionRequired", actionRequired);
        return body;
    }

    private Resp record(String token, UUID teacher, String category, boolean actionRequired) {
        return post(PERF + "/feedback", token, feedbackBody(teacher, category, actionRequired));
    }

    private UUID recordId(String token, UUID teacher, String category, boolean actionRequired) {
        Resp resp = record(token, teacher, category, actionRequired);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) resp.map().get("id"));
    }

    @Test
    void feedbackIsRecordedSeparatelyFromAnyObservation() {
        Setup s = setup();
        Resp resp = record(s.managerToken(), s.teacherId(), "TEACHING", false);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map().get("status")).isEqualTo("RECORDED");
        assertThat(resp.map().get("categoryLabel")).isEqualTo("Teaching issue");
        assertThat(resp.map().get("observationRootId")).isNull();
        assertThat(resp.map().get("offerIssue")).isEqualTo(false);
        assertThat(jdbc.queryForObject("select count(*) from observation where teacher_id = ?", Integer.class, s.teacherId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where teacher_id = ?", Integer.class, s.teacherId())).isZero();
        assertChangeRecorded(s.admin(), "PRINCIPAL_FEEDBACK", resp.map().get("id"), "created");
        assertThat(content(get(PERF + "/feedback?teacherId=" + s.teacherId(), s.director()))).hasSize(1);
    }

    @Test
    void theInputIsValidated() {
        Setup s = setup();
        Map<String, Object> shortComments = feedbackBody(s.teacherId(), "TEACHING", false);
        shortComments.put("comments", "too short");
        assertThat(post(PERF + "/feedback", s.managerToken(), shortComments).status()).isEqualTo(400);
        Map<String, Object> noCategory = feedbackBody(s.teacherId(), "TEACHING", false);
        noCategory.put("category", null);
        assertThat(post(PERF + "/feedback", s.managerToken(), noCategory).status()).isEqualTo(400);
        Map<String, Object> future = feedbackBody(s.teacherId(), "TEACHING", false);
        future.put("givenOn", today().plusDays(2).toString());
        assertThat(post(PERF + "/feedback", s.managerToken(), future).status()).isEqualTo(400);
        Map<String, Object> noPrincipal = feedbackBody(s.teacherId(), "TEACHING", false);
        noPrincipal.put("principalName", " ");
        assertThat(post(PERF + "/feedback", s.managerToken(), noPrincipal).status()).isEqualTo(400);
        Map<String, Object> tooLong = feedbackBody(s.teacherId(), "TEACHING", false);
        tooLong.put("comments", "x".repeat(2001));
        assertThat(post(PERF + "/feedback", s.managerToken(), tooLong).status()).isEqualTo(400);
    }

    @Test
    void feedbackCanBeLinkedToAnObservationOfTheSameTeacherOnly() {
        Setup s = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        Map<String, Object> linked = feedbackBody(s.teacherId(), "GOOD_PERFORMANCE", false);
        linked.put("observationId", observation.get("id"));
        Resp ok = post(PERF + "/feedback", s.managerToken(), linked);
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);
        assertThat(ok.map().get("observationRootId")).isEqualTo(observation.get("rootId"));

        UUID other = anotherTeacher(s);
        Map<String, Object> wrong = feedbackBody(other, "GOOD_PERFORMANCE", false);
        wrong.put("observationId", observation.get("id"));
        assertThat(post(PERF + "/feedback", s.managerToken(), wrong).status()).isEqualTo(400);
    }

    @Test
    void recordingCompletesTheManagersOpenPrincipalFeedbackTask() {
        Setup s = setup();
        Map<String, Object> task = taskBody(s.managerUserId(), today().plusDays(2));
        task.put("type", "PRINCIPAL_FEEDBACK");
        task.put("linkedType", "TEACHER");
        task.put("linkedId", s.teacherId());
        Resp created = post("/api/v1/tasks", s.director(), task);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID taskId = UUID.fromString((String) ((Map<?, ?>) created.map().get("task")).get("id"));

        recordId(s.admin(), s.teacherId(), "ATTENDANCE", false);
        assertThat(statusOf(s.director(), taskId)).as("an Admin is not the assignee").isEqualTo("ASSIGNED");

        recordId(s.managerToken(), s.teacherId(), "ATTENDANCE", false);
        assertThat(statusOf(s.director(), taskId)).isEqualTo("COMPLETED");
    }

    @Test
    void actionRequiredOffersAnIssueAndTheSecondFeedbackJoinsTheFirst() {
        Setup s = setup();
        Resp first = record(s.managerToken(), s.teacherId(), "BEHAVIOUR", true);
        assertThat(first.map().get("offerIssue")).isEqualTo(true);
        UUID firstId = UUID.fromString((String) first.map().get("id"));

        Resp issue = post(PERF + "/feedback/" + firstId + "/issue", s.managerToken(), Map.of());
        assertThat(issue.status()).as(issue.body()).isEqualTo(200);
        assertThat(issue.map().get("status")).isEqualTo("OPEN");
        assertThat(issue.map().get("sourceType")).isEqualTo("FEEDBACK");
        assertThat(issue.map().get("category")).isEqualTo("BEHAVIOUR");
        assertThat(get(PERF + "/feedback/" + firstId, s.managerToken()).map().get("offerIssue")).isEqualTo(false);

        UUID secondId = recordId(s.managerToken(), s.teacherId(), "COMMUNICATION", true);
        Resp joined = post(PERF + "/feedback/" + secondId + "/issue", s.managerToken(), Map.of());
        assertThat(joined.map().get("id")).isEqualTo(issue.map().get("id"));
        assertThat((java.util.List<?>) joined.map().get("links")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where teacher_id = ?", Integer.class, s.teacherId())).isEqualTo(1);
    }

    @Test
    void actionRequiredCanPlanAnObservationCarryingTheFeedback() {
        Setup s = setup();
        UUID id = recordId(s.managerToken(), s.teacherId(), "TEACHING", true);
        Resp plan = post(PERF + "/feedback/" + id + "/plan", s.managerToken(), Map.of());
        assertThat(plan.status()).as(plan.body()).isEqualTo(201);
        assertThat(plan.map().get("reason")).isEqualTo("SCHOOL_FEEDBACK");
        assertThat(plan.map().get("plannedOn")).isEqualTo(today().toString());
        assertThat(plan.map().get("sourceFeedbackRootId")).isEqualTo(id.toString());
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        assertThat(notificationCount(s.managerUserId(), "OBSERVATION_PLANNED")).isEqualTo(1);
    }

    @Test
    void feedbackRecordedInErrorIsWithdrawnAndKept() {
        Setup s = setup();
        Setup other = setup();
        UUID id = recordId(s.managerToken(), s.teacherId(), "OTHER", true);
        Object version = get(PERF + "/feedback/" + id, s.managerToken()).map().get("version");

        assertThat(post(PERF + "/feedback/" + id + "/withdraw", other.managerToken(), Map.of("reason", "Wrong Teacher", "version", version)).status())
                .isEqualTo(404);
        assertThat(post(PERF + "/feedback/" + id + "/withdraw", s.managerToken(), Map.of("version", version)).status()).isEqualTo(400);
        Resp done = post(PERF + "/feedback/" + id + "/withdraw", s.managerToken(), Map.of("reason", "Wrong Teacher", "version", version));
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map().get("status")).isEqualTo("WITHDRAWN");
        assertThat(done.map().get("offerIssue")).isEqualTo(false);
        assertThat(post(PERF + "/feedback/" + id + "/withdraw", s.admin(), Map.of("reason", "again", "version", done.map().get("version"))).status())
                .isEqualTo(409);
        assertThat(post(PERF + "/feedback/" + id + "/issue", s.managerToken(), Map.of()).status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from principal_feedback where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from performance_history where record_id = ? and event = 'WITHDRAWN'", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void aCorrectionIsANewVersionOfTheFeedback() {
        Setup s = setup();
        UUID id = recordId(s.managerToken(), s.teacherId(), "TEACHING", false);
        Object version = get(PERF + "/feedback/" + id, s.managerToken()).map().get("version");
        Map<String, Object> body = new HashMap<>();
        body.put("category", "COMMUNICATION");
        body.put("comments", "The Principal meant the communication with parents");
        body.put("actionRequired", false);
        body.put("reason", "Wrong category");
        body.put("version", version);
        Resp resp = post(PERF + "/feedback/" + id + "/correct", s.managerToken(), body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("versionNo")).isEqualTo(2);
        assertThat(resp.map().get("category")).isEqualTo("COMMUNICATION");
        assertThat(jdbc.queryForObject("select status from principal_feedback where id = ?", String.class, id)).isEqualTo("SUPERSEDED");
        assertThat(content(get(PERF + "/feedback?teacherId=" + s.teacherId(), s.director()))).hasSize(1);
        assertThat(get(PERF + "/feedback/" + resp.map().get("id") + "?versions=true", s.director()).body()).contains("\"versionNo\":1");
    }

    @Test
    void scopeAndRolesAreEnforced() {
        Setup s = setup();
        Setup other = setup();
        UUID id = recordId(s.managerToken(), s.teacherId(), "TEACHING", false);
        assertThat(record(other.managerToken(), s.teacherId(), "TEACHING", false).status()).isEqualTo(404);
        assertThat(get(PERF + "/feedback/" + id, other.managerToken()).status()).isEqualTo(404);
        String teacher = signInAs(com.hls.identity.user.Role.TEACHER).token();
        assertThat(get(PERF + "/feedback", teacher).status()).isEqualTo(403);
        assertThat(record(teacher, s.teacherId(), "TEACHING", false).status()).isEqualTo(403);
    }
}
