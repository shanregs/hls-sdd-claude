package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.PerformanceTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 026 US4: a result that needs improvement opens an issue; correction rounds repeat until improvement or referral. */
class IssueLoopTest extends PerformanceTestBase {

    private Resp correct(String token, UUID issue, int days) {
        Object version = get(PERF + "/issues/" + issue, token).map().get("version");
        Map<String, Object> body = new HashMap<>();
        body.put("discussed", "Talked through the lesson plan and the homework checking");
        body.put("requiredChange", "Check homework daily and plan lessons in steps");
        body.put("followUpOn", today().plusDays(days).toString());
        body.put("version", version);
        return post(PERF + "/issues/" + issue + "/corrective-actions", token, body);
    }

    private UUID issueOf(Setup s) {
        return openIssueOf(s.teacherId());
    }

    @Test
    void aNeedsImprovementObservationOpensAnIssueInTheSameSave() {
        Setup s = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        assertThat(observation.get("issueId")).isNotNull();
        assertThat(observation.get("issueStatus")).isEqualTo("OPEN");
        assertThat(observation.get("nextStep")).isEqualTo("CORRECTIVE_ACTION");

        Resp issue = get(PERF + "/issues/" + observation.get("issueId"), s.managerToken());
        assertThat(issue.status()).as(issue.body()).isEqualTo(200);
        assertThat(issue.map().get("status")).isEqualTo("OPEN");
        assertThat(issue.map().get("sourceType")).isEqualTo("OBSERVATION");
        assertThat(issue.map().get("category")).isEqualTo("NEEDS_IMPROVEMENT");
        assertThat(issue.map().get("managerUserId")).isEqualTo(s.managerUserId().toString());
        assertThat((List<?>) issue.map().get("links")).hasSize(1);
        assertChangeRecorded(s.admin(), "PERFORMANCE_ISSUE", observation.get("issueId"), "issue_opened");
    }

    @Test
    void aGoodObservationOpensNothingAndAnotherNeedsImprovementJoinsTheOpenIssue() {
        Setup s = setup();
        assertThat(observe(s.managerToken(), s.teacherId(), "GOOD", 5).get("issueId")).isNull();
        Map<String, Object> first = observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        Map<String, Object> second = observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 2);
        assertThat(second.get("issueId")).isEqualTo(first.get("issueId"));
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where teacher_id = ?", Integer.class, s.teacherId())).isEqualTo(1);
        Resp issue = get(PERF + "/issues/" + first.get("issueId"), s.managerToken());
        assertThat((List<?>) issue.map().get("links")).hasSize(2);
    }

    @Test
    void aCorrectiveActionPlansExactlyOneFollowUpTask() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);

        Resp resp = correct(s.managerToken(), issue, 14);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("status")).isEqualTo("UNDER_CORRECTION");
        assertThat(resp.map().get("correctionRounds")).isEqualTo(1);
        assertThat(resp.map().get("followUpOn")).isEqualTo(today().plusDays(14).toString());
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select due_on from task where type = 'OBSERVATION' and linked_id = ? and status = 'ASSIGNED'",
                        java.sql.Date.class, s.teacherId()).toLocalDate())
                .isEqualTo(today().plusDays(14));
        assertThat(jdbc.queryForObject("select reason from observation_plan where teacher_id = ? and status = 'OPEN'", String.class, s.teacherId()))
                .isEqualTo("FOLLOW_UP");
        assertThat(notificationCount(s.managerUserId(), "OBSERVATION_PLANNED")).isEqualTo(1);
    }

    @Test
    void theCorrectiveActionIsValidated() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        assertThat(correct(s.managerToken(), issue, 0).status()).isEqualTo(400);
        assertThat(correct(s.managerToken(), issue, 61).status()).isEqualTo(400);
        assertThat(correct(s.managerToken(), issue, 60).status()).isEqualTo(200);

        UUID second = issueOfNewTeacher(s);
        Object version = get(PERF + "/issues/" + second, s.managerToken()).map().get("version");
        Map<String, Object> noText = new HashMap<>();
        noText.put("discussed", "");
        noText.put("requiredChange", "Check homework daily");
        noText.put("followUpOn", today().plusDays(5).toString());
        noText.put("version", version);
        assertThat(post(PERF + "/issues/" + second + "/corrective-actions", s.managerToken(), noText).status()).isEqualTo(400);

        // a stale version is refused
        Map<String, Object> stale = new HashMap<>(noText);
        stale.put("discussed", "Talked it through at length");
        stale.put("version", 999);
        assertThat(post(PERF + "/issues/" + second + "/corrective-actions", s.managerToken(), stale).status()).isEqualTo(409);
    }

    private UUID issueOfNewTeacher(Setup s) {
        UUID teacher = anotherTeacher(s);
        observe(s.managerToken(), teacher, "NEEDS_IMPROVEMENT", 3);
        return openIssueOf(teacher);
    }

    @Test
    void aGoodFollowUpResolvesTheIssueAsImproved() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        correct(s.managerToken(), issue, 14);

        Map<String, Object> followUp = observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        assertThat(followUp.get("followupOfIssueId")).isEqualTo(issue.toString());
        Resp resolved = get(PERF + "/issues/" + issue, s.managerToken());
        assertThat(resolved.map().get("status")).isEqualTo("RESOLVED");
        assertThat(resolved.map().get("resolution")).isEqualTo("IMPROVED");
        assertThat((List<?>) resolved.map().get("links")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) resolved.map().get("correctiveActions")).get(0)).get("followUpObservationRootId")).isNotNull();
        // the follow-up task was completed by the Manager's observation
        assertThat(openObservationTasks(s.teacherId())).isZero();
        assertThat(jdbc.queryForObject("select status from observation_plan where teacher_id = ?", String.class, s.teacherId())).isEqualTo("DONE");
        // a new Needs improvement afterwards opens a fresh issue
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where teacher_id = ?", Integer.class, s.teacherId())).isEqualTo(2);
    }

    @Test
    void thereIsNoLimitOnCorrectionRounds() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        for (int round = 1; round <= 4; round++) {
            Resp resp = correct(s.managerToken(), issue, 7);
            assertThat(resp.status()).as("round " + round + ": " + resp.body()).isEqualTo(200);
            assertThat(resp.map().get("correctionRounds")).isEqualTo(round);
            Map<String, Object> followUp = observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
            assertThat(followUp.get("followupOfIssueId")).isEqualTo(issue.toString());
            assertThat(followUp.get("nextStep")).isEqualTo("CORRECTIVE_ACTION");
            Resp state = get(PERF + "/issues/" + issue, s.managerToken());
            assertThat(state.map().get("status")).isEqualTo("UNDER_CORRECTION");
            assertThat(state.map().get("needsCorrectiveAction")).isEqualTo(true);
        }
        assertThat(jdbc.queryForObject("select count(*) from corrective_action where issue_id = ?", Integer.class, issue)).isEqualTo(4);
        // exactly one follow-up task per corrective action was ever created, and only the last is still open
        assertThat(openObservationTasks(s.teacherId())).isZero();
    }

    @Test
    void theDirectorCancelsAnIssueWithAReasonAndItsFollowUpIsCancelled() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        correct(s.managerToken(), issue, 10);
        Object version = get(PERF + "/issues/" + issue, s.director()).map().get("version");

        assertThat(post(PERF + "/issues/" + issue + "/close", s.managerToken(), Map.of("outcome", "CANCELLED", "reason", "Raised in error", "version", version))
                        .status())
                .isEqualTo(403);
        assertThat(post(PERF + "/issues/" + issue + "/close", s.director(), Map.of("outcome", "CANCELLED", "version", version)).status()).isEqualTo(400);
        assertThat(post(PERF + "/issues/" + issue + "/close", s.director(), Map.of("outcome", "CONTINUE", "reason", "Keep them", "version", version)).status())
                .as("only a referred issue can continue")
                .isEqualTo(409);
        Resp done = post(PERF + "/issues/" + issue + "/close", s.director(), Map.of("outcome", "CANCELLED", "reason", "Raised in error", "version", version));
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map().get("status")).isEqualTo("CANCELLED");
        assertThat(done.map().get("closingReason")).isEqualTo("Raised in error");
        assertThat(openObservationTasks(s.teacherId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from performance_issue where id = ?", Integer.class, issue)).isEqualTo(1);
        // a cancelled issue takes no more corrective action
        assertThat(correct(s.managerToken(), issue, 5).status()).isEqualTo(409);
    }

    @Test
    void escalatingIsRecordedInTheHistoryWithoutChangingTheStatus() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        assertThat(post(PERF + "/issues/" + issue + "/escalate", s.managerToken(), Map.of("note", "")).status()).isEqualTo(400);
        Resp resp = post(PERF + "/issues/" + issue + "/escalate", s.managerToken(), Map.of("note", "The Teacher has not responded to feedback"));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("status")).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select count(*) from performance_history where record_id = ? and event = 'ESCALATED'", Integer.class, issue))
                .isEqualTo(1);
    }

    @Test
    void anIssueIsHiddenFromOtherManagersAndFromTeachers() {
        Setup s = setup();
        Setup other = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = issueOf(s);
        assertThat(get(PERF + "/issues/" + issue, other.managerToken()).status()).isEqualTo(404);
        assertThat(correct(other.managerToken(), issue, 5).status()).isEqualTo(404);
        assertThat(get(PERF + "/issues/" + issue, signInAs(com.hls.identity.user.Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(PERF + "/issues?view=open", other.managerToken()).body()).doesNotContain(issue.toString());
        assertThat(get(PERF + "/issues?view=open", s.director()).body()).contains(issue.toString());
    }
}
