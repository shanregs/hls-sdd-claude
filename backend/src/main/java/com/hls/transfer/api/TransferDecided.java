package com.hls.transfer.api;

import java.time.LocalDate;
import java.util.UUID;

/** A request was approved, rejected or cancelled (spec 027). {@code outcome} is APPROVED, REJECTED or CANCELLED. */
public record TransferDecided(
        UUID requestId,
        String kind,
        String outcome,
        UUID teacherId,
        UUID replacementTeacherId,
        UUID fromSchoolId,
        UUID toSchoolId,
        UUID requestedBy,
        UUID decidedBy,
        LocalDate effectiveOn) {}
