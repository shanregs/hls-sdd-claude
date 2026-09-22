package com.hls.teacher.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** FR-003. The read-side shape of one recorded salary change. */
public record TeacherSalaryHistoryView(UUID id, UUID teacherId, BigDecimal amount, LocalDate effectiveFrom) {
}
