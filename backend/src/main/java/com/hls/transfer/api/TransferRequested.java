package com.hls.transfer.api;

import java.time.LocalDate;
import java.util.UUID;

/** A transfer or replacement request was raised (spec 027). Published in the raiser's transaction. */
public record TransferRequested(
        UUID requestId, String kind, UUID teacherId, UUID fromSchoolId, UUID toSchoolId, UUID requestedBy, LocalDate effectiveOn) {}
