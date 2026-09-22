package com.hls.attendance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** FR-005. Admin-or-Director only (enforced by controller). */
public record CreateStatusCodeRequest(
        @NotBlank String code,
        @NotBlank String label,
        @NotNull AttendanceCategory category,
        @NotNull BigDecimal weight) {
}
