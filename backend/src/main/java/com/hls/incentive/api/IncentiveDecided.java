package com.hls.incentive.api;

import java.util.UUID;

/**
 * An incentive was approved or rejected (spec 030). {@code outcome} is APPROVED or REJECTED; {@code reason} is the
 * rejection reason, else null. The earner hears of it unless they decided it themselves.
 */
public record IncentiveDecided(
        UUID incentiveId,
        UUID schoolId,
        UUID earnerUserId,
        String outcome,
        String reason,
        UUID decidedBy,
        boolean approvedByEarner) {}
