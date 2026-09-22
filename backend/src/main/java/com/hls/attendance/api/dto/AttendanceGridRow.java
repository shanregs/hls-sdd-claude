package com.hls.attendance.api.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** FR-019/FR-020. One Teacher's row in the attendance grid. */
public record AttendanceGridRow(UUID teacherId, String teacherName, Map<LocalDate, GridCell> cells) {
}
