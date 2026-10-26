package com.hls.schoolbilling.api;

import java.util.UUID;

/**
 * A MoU was approved or rejected (spec 031). {@code outcome} is APPROVED or REJECTED; {@code reason} is the rejection
 * reason, else null; {@code termsAmended} says the approver corrected terms in this round.
 */
public record MouDecided(
        UUID contractId,
        UUID schoolId,
        String outcome,
        String reason,
        UUID submittedBy,
        UUID decidedBy,
        boolean termsAmended) {}
