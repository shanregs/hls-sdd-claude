package com.hls.teacher.api;

import com.hls.teacher.api.dto.TeacherSalaryHistoryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public write surface for salary history (FR-003). Admin-only — enforced
 * by {@code TeacherController} reading the caller's role from the JWT
 * directly, not by this interface.
 */
public interface TeacherSalaryCommands {

    /** {@code effectiveFrom} null defaults to today. */
    TeacherSalaryHistoryView recordSalaryChange(UUID teacherId, BigDecimal amount, LocalDate effectiveFrom, UUID actingUserId);
}
