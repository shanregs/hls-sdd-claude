package com.hls.attendance.api;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The working days a School counts in a month (spec 037). Payroll, Manager attendance and the attendance rollups ask
 * here instead of reading the master's tables, so every module agrees on the same figure.
 *
 * <p>A month with a stored figure answers it ({@link Source#STORED}), even when the calendar would give another count;
 * a month without one answers the number of working dates of that School's spec 008 calendar
 * ({@link Source#CALENDAR_DERIVED}). A missing row is neither an error nor zero.
 *
 * <p>This is a module-to-module read: it checks no screen permission and holds no person's data. The number of queries
 * does not depend on the number of Schools asked for.
 */
public interface SchoolWorkingDays {

    enum Source {
        /** A figure entered on the School Working Days screen. */
        STORED,
        /** No figure entered: the count of working dates in the School's calendar for that month. */
        CALENDAR_DERIVED
    }

    /** The working days a School counts in a month; half days are possible, so the figure is a decimal. */
    record MonthDays(UUID schoolId, YearMonth month, BigDecimal workingDays, Source source) {}

    /** One School, one month. Never empty: it falls back to the calendar-derived count. */
    MonthDays forMonth(UUID schoolId, YearMonth month);

    /** Many Schools, one month: one entry per School id. */
    Map<UUID, MonthDays> forMonth(Collection<UUID> schoolIds, YearMonth month);

    /** Many Schools, a whole year: twelve entries per School, January first. */
    Map<UUID, List<MonthDays>> forYear(Collection<UUID> schoolIds, int year);

    /** Many Schools, the months from {@code from} to {@code to}, both included, oldest first. */
    Map<UUID, List<MonthDays>> forRange(Collection<UUID> schoolIds, YearMonth from, YearMonth to);
}
