package com.hls.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The result of a successful assign/reassign command (FR-001/002/004/010). */
public record CurrentAssignment(UUID id, UUID managerId, Instant effectiveFrom) {
}
