package com.hls.peoplereports.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.RollupView;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.performance.api.PerformanceSummary;
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
import com.hls.teacher.api.TeacherDirectory.Lifecycle;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherMovementSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Teacher report (spec 014c US3): one row per Teacher who was placed or in training in the month, plus those who exited in
 * it (Q4), with status, whether they joined or exited in the month, School and Zone of the placement on the last day (the
 * last placement for an exited Teacher), tenure, observations done and open performance issues (026), transfers and
 * replacements in the month (027) and training days attended (008); and a count of those rows by status. Scope follows the
 * placement on the last day, so a Teacher who moved Zone mid-month appears once, for the Manager of the last placement.
 */
@Component
class TeacherReportSource implements ReportSource {

    static final String KEY = "teacher";
    private static final Logger LOG = LoggerFactory.getLogger(TeacherReportSource.class);
    private static final String NOT_PLACED = "Not placed";

    private static final List<String> STATUSES = List.of("IN_TRAINING", "ACTIVE", "ON_LEAVE", "EXITED");

    private static final List<Column> STATUS_COLUMNS = List.of(new Column("teachers", "Teachers", Unit.COUNT));

    private static final List<Column> TEACHER_COLUMNS = List.of(
            new Column("status", "Status", Unit.TEXT),
            new Column("event", "In the month", Unit.TEXT),
            new Column("school", "School", Unit.TEXT),
            new Column("zone", "Zone", Unit.TEXT),
            new Column("tenure", "Tenure (days)", Unit.DAYS),
            new Column("observations", "Observations done", Unit.COUNT),
            new Column("issues", "Open performance issues", Unit.COUNT),
            new Column("transfers", "Transfers", Unit.COUNT),
            new Column("replacements", "Replacements", Unit.COUNT),
            new Column("training", "Training days attended", Unit.DAYS));

    /** One Teacher's row, kept so the status counts and the rows come from the same list. */
    record Entry(
            TeacherInfo info,
            Lifecycle lifecycle,
            PlacementSpan placement,
            SchoolInfo school,
            String zone,
            Cell tenure,
            Cell observations,
            Cell issues,
            Cell transfers,
            Cell replacements,
            Cell training) {}

    /** The listed Teachers with whether every month of theirs is locked; memoised for the sections of one request. */
    record Listing(List<Entry> entries, boolean allLocked) {}

    private final TeacherDirectory teachers;
    private final TeacherMovementSource movements;
    private final PerformanceSummary performance;
    private final AttendanceReadApi attendance;
    private final SchoolDirectory schools;
    private final ManagerQueries managers;
    private final TransactionTemplate isolated;

    TeacherReportSource(
            TeacherDirectory teachers,
            TeacherMovementSource movements,
            PerformanceSummary performance,
            AttendanceReadApi attendance,
            SchoolDirectory schools,
            ManagerQueries managers,
            PlatformTransactionManager transactionManager) {
        this.teachers = teachers;
        this.movements = movements;
        this.performance = performance;
        this.attendance = attendance;
        this.schools = schools;
        this.managers = managers;
        // each optional source runs in its own read-only transaction, so one that fails cannot poison the section
        this.isolated = new TransactionTemplate(transactionManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.isolated.setReadOnly(true);
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY,
                "Teachers",
                PermissionModule.REPORT_TEACHER,
                Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL, ReportFilter.TEACHER, ReportFilter.MANAGER),
                true,
                true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("byStatus", "Teachers by status", STATUS_COLUMNS, this::byStatus),
                new Part("teachers", "Teachers", TEACHER_COLUMNS, this::teacherSection));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    // ---- Sections -------------------------------------------------------------------------------------------------

    private Section byStatus(ReportContext ctx) {
        List<Entry> entries = listing(ctx).entries();
        Map<String, Long> counts = entries.stream().collect(Collectors.groupingBy(e -> e.lifecycle().status(), Collectors.counting()));
        List<Row> rows = new ArrayList<>();
        for (String status : STATUSES) {
            rows.add(new Row(status, statusLabel(status), List.of(Cell.of(counts.getOrDefault(status, 0L)))));
        }
        return Section.of("byStatus", "Teachers by status", STATUS_COLUMNS, rows, new Row("total", "All Teachers listed", List.of(Cell.of((long) entries.size()))));
    }

    private Section teacherSection(ReportContext ctx) {
        List<Entry> entries = listing(ctx).entries();
        List<Row> rows = new ArrayList<>();
        YearMonth month = ctx.month();
        for (Entry e : entries) {
            rows.add(new Row(
                    e.info().id().toString(),
                    e.info().name(),
                    List.of(
                            Cell.of(statusLabel(e.lifecycle().status())),
                            Cell.of(event(e.lifecycle(), month)),
                            Cell.of(e.school() == null ? NOT_PLACED : e.school().name()),
                            Cell.of(e.school() == null ? NOT_PLACED : e.zone()),
                            e.tenure(),
                            e.observations(),
                            e.issues(),
                            e.transfers(),
                            e.replacements(),
                            e.training())));
        }
        return Section.of("teachers", "Teachers", TEACHER_COLUMNS, rows, null);
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "IN_TRAINING" -> "In training";
            case "ACTIVE" -> "Active";
            case "ON_LEAVE" -> "On leave";
            case "EXITED" -> "Exited";
            default -> status;
        };
    }

    private static String event(Lifecycle lifecycle, YearMonth month) {
        boolean joined = lifecycle.joinedOn() != null && YearMonth.from(lifecycle.joinedOn()).equals(month);
        boolean exited = "EXITED".equals(lifecycle.status()) && YearMonth.from(lifecycle.statusEffectiveOn()).equals(month);
        if (joined && exited) {
            return "Joined and exited";
        }
        return joined ? "Joined" : exited ? "Exited" : "";
    }

    // ---- The list -------------------------------------------------------------------------------------------------

    private Listing listing(ReportContext ctx) {
        return ctx.memo("teacher.listing", () -> load(ctx));
    }

    private Listing load(ReportContext ctx) {
        YearMonth month = ctx.month();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        ReportScope scope = ctx.scope();
        if (scope.ownOnly()) {
            return new Listing(List.of(), true);
        }
        List<UUID> everyone = teachers.allTeachers().stream().map(TeacherInfo::id).toList();
        Map<UUID, Lifecycle> lifecycles = teachers.lifecycle(everyone);
        Set<UUID> placed = teachers.teachersPlacedDuring(from, to);
        Set<UUID> listed = new HashSet<>();
        for (UUID id : everyone) {
            Lifecycle l = lifecycles.get(id);
            if (l == null) {
                continue;
            }
            boolean inTraining = "IN_TRAINING".equals(l.status()) && !l.statusEffectiveOn().isAfter(to);
            boolean exitedInMonth = "EXITED".equals(l.status()) && YearMonth.from(l.statusEffectiveOn()).equals(month);
            if (placed.contains(id) || inTraining || exitedInMonth) {
                listed.add(id);
            }
        }
        Map<UUID, PlacementSpan> placement = lastPlacements(listed, from, to);
        Map<UUID, SchoolInfo> schoolOf = schools.schools(
                        placement.values().stream().map(PlacementSpan::schoolId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolInfo::id, s -> s));
        Map<UUID, String> zoneNames = schools.zones(schoolOf.values().stream().map(SchoolInfo::zoneId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));

        // scope first, on the placement of the last day (an unplaced Teacher is visible to org-wide callers only)
        Set<UUID> visible = new HashSet<>();
        for (UUID id : listed) {
            PlacementSpan span = placement.get(id);
            SchoolInfo school = span == null ? null : schoolOf.get(span.schoolId());
            if (scope.orgWide() || (school != null && scope.allowsSchool(school.id(), school.zoneId()))) {
                visible.add(id);
            }
        }
        UUID teacherFilter = ctx.request().teacherId();
        if (teacherFilter != null) {
            boolean exists = lifecycles.containsKey(teacherFilter);
            if (!exists || (!scope.orgWide() && !visible.contains(teacherFilter))) {
                throw new ReportOutOfScopeException("That Teacher is outside your area.");
            }
            visible.retainAll(Set.of(teacherFilter));
        }
        Set<UUID> managerSchools = managerSchools(ctx);
        UUID zoneFilter = ctx.request().zoneId();
        UUID schoolFilter = ctx.request().schoolId();
        visible.removeIf(id -> {
            PlacementSpan span = placement.get(id);
            SchoolInfo school = span == null ? null : schoolOf.get(span.schoolId());
            if (zoneFilter != null && (school == null || !zoneFilter.equals(school.zoneId()))) {
                return true;
            }
            if (schoolFilter != null && (school == null || !schoolFilter.equals(school.id()))) {
                return true;
            }
            return managerSchools != null && (school == null || !managerSchools.contains(school.id()));
        });
        if (visible.isEmpty()) {
            return new Listing(List.of(), true);
        }

        Map<UUID, TeacherInfo> info = teachers.teacherInfo(visible);
        Map<UUID, PerformanceSummary.Counts> perf = read("performance", () -> performance.byTeacher(from, to));
        Map<UUID, TeacherMovementSource.Movements> moves = read("movements", () -> movements.movementsBetween(from, to));
        Map<UUID, RollupView> rollups = read("attendance", () -> attendance.rollupsOf(visible, month));
        LocalDate today = ctx.today();
        List<Entry> entries = new ArrayList<>();
        for (UUID id : visible) {
            TeacherInfo t = info.get(id);
            Lifecycle l = lifecycles.get(id);
            if (t == null || l == null) {
                continue;
            }
            PlacementSpan span = placement.get(id);
            SchoolInfo school = span == null ? null : schoolOf.get(span.schoolId());
            String zone = school == null || school.zoneId() == null ? NOT_PLACED : zoneNames.getOrDefault(school.zoneId(), "-");
            PerformanceSummary.Counts counts = perf == null ? null : perf.get(id);
            TeacherMovementSource.Movements moved = moves == null ? null : moves.get(id);
            entries.add(new Entry(
                    t, l, span, school, zone,
                    tenure(l, to, today),
                    perf == null ? Cell.notAvailable() : Cell.of(counts == null ? 0L : counts.observationsDone()),
                    perf == null ? Cell.notAvailable() : Cell.of(counts == null ? 0L : counts.openIssues()),
                    moves == null ? Cell.notAvailable() : Cell.of((long) (moved == null ? 0 : moved.transfers())),
                    moves == null ? Cell.notAvailable() : Cell.of((long) (moved == null ? 0 : moved.replacements())),
                    rollups == null || rollups.get(id) == null ? Cell.notAvailable() : Cell.of(rollups.get(id).trainingAttended())));
        }
        entries.sort(Comparator.comparing((Entry e) -> e.info().name(), String.CASE_INSENSITIVE_ORDER).thenComparing(e -> e.info().id()));
        Set<UUID> locked = read("attendance lock", () -> attendance.lockedTeachers(visible, month));
        return new Listing(List.copyOf(entries), locked != null && locked.containsAll(visible));
    }

    /** Tenure in days from the joining date to the exit date, or to the end of the month (today for the current month). */
    private static Cell tenure(Lifecycle l, LocalDate monthEnd, LocalDate today) {
        if (l.joinedOn() == null) {
            return Cell.noData();
        }
        LocalDate end = "EXITED".equals(l.status()) && l.statusEffectiveOn().isBefore(monthEnd) ? l.statusEffectiveOn() : monthEnd;
        if (end.isAfter(today)) {
            end = today;
        }
        if (end.isBefore(l.joinedOn())) {
            return Cell.noData();
        }
        return Cell.of(ChronoUnit.DAYS.between(l.joinedOn(), end));
    }

    /** The placement the row is about: among those overlapping the month, the one covering the last day, else the latest to start. */
    private Map<UUID, PlacementSpan> lastPlacements(Set<UUID> ids, LocalDate from, LocalDate to) {
        Map<UUID, PlacementSpan> latest = new HashMap<>();
        if (ids.isEmpty()) {
            return latest;
        }
        for (PlacementSpan span : teachers.placementsOverlapping(ids, from, to)) {
            latest.merge(span.teacherId(), span, (a, b) -> b.startsOn().isAfter(a.startsOn()) ? b : a);
        }
        return latest;
    }

    /** The School ids of the Manager filter (current Schools); null when no Manager filter. A Zone Manager may filter by themselves only. */
    private Set<UUID> managerSchools(ReportContext ctx) {
        UUID managerId = ctx.request().managerId();
        if (managerId == null) {
            return null;
        }
        Optional<ManagerQueries.ManagerRef> own = ctx.scope().orgWide() ? Optional.empty() : managers.managerByUserId(ctx.userId());
        if (!ctx.scope().orgWide() && (own.isEmpty() || !own.get().id().equals(managerId))) {
            throw new ReportOutOfScopeException("That Manager is outside your area.");
        }
        return managers.currentSchoolIds(List.of(managerId)).getOrDefault(managerId, Set.of());
    }

    private <T> T read(String what, java.util.function.Supplier<T> source) {
        try {
            return isolated.execute(status -> source.get());
        } catch (RuntimeException e) {
            LOG.warn("Teacher report: {} could not be read", what, e);
            return null;
        }
    }

    // ---- Month state and default month ------------------------------------------------------------------------------

    @Override
    public MonthState monthState(ReportContext ctx) {
        Listing listing = listing(ctx);
        if (listing.entries().isEmpty()) {
            return MonthState.NONE;
        }
        return listing.allLocked() ? MonthState.LOCKED : MonthState.OPEN;
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return state == MonthState.OPEN
                ? List.of("This month is still open: attendance figures can change until it is locked.")
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
            Set<UUID> placed = teachers.teachersPlacedDuring(month.atDay(1), month.atEndOfMonth());
            Set<UUID> ids = scope.orgWide() ? placed : inScope(placed, scope, month);
            if (!ids.isEmpty() && attendance.lockedTeachers(ids, month).containsAll(ids)) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }

    private Set<UUID> inScope(Set<UUID> ids, ReportScope scope, YearMonth month) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        Map<UUID, PlacementSpan> last = lastPlacements(ids, month.atDay(1), month.atEndOfMonth());
        Map<UUID, SchoolInfo> schoolOf = schools.schools(last.values().stream().map(PlacementSpan::schoolId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SchoolInfo::id, s -> s));
        Set<UUID> result = new HashSet<>();
        last.forEach((teacher, span) -> {
            SchoolInfo school = schoolOf.get(span.schoolId());
            if (school != null && scope.allowsSchool(school.id(), school.zoneId())) {
                result.add(teacher);
            }
        });
        return result;
    }
}
