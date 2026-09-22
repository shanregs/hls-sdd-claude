package com.hls.attendance.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-001/FR-002/FR-004/FR-024. {@code fractionalValue} defaults to {@code 1.00} when omitted
 * (data-model.md). {@code teacherId} is never part of this body — it comes from the JWT
 * ({@code /attendance/me/marks}) or the path ({@code /attendance/teachers/{teacherId}/marks}),
 * so a caller can't spoof whose attendance they're marking.
 */
public record MarkAttendanceRequest(
        @NotNull LocalDate markDate,
        @NotNull UUID schoolId,
        @NotNull String statusCode,
        BigDecimal fractionalValue,
        EvidenceInput evidence) {
}
