package com.hls.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/** FR-005/FR-006: one past-or-current assignment period for a given School/Teacher. */
public record AssignmentHistoryEntry(
        UUID id, UUID managerId, Instant effectiveFrom, Instant effectiveTo, UUID assignedBy, Instant assignedAt) {
}
