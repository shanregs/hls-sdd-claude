package com.hls.attendance.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which dates are working days, and what "today" is for business purposes (spec 008 amendment A6, added for
 * spec 013a). Payroll asks here instead of reimplementing the weekly-off and holiday rules, so every module agrees
 * on the same working days.
 */
public interface WorkingCalendar {

    /** Today's date in the business time zone (Asia/Kolkata), which differs from the UTC date in the early hours. */
    LocalDate businessToday();

    /**
     * The working days from {@code from} to {@code to}, both included, oldest first: not a weekly off day of the
     * School and not a non-working date. A null School means the default calendar. Empty when {@code from} is after
     * {@code to}.
     */
    List<LocalDate> workingDays(UUID schoolIdOrNull, LocalDate from, LocalDate to);

    /**
     * Bulk form: the working days of each given School (a School without an override gets the default calendar's
     * days). A null element stands for the default calendar and is keyed by null in the result. The number of
     * queries does not depend on the number of Schools.
     */
    Map<UUID, List<LocalDate>> workingDaysBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to);
}
