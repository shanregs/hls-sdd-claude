package com.hls.attendance.internal;

import com.hls.attendance.internal.RollupCalculator.WeeklyOffRules;
import com.hls.cache.SnapshotCache;
import com.hls.cache.SnapshotCaches;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.StaleVersion;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The non-working calendar (spec 008 FR-008): default weekly off days, School overrides and dates. */
@Service
public class CalendarService {

    public record SchoolOverrideView(UUID schoolId, String schoolName, List<String> weeklyOff) {}

    public record NonWorkingView(LocalDate date, String description) {}

    public record CalendarView(
            List<String> defaultWeeklyOff,
            Long defaultVersion,
            List<SchoolOverrideView> schoolOverrides,
            List<NonWorkingView> nonWorkingDates) {}

    /** What every rollup, grid and leave count needs: the weekly off rules and the holiday dates, as one copy. */
    private record CalendarData(WeeklyOffRules rules, NavigableSet<LocalDate> holidays) {}

    private final CalendarSettingRepository settings;
    private final NonWorkingDateRepository dates;
    private final SchoolDirectory schools;
    private final AttendanceAudit audit;
    private final SnapshotCache<CalendarData> data;

    public CalendarService(
            CalendarSettingRepository settings,
            NonWorkingDateRepository dates,
            SchoolDirectory schools,
            AttendanceAudit audit,
            SnapshotCaches caches) {
        this.settings = settings;
        this.dates = dates;
        this.schools = schools;
        this.audit = audit;
        this.data = caches.create("attendanceCalendar", this::loadData);
    }

    private CalendarData loadData() {
        Map<UUID, Set<DayOfWeek>> overrides = new HashMap<>();
        settings.findOverrides().forEach(o -> overrides.put(o.getSchoolId(), Set.copyOf(o.weeklyOff())));
        NavigableSet<LocalDate> holidays = new TreeSet<>();
        dates.findAll().forEach(d -> holidays.add(d.getOnDate()));
        return new CalendarData(
                new WeeklyOffRules(Set.copyOf(defaultSetting().weeklyOff()), Map.copyOf(overrides)),
                Collections.unmodifiableNavigableSet(holidays));
    }

    @Transactional(readOnly = true)
    public CalendarView calendar() {
        CalendarSetting def = defaultSetting();
        Map<UUID, String> names = schools.schools(settings.findOverrides().stream().map(CalendarSetting::getSchoolId).toList())
                .stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name));
        List<SchoolOverrideView> overrides = settings.findOverrides().stream()
                .map(o -> new SchoolOverrideView(o.getSchoolId(), names.getOrDefault(o.getSchoolId(), "-"), codes(o.weeklyOff())))
                .sorted(Comparator.comparing(SchoolOverrideView::schoolName))
                .toList();
        List<NonWorkingView> holidays = dates.findAllByOrderByOnDate().stream()
                .map(d -> new NonWorkingView(d.getOnDate(), d.getDescription()))
                .toList();
        return new CalendarView(codes(def.weeklyOff()), def.getVersion(), overrides, holidays);
    }

    @Transactional
    public CalendarView updateDefault(UUID actor, List<String> weeklyOff, Long version) {
        CalendarSetting def = defaultSetting();
        StaleVersion.check(CalendarSetting.class, def.getId(), def.getVersion(), version);
        Set<DayOfWeek> next = parseDays(weeklyOff);
        audit.changed(actor, AttendanceAudit.CALENDAR, "default", "weeklyOff", codes(def.weeklyOff()), codes(next));
        def.setWeeklyOff(next);
        settings.saveAndFlush(def);
        data.invalidateAround();
        return calendar();
    }

    @Transactional
    public CalendarView putSchoolOverride(UUID actor, UUID schoolId, List<String> weeklyOff) {
        if (schools.school(schoolId).isEmpty()) {
            throw new NotFoundException("School not found.");
        }
        Set<DayOfWeek> next = parseDays(weeklyOff);
        CalendarSetting existing = settings.findBySchoolId(schoolId).orElse(null);
        if (existing == null) {
            settings.save(new CalendarSetting(schoolId, next));
            audit.changed(actor, AttendanceAudit.CALENDAR, "school:" + schoolId, "weeklyOff", null, codes(next));
        } else {
            audit.changed(actor, AttendanceAudit.CALENDAR, "school:" + schoolId, "weeklyOff", codes(existing.weeklyOff()), codes(next));
            existing.setWeeklyOff(next);
            settings.save(existing);
        }
        settings.flush();
        data.invalidateAround();
        return calendar();
    }

    @Transactional
    public CalendarView removeSchoolOverride(UUID actor, UUID schoolId) {
        CalendarSetting existing = settings.findBySchoolId(schoolId).orElseThrow(() -> new NotFoundException("No override for this School."));
        audit.changed(actor, AttendanceAudit.CALENDAR, "school:" + schoolId, "weeklyOff", codes(existing.weeklyOff()), null);
        settings.delete(existing);
        settings.flush();
        data.invalidateAround();
        return calendar();
    }

    @Transactional
    public CalendarView addNonWorkingDate(UUID actor, LocalDate onDate, String description) {
        if (onDate == null) {
            throw new InvalidInputException("The date is required.");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("The description is required.");
        }
        String text = description.trim();
        if (text.length() > 200) {
            throw new InvalidInputException("The description can be at most 200 characters.");
        }
        if (dates.findByOnDate(onDate).isPresent()) {
            throw new ConflictException("That date is already a non-working date.");
        }
        dates.saveAndFlush(new NonWorkingDate(onDate, text, actor));
        data.invalidateAround();
        audit.lifecycle(actor, AttendanceAudit.CALENDAR, "date:" + onDate, "created", text);
        return calendar();
    }

    @Transactional
    public CalendarView removeNonWorkingDate(UUID actor, LocalDate onDate) {
        NonWorkingDate found = dates.findByOnDate(onDate).orElseThrow(() -> new NotFoundException("That date is not a non-working date."));
        audit.lifecycle(actor, AttendanceAudit.CALENDAR, "date:" + onDate, "deleted", found.getDescription());
        dates.delete(found);
        dates.flush();
        data.invalidateAround();
        return calendar();
    }

    /** The weekly off rules in force, for the rollup calculator. */
    public WeeklyOffRules rules() {
        return data.get().rules();
    }

    /** The non-working dates from {@code from} to {@code to}, both included. The result is a new set. */
    public Set<LocalDate> nonWorkingDates(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return new HashSet<>();
        }
        return new HashSet<>(data.get().holidays().subSet(from, true, to, true));
    }

    private CalendarSetting defaultSetting() {
        return settings.findDefault().orElseThrow(() -> new IllegalStateException("The default calendar setting is missing."));
    }

    private static Set<DayOfWeek> parseDays(Collection<String> codes) {
        if (codes == null) {
            throw new InvalidInputException("The weekly off days are required (use an empty list for none).");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String code : codes) {
            try {
                days.add(CalendarSetting.fromCode(code == null ? "" : code.trim()));
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Unknown weekday " + code + ". Use MON, TUE, WED, THU, FRI, SAT or SUN.");
            }
        }
        return days;
    }

    private static List<String> codes(Set<DayOfWeek> days) {
        return days.stream().sorted().map(CalendarSetting::code).toList();
    }
}
