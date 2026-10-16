package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.workingdays.internal.WorkingDaysService.MonthOutcome;
import com.hls.identity.user.Role;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The year entry screen (spec 038): the twelve months of one School and year, and one Save for the months the user
 * changed. Each month is saved on its own by {@link WorkingDaysService#saveMonth}, so valid months stay stored when
 * others are refused, and a repeated Save changes nothing.
 */
@Service
public class YearEntryService {

    /** One month of the year: the stored figure (all null when none) and the calendar-derived hint. */
    public record MonthState(
            int month,
            int daysInMonth,
            int calendarDerived,
            UUID id,
            BigDecimal workingDays,
            Long version,
            String updatedBy,
            Instant updatedAt) {}

    public record YearView(UUID schoolId, String schoolName, String zoneName, int year, List<MonthState> months) {}

    public record MonthRequest(Integer month, BigDecimal workingDays, Long version) {}

    public record SaveRequest(UUID schoolId, Integer year, List<MonthRequest> months) {}

    public record MonthResult(int month, String status, String reason, String message, MonthState current) {}

    public record SaveResult(int created, int updated, int unchanged, int notSaved, List<MonthResult> months) {}

    private static final int MAX_MONTHS = 12;

    private final SchoolWorkingDaysRepository rows;
    private final WorkingDaysService service;
    private final WorkingDaysScope scope;
    private final SchoolDirectory schools;
    private final WorkingCalendar calendar;
    private final WorkingDaysViewFactory views;

    public YearEntryService(
            SchoolWorkingDaysRepository rows,
            WorkingDaysService service,
            WorkingDaysScope scope,
            SchoolDirectory schools,
            WorkingCalendar calendar,
            WorkingDaysViewFactory views) {
        this.rows = rows;
        this.service = service;
        this.scope = scope;
        this.schools = schools;
        this.calendar = calendar;
        this.views = views;
    }

    public YearView loadYear(UUID actor, Set<Role> roles, UUID schoolId, Integer year) {
        if (schoolId == null || year == null) {
            throw new InvalidInputException("School and year are required.");
        }
        SchoolInfo school = checkSchoolAndYear(actor, roles, schoolId, year);
        Map<Integer, SchoolWorkingDaysRow> stored = storedByMonth(schoolId, year);
        return new YearView(
                schoolId,
                school.name(),
                zoneName(school),
                year,
                states(schoolId, year, stored, derived(schoolId, year), names(stored.values())));
    }

    public SaveResult saveYear(UUID actor, Set<Role> roles, SaveRequest request, boolean canCreate, boolean canEdit) {
        if (request == null || request.schoolId() == null || request.year() == null || request.months() == null) {
            throw new InvalidInputException("School, year and months are required.");
        }
        checkMonths(request.months());
        UUID schoolId = request.schoolId();
        int year = request.year();
        checkSchoolAndYear(actor, roles, schoolId, year);

        List<MonthOutcome> outcomes = new ArrayList<>();
        for (MonthRequest m : request.months()) {
            outcomes.add(service.saveMonth(
                    actor, roles, schoolId, year, m.month(), m.workingDays(), m.version(), canCreate, canEdit));
        }

        // the answer carries each month as it is now, with names and hints read once for the whole answer
        Map<Integer, SchoolWorkingDaysRow> now = storedByMonth(schoolId, year);
        Map<Integer, MonthState> current = states(schoolId, year, now, derived(schoolId, year), names(now.values())).stream()
                .collect(Collectors.toMap(MonthState::month, Function.identity()));
        List<MonthResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int notSaved = 0;
        for (int i = 0; i < outcomes.size(); i++) {
            MonthOutcome outcome = outcomes.get(i);
            int month = request.months().get(i).month();
            switch (outcome.status()) {
                case "CREATED" -> created++;
                case "UPDATED" -> updated++;
                case "UNCHANGED" -> unchanged++;
                default -> notSaved++;
            }
            results.add(new MonthResult(month, outcome.status(), outcome.reason(), outcome.message(), current.get(month)));
        }
        return new SaveResult(created, updated, unchanged, notSaved, results);
    }

    private static void checkMonths(List<MonthRequest> months) {
        if (months.isEmpty() || months.size() > MAX_MONTHS) {
            throw new InvalidInputException("Send between 1 and 12 months.");
        }
        Set<Integer> seen = new HashSet<>();
        for (MonthRequest m : months) {
            if (m == null || m.month() == null || m.month() < 1 || m.month() > 12) {
                throw new InvalidInputException("Month must be 1 to 12.");
            }
            if (!seen.add(m.month())) {
                throw new InvalidInputException(
                        "Month " + WorkingDaysRules.monthName(m.month()) + " was sent more than once.");
            }
        }
    }

    private SchoolInfo checkSchoolAndYear(UUID actor, Set<Role> roles, UUID schoolId, int year) {
        scope.requireSchool(actor, roles, schoolId);
        service.requireSchoolExists(schoolId);
        WorkingDaysRules.checkNewYear(year, calendar.businessToday());
        return schools.school(schoolId).orElseThrow(() -> new InvalidInputException("School not found."));
    }

    private Map<Integer, SchoolWorkingDaysRow> storedByMonth(UUID schoolId, int year) {
        return rows.findBySchoolIdAndYear(schoolId, year).stream()
                .collect(Collectors.toMap(SchoolWorkingDaysRow::getMonth, Function.identity(), (a, b) -> a));
    }

    private Map<YearMonth, Integer> derived(UUID schoolId, int year) {
        return CalendarCounts.byMonth(calendar, List.of(schoolId), YearMonth.of(year, 1), YearMonth.of(year, 12))
                .getOrDefault(schoolId, Map.of());
    }

    private Map<UUID, String> names(java.util.Collection<SchoolWorkingDaysRow> stored) {
        List<UUID> ids = stored.stream().map(SchoolWorkingDaysRow::getUpdatedBy).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : views.userNames(ids);
    }

    private String zoneName(SchoolInfo school) {
        if (school.zoneId() == null) {
            return "";
        }
        return schools.zones(List.of(school.zoneId())).stream()
                .findFirst()
                .map(SchoolDirectory.ZoneInfo::name)
                .orElse("");
    }

    private static List<MonthState> states(
            UUID schoolId,
            int year,
            Map<Integer, SchoolWorkingDaysRow> stored,
            Map<YearMonth, Integer> derived,
            Map<UUID, String> names) {
        List<MonthState> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            SchoolWorkingDaysRow row = stored.get(month);
            months.add(new MonthState(
                    month,
                    ym.lengthOfMonth(),
                    derived.getOrDefault(ym, 0),
                    row == null ? null : row.getId(),
                    row == null ? null : row.getWorkingDays(),
                    row == null ? null : row.getVersion(),
                    row == null ? null : names.getOrDefault(row.getUpdatedBy(), "Unknown user"),
                    row == null ? null : row.getUpdatedAt()));
        }
        return months;
    }
}
