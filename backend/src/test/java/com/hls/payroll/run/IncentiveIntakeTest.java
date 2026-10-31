package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.IncentiveIntake;
import com.hls.payroll.api.IncentiveIntake.OfferStatus;
import com.hls.support.AdjustmentAppliedRecorder;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 030 T008: the additive payroll interface for Manager incentives, on the rules the pay run already has. */
class IncentiveIntakeTest extends PayrollRunTestBase {

    @Autowired
    private IncentiveIntake intake;

    @Autowired
    private AdjustmentAppliedRecorder applied;

    private String admin;
    private UUID adminUser;
    private ManagerCtx manager;

    @BeforeEach
    void setUp() {
        resetRuns();
        Signed signed = signInAs(com.hls.identity.user.Role.ADMIN);
        admin = signed.token();
        adminUser = signed.userId();
        manager = paidManager(admin, "30000");
        lockWorkedManagerMonth(admin, manager.managerId());
    }

    private static String ref() {
        return "INCENTIVE:" + UUID.randomUUID();
    }

    private BigDecimal lineAdjustments(UUID run) {
        Object value = lineOf(admin, run, manager.managerId()).get("adjustments");
        return new BigDecimal(value == null ? "0" : (String) value);
    }

    private int rows(String ref) {
        return jdbc.queryForObject("select count(*) from payroll_adjustment where source_ref = ?", Integer.class, ref);
    }

    private boolean offer(String ref, String amount) {
        return intake.offer(adminUser, manager.managerId(), new BigDecimal(amount), "Manager incentive: closed School X", ref).accepted();
    }

    @Test
    void anOfferTwiceGivesOneAdjustment() {
        String ref = ref();
        assertThat(offer(ref, "5000.00")).isTrue();
        assertThat(offer(ref, "5000.00")).isTrue();
        assertThat(rows(ref)).isEqualTo(1);
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.WAITING);
        assertThat(intake.status(ref())).isEqualTo(OfferStatus.NONE);
        assertThat(jdbc.queryForObject("select kind from payroll_adjustment where source_ref = ?", String.class, ref))
                .isEqualTo("INCENTIVE");
    }

    @Test
    void onlyAPayrollManagerWithAPositiveAmountIsAccepted() {
        assertThat(intake.offer(adminUser, manager.managerId(), BigDecimal.ZERO, "x", ref()).accepted()).isFalse();
        assertThat(intake.offer(adminUser, manager.managerId(), new BigDecimal("-5"), "x", ref()).accepted()).isFalse();
        // a teacher's or any other id is not a payroll Manager
        var refused = intake.offer(adminUser, UUID.randomUUID(), new BigDecimal("10"), "x", ref());
        assertThat(refused.accepted()).isFalse();
        assertThat(refused.refusal()).contains("not on the payroll");
        assertThat(intake.offer(adminUser, null, new BigDecimal("10"), "x", ref()).accepted()).isFalse();
        assertThat(intake.offer(adminUser, manager.managerId(), new BigDecimal("10"), "x", " ").accepted()).isFalse();
    }

    @Test
    void aDraftRunTakesItAndRecomputeAndReturnKeepOneLine() {
        String ref = ref();
        offer(ref, "5000.00");

        UUID run = startAndCompute(admin);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("5000.00");
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_BY_DRAFT);

        assertThat(post(RUNS + "/" + run + "/recompute", admin, Map.of()).status()).isEqualTo(202);
        awaitComputed(admin, run);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("5000.00");

        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        String director = signInAs(com.hls.identity.user.Role.DIRECTOR).token();
        assertThat(returnRun(director, run, "Check again").status()).isEqualTo(200);
        assertThat(post(RUNS + "/" + run + "/recompute", admin, Map.of()).status()).isEqualTo(202);
        awaitComputed(admin, run);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("5000.00");
        assertThat(rows(ref)).isEqualTo(1);
    }

    @Test
    void approvalAppliesItAndPublishesOneIncentiveEvent() {
        String ref = ref();
        offer(ref, "5000.00");
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);

        assertThat(intake.status(ref)).isEqualTo(OfferStatus.APPLIED);
        var events = applied.forSource(ref);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo("INCENTIVE");
        assertThat(events.get(0).runId()).isEqualTo(run);
        assertThat(events.get(0).month()).isEqualTo(month.atDay(1));
    }

    @Test
    void withdrawWorksWhileWaitingOrHeldByADraftAndAnOfferAfterItCreatesANewOne() {
        String waiting = ref();
        offer(waiting, "100.00");
        assertThat(intake.withdraw(adminUser, waiting, "Not payable")).isTrue();
        assertThat(intake.status(waiting)).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(intake.withdraw(adminUser, waiting, "Again")).isTrue();
        assertThat(intake.withdraw(adminUser, ref(), "Never offered")).isTrue();

        String held = ref();
        offer(held, "300.00");
        UUID run = startAndCompute(admin);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("300.00");
        assertThat(intake.withdraw(adminUser, held, "MoU not approved")).isTrue();
        assertThat(lineAdjustments(run)).isEqualByComparingTo("0.00");

        assertThat(offer(held, "300.00")).isTrue();
        assertThat(intake.status(held)).isEqualTo(OfferStatus.WAITING);
        assertThat(rows(held)).isEqualTo(2);
    }

    @Test
    void withdrawIsRefusedWhenTheRunAwaitsApprovalOrItIsApplied() {
        String ref = ref();
        offer(ref, "5000.00");
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);

        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_AWAITING_APPROVAL);
        assertThat(intake.withdraw(adminUser, ref, "Too late")).isFalse();
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_AWAITING_APPROVAL);

        String director = signInAs(com.hls.identity.user.Role.DIRECTOR).token();
        assertThat(approveRun(director, run).status()).isEqualTo(200);
        assertThat(intake.withdraw(adminUser, ref, "Too late")).isFalse();
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.APPLIED);
    }

    @Test
    void aManagerWhoCannotBePaidKeepsTheIncentiveWaiting() {
        ManagerCtx unpaid = paidManager(admin, null);
        String ref = ref();
        assertThat(intake.offer(adminUser, unpaid.managerId(), new BigDecimal("5000.00"), "x", ref).accepted()).isTrue();

        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);

        assertThat(intake.status(ref)).isEqualTo(OfferStatus.WAITING);
        assertThat(applied.forSource(ref)).isEmpty();
    }

    @Test
    void manualRecordingOfAnIncentiveIsStillRefusedWithTheIntakeText() {
        var refused = post("/api/v1/payroll/adjustments", admin, Map.of(
                "personKind", "MANAGER", "personId", manager.managerId().toString(),
                "kind", "INCENTIVE", "amount", 100, "reason", "By hand"));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("spec 030").contains("incentive intake");
    }
}
