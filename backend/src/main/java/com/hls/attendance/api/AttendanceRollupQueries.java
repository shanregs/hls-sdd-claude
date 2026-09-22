package com.hls.attendance.api;

import com.hls.attendance.api.dto.MonthlyAttendanceRollupView;
import java.util.UUID;

/** Public read surface for the monthly attendance rollup (FR-007/FR-008/FR-009/FR-010). */
public interface AttendanceRollupQueries {

    MonthlyAttendanceRollupView rollupForMonth(UUID teacherId, String period);
}
