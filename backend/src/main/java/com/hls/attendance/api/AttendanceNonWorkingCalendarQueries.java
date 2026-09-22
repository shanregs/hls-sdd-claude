package com.hls.attendance.api;

import com.hls.attendance.api.dto.NonWorkingDateView;
import java.util.List;

/** Public read surface for the shared Non-Working Calendar (FR-022). */
public interface AttendanceNonWorkingCalendarQueries {

    List<NonWorkingDateView> datesForMonth(String period);
}
