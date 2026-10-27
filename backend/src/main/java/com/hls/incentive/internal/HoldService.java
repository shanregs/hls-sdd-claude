package com.hls.incentive.internal;

import com.hls.incentive.api.IncentiveMouChanged;
import com.hls.payroll.api.IncentiveIntake.OfferStatus;
import com.hls.schoolbilling.api.MouApprovals;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps an approved incentive in step with its School's MoU (spec 030 US5, research D-9, D-10). When the MoU stops counting
 * as approved the offer to payroll is withdrawn and the incentive is HELD; when it counts again the amount is offered anew
 * (same source reference) and the incentive returns to APPROVED. If the run that holds the amount already awaits approval
 * the line cannot be released: the incentive stays APPROVED with a flag and the Director is told. An incentive that a run
 * has applied never changes. The decision is made from the MoU's actual state, so an event handled late or twice gives the
 * same answer, and the daily job calls the same method for every live incentive.
 */
@Service
public class HoldService {

    static final String HOLD_REASON = "MoU not approved";

    private final IncentiveRepository incentives;
    private final PayrollLink payroll;
    private final IncentiveRecorder recorder;
    private final MouApprovals mou;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    HoldService(
            IncentiveRepository incentives,
            PayrollLink payroll,
            IncentiveRecorder recorder,
            MouApprovals mou,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.incentives = incentives;
        this.payroll = payroll;
        this.recorder = recorder;
        this.mou = mou;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** What one look found. */
    public enum Outcome {
        UNCHANGED,
        HELD,
        RELEASED,
        FLAGGED,
        RETRIED_OFFER
    }

    /** Brings the School's incentive in line with its MoU. Safe to call any number of times. */
    @Transactional
    public Outcome reevaluate(UUID schoolId) {
        Optional<Incentive> found = incentives.findBySchoolId(schoolId);
        if (found.isEmpty()) {
            return Outcome.UNCHANGED;
        }
        Incentive incentive = incentives.lockById(found.get().getId()).orElse(null);
        if (incentive == null) {
            return Outcome.UNCHANGED;
        }
        LocalDate today = LocalDate.now(clock.withZone(IncentiveRecorder.BUSINESS_ZONE));
        boolean counts = mou.stateOn(schoolId, today).countsAsApproved();
        return switch (incentive.getStatus()) {
            case APPROVED -> counts ? approvedAndCounting(incentive) : approvedButNotCounting(incentive);
            case HELD -> counts ? release(incentive) : Outcome.UNCHANGED;
            default -> Outcome.UNCHANGED;
        };
    }

    private Outcome approvedAndCounting(Incentive incentive) {
        Outcome outcome = Outcome.UNCHANGED;
        if (incentive.isMouChanged()) {
            incentive.flagMouChanged(false, clock.instant());
            incentives.saveAndFlush(incentive);
            recorder.record(null, incentive.getId(), "RELEASED", "MoU approved again; the incentive stays with the pay run");
            outcome = Outcome.RELEASED;
        }
        OfferStatus status = payroll.status(incentive);
        if (status == OfferStatus.NONE || status == OfferStatus.WITHDRAWN) {
            // an offer that never happened or was refused: try again (the same source reference makes it safe)
            return payroll.offer(null, incentive) ? Outcome.RETRIED_OFFER : outcome;
        }
        return outcome;
    }

    private Outcome approvedButNotCounting(Incentive incentive) {
        boolean nothingLeftToPay = payroll.withdraw(null, incentive, HOLD_REASON);
        if (nothingLeftToPay) {
            // never offered, or taken back now (a draft run gives the line up)
            incentive.hold(HOLD_REASON, clock.instant());
            incentives.saveAndFlush(incentive);
            recorder.record(null, incentive.getId(), "HELD", HOLD_REASON);
            recorder.field(null, incentive.getId(), "status", IncentiveStatus.APPROVED, IncentiveStatus.HELD);
            return Outcome.HELD;
        }
        OfferStatus status = payroll.status(incentive);
        if (status == OfferStatus.HELD_AWAITING_APPROVAL && !incentive.isMouChanged()) {
            incentive.flagMouChanged(true, clock.instant());
            incentives.saveAndFlush(incentive);
            recorder.record(null, incentive.getId(), "MOU_CHANGED",
                    "The MoU no longer counts as approved, but the pay run holding this incentive awaits approval");
            publisher.publishEvent(new IncentiveMouChanged(incentive.getId(), incentive.getSchoolId(), incentive.getEarnerUserId()));
            return Outcome.FLAGGED;
        }
        // already applied by a run: it is paid and never changes
        return Outcome.UNCHANGED;
    }

    private Outcome release(Incentive incentive) {
        if (!payroll.offer(null, incentive)) {
            return Outcome.UNCHANGED;
        }
        incentive.release(clock.instant());
        incentives.saveAndFlush(incentive);
        recorder.record(null, incentive.getId(), "RELEASED", "MoU approved again; offered to payroll");
        recorder.field(null, incentive.getId(), "status", IncentiveStatus.HELD, IncentiveStatus.APPROVED);
        return Outcome.RELEASED;
    }
}
