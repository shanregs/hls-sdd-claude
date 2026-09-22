package com.hls.attendance.api;

import com.hls.attendance.api.dto.AttendanceMarkView;
import com.hls.attendance.api.dto.MarkAttendanceRequest;
import com.hls.attendance.api.dto.MarkedByRole;
import java.util.UUID;

/**
 * Public write surface for Attendance Marks (FR-001/FR-002/FR-004/FR-006/FR-023/FR-024).
 * The single write path every mark goes through, self or on-behalf, including the
 * grid's inline edit (FR-025) — no second write path exists (research.md §9).
 */
public interface AttendanceMarkCommands {

    AttendanceMarkView markAttendance(UUID teacherId, MarkAttendanceRequest request, UUID actingUserId, MarkedByRole actingRole);
}
