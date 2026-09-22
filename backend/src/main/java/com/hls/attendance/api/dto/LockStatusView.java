package com.hls.attendance.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** FR-011/FR-013. {@code lockedAt}/{@code lockedBy} are null when {@code status = UNLOCKED}. */
public record LockStatusView(UUID teacherId, String period, LockStatus status, Instant lockedAt, UUID lockedBy, List<ReopenEntry> reopenHistory) {
}
