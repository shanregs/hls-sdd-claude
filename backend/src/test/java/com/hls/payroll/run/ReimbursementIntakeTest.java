package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.ReimbursementIntake;
import com.hls.payroll.api.ReimbursementIntake.OfferStatus;
import com.hls.support.AdjustmentAppliedRecorder;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 015 T040: the additive payroll interface for reimbursements, on the rules the pay run already has. */
class ReimbursementIntakeTest extends PayrollRunTestBase {

    @Autowired
    private ReimbursementIntake intake;

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

    private String ref() {
        return UUID.randomUUID().toString();
    }

    private BigDecimal lineAdjustments(UUID run) {
        Object value = lineOf(admin, run, manager.managerId()).get("adjustments");
        return new BigDecimal(value == null ? "0" : (String) value);
    }

    private int rows(String ref) {
        return jdbc.queryForObject("select count(*) from payroll_adjustment where source_ref = ?", Integer.class, ref);
    }

    @Test
    void anOfferIsIdempotentPerSource() {
        String ref = ref();
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("1200.00"), "Claim A", ref).accepted()).isTrue();
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("1200.00"), "Claim A", ref).accepted()).isTrue();
        assertThat(rows(ref)).isEqualTo(1);
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.WAITING);
        assertThat(intake.status(ref())).isEqualTo(OfferStatus.NONE);
    }

    @Test
    void anOfferNeedsAPositiveAmountAndAPayrollPerson() {
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), BigDecimal.ZERO, "x", ref()).accepted()).isFalse();
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("-5"), "x", ref()).accepted()).isFalse();
        var refused = intake.offerForUser(adminUser, UUID.randomUUID(), new BigDecimal("10"), "x", ref());
        assertThat(refused.accepted()).isFalse();
        assertThat(refused.refusal()).contains("not on the payroll");
        assertThat(intake.offer(adminUser, Kind.MANAGER, UUID.randomUUID(), new BigDecimal("10"), "x", ref()).accepted()).isFalse();
        assertThat(intake.offer(adminUser, Kind.MANAGER, manager.managerId(), new BigDecimal("10"), "x", ref()).accepted()).isTrue();
    }

    @Test
    void theOfferJoinsADraftRunAndCancellingReleasesIt() {
        String ref = ref();
        intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("1200.00"), "Claim A", ref);

        UUID run = startAndCompute(admin);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("1200.00");
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_BY_DRAFT);

        assertThat(post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "Wrong month")).status()).isEqualTo(200);
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.WAITING);
    }

    @Test
    void withdrawWorksWhileWaitingOrHeldByADraft() {
        String waiting = ref();
        intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("100.00"), "Claim W", waiting);
        assertThat(intake.withdraw(adminUser, waiting, "Not payable")).isTrue();
        assertThat(intake.status(waiting)).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(intake.withdraw(adminUser, waiting, "Again")).isTrue(); // nothing left to pay
        assertThat(intake.withdraw(adminUser, ref(), "Never offered")).isTrue();

        String held = ref();
        intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("300.00"), "Claim H", held);
        UUID run = startAndCompute(admin);
        assertThat(lineAdjustments(run)).isEqualByComparingTo("300.00");
        assertThat(intake.withdraw(adminUser, held, "Reversed")).isTrue();
        assertThat(lineAdjustments(run)).isEqualByComparingTo("0.00");
        assertThat(intake.status(held)).isEqualTo(OfferStatus.WITHDRAWN);

        // withdrawn offers can be made again
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("300.00"), "Claim H", held).accepted()).isTrue();
        assertThat(intake.status(held)).isEqualTo(OfferStatus.WAITING);
        assertThat(rows(held)).isEqualTo(2);
    }

    @Test
    void withdrawIsRefusedWhenTheRunIsAwaitingApprovalOrTheAdjustmentIsApplied() {
        String ref = ref();
        intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("1200.00"), "Claim A", ref);
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);

        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_AWAITING_APPROVAL);
        assertThat(intake.withdraw(adminUser, ref, "Too late")).isFalse();
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.HELD_AWAITING_APPROVAL);

        String director = signInAs(com.hls.identity.user.Role.DIRECTOR).token();
        assertThat(approveRun(director, run).status()).isEqualTo(200);
        assertThat(intake.status(ref)).isEqualTo(OfferStatus.APPLIED);
        assertThat(intake.withdraw(adminUser, ref, "Too late")).isFalse();
    }

    @Test
    void approvalAppliesTheAdjustmentAndPublishesOneEventPerSource() {
        String ref = ref();
        intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("1200.00"), "Claim A", ref);
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);

        assertThat(intake.status(ref)).isEqualTo(OfferStatus.APPLIED);
        var events = applied.forSource(ref);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo("REIMBURSEMENT");
        assertThat(events.get(0).runId()).isEqualTo(run);
        assertThat(events.get(0).month()).isEqualTo(month.atDay(1));
    }

    @Test
    void anApprovedRunIsNotChangedByLaterOffers() {
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);
        Map<String, Object> before = lineOf(admin, run, manager.managerId());

        String ref = ref();
        assertThat(intake.offerForUser(adminUser, manager.signed().userId(), new BigDecimal("999.00"), "Late", ref).accepted()).isTrue();
        assertThat(intake.withdraw(adminUser, ref, "Never mind")).isTrue();

        Map<String, Object> after = lineOf(admin, run, manager.managerId());
        assertThat(after.get("net")).isEqualTo(before.get("net"));
        assertThat(after.get("adjustments")).isEqualTo(before.get("adjustments"));
        assertThat(runOf(admin, run).get("status")).isEqualTo("APPROVED");
    }
}
