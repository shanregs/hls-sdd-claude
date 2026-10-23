package com.hls.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.performance.api.PerformanceReferrals;
import com.hls.school.api.ConflictException;
import com.hls.support.PerformanceTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 026 US5: an Unsuitable Teacher, or one whose correction failed, is referred; spec 027 reads the referral. */
class ReferralTest extends PerformanceTestBase {

    @Autowired
    PerformanceReferrals referrals;

    private Resp refer(String token, UUID issue, String recommendation, String reason) {
        Object version = get(PERF + "/issues/" + issue, token).map().get("version");
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("recommendation", recommendation);
        body.put("reason", reason);
        body.put("version", version);
        return post(PERF + "/issues/" + issue + "/referral", token, body);
    }

    @Test
    void anUnsuitableObservationOpensAnIssueAndAsksForAReferral() {
        Setup s = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "UNSUITABLE", 1);
        assertThat(observation.get("issueStatus")).isEqualTo("OPEN");
        assertThat(observation.get("nextStep")).isEqualTo("REFERRAL");
        UUID issue = openIssueOf(s.teacherId());

        assertThat(refer(s.managerToken(), issue, "REPLACE", "Too short").status()).isEqualTo(400);
        assertThat(refer(s.managerToken(), issue, null, "The Teacher cannot manage the class and has not improved").status()).isEqualTo(400);
        Resp resp = refer(s.managerToken(), issue, "REPLACE", "The Teacher cannot manage the class and has not improved");
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("status")).isEqualTo("REFERRED");
        assertThat(((Map<?, ?>) resp.map().get("referral")).get("recommendation")).isEqualTo("REPLACE");
        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'REFERRAL_RECORDED' and link like ?", Integer.class,
                        "%" + issue))
                .isGreaterThanOrEqualTo(1);
        assertChangeRecorded(s.admin(), "PERFORMANCE_ISSUE", issue, "status");

        // the Teacher stays where they are: a referral changes nothing by itself
        assertThat(get("/api/v1/teachers/" + s.teacherId(), s.admin()).body()).contains(s.schoolId().toString());
        assertThat(refer(s.managerToken(), issue, "TRANSFER", "A second referral is not possible while one is open").status()).isEqualTo(409);
    }

    @Test
    void failedCorrectionCanBeReferredAndAPendingFollowUpIsCancelled() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "NEEDS_IMPROVEMENT", 3);
        UUID issue = openIssueOf(s.teacherId());
        Object version = get(PERF + "/issues/" + issue, s.managerToken()).map().get("version");
        post(PERF + "/issues/" + issue + "/corrective-actions", s.managerToken(), Map.of("discussed", "Discussed at length", "requiredChange",
                "Check homework daily", "followUpOn", today().plusDays(10).toString(), "version", version));
        assertThat(openObservationTasks(s.teacherId())).isEqualTo(1);

        Resp resp = refer(s.managerToken(), issue, "TRANSFER", "Two rounds of correction and the Teacher is still struggling");
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(openObservationTasks(s.teacherId())).as("the follow-up no longer makes sense").isZero();
    }

    @Test
    void module027ReadsTheReferralWithItsSupportingRecordsAndReportsTheDecision() {
        Setup s = setup();
        Map<String, Object> observation = observe(s.managerToken(), s.teacherId(), "UNSUITABLE", 1);
        UUID issue = openIssueOf(s.teacherId());
        refer(s.managerToken(), issue, "REPLACE", "The Teacher cannot manage the class and has not improved");

        List<PerformanceReferrals.ReferralInfo> found = referrals.referredFor(s.teacherId());
        assertThat(found).hasSize(1);
        PerformanceReferrals.ReferralInfo info = found.get(0);
        assertThat(info.issueId()).isEqualTo(issue);
        assertThat(info.recommendation()).isEqualTo("REPLACE");
        assertThat(info.reason()).contains("cannot manage the class");
        assertThat(info.schoolId()).isEqualTo(s.schoolId());
        assertThat(info.supporting()).extracting(PerformanceReferrals.LinkedRecord::type).containsExactly("OBSERVATION");
        assertThat(info.supporting().get(0).id().toString()).isEqualTo(observation.get("id"));
        assertThat(referrals.referral(issue)).isPresent();

        referrals.reportDecision(issue, "REPLACED", UUID.randomUUID());
        Map<String, Object> closed = jdbc.queryForMap("select status, resolution from performance_issue where id = ?", issue);
        assertThat(closed.get("status")).isEqualTo("RESOLVED");
        assertThat(closed.get("resolution")).isEqualTo("REPLACED");
        assertThat(referrals.referredFor(s.teacherId())).isEmpty();
        assertThatThrownBy(() -> referrals.reportDecision(issue, "REPLACED", UUID.randomUUID())).isInstanceOf(ConflictException.class);
        // still placed: only 027 changes placements
        assertThat(get("/api/v1/teachers/" + s.teacherId(), s.admin()).body()).contains(s.schoolId().toString());
    }

    @Test
    void theDirectorMayDecideTheTeacherShouldContinue() {
        Setup s = setup();
        observe(s.managerToken(), s.teacherId(), "UNSUITABLE", 1);
        UUID issue = openIssueOf(s.teacherId());
        refer(s.managerToken(), issue, "TRANSFER", "The Teacher would do better at another School");
        Object version = get(PERF + "/issues/" + issue, s.director()).map().get("version");

        assertThat(post(PERF + "/issues/" + issue + "/close", s.managerToken(), Map.of("outcome", "CONTINUE", "reason", "Keep", "version", version)).status())
                .isEqualTo(403);
        assertThat(post(PERF + "/issues/" + issue + "/close", s.director(), Map.of("outcome", "CONTINUE", "version", version)).status()).isEqualTo(400);
        Resp done = post(PERF + "/issues/" + issue + "/close", s.director(), Map.of("outcome", "CONTINUE", "reason", "Given another term to improve", "version", version));
        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map().get("status")).isEqualTo("RESOLVED");
        assertThat(done.map().get("resolution")).isEqualTo("CONTINUE");
    }

    @Test
    void anotherManagerCannotReferSomeoneElsesTeacher() {
        Setup s = setup();
        Setup other = setup();
        observe(s.managerToken(), s.teacherId(), "UNSUITABLE", 1);
        UUID issue = openIssueOf(s.teacherId());
        assertThat(refer(other.managerToken(), issue, "REPLACE", "The Teacher cannot manage the class and has not improved").status()).isEqualTo(404);
    }
}
