package com.hls.attendance.api;

import com.hls.attendance.api.dto.AttendanceGridView;
import java.util.UUID;

/**
 * Public read surface for the attendance grid (User Story 5, FR-019/FR-020/FR-021).
 * {@code callerCanEdit} is {@code true} only for the Manager themself viewing their
 * own portfolio, or an Admin (FR-024/FR-025) — {@code false} for a Director, who
 * gains no new write authority from the grid (research.md §9).
 */
public interface AttendanceGridQueries {

    AttendanceGridView gridForManager(UUID managerId, String period, boolean callerCanEdit);

    AttendanceGridView gridForAllTeachers(String period, boolean callerCanEdit);
}
