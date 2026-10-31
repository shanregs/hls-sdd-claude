package com.hls.incentive.api;

import java.util.Set;
import java.util.UUID;

/**
 * An incentive was created and waits for a decision (spec 030). {@code zoneManagerUserIds} are all the people who may
 * decide it besides the Directors: every Zone Manager of the School's Zone; empty when there is none. Published inside the earning transaction; {@code notification} listens.
 */
public record IncentiveEarned(UUID incentiveId, UUID schoolId, UUID earnerUserId, Set<UUID> zoneManagerUserIds) {}
