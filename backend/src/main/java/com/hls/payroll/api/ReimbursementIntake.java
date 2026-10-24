package com.hls.payroll.api;

import com.hls.payroll.api.PayRules.Kind;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * How an approved expense claim reaches the next pay run (spec 015, contracts/payroll-reimbursement.md). Additive: it
 * creates, withdraws and reads {@code REIMBURSEMENT} adjustments through the rules the run already has (attached to a
 * draft at compute, released on cancel, applied at approval) and never touches an approved run.
 */
public interface ReimbursementIntake {

    enum OfferStatus {
        NONE,
        WAITING,
        HELD_BY_DRAFT,
        HELD_AWAITING_APPROVAL,
        APPLIED,
        WITHDRAWN
    }

    /** {@code accepted} false carries the reason in {@code refusal}. */
    record OfferResult(boolean accepted, String refusal) {

        public static OfferResult ok() {
            return new OfferResult(true, null);
        }

        public static OfferResult refused(String reason) {
            return new OfferResult(false, reason);
        }
    }

    /**
     * Records a waiting reimbursement for the payroll person behind the user (a Manager, a Teacher, or an Admin or
     * Director). Idempotent per {@code sourceRef}. Refused with a reason when the user is not a payroll person.
     */
    OfferResult offerForUser(UUID actor, UUID userId, BigDecimal amount, String reason, String sourceRef);

    /** The same for a known payroll person. */
    OfferResult offer(UUID actor, Kind kind, UUID personId, BigDecimal amount, String reason, String sourceRef);

    /**
     * Withdraws the reimbursement of the source. Returns true when nothing is left to pay (withdrawn now, already
     * withdrawn, or never offered); false when the run holding it is awaiting approval or it is already applied.
     */
    boolean withdraw(UUID actor, String sourceRef, String reason);

    OfferStatus status(String sourceRef);
}
