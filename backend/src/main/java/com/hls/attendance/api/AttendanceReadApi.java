package com.hls.attendance.api;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Read-only attendance facts for later modules such as leave, payroll and reports (spec 008). Callers
 * use this instead of recomputing attendance, so every module agrees on the same figures. A Teacher
 * the module does not know yields zero figures and no marks, never an error.
 */
public interface AttendanceReadApi {

    /** The month's rollup: frozen at lock time when the month is locked, otherwise computed live. */
    RollupView rollupOf(UUID teacherId, YearMonth month);

    /** The Teacher's marks in the month, oldest date first. */
    List<MarkView> marksOf(UUID teacherId, YearMonth month);

    /** True while the Teacher-month is locked; a reopened month is not locked. */
    boolean isLocked(UUID teacherId, YearMonth month);
}
