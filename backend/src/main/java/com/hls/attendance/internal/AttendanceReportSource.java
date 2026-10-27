package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.LossOfPayDay;
import com.hls.attendance.api.LossOfPayDays;
import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.attendance.api.RollupView;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.MonthState;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportOutOfScopeException;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportScope;
import com.hls.reporting.api.ReportSource;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Attendance and LOP report (spec 014a US1): per Teacher the working days, present, absent, leave, loss-of-pay days and
 * attendance %, rolled up by School, Zone and organization, and per Manager the working days, checked-in days, leave, missed
 * check-ins and Manager attendance %. Every figure comes from the same read contracts the attendance screens use
 * ({@link AttendanceReadApi#rollupsOf}, {@link LossOfPayDays}, {@link ManagerAttendanceReadApi#months}), so the report and
 * the grid agree. A month with no working days shows "no data" for the %, never 0.
 */
@Component
class AttendanceReportSource implements ReportSource {

    static final String KEY = "attendance";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final List<Column> TEACHER_COLUMNS = List.of(
            new Column("school", "School", Unit.TEXT),
            new Column("zone", "Zone", Unit.TEXT),
            new Column("workingDays", "Working days", Unit.DAYS),
            new Column("present", "Present", Unit.DAYS),
            new Column("absent", "Absent", Unit.DAYS),
            new Column("leave", "Leave", Unit.DAYS),
            new Column("lop", "LOP days", Unit.DAYS),
            new Column("percent", "Attendance %", Unit.PERCENT));

    private static final List<Column> ROLLUP_COLUMNS = List.of(
            new Column("teachers", "Teachers", Unit.COUNT),
            new Column("workingDays", "Working days", Unit.DAYS),
            new Column("present", "Present", Unit.DAYS),
            new Column("absent", "Absent", Unit.DAYS),
            new Column("leave", "Leave", Unit.DAYS),
            new Column("lop", "LOP days", Unit.DAYS),
            new Column("percent", "Attendance %", Unit.PERCENT));

    private static final List<Column> MANAGER_COLUMNS = List.of(
            new Column("workingDays", "Working days", Unit.DAYS),
            new Column("checkedIn", "Checked-in days", Unit.DAYS),
            new Column("leave", "Leave", Unit.DAYS),
            new Column("missed", "Missed check-ins", Unit.DAYS),
            new Column("percent", "Manager attendance %", Unit.PERCENT));

    /** One Teacher's figures for the month. */
    record TeacherFigures(
            UUID id, String name, UUID schoolId, String school, UUID zoneId, String zone, BigDecimal working, BigDecimal present,
            BigDecimal leave, BigDecimal lop) {

        BigDecimal absent() {
            return working.subtract(present).subtract(leave).max(BigDecimal.ZERO);
        }
    }

    /** The Teachers in scope for the request, with their figures; memoised for the sections of one request. */
    record TeacherData(List<TeacherFigures> rows, boolean allLocked) {}

    private final AttendanceReadApi attendance;
    private final LossOfPayDays lossOfPay;
    private final ManagerAttendanceReadApi managerAttendance;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;

    AttendanceReportSource(
            AttendanceReadApi attendance,
            LossOfPayDays lossOfPay,
            ManagerAttendanceReadApi managerAttendance,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ManagerQueries managers) {
        this.attendance = attendance;
        this.lossOfPay = lossOfPay;
        this.managerAttendance = managerAttendance;
        this.teachers = teachers;
        this.schools = schools;
        this.managers = managers;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY,
                "Attendance and LOP",
                PermissionModule.REPORT_ATTENDANCE,
                Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL, ReportFilter.TEACHER, ReportFilter.MANAGER),
                true,
                true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("teachers", "Teachers", TEACHER_COLUMNS, this::teacherSection),
                new Part("schools", "Schools", ROLLUP_COLUMNS, ctx -> rollupSection(ctx, "schools", "Schools", true)),
                new Part("zones", "Zones", ROLLUP_COLUMNS, ctx -> rollupSection(ctx, "zones", "Zones", false)),
                new Part("managers", "Managers", MANAGER_COLUMNS, this::managerSection));
    }

    private record Part(String key, String title, List<Column> columns, java.util.function.Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    // ---- Teachers -------------------------------------------------------------------------------------------------

    private Section teacherSection(ReportContext ctx) {
        TeacherData data = teacherData(ctx);
        List<Row> rows = new ArrayList<>();
        for (TeacherFigures t : data.rows()) {
            rows.add(new Row(
                    t.id().toString(),
                    t.name(),
                    List.of(
                            Cell.of(t.school()),
                            Cell.of(t.zone()),
                            Cell.of(t.working()),
                            Cell.of(t.present()),
                            Cell.of(t.absent()),
                            Cell.of(t.leave()),
                            Cell.of(t.lop()),
                            percent(t.present(), t.working()))));
        }
        Row total = data.rows().isEmpty() ? null : totalsRow("total", "Organization", data.rows(), false);
        return Section.of("teachers", "Teachers", TEACHER_COLUMNS, rows, total);
    }

    private Section rollupSection(ReportContext ctx, String key, String title, boolean bySchool) {
        TeacherData data = teacherData(ctx);
        Map<UUID, List<TeacherFigures>> groups = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (TeacherFigures t : data.rows()) {
            UUID group = bySchool ? t.schoolId() : t.zoneId();
            if (group == null) {
                continue;
            }
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(t);
            names.put(group, bySchool ? t.school() : t.zone());
        }
        List<Row> rows = groups.entrySet().stream()
                .sorted(Comparator.comparing(e -> names.get(e.getKey()), String.CASE_INSENSITIVE_ORDER))
                .map(e -> totalsRow(e.getKey().toString(), names.get(e.getKey()), e.getValue(), true))
                .toList();
        Row total = data.rows().isEmpty() ? null : totalsRow("total", "Organization", data.rows(), true);
        return Section.of(key, title, ROLLUP_COLUMNS, rows, total);
    }

    private static Row totalsRow(String id, String label, List<TeacherFigures> group, boolean withCount) {
        BigDecimal working = sum(group, TeacherFigures::working);
        BigDecimal present = sum(group, TeacherFigures::present);
        BigDecimal leave = sum(group, TeacherFigures::leave);
        BigDecimal lop = sum(group, TeacherFigures::lop);
        BigDecimal absent = sum(group, TeacherFigures::absent);
        List<Cell> cells = new ArrayList<>();
        if (withCount) {
            cells.add(Cell.of((long) group.size()));
        } else {
            cells.add(Cell.of(""));
            cells.add(Cell.of(""));
        }
        cells.add(Cell.of(working));
        cells.add(Cell.of(present));
        cells.add(Cell.of(absent));
        cells.add(Cell.of(leave));
        cells.add(Cell.of(lop));
        cells.add(percent(present, working));
        return new Row(id, label, cells);
    }

    private static BigDecimal sum(List<TeacherFigures> rows, java.util.function.Function<TeacherFigures, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static Cell percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return Cell.noData();
        }
        return Cell.of(part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP));
    }

    TeacherData teacherData(ReportContext ctx) {
        return ctx.memo("attendance.teacherData", () -> loadTeachers(ctx));
    }

    private TeacherData loadTeachers(ReportContext ctx) {
        YearMonth month = ctx.month();
        Set<UUID> ids = teachersInScope(ctx, month);
        if (ids.isEmpty()) {
            return new TeacherData(List.of(), false);
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<UUID, TeacherDirectory.TeacherInfo> info = teachers.teacherInfo(ids);
        Map<UUID, PlacementSpan> placement = latestPlacements(ids, from, to);
        Map<UUID, SchoolInfo> schoolInfo = schools.schools(placement.values().stream().map(PlacementSpan::schoolId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SchoolInfo::id, s -> s));
        Map<UUID, String> zoneNames = schools.zones(schoolInfo.values().stream().map(SchoolInfo::zoneId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<UUID, RollupView> rollups = attendance.rollupsOf(ids, month);
        Map<UUID, List<LossOfPayDay>> lopDays = lossOfPay.lossOfPayDaysOf(ids, from, to);
        List<TeacherFigures> rows = new ArrayList<>();
        for (UUID id : ids) {
            TeacherDirectory.TeacherInfo t = info.get(id);
            if (t == null) {
                continue;
            }
            PlacementSpan span = placement.get(id);
            SchoolInfo school = span == null ? null : schoolInfo.get(span.schoolId());
            RollupView r = rollups.get(id);
            BigDecimal lop = lopDays.getOrDefault(id, List.of()).stream()
                    .map(LossOfPayDay::dayValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new TeacherFigures(
                    id,
                    t.name(),
                    school == null ? null : school.id(),
                    school == null ? "-" : school.name(),
                    school == null ? null : school.zoneId(),
                    school == null ? "-" : zoneNames.getOrDefault(school.zoneId(), "-"),
                    r.workingDays(),
                    r.daysWorked(),
                    r.daysLeave(),
                    lop));
        }
        rows.sort(Comparator.comparing(TeacherFigures::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeacherFigures::id));
        Set<UUID> locked = attendance.lockedTeachers(ids, month);
        return new TeacherData(List.copyOf(rows), locked.containsAll(ids));
    }

    /** The Teacher's placement for the month: the one covering its last day, else the latest to start. */
    private Map<UUID, PlacementSpan> latestPlacements(Set<UUID> ids, LocalDate from, LocalDate to) {
        Map<UUID, PlacementSpan> latest = new HashMap<>();
        for (PlacementSpan span : teachers.placementsOverlapping(ids, from, to)) {
            latest.merge(span.teacherId(), span, (a, b) -> b.startsOn().isAfter(a.startsOn()) ? b : a);
        }
        return latest;
    }

    /**
     * The Teachers the caller may see for the month and the filters: placed during the month at a School in scope (so a
     * Teacher who moved out of a Manager's School drops out of later months), narrowed by Zone, School and Teacher.
     */
    private Set<UUID> teachersInScope(ReportContext ctx, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        ReportScope scope = ctx.scope();
        UUID teacherFilter = ctx.request().teacherId();
        Set<UUID> candidates;
        if (scope.ownOnly()) {
            Set<UUID> own = teachers.teacherOfUser(ctx.userId()).map(t -> Set.of(t.id())).orElse(Set.of());
            if (teacherFilter != null && !own.contains(teacherFilter)) {
                throw new ReportOutOfScopeException("That Teacher is outside your area.");
            }
            return own;
        }
        Set<UUID> placed = teachers.teachersPlacedDuring(from, to);
        if (teacherFilter != null) {
            boolean exists = !teachers.teacherInfo(List.of(teacherFilter)).isEmpty();
            if (!exists || (!scope.orgWide() && !placedInScope(Set.of(teacherFilter), scope, from, to).contains(teacherFilter))) {
                throw new ReportOutOfScopeException("That Teacher is outside your area.");
            }
            candidates = Set.of(teacherFilter);
        } else {
            candidates = scope.orgWide() ? placed : placedInScope(placed, scope, from, to);
        }
        Set<UUID> schoolFilter = filterSchools(ctx);
        if (schoolFilter == null) {
            return new HashSet<>(candidates);
        }
        Set<UUID> result = new HashSet<>();
        for (PlacementSpan span : teachers.placementsOverlapping(candidates, from, to)) {
            if (schoolFilter.contains(span.schoolId())) {
                result.add(span.teacherId());
            }
        }
        return result;
    }

    private Set<UUID> placedInScope(Set<UUID> ids, ReportScope scope, LocalDate from, LocalDate to) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> allowedSchools = new HashSet<>(scope.schoolIds());
        scope.zoneIds().forEach(z -> schools.schoolsInZone(z).forEach(s -> allowedSchools.add(s.id())));
        Set<UUID> result = new HashSet<>();
        for (PlacementSpan span : teachers.placementsOverlapping(ids, from, to)) {
            if (allowedSchools.contains(span.schoolId())) {
                result.add(span.teacherId());
            }
        }
        return result;
    }

    /** The Schools the Zone and School filters allow; null when neither is set. */
    private Set<UUID> filterSchools(ReportContext ctx) {
        Set<UUID> allowed = null;
        if (ctx.request().zoneId() != null) {
            allowed = schools.schoolsInZone(ctx.request().zoneId()).stream().map(SchoolInfo::id).collect(Collectors.toSet());
        }
        if (ctx.request().schoolId() != null) {
            Set<UUID> one = Set.of(ctx.request().schoolId());
            allowed = allowed == null ? one : allowed.stream().filter(one::contains).collect(Collectors.toSet());
        }
        return allowed;
    }

    // ---- Managers -------------------------------------------------------------------------------------------------

    private Section managerSection(ReportContext ctx) {
        List<ManagerQueries.ManagerRef> refs = managersInScope(ctx);
        if (refs.isEmpty()) {
            return Section.of("managers", "Managers", MANAGER_COLUMNS, List.of(), null);
        }
        Map<UUID, ManagerMonth> months =
                managerAttendance.months(refs.stream().map(ManagerQueries.ManagerRef::id).toList(), ctx.month());
        List<Row> rows = new ArrayList<>();
        for (ManagerQueries.ManagerRef ref : refs) {
            rows.add(managerRow(ref, months.get(ref.id())));
        }
        return Section.of("managers", "Managers", MANAGER_COLUMNS, rows, null);
    }

    private static Row managerRow(ManagerQueries.ManagerRef ref, ManagerMonth month) {
        if (month instanceof ManagerMonth.Found f) {
            BigDecimal leave = f.leavePaid().add(f.leaveUnpaid());
            BigDecimal missed = f.unpaid().subtract(f.leaveUnpaid()).max(BigDecimal.ZERO);
            return new Row(
                    ref.id().toString(),
                    ref.displayName(),
                    List.of(Cell.of(f.workingDays()), Cell.of(f.daysWorked()), Cell.of(leave), Cell.of(missed),
                            percent(f.daysWorked(), f.workingDays())));
        }
        return new Row(
                ref.id().toString(),
                ref.displayName(),
                List.of(Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData()));
    }

    private List<ManagerQueries.ManagerRef> managersInScope(ReportContext ctx) {
        ReportScope scope = ctx.scope();
        List<ManagerQueries.ManagerRef> allowed;
        if (scope.orgWide()) {
            allowed = managers.allManagers().stream().filter(ManagerQueries.ManagerRef::active).toList();
        } else {
            allowed = managers.managerByUserId(ctx.userId()).filter(ManagerQueries.ManagerRef::active).map(List::of).orElse(List.of());
        }
        UUID managerFilter = ctx.request().managerId();
        if (managerFilter != null) {
            allowed = allowed.stream().filter(m -> m.id().equals(managerFilter)).toList();
            if (allowed.isEmpty()) {
                throw new ReportOutOfScopeException("That Manager is outside your area.");
            }
        }
        if (ctx.request().zoneId() != null) {
            Set<UUID> inZone = managers.managersOfZone(ctx.request().zoneId()).stream()
                    .map(ManagerQueries.ManagerRef::id)
                    .collect(Collectors.toSet());
            allowed = allowed.stream().filter(m -> inZone.contains(m.id())).toList();
        }
        if (ctx.request().schoolId() != null) {
            Optional<ManagerQueries.ManagerRef> ofSchool = managers.managerOfSchool(ctx.request().schoolId());
            allowed = allowed.stream().filter(m -> ofSchool.isPresent() && ofSchool.get().id().equals(m.id())).toList();
        }
        if (ctx.request().teacherId() != null) {
            return List.of();
        }
        return allowed.stream()
                .sorted(Comparator.comparing(ManagerQueries.ManagerRef::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ---- Month state and default month ------------------------------------------------------------------------------

    @Override
    public MonthState monthState(ReportContext ctx) {
        TeacherData data = teacherData(ctx);
        List<ManagerQueries.ManagerRef> refs = managersInScope(ctx);
        boolean any = !data.rows().isEmpty() || !refs.isEmpty();
        if (!any) {
            return MonthState.NONE;
        }
        boolean managersLocked = true;
        if (!refs.isEmpty()) {
            Map<UUID, ManagerMonth> months =
                    managerAttendance.months(refs.stream().map(ManagerQueries.ManagerRef::id).toList(), ctx.month());
            managersLocked = months.values().stream().allMatch(m -> m instanceof ManagerMonth.Found f && f.locked());
        }
        return (data.rows().isEmpty() || data.allLocked()) && managersLocked ? MonthState.LOCKED : MonthState.OPEN;
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return state == MonthState.OPEN
                ? List.of("This month is still open: figures can change until attendance is locked.")
                : List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope scope) {
        if (scope.ownOnly()) {
            return Optional.empty();
        }
        YearMonth month = YearMonth.now(java.time.ZoneId.of("Asia/Kolkata"));
        for (int i = 0; i < 12; i++, month = month.minusMonths(1)) {
            LocalDate from = month.atDay(1);
            LocalDate to = month.atEndOfMonth();
            Set<UUID> placed = teachers.teachersPlacedDuring(from, to);
            Set<UUID> ids = scope.orgWide() ? placed : placedInScope(placed, scope, from, to);
            if (!ids.isEmpty() && attendance.lockedTeachers(ids, month).containsAll(ids)) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }
}
