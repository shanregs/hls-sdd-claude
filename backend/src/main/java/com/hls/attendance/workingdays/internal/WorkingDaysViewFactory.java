package com.hls.attendance.workingdays.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the grid rows of the screen: the stored figure with the School, Zone, days in the month, the calendar-derived
 * count for reference and the name of whoever last changed it. School names, user names and the calendar are each read
 * once per request, whatever the number of rows.
 */
@Component
public class WorkingDaysViewFactory {

    /** One row of the grid (contracts/school-working-days-api.md). */
    public record RowView(
            UUID id,
            UUID schoolId,
            String schoolName,
            UUID zoneId,
            String zoneName,
            int year,
            int month,
            BigDecimal workingDays,
            int daysInMonth,
            int calendarDerived,
            String updatedBy,
            Instant updatedAt,
            Long version) {}

    private final SchoolDirectory schools;
    private final WorkingCalendar calendar;
    private final AppUserRepository users;

    public WorkingDaysViewFactory(SchoolDirectory schools, WorkingCalendar calendar, AppUserRepository users) {
        this.schools = schools;
        this.calendar = calendar;
        this.users = users;
    }

    public List<RowView> views(Collection<SchoolWorkingDaysRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> schoolIds = rows.stream().map(SchoolWorkingDaysRow::getSchoolId).distinct().toList();
        Map<UUID, SchoolInfo> schoolById =
                schools.schools(schoolIds).stream().collect(Collectors.toMap(SchoolInfo::id, s -> s));
        Map<UUID, String> zoneNames = schools
                .zones(schoolById.values().stream().map(SchoolInfo::zoneId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ZoneInfo::id, ZoneInfo::name));
        Map<UUID, String> userNames = users
                .findAllById(rows.stream().map(SchoolWorkingDaysRow::getUpdatedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        YearMonth first = rows.stream()
                .map(r -> YearMonth.of(r.getYear(), r.getMonth()))
                .min(YearMonth::compareTo)
                .orElseThrow();
        YearMonth last = rows.stream()
                .map(r -> YearMonth.of(r.getYear(), r.getMonth()))
                .max(YearMonth::compareTo)
                .orElseThrow();
        Map<UUID, Map<YearMonth, Integer>> derived = CalendarCounts.byMonth(calendar, schoolIds, first, last);
        return rows.stream()
                .map(r -> {
                    YearMonth month = YearMonth.of(r.getYear(), r.getMonth());
                    SchoolInfo school = schoolById.get(r.getSchoolId());
                    UUID zoneId = school == null ? null : school.zoneId();
                    return new RowView(
                            r.getId(),
                            r.getSchoolId(),
                            school == null ? "Unknown School" : school.name(),
                            zoneId,
                            zoneId == null ? "" : zoneNames.getOrDefault(zoneId, ""),
                            r.getYear(),
                            r.getMonth(),
                            r.getWorkingDays(),
                            month.lengthOfMonth(),
                            derived.getOrDefault(r.getSchoolId(), Map.of()).getOrDefault(month, 0),
                            userNames.getOrDefault(r.getUpdatedBy(), "Unknown user"),
                            r.getUpdatedAt(),
                            r.getVersion());
                })
                .toList();
    }

    public RowView view(SchoolWorkingDaysRow row) {
        return views(List.of(row)).get(0);
    }

    /** Display names for the given user ids, one lookup. */
    public Map<UUID, String> userNames(Collection<UUID> userIds) {
        return users.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
