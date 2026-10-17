package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T039: corrections wait for a run, travel into its lines, and are applied exactly once (FR-018 to FR-020). */
class PayAdjustmentTest extends PayrollRunTestBase {

    private static final String ADJ = "/api/v1/payroll/adjustments";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private Resp record(String token, UUID teacher, Object amount, String reason) {
        return recordKind(token, "CORRECTION", teacher, amount, reason);
    }

    private Resp recordKind(String token, String kind, UUID teacher, Object amount, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("personKind", "TEACHER");
        body.put("personId", teacher.toString());
        body.put("kind", kind);
        body.put("amount", amount);
        body.put("reason", reason);
        return post(ADJ, token, body);
    }

    private UUID paidTeacher(String admin, Site site, String salary) {
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, salary, LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);
        return teacher;
    }

    private Map<String, Object> adjustment(String token, UUID id) {
        return content(get(ADJ + "?size=200", token)).stream()
                .filter(a -> id.toString().equals(a.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private static BigDecimal amount(Object text) {
        return new BigDecimal((String) text);
    }

    @Test
    void recordingSavesAWaitingAdjustmentWithWhoAndWhen() {
        Signed admin = signInAs(Role.ADMIN);
        UUID teacher = teacher(admin.token());

        Resp created = record(admin.token(), teacher, 2000, "August was underpaid");

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        Map<String, Object> a = created.map();
        assertThat(a.get("status")).isEqualTo("WAITING");
        assertThat(a.get("kind")).isEqualTo("CORRECTION");
        assertThat(amount(a.get("amount"))).isEqualByComparingTo("2000.00");
        assertThat(a.get("reason")).isEqualTo("August was underpaid");
        assertThat(a.get("personId")).isEqualTo(teacher.toString());
        assertThat(a.get("personName")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> by = (Map<String, Object>) a.get("createdBy");
        assertThat(by.get("id")).isEqualTo(admin.userId().toString());
        assertThat(a.get("createdAt")).isNotNull();
        assertThat(adjustment(admin.token(), created.id()).get("status")).isEqualTo("WAITING");
    }

    @Test
    void aNegativeCorrectionIsAllowed() {
        String admin = signInAs(Role.ADMIN).token();

        Resp created = record(admin, teacher(admin), -750.5, "Advance recovered");

        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(amount(created.map().get("amount"))).isEqualByComparingTo("-750.50");
    }

    @Test
    void invalidInputIsRefusedNamingTheProblem() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);

        assertThat(record(admin, teacher, 0, "zero").body()).contains("cannot be zero");
        assertThat(record(admin, teacher, 10.123, "three decimals").body()).contains("at most two decimals");
        assertThat(record(admin, teacher, 100, "").body()).contains("reason is required");
        assertThat(record(admin, teacher, 100, "x".repeat(501)).body()).contains("at most 500");
        assertThat(record(admin, teacher, null, "no amount").body()).contains("amount is required");
        assertThat(recordKind(admin, "BONUS", teacher, 100, "unknown kind").body()).contains("Unknown kind");
        assertThat(record(admin, UUID.randomUUID(), 100, "no such person").body()).contains("No such Teacher");
        for (Resp r : List.of(
                record(admin, teacher, 0, "zero"),
                record(admin, teacher, 100, ""),
                recordKind(admin, "BONUS", teacher, 100, "x"))) {
            assertThat(r.status()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("select count(*) from payroll_adjustment", Long.class)).isZero();
    }

    @Test
    void theReservedKindsAreRefusedNamingTheSpecThatWillCreateThem() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);

        Resp incentive = recordKind(admin, "INCENTIVE", teacher, 100, "x");
        Resp reimbursement = recordKind(admin, "REIMBURSEMENT", teacher, 100, "x");
        Resp substitute = recordKind(admin, "SUBSTITUTE_CREDIT", teacher, 100, "x");

        assertThat(incentive.status()).isEqualTo(400);
        assertThat(incentive.body()).contains("spec 030");
        assertThat(reimbursement.body()).contains("spec 015");
        assertThat(substitute.body()).contains("spec 017b");
        assertThat(jdbc.queryForObject("select count(*) from payroll_adjustment", Long.class)).isZero();
    }

    @Test
    void aRunTakesTheWaitingAdjustmentIntoTheLineAndTheNetIncludesIt() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID adjustmentId = record(admin, teacher, 2000, "August was underpaid").id();

        UUID run = startAndCompute(admin);

        Map<String, Object> line = lineOf(admin, run, teacher);
        assertThat(amount(line.get("earned"))).isEqualByComparingTo("26000.00");
        assertThat(amount(line.get("adjustments"))).isEqualByComparingTo("2000.00");
        assertThat(amount(line.get("net")))
                .isEqualByComparingTo(amount(line.get("earned")).subtract(amount(line.get("deduction"))).add(new BigDecimal("2000")));
        Map<String, Object> detail = get(RUNS + "/" + run + "/lines/" + line.get("id"), admin).map();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> copies = (List<Map<String, Object>>) detail.get("adjustmentLines");
        assertThat(copies).singleElement().satisfies(c -> {
            assertThat(c.get("adjustmentId")).isEqualTo(adjustmentId.toString());
            assertThat(c.get("reason")).isEqualTo("August was underpaid");
            assertThat(amount(c.get("amount"))).isEqualByComparingTo("2000.00");
        });
        assertThat(get(RUNS + "/" + run + "/lines/" + line.get("id"), admin).body()).contains("August was underpaid");
        // taken by the run, still waiting until it is approved
        Map<String, Object> stored = adjustment(admin, adjustmentId);
        assertThat(stored.get("status")).isEqualTo("WAITING");
        assertThat(stored.get("attachedRunId")).isEqualTo(run.toString());
    }

    @Test
    void anAdjustmentOfAPersonWhoCannotBePaidStaysWaitingAndIsNotApplied() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "20000", LocalDate.now().minusDays(300)); // month not locked
        UUID adjustmentId = record(admin, teacher, 500, "Owed").id();

        UUID run = startAndCompute(admin);

        assertThat(lineOf(admin, run, teacher).get("state")).isEqualTo("CANNOT_PAY");
        Map<String, Object> stored = adjustment(admin, adjustmentId);
        assertThat(stored.get("status")).isEqualTo("WAITING");
        assertThat(stored.get("attachedRunId")).isNull();
    }

    @Test
    void approvalAppliesTheAdjustmentOnceAndItCanNeitherBeWithdrawnNorTakenAgain() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID adjustmentId = record(admin, teacher, 2000, "August was underpaid").id();
        UUID run = startAndCompute(admin);
        UUID lineId = UUID.fromString((String) lineOf(admin, run, teacher).get("id"));
        submitAndApprove(admin, run);

        Map<String, Object> stored = adjustment(admin, adjustmentId);
        assertThat(stored.get("status")).isEqualTo("APPLIED");
        assertThat(stored.get("appliedRunId")).isEqualTo(run.toString());
        assertThat(stored.get("appliedLineId")).isEqualTo(lineId.toString());
        Resp withdraw = post(ADJ + "/" + adjustmentId + "/withdraw", admin, Map.of("reason", "oops"));
        assertThat(withdraw.status()).isEqualTo(409);
        assertThat(withdraw.body()).contains("applied");
        // a run for another month does not take an applied adjustment
        Resp earlier = post(RUNS, admin, Map.of("month", month.minusMonths(1).toString()));
        awaitComputed(admin, earlier.id());
        Map<String, Object> other = lineOf(admin, earlier.id(), teacher);
        assertThat(other.get("adjustments")).isNull();
        assertThat(adjustment(admin, adjustmentId).get("status")).isEqualTo("APPLIED");
    }

    @Test
    void aWaitingAdjustmentCanBeWithdrawnWithAReasonAndKeepsItsHistory() {
        Signed admin = signInAs(Role.ADMIN);
        UUID teacher = teacher(admin.token());
        UUID id = record(admin.token(), teacher, 300, "Entered twice").id();

        assertThat(post(ADJ + "/" + id + "/withdraw", admin.token(), Map.of()).status()).isEqualTo(400);
        Resp withdrawn = post(ADJ + "/" + id + "/withdraw", admin.token(), Map.of("reason", "Entered twice by mistake"));

        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);
        Map<String, Object> a = withdrawn.map();
        assertThat(a.get("status")).isEqualTo("WITHDRAWN");
        assertThat(a.get("withdrawReason")).isEqualTo("Entered twice by mistake");
        @SuppressWarnings("unchecked")
        Map<String, Object> by = (Map<String, Object>) a.get("withdrawnBy");
        assertThat(by.get("id")).isEqualTo(admin.userId().toString());
        assertThat(post(ADJ + "/" + id + "/withdraw", admin.token(), Map.of("reason", "again")).status()).isEqualTo(409);
        assertThat(adjustment(admin.token(), id).get("status")).isEqualTo("WITHDRAWN");
    }

    @Test
    void withdrawingWhileADraftHoldsItRemovesTheLineCopyAndWorksTheNetOutAgain() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID keep = record(admin, teacher, 1000, "Keep this").id();
        UUID drop = record(admin, teacher, 2000, "Drop this").id();
        UUID run = startAndCompute(admin);
        Map<String, Object> before = lineOf(admin, run, teacher);
        assertThat(amount(before.get("adjustments"))).isEqualByComparingTo("3000.00");
        BigDecimal totalBefore = amount(runOf(admin, run).get("totals") instanceof Map<?, ?> t ? t.get("net") : null);

        Resp withdrawn = post(ADJ + "/" + drop + "/withdraw", admin, Map.of("reason", "Wrong person"));

        assertThat(withdrawn.status()).as(withdrawn.body()).isEqualTo(200);
        Map<String, Object> after = lineOf(admin, run, teacher);
        assertThat(amount(after.get("adjustments"))).isEqualByComparingTo("1000.00");
        assertThat(amount(after.get("net"))).isEqualByComparingTo(amount(before.get("net")).subtract(new BigDecimal("2000")));
        String working = get(RUNS + "/" + run + "/lines/" + after.get("id"), admin).body();
        assertThat(working).contains("Keep this").doesNotContain("Drop this");
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) runOf(admin, run).get("totals");
        assertThat(amount(totals.get("net"))).isEqualByComparingTo(totalBefore.subtract(new BigDecimal("2000")));
        assertThat(get(RUNS + "/" + run, admin).body()).contains("ADJUSTMENT_WITHDRAWN").contains("Wrong person");
        assertThat(adjustment(admin, drop).get("status")).isEqualTo("WITHDRAWN");
        assertThat(adjustment(admin, keep).get("status")).isEqualTo("WAITING");
        // computing again does not bring it back
        post(RUNS + "/" + run + "/recompute", admin, Map.of());
        awaitComputed(admin, run);
        assertThat(amount(lineOf(admin, run, teacher).get("adjustments"))).isEqualByComparingTo("1000.00");
    }

    @Test
    void withdrawingWhileTheRunAwaitsApprovalIsRefusedWithTheReason() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID id = record(admin, teacher, 2000, "Underpaid").id();
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);

        Resp refused = post(ADJ + "/" + id + "/withdraw", admin, Map.of("reason", "changed my mind"));

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("awaiting approval").contains("returned");
        assertThat(adjustment(admin, id).get("status")).isEqualTo("WAITING");
        assertThat(amount(lineOf(admin, run, teacher).get("adjustments"))).isEqualByComparingTo("2000.00");
    }

    @Test
    void cancellingADraftLetsItsAdjustmentsWaitAgain() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID id = record(admin, teacher, 2000, "Underpaid").id();
        UUID run = startAndCompute(admin);
        assertThat(adjustment(admin, id).get("attachedRunId")).isEqualTo(run.toString());

        post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "Wrong month"));

        Map<String, Object> stored = adjustment(admin, id);
        assertThat(stored.get("status")).isEqualTo("WAITING");
        assertThat(stored.get("attachedRunId")).isNull();
        // and the next run takes it
        UUID next = startAndCompute(admin);
        assertThat(amount(lineOf(admin, next, teacher).get("adjustments"))).isEqualByComparingTo("2000.00");
    }

    @Test
    void anAdjustmentRecordedWhileADraftIsOpenAppearsOnlyAfterComputingAgain() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        UUID run = startAndCompute(admin);
        assertThat(runOf(admin, run).get("adjustmentsWaitingSinceComputed")).isEqualTo(0);

        record(admin, teacher, 1500, "Late correction");

        assertThat(amount(lineOf(admin, run, teacher).get("adjustments"))).isEqualByComparingTo("0.00");
        assertThat(runOf(admin, run).get("adjustmentsWaitingSinceComputed")).isEqualTo(1);
        post(RUNS + "/" + run + "/recompute", admin, Map.of());
        awaitComputed(admin, run);
        assertThat(amount(lineOf(admin, run, teacher).get("adjustments"))).isEqualByComparingTo("1500.00");
        assertThat(runOf(admin, run).get("adjustmentsWaitingSinceComputed")).isEqualTo(0);
    }

    @Test
    void aNegativeNetAfterAnAdjustmentBlocksSubmit() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        record(admin, teacher, -50000, "Recover a large advance");
        UUID run = startAndCompute(admin);

        Resp refused = submitRun(admin, run);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("below zero");
        assertThat(runOf(admin, run).get("status")).isEqualTo("DRAFT");
    }

    @Test
    void theApprovedPayslipAndRunKeepTheAdjustmentWhateverHappensNext() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = paidTeacher(admin, site, "26000");
        record(admin, teacher, 2000, "August was underpaid");
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);

        Map<String, Object> line = lineOf(admin, run, teacher);

        assertThat(amount(line.get("adjustments"))).isEqualByComparingTo("2000.00");
        assertThat(get(RUNS + "/" + run + "/lines/" + line.get("id"), admin).body()).contains("August was underpaid");
    }
}
