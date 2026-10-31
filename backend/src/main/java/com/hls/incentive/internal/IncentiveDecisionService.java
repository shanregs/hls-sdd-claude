package com.hls.incentive.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.incentive.api.IncentiveDecided;
import com.hls.incentive.internal.IncentiveAccess.Caller;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import com.hls.schoolbilling.api.MouApprovals;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approve, reject and reopen an incentive (spec 030 US2, FR-006 to FR-009, D-5, D-7). A decision takes a row lock and
 * checks the version, so exactly one decision is recorded; the loser is told who decided and when. The earner may approve
 * their own incentive when they are the School's Zone Manager: the row is then flagged and the audit names both people.
 * Approval offers the amount to payroll in the same transaction.
 */
@Service
public class IncentiveDecisionService {

    static final int MAX_REASON = 500;

    private final IncentiveRepository incentives;
    private final IncentiveAccess access;
    private final IncentiveRecorder recorder;
    private final PayrollLink payroll;
    private final MouApprovals mou;
    private final AppUserRepository users;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    IncentiveDecisionService(
            IncentiveRepository incentives,
            IncentiveAccess access,
            IncentiveRecorder recorder,
            PayrollLink payroll,
            MouApprovals mou,
            AppUserRepository users,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.incentives = incentives;
        this.access = access;
        this.recorder = recorder;
        this.payroll = payroll;
        this.mou = mou;
        this.users = users;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public void approve(Caller caller, UUID id, Long version) {
        Incentive incentive = decidable(caller, id, version);
        if (!mou.stateOn(incentive.getSchoolId(), today()).countsAsApproved()) {
            throw new ConflictException("The MoU of this School is not approved at the moment, so its incentive cannot be approved.");
        }
        boolean byEarner = incentive.getEarnerUserId().equals(caller.userId());
        incentive.approve(caller.userId(), byEarner, clock.instant());
        incentives.saveAndFlush(incentive);
        recorder.record(caller.userId(), id, "APPROVED", byEarner ? "Approved by the earner" : null);
        recorder.field(caller.userId(), id, "status", IncentiveStatus.PENDING_APPROVAL, IncentiveStatus.APPROVED);
        if (byEarner) {
            recorder.field(caller.userId(), id, "approvedByEarner", false, true);
        }
        payroll.offer(caller.userId(), incentive);
        publisher.publishEvent(new IncentiveDecided(
                id, incentive.getSchoolId(), incentive.getEarnerUserId(), "APPROVED", null, caller.userId(), byEarner));
    }

    @Transactional
    public void reject(Caller caller, UUID id, Long version, String reason) {
        String why = requiredReason(reason, "The reason for rejecting");
        Incentive incentive = decidable(caller, id, version);
        incentive.reject(caller.userId(), why, clock.instant());
        incentives.saveAndFlush(incentive);
        recorder.record(caller.userId(), id, "REJECTED", why);
        recorder.field(caller.userId(), id, "status", IncentiveStatus.PENDING_APPROVAL, IncentiveStatus.REJECTED);
        publisher.publishEvent(new IncentiveDecided(
                id, incentive.getSchoolId(), incentive.getEarnerUserId(), "REJECTED", why, caller.userId(), false));
    }

    /**
     * A rejected incentive goes back to pending for a corrected decision (Director only, so a decider cannot undo a
     * rejection of their own incentive), with a reason, so the rule "one incentive per School" still allows a second look.
     */
    @Transactional
    public void reopen(Caller caller, UUID id, Long version, String reason) {
        String why = requiredReason(reason, "The reason for reopening");
        Incentive incentive = incentives.lockById(id).orElseThrow(() -> new NotFoundException("Incentive not found."));
        if (!access.canSee(caller, incentive)) {
            throw new NotFoundException("Incentive not found.");
        }
        if (!access.director(caller)) {
            throw new ForbiddenFieldException("Only the Director can reopen an incentive.");
        }
        if (incentive.getStatus() != IncentiveStatus.REJECTED) {
            throw new ConflictException("Only a rejected incentive can be reopened.");
        }
        StaleVersion.check(Incentive.class, id, incentive.getVersion(), version);
        incentive.reopen(clock.instant());
        incentives.saveAndFlush(incentive);
        recorder.record(caller.userId(), id, "REOPENED", why);
        recorder.field(caller.userId(), id, "status", IncentiveStatus.REJECTED, IncentiveStatus.PENDING_APPROVAL);
    }

    /** The locked incentive, once the caller may decide it and it is still pending. */
    private Incentive decidable(Caller caller, UUID id, Long version) {
        Incentive incentive = incentives.lockById(id).orElseThrow(() -> new NotFoundException("Incentive not found."));
        if (!access.canSee(caller, incentive)) {
            throw new NotFoundException("Incentive not found.");
        }
        if (!access.canDecide(caller, incentive)) {
            throw new ForbiddenFieldException("Only the Director or the Zone Manager of this School can decide an incentive.");
        }
        if (incentive.getStatus() != IncentiveStatus.PENDING_APPROVAL) {
            throw new ConflictException(alreadyDecided(incentive));
        }
        StaleVersion.check(Incentive.class, id, incentive.getVersion(), version);
        return incentive;
    }

    private String alreadyDecided(Incentive incentive) {
        if (incentive.getDecidedBy() == null || incentive.getDecidedAt() == null) {
            return "This incentive is " + incentive.getStatus().name().toLowerCase().replace('_', ' ') + " and cannot be decided now.";
        }
        String who = users.findById(incentive.getDecidedBy()).map(AppUser::getDisplayName).orElse("another approver");
        String what = incentive.getStatus() == IncentiveStatus.REJECTED ? "rejected" : "approved";
        return "This incentive was already decided: " + what + " by " + who + " on "
                + IncentiveRecorder.WHEN.format(incentive.getDecidedAt()) + ".";
    }

    private static String requiredReason(String reason, String what) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new InvalidInputException(what + " is required.");
        }
        if (text.length() > MAX_REASON) {
            throw new InvalidInputException(what + " can be at most " + MAX_REASON + " characters.");
        }
        return text;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(IncentiveRecorder.BUSINESS_ZONE));
    }
}
