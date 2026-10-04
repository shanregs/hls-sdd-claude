package com.hls.attendance.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hls.attendance.api.RollupView;
import com.hls.attendance.internal.RollupCalculator.DayPlan;
import com.hls.attendance.internal.RollupCalculator.MarkFacts;
import com.hls.attendance.internal.TeacherMonthViewService.DayState;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a page of the month grid (spec 008 FR-013, research.md section 10): resolve the allowed
 * Teachers, apply filters, page, then load marks, placements and settings in bulk for just that page
 * so the cost does not grow with one query per Teacher.
 */
@Service
public class AttendanceGridService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Optional filters; all combine with AND. */
    public record Filter(String query, UUID zoneId, UUID schoolId, UUID managerId, String status) {

        public static Filter none() {
            return new Filter(null, null, null, null, null);
        }
    }

    public record Ref(UUID id, String name) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cell(
            LocalDate date, String code, String category, BigDecimal dayValue, String setByKind, DayState state) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(
            UUID teacherId,
            String name,
            String status,
            Ref school,
            Ref manager,
            boolean locked,
            RollupView rollup,
            List<Cell> cells) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record GridResponse(String month, int days, List<Row> content, int page, int size, long totalElements) {}

    private final AttendanceScope scope;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final AttendanceMarkRepository marks;
    private final StatusCodeRepository codes;
    private final TeacherMonthRepository months;
    private final CalendarService calendar;
    private final BusinessCalendar business;

    public AttendanceGridService(
            AttendanceScope scope,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ManagerQueries managers,
            AttendanceMarkRepository marks,
            StatusCodeRepository codes,
            TeacherMonthRepository months,
            CalendarService calendar,
            BusinessCalendar business) {
        this.scope = scope;
        this.teachers = teachers;
        this.schools = schools;
        this.managers = managers;
        this.marks = marks;
        this.codes = codes;
        this.months = months;
        this.calendar = calendar;
        this.business = business;
    }

    @Transactional(readOnly = true)
    public GridResponse grid(UUID userId, Set<Role> roles, YearMonth month, Filter filter, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageIndex = Math.max(0, page);
        List<UUID> ordered = filteredTeacherIds(userId, roles, month, filter);
        long total = ordered.size();
        int from = (int) Math.min((long) pageIndex * pageSize, total);
        int to = (int) Math.min(from + (long) pageSize, total);
        List<Row> rows = rowsFor(ordered.subList(from, to), month);
        return new GridResponse(month.toString(), month.lengthOfMonth(), rows, pageIndex, pageSize, total);
    }

    /** Every Teacher of the filtered grid, in name order; used by the CSV export too. */
    @Transactional(readOnly = true)
    public List<UUID> filteredTeacherIds(UUID userId, Set<Role> roles, YearMonth month, Filter filter) {
        Set<UUID> allowed = scope.allowedDuring(userId, roles, month);
        if (allowed.isEmpty()) {
            return List.of();
        }
        Map<UUID, TeacherInfo> infos = teachers.teacherInfo(allowed);
        String query = filter.query() == null ? "" : filter.query().trim().toLowerCase(Locale.ROOT);
        List<TeacherInfo> candidates = infos.values().stream()
                .filter(t -> query.isEmpty() || t.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(t -> filter.status() == null || filter.status().isBlank() || t.status().equalsIgnoreCase(filter.status()))
                .sorted(Comparator.comparing((TeacherInfo t) -> t.name().toLowerCase(Locale.ROOT)).thenComparing(TeacherInfo::id))
                .toList();
        boolean placementFilter = filter.zoneId() != null || filter.schoolId() != null || filter.managerId() != null;
        if (!placementFilter) {
            return candidates.stream().map(TeacherInfo::id).toList();
        }
        Map<UUID, List<PlacementSpan>> placements = placementsByTeacher(
                candidates.stream().map(TeacherInfo::id).toList(), month);
        Map<UUID, SchoolInfo> schoolInfo = schoolsOf(placements.values());
        Map<UUID, ManagerRef> managerOf = filter.managerId() == null ? Map.of() : managers.managersOfSchools(schoolInfo.keySet());
        return candidates.stream()
                .map(TeacherInfo::id)
                .filter(id -> placements.getOrDefault(id, List.of()).stream().anyMatch(p -> matches(p, filter, schoolInfo, managerOf)))
                .toList();
    }

    private static boolean matches(
            PlacementSpan placement, Filter filter, Map<UUID, SchoolInfo> schools, Map<UUID, ManagerRef> managerOf) {
        if (filter.schoolId() != null && !filter.schoolId().equals(placement.schoolId())) {
            return false;
        }
        if (filter.zoneId() != null) {
            SchoolInfo info = schools.get(placement.schoolId());
            if (info == null || !filter.zoneId().equals(info.zoneId())) {
                return false;
            }
        }
        if (filter.managerId() != null) {
            ManagerRef manager = managerOf.get(placement.schoolId());
            return manager != null && filter.managerId().equals(manager.id());
        }
        return true;
    }

    private Map<UUID, List<PlacementSpan>> placementsByTeacher(Collection<UUID> ids, YearMonth month) {
        return teachers.placementsOverlapping(ids, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(PlacementSpan::teacherId));
    }

    private Map<UUID, SchoolInfo> schoolsOf(Collection<List<PlacementSpan>> placements) {
        List<UUID> schoolIds = placements.stream()
                .flatMap(List::stream)
                .map(PlacementSpan::schoolId)
                .distinct()
                .toList();
        return schools.schools(schoolIds).stream().collect(Collectors.toMap(SchoolInfo::id, Function.identity()));
    }

    /** Rows for one page of Teachers, loading everything in bulk. */
    @Transactional(readOnly = true)
    public List<Row> rowsFor(List<UUID> teacherIds, YearMonth month) {
        if (teacherIds.isEmpty()) {
            return List.of();
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = business.today();
        Map<UUID, TeacherInfo> infos = teachers.teacherInfo(teacherIds);
        Map<UUID, List<PlacementSpan>> placements = placementsByTeacher(teacherIds, month);
        Map<UUID, SchoolInfo> schoolInfo = schoolsOf(placements.values());
        Map<UUID, ManagerRef> managerOf = managers.managersOfSchools(schoolInfo.keySet());
        Map<UUID, Map<LocalDate, AttendanceMark>> marksByTeacher = new HashMap<>();
        for (AttendanceMark m : marks.findByTeacherIdInAndMarkDateBetween(teacherIds, from, to)) {
            marksByTeacher.computeIfAbsent(m.getTeacherId(), k -> new HashMap<>()).put(m.getMarkDate(), m);
        }
        Map<UUID, StatusCode> codeById =
                codes.findAll().stream().collect(Collectors.toMap(StatusCode::getId, Function.identity()));
        Map<UUID, TeacherMonth> monthRows = months.findByTeacherIdInAndYearMonth(teacherIds, month.toString()).stream()
                .collect(Collectors.toMap(TeacherMonth::getTeacherId, Function.identity()));
        var rules = calendar.rules();
        Set<LocalDate> nonWorking = calendar.nonWorkingDates(from, to);

        List<Row> rows = new ArrayList<>();
        for (UUID id : teacherIds) {
            TeacherInfo info = infos.get(id);
            List<PlacementSpan> own = placements.getOrDefault(id, List.of());
            Map<LocalDate, AttendanceMark> ownMarks = marksByTeacher.getOrDefault(id, Map.of());
            Map<LocalDate, MarkFacts> facts = new HashMap<>();
            ownMarks.forEach((date, m) -> {
                StatusCode code = codeById.get(m.getStatusCodeId());
                facts.put(date, new MarkFacts(code.getCategory(), m.getDayValue(), code.getWeight()));
            });
            TeacherMonth row = monthRows.get(id);
            boolean locked = row != null && row.getState() == MonthState.LOCKED;
            List<Cell> cells = new ArrayList<>();
            for (DayPlan plan : RollupCalculator.plan(month, own, rules, nonWorking)) {
                AttendanceMark m = ownMarks.get(plan.date());
                cells.add(new Cell(
                        plan.date(),
                        m == null ? null : codeById.get(m.getStatusCodeId()).getShortCode(),
                        m == null ? null : codeById.get(m.getStatusCodeId()).getCategory().name(),
                        m == null ? null : m.getDayValue(),
                        m == null ? null : m.getSetByKind().name(),
                        TeacherMonthViewService.stateOf(plan, m != null, today)));
            }
            Rollup rollup = locked
                    ? row.frozenRollup()
                    : RollupCalculator.compute(month, own, facts, rules, nonWorking, today);
            PlacementSpan last = own.isEmpty() ? null : own.get(own.size() - 1);
            SchoolInfo school = last == null ? null : schoolInfo.get(last.schoolId());
            ManagerRef manager = last == null ? null : managerOf.get(last.schoolId());
            rows.add(new Row(
                    id,
                    info.name(),
                    info.status(),
                    school == null ? null : new Ref(school.id(), school.name()),
                    manager == null ? null : new Ref(manager.id(), manager.displayName()),
                    locked,
                    TeacherMonthViewService.toView(rollup, locked),
                    cells));
        }
        return rows;
    }
}
