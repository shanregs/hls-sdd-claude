package com.hls.incentive.api;

import java.util.UUID;

/**
 * An incentive was created and waits for a decision (spec 030). {@code zoneManagerUserId} is the School's Zone Manager,
 * null when the School has none. Published inside the earning transaction; {@code notification} listens.
 */
public record IncentiveEarned(UUID incentiveId, UUID schoolId, UUID earnerUserId, UUID zoneManagerUserId) {}
