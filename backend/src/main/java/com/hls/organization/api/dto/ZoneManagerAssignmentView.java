package com.hls.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/** FR-001. The read-side shape of one Zone-Manager coverage assignment. */
public record ZoneManagerAssignmentView(UUID id, UUID zoneId, UUID managerId, Instant effectiveFrom) {
}
