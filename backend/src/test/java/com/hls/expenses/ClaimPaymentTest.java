package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US4 (SC-005, FR-018 to FR-020): an approved claim is paid exactly once, through payroll or outside it. */
class ClaimPaymentTest extends ExpensesTestBase {

    private String admin;
    private String director;
    private ManagerCtx manager;

    @BeforeEach
    void setUp() {
        resetRuns();
        admin = admin().token();
        director = director().token();
        manager = paidManager(admin, "30000");
        lockWorkedManagerMonth(admin, manager.managerId());
    }

    private int adjustmentRows(UUID claim, String status) {
        return jdbc.queryForObject(
                "select count(*) from payroll_adjustment where source_ref = ? and kind = 'REIMBURSEMENT' and status = ?",
                Integer.class,
                claim.toString(),
                status);
    }

    private int adjustmentRows(UUID claim) {
        return jdbc.queryForObject(
                "select count(*) from payroll_adjustment where source_ref = ?", Integer.class, claim.toString());
    }

    private String payrollState(UUID claim) {
        return (String) get(REVIEW + "/" + claim, admin).map().get("payrollState");
    }

    private UUID approvedClaim(String token, String amount) {
        UUID id = newSubmittedClaim(token, amount);
        assertThat(approve(director, id).status()).isEqualTo(200);
        return id;
    }

    @Test
    void anApprovedClaimReachesTheNextRunOnceAndIsPaidWhenTheRunIsApproved() {
        UUID claim = approvedClaim(manager.token(), "1200.00");
        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");
        assertThat(payrollState(claim)).isEqualTo("WAITING");
        assertThat(adjustmentRows(claim)).isEqualTo(1);
        assertThat(eventCount(claim, "OFFERED_TO_PAYROLL")).isEqualTo(1);

        UUID run = startAndCompute(admin);
        Map<String, Object> line = lineOf(admin, run, manager.managerId());
        assertThat(new BigDecimal((String) line.get("adjustments"))).isEqualByComparingTo("1200.00");
        assertThat(payrollState(claim)).isEqualTo("HELD_BY_DRAFT");

        // cancelling the draft frees the claim; it is still Approved and still offered once
        assertThat(post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "Wrong month")).status()).isEqualTo(200);
        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");
        assertThat(payrollState(claim)).isEqualTo("WAITING");

        UUID second = startAndCompute(admin);
        assertThat(new BigDecimal((String) lineOf(admin, second, manager.managerId()).get("adjustments")))
                .isEqualByComparingTo("1200.00");
        submitAndApprove(admin, second);

        Resp paid = get(REVIEW + "/" + claim, admin);
        assertThat(paid.map()).containsEntry("status", "PAID").containsEntry("payrollState", "APPLIED");
        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) paid.map().get("payment");
        assertThat(payment).containsEntry("how", "PAYROLL").containsEntry("payrollRunId", second.toString());
        assertThat(payment.get("payrollMonth")).isEqualTo(month.atDay(1).toString());
        assertThat(eventCount(claim, "PAID_WITH_PAYROLL")).isEqualTo(1);
        assertThat(adjustmentRows(claim)).isEqualTo(1);
        assertThat(adjustmentRows(claim, "APPLIED")).isEqualTo(1);
        assertThat(get(MINE, manager.token()).map()).containsEntry("owed", "0.00");
        // a paid claim offers nothing
        assertThat(allowedActions(paid)).containsExactly("REMOVE_BILL");
    }

    @Test
    void recordingAClaimPaidOutsideStopsPayrollOfferingIt() {
        UUID claim = approvedClaim(manager.token(), "700.00");
        Resp paid = recordPaid(admin, claim, today(), "BANK_TRANSFER", "UTR-123456");
        assertThat(paid.status()).as(paid.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> payment = (Map<String, Object>) paid.map().get("payment");
        assertThat(paid.map()).containsEntry("status", "PAID");
        assertThat(payment).containsEntry("how", "OUTSIDE").containsEntry("mode", "BANK_TRANSFER").containsEntry("reference", "UTR-123456");
        assertThat(adjustmentRows(claim, "WITHDRAWN")).isEqualTo(1);
        assertThat(adjustmentRows(claim, "WAITING")).isEqualTo(0);

        UUID run = startAndCompute(admin);
        Map<String, Object> line = lineOf(admin, run, manager.managerId());
        assertThat(new BigDecimal(String.valueOf(line.get("adjustments") == null ? "0" : line.get("adjustments"))))
                .isEqualByComparingTo("0");
        // never both: it cannot be recorded again, and it cannot be reversed
        assertThat(recordPaid(admin, claim, today(), "CASH", null).status()).isEqualTo(409);
        assertThat(reverse(admin, claim, "Mistake").status()).isEqualTo(409);
        assertThat(eventCount(claim, "PAID_OUTSIDE")).isEqualTo(1);
    }

    @Test
    void aCompanyPaidClaimBecomesPaidByCompanyAndNeverReachesPayroll() {
        Map<String, Object> body = travelBody("5000.00");
        body.put("paidBy", "COMPANY");
        UUID claim = newDraft(manager.token(), body);
        uploadBill(manager.token(), claim, "bill.jpg", JPG);
        assertThat(submit(manager.token(), claim).status()).isEqualTo(200);
        assertThat(approve(director, claim).status()).isEqualTo(200);

        assertThat(reviewStatusOf(admin, claim)).isEqualTo("PAID_BY_COMPANY");
        assertThat(adjustmentRows(claim)).isEqualTo(0);
        assertThat(eventCount(claim, "OFFERED_TO_PAYROLL")).isEqualTo(0);
        assertThat(recordPaid(admin, claim, today(), "CASH", null).status()).isEqualTo(409);
        assertThat(get(MINE, manager.token()).map()).containsEntry("owed", "0.00");
    }

    @Test
    void aClaimantWithNoPayrollPersonStaysApprovedAndCanBeRecordedPaidOutside() {
        String gone = signInAs(Role.MANAGER).token(); // a login with no Manager record: not on the payroll
        UUID claim = approvedClaim(gone, "260.00");

        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");
        assertThat(payrollState(claim)).isEqualTo("NONE");
        assertThat(adjustmentRows(claim)).isEqualTo(0);
        assertThat(eventCount(claim, "PAYROLL_OFFER_SKIPPED")).isEqualTo(1);
        assertThat(get(MINE, gone).map()).containsEntry("owed", "260.00");

        Resp paid = recordPaid(admin, claim, today(), "UPI", "9876543210@upi");
        assertThat(paid.status()).as(paid.body()).isEqualTo(200);
        assertThat(paid.map()).containsEntry("status", "PAID");
    }

    @Test
    void aClaimApprovedAfterItsMonthWasPaidWaitsForTheNextRun() {
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);

        UUID late = approvedClaim(manager.token(), "150.00");
        assertThat(reviewStatusOf(admin, late)).isEqualTo("APPROVED");
        assertThat(payrollState(late)).isEqualTo("WAITING");
        assertThat(adjustmentRows(late, "WAITING")).isEqualTo(1);
    }

    @Test
    void recordingPaidChecksItsInputs() {
        UUID claim = approvedClaim(manager.token(), "45.00");
        Map<String, Object> body = new HashMap<>();
        body.put("version", reviewVersion(admin, claim));
        body.put("paidOn", today().toString());
        body.put("mode", "CASH");

        Map<String, Object> noDate = new HashMap<>(body);
        noDate.remove("paidOn");
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, noDate).status()).isEqualTo(400);
        Map<String, Object> future = new HashMap<>(body);
        future.put("paidOn", today().plusDays(1).toString());
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, future).status()).isEqualTo(400);
        Map<String, Object> badMode = new HashMap<>(body);
        badMode.put("mode", "CHEQUE");
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, badMode).status()).isEqualTo(400);
        Map<String, Object> noMode = new HashMap<>(body);
        noMode.remove("mode");
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, noMode).status()).isEqualTo(400);
        Map<String, Object> longRef = new HashMap<>(body);
        longRef.put("reference", "R".repeat(101));
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, longRef).status()).isEqualTo(400);
        Map<String, Object> stale = new HashMap<>(body);
        stale.put("version", 99);
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, stale).status()).isEqualTo(409);
        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");

        Map<String, Object> ok = new HashMap<>(body);
        ok.put("reference", "R".repeat(100));
        assertThat(post(REVIEW + "/" + claim + "/record-paid", admin, ok).status()).isEqualTo(200);
    }
}
