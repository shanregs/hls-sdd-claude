package com.hls.attendance.api;

import com.hls.attendance.api.dto.LockStatusView;
import java.util.UUID;

/**
 * Public write surface for locking/reopening a teacher-month (FR-011/FR-013).
 * Director-only for both — locking stands in for an actual payroll run
 * (research.md §2), and Constitution Principle II's payroll-approval-adjacent
 * authority restriction applies at both ends of the cycle (`/speckit-analyze`
 * finding C1/I1), not only at reopen.
 */
public interface AttendanceLockCommands {

    LockStatusView lockMonth(UUID teacherId, String period, UUID actingUserId);

    LockStatusView reopenMonth(UUID teacherId, String period, String reason, UUID actingUserId);
}
