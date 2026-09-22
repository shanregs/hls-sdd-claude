package com.hls.teacher.api;

import com.hls.teacher.api.dto.SalaryAsOfAnswer;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read surface for salary history (FR-005/006/007). Both methods
 * return {@link SalaryAsOfAnswer#notYetRecorded()}, never an error or a
 * misleading zero, when nothing matches.
 */
public interface TeacherSalaryQueries {

    /** FR-005: the amount effective today (or the latest one before today). */
    SalaryAsOfAnswer currentSalary(UUID teacherId);

    /** FR-006/007: the amount effective on {@code date}, or not-yet-recorded if none. */
    SalaryAsOfAnswer salaryAsOf(UUID teacherId, LocalDate date);
}
