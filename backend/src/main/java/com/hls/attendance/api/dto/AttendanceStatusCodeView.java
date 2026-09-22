package com.hls.attendance.api.dto;

import java.math.BigDecimal;

/** FR-005. The read-side shape of one Attendance Status Code. */
public record AttendanceStatusCodeView(String code, String label, AttendanceCategory category, BigDecimal weight, boolean active) {
}
