package com.hls.school.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-003/FR-004. The result of assigning or reassigning a School's Zone —
 * {@code id} is the assignment row's own id, needed by a later reassignment's
 * {@code endsAssignmentId} (FR-005). Same shape/rationale as
 * {@code organization.api.dto.CurrentAssignment}.
 */
public record CurrentSchoolZoneAssignment(UUID id, UUID zoneId, Instant effectiveFrom) {
}
