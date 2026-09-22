package com.hls.attendance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** FR-022. Admin-only. */
public record AddNonWorkingDateRequest(@NotNull LocalDate date, @NotBlank String label) {
}
