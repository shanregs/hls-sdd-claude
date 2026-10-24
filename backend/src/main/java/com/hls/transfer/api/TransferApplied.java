package com.hls.transfer.api;

import java.time.LocalDate;
import java.util.UUID;

/** An approved request took effect (its date arrived); published once by the daily job (spec 027). */
public record TransferApplied(
        UUID requestId,
        String kind,
        UUID teacherId,
        UUID replacementTeacherId,
        UUID fromSchoolId,
        UUID toSchoolId,
        LocalDate effectiveOn) {}
