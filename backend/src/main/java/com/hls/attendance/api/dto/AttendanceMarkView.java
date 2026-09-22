package com.hls.attendance.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** FR-001-FR-006. The read-side shape of one Attendance Mark, including evidence and attribution. */
public record AttendanceMarkView(
        UUID id,
        UUID teacherId,
        LocalDate markDate,
        UUID schoolId,
        String statusCode,
        BigDecimal fractionalValue,
        EvidenceInput evidence,
        UUID markedBy,
        MarkedByRole markedByRole,
        Instant markedAt) {
}
