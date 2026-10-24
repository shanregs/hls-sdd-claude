package com.hls.expenses.internal;

import com.hls.expenses.api.ExpenseDecided;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approve and reject (spec 015 US3): decided once, never by the person who raised the claim (FR-015), with no amount
 * limit (FR-013). A decision runs under a row lock, so of two simultaneous decisions the second sees the first and is
 * told who decided and when (FR-016). A company-paid claim goes to Paid by company in the same transaction; a personally
 * paid claim is offered to payroll through {@link ExpensePaymentService}.
 */
@Service
public class DecisionService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final ExpenseClaimRepository claims;
    private final ClaimRecorder recorder;
    private final ExpensePaymentService payments;
    private final AppUserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DecisionService(
            ExpenseClaimRepository claims,
            ClaimRecorder recorder,
            ExpensePaymentService payments,
            AppUserRepository users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.claims = claims;
        this.recorder = recorder;
        this.payments = payments;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void approve(ExpenseCaller caller, UUID claimId, Long version) {
        ExpenseClaim claim = decidable(caller, claimId, version);
        boolean company = claim.getPaidBy() == PaidBy.COMPANY;
        claim.approve(caller.userId(), clock.instant(), company);
        claims.saveAndFlush(claim);
        recorder.event(claimId, caller.userId(), "APPROVED", null, null);
        recorder.field(caller.userId(), claimId, "status", ClaimStatus.SUBMITTED, claim.getStatus());
        if (company) {
            recorder.historyOnly(claimId, caller.userId(), "PAID_BY_COMPANY", null, "Paid by the company; never goes to payroll");
        } else {
            payments.offerToPayroll(caller, claim);
        }
        events.publishEvent(new ExpenseDecided(claimId, claim.getClaimantId(), "APPROVED", null));
    }

    @Transactional
    public void reject(ExpenseCaller caller, UUID claimId, Long version, String reason) {
        String why = ExpenseInput.requiredText(reason, ExpenseInput.MAX_REASON, "The reason for rejecting");
        ExpenseClaim claim = decidable(caller, claimId, version);
        claim.reject(caller.userId(), why, clock.instant());
        claims.saveAndFlush(claim);
        recorder.event(claimId, caller.userId(), "REJECTED", why, null);
        recorder.field(caller.userId(), claimId, "status", ClaimStatus.SUBMITTED, ClaimStatus.REJECTED);
        events.publishEvent(new ExpenseDecided(claimId, claim.getClaimantId(), "REJECTED", why));
    }

    /** The locked claim, once the caller is known not to be its claimant and it is still Submitted. */
    private ExpenseClaim decidable(ExpenseCaller caller, UUID claimId, Long version) {
        ExpenseClaim claim = claims.lockById(claimId)
                .filter(c -> c.getStatus() != ClaimStatus.DRAFT || c.getClaimantId().equals(caller.userId()))
                .orElseThrow(() -> new NotFoundException("Claim not found."));
        if (claim.getClaimantId().equals(caller.userId())) {
            throw new ForbiddenFieldException("You raised this claim, so someone else must decide it.");
        }
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new ConflictException(alreadyDecided(claim));
        }
        StaleVersion.check(ExpenseClaim.class, claimId, claim.getVersion(), version);
        return claim;
    }

    private String alreadyDecided(ExpenseClaim claim) {
        if (claim.getDecidedBy() == null || claim.getDecidedAt() == null) {
            return "This claim is " + claim.getStatus().label().toLowerCase() + " and cannot be decided now.";
        }
        String who = users.findById(claim.getDecidedBy()).map(AppUser::getDisplayName).orElse("another reviewer");
        return "This claim was already decided: " + claim.getStatus().label().toLowerCase() + " by " + who + " on "
                + WHEN.format(claim.getDecidedAt()) + ".";
    }
}
