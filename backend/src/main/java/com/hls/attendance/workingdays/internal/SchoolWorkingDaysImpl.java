package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.SchoolWorkingDays;
import com.hls.attendance.api.WorkingCalendar;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The published answer (spec 037 FR-011 to FR-014): one query for the stored rows of every School in the range and one
 * calendar read for the same range, whatever the number of Schools, so every consumer gets the same figure.
 */
@Service
@Transactional(readOnly = true)
class SchoolWorkingDaysImpl implements SchoolWorkingDays {

    private final SchoolWorkingDaysRepository rows;
    private final WorkingCalendar calendar;

    SchoolWorkingDaysImpl(SchoolWorkingDaysRepository rows, WorkingCalendar calendar) {
        this.rows = rows;
        this.calendar = calendar;
    }

    @Override
    public MonthDays forMonth(UUID schoolId, YearMonth month) {
        return forMonth(List.of(schoolId), month).get(schoolId);
    }

    @Override
    public Map<UUID, MonthDays> forMonth(Collection<UUID> schoolIds, YearMonth month) {
        Map<UUID, MonthDays> result = new LinkedHashMap<>();
        forRange(schoolIds, month, month).forEach((id, list) -> result.put(id, list.get(0)));
        return result;
    }

    @Override
    public Map<UUID, List<MonthDays>> forYear(Collection<UUID> schoolIds, int year) {
        return forRange(schoolIds, YearMonth.of(year, 1), YearMonth.of(year, 12));
    }

    @Override
    public Map<UUID, List<MonthDays>> forRange(Collection<UUID> schoolIds, YearMonth from, YearMonth to) {
        Map<UUID, List<MonthDays>> result = new LinkedHashMap<>();
        List<UUID> ids = schoolIds.stream().distinct().toList();
        if (ids.isEmpty() || from.isAfter(to)) {
            return result;
        }
        Map<UUID, Map<YearMonth, BigDecimal>> stored = new HashMap<>();
        for (SchoolWorkingDaysRow row : rows.findInRange(ids, index(from), index(to))) {
            stored.computeIfAbsent(row.getSchoolId(), k -> new HashMap<>())
                    .put(YearMonth.of(row.getYear(), row.getMonth()), row.getWorkingDays());
        }
        Map<UUID, Map<YearMonth, Integer>> derived = CalendarCounts.byMonth(calendar, ids, from, to);
        for (UUID id : ids) {
            List<MonthDays> months = new ArrayList<>();
            for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
                BigDecimal figure = stored.getOrDefault(id, Map.of()).get(month);
                if (figure != null) {
                    months.add(new MonthDays(id, month, figure, Source.STORED));
                } else {
                    int count = derived.getOrDefault(id, Map.of()).getOrDefault(month, 0);
                    months.add(new MonthDays(id, month, BigDecimal.valueOf(count).setScale(1), Source.CALENDAR_DERIVED));
                }
            }
            result.put(id, months);
        }
        return result;
    }

    private static int index(YearMonth month) {
        return month.getYear() * 12 + month.getMonthValue() - 1;
    }
}
