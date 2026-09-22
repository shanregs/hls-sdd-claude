package com.hls.attendance.api.dto;

import java.time.Instant;
import java.util.UUID;

/** FR-013. One reopen-and-correct cycle. {@code relockedAt}/{@code relockedBy} are null while still reopened. */
public record ReopenEntry(String reason, Instant reopenedAt, UUID reopenedBy, Instant relockedAt, UUID relockedBy) {
}
