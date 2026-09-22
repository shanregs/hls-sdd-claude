package com.hls.attendance.api;

import com.hls.attendance.api.dto.AttendanceStatusCodeView;
import java.util.List;

/** Public read surface for the configurable Attendance Status Codes (FR-005). */
public interface AttendanceStatusCodeQueries {

    List<AttendanceStatusCodeView> listActiveCodes();
}
