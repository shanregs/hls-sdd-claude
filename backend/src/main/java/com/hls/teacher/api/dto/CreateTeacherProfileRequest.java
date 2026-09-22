package com.hls.teacher.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** FR-001. Every field here is required (spec.md AC2 — a missing required detail must be rejected). */
public record CreateTeacherProfileRequest(
        @NotBlank String name,
        @NotBlank String phone,
        String email,
        @NotNull BigDecimal hlsOfferedSalary,
        @NotNull TeacherStatus status) {
}
