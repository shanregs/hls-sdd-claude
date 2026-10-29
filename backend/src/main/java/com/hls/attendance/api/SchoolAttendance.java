package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Teacher attendance rolled up by School for a month, for the School report (spec 014b A6). The Attendance report's Schools
 * section reads the same grouping, so the two cannot differ: a Teacher counts at the School of their placement covering the
 * last day of the month (else the latest to start), and a half day counts 0.5. It checks no permission; callers apply their own
 * scope.
 */
public interface SchoolAttendance {

    /** {@code teachers} is the number of Teachers counted at the School; the rest are day values. */
    record SchoolRollup(
            UUID schoolId, long teachers, BigDecimal working, BigDecimal present, BigDecimal absent, BigDecimal leave, BigDecimal lossOfPay) {}

    /** Only Schools with at least one Teacher counted have an entry; an empty set gives an empty map. */
    Map<UUID, SchoolRollup> rollupsBySchool(Collection<UUID> schoolIds, YearMonth month);
}
