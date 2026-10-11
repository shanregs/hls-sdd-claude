package com.hls.attendance.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.RollupCalculator.WeeklyOffRules;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The public working-day answers, over the same calendar the rollups and the grid use. */
@Service
class WorkingCalendarImpl implements WorkingCalendar {

    private final CalendarService calendar;
    private final BusinessCalendar business;

    WorkingCalendarImpl(CalendarService calendar, BusinessCalendar business) {
        this.calendar = calendar;
        this.business = business;
    }

    @Override
    public LocalDate businessToday() {
        return business.today();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> workingDays(UUID schoolIdOrNull, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return List.of();
        }
        return days(calendar.rules(), calendar.nonWorkingDates(from, to), schoolIdOrNull, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<LocalDate>> workingDaysBySchool(Collection<UUID> schoolIds, LocalDate from, LocalDate to) {
        Map<UUID, List<LocalDate>> result = new HashMap<>();
        if (from.isAfter(to)) {
            schoolIds.forEach(id -> result.put(id, List.of()));
            return result;
        }
        // one read of the (cached) rules and of the holidays, shared by every School
        WeeklyOffRules rules = calendar.rules();
        Set<LocalDate> nonWorking = calendar.nonWorkingDates(from, to);
        for (UUID schoolId : schoolIds) {
            if (!result.containsKey(schoolId)) {
                result.put(schoolId, days(rules, nonWorking, schoolId, from, to));
            }
        }
        return result;
    }

    private static List<LocalDate> days(
            WeeklyOffRules rules, Set<LocalDate> nonWorking, UUID schoolId, LocalDate from, LocalDate to) {
        // the rules keep their overrides in an immutable map, which refuses a null key
        Set<DayOfWeek> weeklyOff = schoolId == null ? rules.defaults() : rules.forSchool(schoolId);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!weeklyOff.contains(date.getDayOfWeek()) && !nonWorking.contains(date)) {
                days.add(date);
            }
        }
        return days;
    }
}
