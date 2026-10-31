package com.hls.payroll.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How an approved Manager incentive reaches the next pay run (spec 030, amendment A to 013b; contracts/
 * payroll-incentive-intake.md). Additive and shaped like {@link ReimbursementIntake}: it creates, withdraws and reads
 * {@code INCENTIVE} adjustments through the rules the run already has (attached to a draft at compute, released on
 * cancel, applied at approval) and never touches an approved run.
 */
public interface IncentiveIntake {

    enum OfferStatus {
        NONE,
        WAITING,
        HELD_BY_DRAFT,
        HELD_AWAITING_APPROVAL,
        APPLIED,
        WITHDRAWN
    }

    /**
     * Records a waiting incentive for a payroll Manager. Idempotent per {@code sourceRef} (an existing, non-withdrawn one
     * is kept); an offer after a withdrawal creates a new waiting adjustment. Refused with a reason when the person is not
     * a payroll Manager or the amount is not above zero.
     */
    ReimbursementIntake.OfferResult offer(UUID actor, UUID managerId, BigDecimal amount, String reason, String sourceRef);

    /**
     * Withdraws the incentive of the source. Returns true when nothing is left to pay (withdrawn now, already withdrawn,
     * or never offered); false when the run holding it is awaiting approval or it is already applied.
     */
    boolean withdraw(UUID actor, String sourceRef, String reason);

    OfferStatus status(String sourceRef);
}
