package com.hls.schoolbilling.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * The working days billing needs, owned by {@code schoolbilling} and implemented by {@code attendance} (the same direction as
 * {@code TeacherPlacementSource}): billing never depends on attendance, so the module graph has no cycle (spec 022 research R1).
 */
public interface BillingCalendar {

    /** The working days the School counts in the month (spec 037: the stored figure, else the calendar-derived count). */
    BigDecimal monthWorkingDays(UUID schoolId, YearMonth month);

    /** How many dates from {@code from} to {@code to}, both included, are working days of the School (spec 008 calendar). */
    int workingDaysBetween(UUID schoolId, LocalDate from, LocalDate to);
}
