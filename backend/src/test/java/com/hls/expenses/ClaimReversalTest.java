package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.ExpensesTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US4 (FR-017): an approval is reversed with a reason, but only before the claim is paid or in an approved run. */
class ClaimReversalTest extends ExpensesTestBase {

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

    private UUID approvedClaim(String amount) {
        UUID id = newSubmittedClaim(manager.token(), amount);
        assertThat(approve(director, id).status()).isEqualTo(200);
        return id;
    }

    private int adjustmentRows(UUID claim, String status) {
        return jdbc.queryForObject(
                "select count(*) from payroll_adjustment where source_ref = ? and status = ?", Integer.class, claim.toString(), status);
    }

    @Test
    void reversalNeedsAReasonReturnsTheClaimToSubmittedAndWithdrawsTheOffer() {
        UUID claim = approvedClaim("800.00");
        assertThat(reverse(admin, claim, "").status()).isEqualTo(400);
        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");

        Resp reversed = reverse(admin, claim, "Wrong category");
        assertThat(reversed.status()).as(reversed.body()).isEqualTo(200);
        assertThat(reversed.map()).containsEntry("status", "SUBMITTED").containsEntry("payrollState", "NONE");
        assertThat(historyOf(reversed)).extracting(h -> h.get("event")).containsSubsequence("APPROVED", "REVERSED");
        assertThat(historyOf(reversed).stream().map(h -> h.get("reason")).toList()).contains("Wrong category");
        assertThat(adjustmentRows(claim, "WITHDRAWN")).isEqualTo(1);
        assertThat(adjustmentRows(claim, "WAITING")).isEqualTo(0);
        assertThat(notificationCount(manager.signed().userId(), "EXPENSE_REVERSED")).isEqualTo(1);

        // it can be decided again, and is offered afresh
        assertThat(approve(director, claim).status()).isEqualTo(200);
        assertThat(adjustmentRows(claim, "WAITING")).isEqualTo(1);
        assertThat(adjustmentRows(claim, "WITHDRAWN")).isEqualTo(1);
    }

    @Test
    void aReversedClaimTurnsFromActualBackToPendingInTheBudget() {
        UUID claim = approvedClaim("900.00");
        String month = java.time.YearMonth.from(today().minusDays(1)).toString();
        Map<?, ?> before = (Map<?, ?>) get(REVIEW + "/budgets?month=" + month, admin).map().get("total");
        assertThat(reverse(admin, claim, "Check the bill").status()).isEqualTo(200);
        Map<?, ?> after = (Map<?, ?>) get(REVIEW + "/budgets?month=" + month, admin).map().get("total");
        assertThat(new java.math.BigDecimal((String) before.get("actual")).subtract(new java.math.BigDecimal((String) after.get("actual"))))
                .isEqualByComparingTo("900.00");
        assertThat(new java.math.BigDecimal((String) after.get("pending")).subtract(new java.math.BigDecimal((String) before.get("pending"))))
                .isEqualByComparingTo("900.00");
    }

    @Test
    void reversalIsRefusedWhileARunAwaitingApprovalHoldsTheClaimAndAllowedAfterItIsReturned() {
        UUID claim = approvedClaim("510.00");
        UUID run = startAndCompute(admin);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);

        Resp refused = reverse(director, claim, "Too late");
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("awaiting approval");
        assertThat(reviewStatusOf(admin, claim)).isEqualTo("APPROVED");
        Resp recordRefused = recordPaid(admin, claim, today(), "CASH", null);
        assertThat(recordRefused.status()).isEqualTo(409);
        assertThat(get(REVIEW + "/" + claim, admin).map()).containsEntry("payrollState", "HELD_AWAITING_APPROVAL");
        assertThat(allowedActions(get(REVIEW + "/" + claim, admin))).doesNotContain("REVERSE", "RECORD_PAID");

        assertThat(returnRun(director, run, "Check it").status()).isEqualTo(200);
        assertThat(reverse(director, claim, "Now allowed").status()).isEqualTo(200);
    }

    @Test
    void reversalIsRefusedOnceTheClaimIsPaidWithPayrollOrOutsideOrByTheCompany() {
        UUID viaPayroll = approvedClaim("100.00");
        UUID outside = approvedClaim("200.00");
        assertThat(recordPaid(admin, outside, today(), "CASH", null).status()).isEqualTo(200);
        UUID run = startAndCompute(admin);
        submitAndApprove(admin, run);
        assertThat(reviewStatusOf(admin, viaPayroll)).isEqualTo("PAID");

        Resp a = reverse(director, viaPayroll, "x");
        Resp b = reverse(director, outside, "x");
        assertThat(a.status()).isEqualTo(409);
        assertThat(a.body()).contains("already paid");
        assertThat(b.status()).isEqualTo(409);

        Map<String, Object> body = travelBody("300.00");
        body.put("paidBy", "COMPANY");
        UUID company = newDraft(manager.token(), body);
        uploadBill(manager.token(), company, "bill.jpg", JPG);
        submit(manager.token(), company);
        approve(director, company);
        assertThat(reviewStatusOf(admin, company)).isEqualTo("PAID_BY_COMPANY");
        assertThat(reverse(director, company, "x").status()).isEqualTo(409);
    }

    @Test
    void onlyAnApprovedClaimCanBeReversed() {
        UUID submitted = newSubmittedClaim(manager.token(), "12.00");
        assertThat(reverse(admin, submitted, "x").status()).isEqualTo(409);
        UUID rejected = newSubmittedClaim(manager.token(), "13.00");
        reject(admin, rejected, "No");
        assertThat(reverse(admin, rejected, "x").status()).isEqualTo(409);
    }
}
