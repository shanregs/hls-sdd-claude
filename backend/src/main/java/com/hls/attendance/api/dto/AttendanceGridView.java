package com.hls.attendance.api.dto;

import java.time.LocalDate;
import java.util.List;

/** FR-019. {@code days} is every calendar day in the period, so column count always matches the month's actual length. */
public record AttendanceGridView(String period, List<LocalDate> days, List<AttendanceGridRow> rows) {
}
