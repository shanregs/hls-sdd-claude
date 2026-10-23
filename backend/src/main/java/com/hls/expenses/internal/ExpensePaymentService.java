package com.hls.expenses.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.expenses.api.ExpenseDecided;
import com.hls.payroll.api.ReimbursementIntake;
import com.hls.payroll.api.ReimbursementIntake.OfferResult;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What happens to an approved claim next (spec 015 US4): a personally paid claim is offered to the claimant next pay run
 * as a reimbursement exactly once; the Admin may instead record it paid outside payroll; an approval may be reversed until
 * the claim is paid or its pay run is awaiting approval. A claim is never both: recording it paid withdraws the offer first.
 */
@Service
public class ExpensePaymentService {

    private final ExpenseClaimRepository claims;
    private final ClaimRecorder recorder;
    private final ReimbursementIntake payroll;
    private final ApplicationEventPublisher events;
    private final BusinessDate businessDate;
    private final Clock clock;

    ExpensePaymentService(
            ExpenseClaimRepository claims,
            ClaimRecorder recorder,
            ReimbursementIntake payroll,
            ApplicationEventPublisher events,
            BusinessDate businessDate,
            Clock clock) {
        this.claims = claims;
        this.recorder = recorder;
        this.payroll = payroll;
        this.events = events;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    /**
     * Offers an approved, personally paid claim to payroll. When the claimant is not a payroll person the claim stays
     * Approved and the history says why, so an Admin records it paid outside.
     */
    void offerToPayroll(ExpenseCaller caller, ExpenseClaim claim) {
        String reason = "Expense claim " + claim.getExpenseDate() + ", " + claim.getCategory().label();
        OfferResult result = payroll.offerForUser(caller.userId(), claim.getClaimantId(), claim.getAmount(), reason, sourceRef(claim.getId()));
        if (result.accepted()) {
            recorder.historyOnly(claim.getId(), caller.userId(), "OFFERED_TO_PAYROLL", null, "Offered to the next pay run");
        } else {
            recorder.historyOnly(claim.getId(), caller.userId(), "PAYROLL_OFFER_SKIPPED", result.refusal(), null);
        }
    }

    @Transactional
    public void reverse(ExpenseCaller caller, UUID claimId, Long version, String reason) {
        String why = ExpenseInput.requiredText(reason, ExpenseInput.MAX_REASON, "The reason for reversing");
        ExpenseClaim claim = visibleLocked(caller, claimId);
        if (claim.getClaimantId().equals(caller.userId())) {
            throw new ForbiddenFieldException("You raised this claim, so someone else must reverse it.");
        }
        if (claim.getStatus() == ClaimStatus.PAID || claim.getStatus() == ClaimStatus.PAID_BY_COMPANY) {
            throw new ConflictException("This claim is already paid and its approval cannot be reversed.");
        }
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ConflictException("Only an approved claim can be reversed; this one is "
                    + claim.getStatus().label().toLowerCase() + ".");
        }
        StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), version);
        if (!payroll.withdraw(caller.userId(), sourceRef(claimId), "Approval reversed: " + why)) {
            throw new ConflictException(
                    "This claim is in a pay run that is awaiting approval or has been applied, so it cannot be reversed now.");
        }
        claim.reverse(clock.instant());
        claims.saveAndFlush(claim);
        recorder.event(claimId, caller.userId(), "REVERSED", why, null);
        recorder.field(caller.userId(), claimId, "status", ClaimStatus.APPROVED, ClaimStatus.SUBMITTED);
        events.publishEvent(new ExpenseDecided(claimId, claim.getClaimantId(), "REVERSED", why));
    }

    @Transactional
    public void recordPaid(
            ExpenseCaller caller, UUID claimId, Long version, LocalDate paidOn, String mode, String reference) {
        ExpenseClaim claim = visibleLocked(caller, claimId);
        if (claim.getStatus() != ClaimStatus.APPROVED || claim.getPaidBy() != PaidBy.CLAIMANT) {
            throw new ConflictException(claim.getPaidBy() == PaidBy.COMPANY
                    ? "A claim the company paid is never paid back to the claimant."
                    : "Only an approved claim can be recorded as paid; this one is "
                            + claim.getStatus().label().toLowerCase() + ".");
        }
        StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), version);
        if (paidOn == null) {
            throw new InvalidInputException("The payment date is required.");
        }
        if (paidOn.isAfter(businessDate.today())) {
            throw new InvalidInputException("The payment date cannot be in the future.");
        }
        PaidMode paidMode = ExpenseInput.paidMode(mode);
        String ref = ExpenseInput.text(reference, ExpenseInput.MAX_REFERENCE, "The reference");
        if (!payroll.withdraw(caller.userId(), sourceRef(claimId), "Recorded as paid outside payroll")) {
            throw new ConflictException(
                    "This claim is in a pay run that is awaiting approval or has been applied, so it cannot be recorded as paid now.");
        }
        claim.markPaidOutside(caller.userId(), paidOn, paidMode, ref, clock.instant());
        claims.saveAndFlush(claim);
        recorder.event(claimId, caller.userId(), "PAID_OUTSIDE", null, paidMode + (ref == null ? "" : " " + ref) + " on " + paidOn);
        recorder.field(caller.userId(), claimId, "status", ClaimStatus.APPROVED, ClaimStatus.PAID);
    }

    /**
     * An approved pay run applied the claim reimbursement. Idempotent: a claim already Paid is left alone. Runs in the
     * pay run approval transaction, so the run and the claim settle together or not at all.
     */
    void settleWithPayroll(String sourceRef, UUID runId, LocalDate month) {
        UUID claimId;
        try {
            claimId = UUID.fromString(sourceRef);
        } catch (IllegalArgumentException e) {
            return; // not one of ours
        }
        ExpenseClaim claim = claims.lockById(claimId).orElse(null);
        if (claim == null || claim.getStatus() != ClaimStatus.APPROVED) {
            return;
        }
        claim.markPaidWithPayroll(runId, month, clock.instant());
        claims.saveAndFlush(claim);
        recorder.event(claimId, null, "PAID_WITH_PAYROLL", null, "Pay run for " + month.getMonth() + " " + month.getYear());
        recorder.field(null, claimId, "status", ClaimStatus.APPROVED, ClaimStatus.PAID);
    }

    static String sourceRef(UUID claimId) {
        return claimId.toString();
    }

    /** The locked claim, hidden (as not found) when it is somebody else Draft. */
    private ExpenseClaim visibleLocked(ExpenseCaller caller, UUID claimId) {
        return claims.lockById(claimId)
                .filter(c -> c.getStatus() != ClaimStatus.DRAFT || c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
    }
}
