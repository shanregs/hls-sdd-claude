package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The number of working dates the spec 008 calendar gives each School in each month: one {@link WorkingCalendar} call
 * for the whole range, then bucketed by month in memory, so the cost does not depend on the number of Schools or months
 * asked for.
 */
final class CalendarCounts {

    private CalendarCounts() {}

    /** School id to month to working dates; a School the calendar returned nothing for counts 0 in every month. */
    static Map<UUID, Map<YearMonth, Integer>> byMonth(
            WorkingCalendar calendar, Collection<UUID> schoolIds, YearMonth from, YearMonth to) {
        Map<UUID, Map<YearMonth, Integer>> result = new HashMap<>();
        if (schoolIds.isEmpty() || from.isAfter(to)) {
            return result;
        }
        Map<UUID, List<LocalDate>> dates = calendar.workingDaysBySchool(schoolIds, from.atDay(1), to.atEndOfMonth());
        for (UUID schoolId : schoolIds) {
            Map<YearMonth, Integer> months = new HashMap<>();
            for (LocalDate date : dates.getOrDefault(schoolId, List.of())) {
                months.merge(YearMonth.from(date), 1, Integer::sum);
            }
            result.put(schoolId, months);
        }
        return result;
    }
}
