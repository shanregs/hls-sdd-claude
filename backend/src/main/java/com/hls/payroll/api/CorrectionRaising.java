package com.hls.payroll.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How another module asks for a Correction on a person's next pay run (spec 017b FR-020). Additive: it creates and reads
 * {@code CORRECTION} adjustments through the rules the run already has (attached to a draft at compute, released on
 * cancel, applied at approval) and never touches an approved run. The module that asked settles its own record when
 * {@link PayrollAdjustmentApplied} arrives with its {@code sourceRef}.
 */
public interface CorrectionRaising {

    enum Status {
        NONE,
        WAITING,
        HELD_BY_DRAFT,
        HELD_AWAITING_APPROVAL,
        APPLIED,
        WITHDRAWN
    }

    /** {@code accepted} false carries the reason in {@code refusal}. */
    record Result(boolean accepted, String refusal) {

        public static Result ok() {
            return new Result(true, null);
        }

        public static Result refused(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * Records a waiting Correction (positive pays more, negative recovers) for a Teacher. Idempotent per
     * {@code sourceRef}. Refused with a reason when the amount is zero or the Teacher is unknown.
     */
    Result raiseForTeacher(UUID actor, UUID teacherId, BigDecimal amount, String reason, String sourceRef);

    Status status(String sourceRef);
}
