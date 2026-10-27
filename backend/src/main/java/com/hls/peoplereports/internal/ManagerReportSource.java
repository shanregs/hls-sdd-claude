package com.hls.peoplereports.internal;

import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.attendance.api.ManagerTrainingDays;
import com.hls.expenses.api.ExpenseQueries;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ManagerColumns;
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
import com.hls.reporting.api.TileContext;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager productivity (spec 014c US1): one row per Manager with the columns of every {@link ManagerColumns} bean (tasks,
 * Activity Calendar, marketing: the same sources as the dashboard comparison table, so the shared columns are equal by
 * construction), plus attendance %, training days and expense claimed. A source that fails shows "not available" for its own
 * columns only; a Manager with no planned activity shows "no data yet", never 0. Admin and Director see every Manager, a Zone
 * Manager only their own row (including their own expense claimed).
 */
@Component
class ManagerReportSource implements ReportSource {

    static final String KEY = "manager";
    private static final Logger LOG = LoggerFactory.getLogger(ManagerReportSource.class);

    /** The order the shared columns appear in; any other column follows by key. */
    private static final List<String> PREFERRED = List.of(
            "tasks.assigned", "tasks.completed", "tasks.overdue", "tasks.awaiting", "tasks.escalated",
            "activity.planned", "activity.done", "activity.missed", "activity.rescheduled",
            "marketing.visitsDone", "marketing.visitsMissed", "marketing.wonInMonth", "marketing.won");

    private static final List<Column> OWN_COLUMNS = List.of(
            new Column("percent", "Attendance %", Unit.PERCENT),
            new Column("trainingDays", "Training days attended", Unit.DAYS),
            new Column("claimed", "Expense claimed", Unit.RUPEES),
            new Column("approved", "Expense approved", Unit.RUPEES));

    /** A source column and the bean that produces it. */
    private record SharedColumn(ManagerColumns source, ManagerColumns.Column column) {}

    private final List<ManagerColumns> sources;
    private final List<SharedColumn> shared;
    private final List<Column> columns;
    private final ManagerAttendanceReadApi attendance;
    private final ManagerTrainingDays training;
    private final ExpenseQueries expenses;
    private final ManagerQueries managers;
    private final SchoolDirectory schools;

    ManagerReportSource(
            List<ManagerColumns> sources,
            ManagerAttendanceReadApi attendance,
            ManagerTrainingDays training,
            ExpenseQueries expenses,
            ManagerQueries managers,
            SchoolDirectory schools) {
        this.sources = List.copyOf(sources);
        this.attendance = attendance;
        this.training = training;
        this.expenses = expenses;
        this.managers = managers;
        this.schools = schools;
        List<SharedColumn> all = new ArrayList<>();
        for (ManagerColumns source : sources) {
            for (ManagerColumns.Column c : source.columns()) {
                all.add(new SharedColumn(source, c));
            }
        }
        all.sort(Comparator.comparingInt((SharedColumn c) -> rank(c.column().key())).thenComparing(c -> c.column().key()));
        this.shared = List.copyOf(all);
        List<Column> built = new ArrayList<>();
        built.add(new Column("zone", "Zone", Unit.TEXT));
        shared.forEach(c -> built.add(new Column(c.column().key(), c.column().label(), Unit.COUNT)));
        built.addAll(OWN_COLUMNS);
        this.columns = List.copyOf(built);
    }

    private static int rank(String key) {
        int i = PREFERRED.indexOf(key);
        return i < 0 ? PREFERRED.size() : i;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY, "Manager productivity", PermissionModule.REPORT_MANAGER, Set.of(ReportFilter.ZONE, ReportFilter.MANAGER), true, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(new SectionSource() {
            @Override
            public String key() {
                return "managers";
            }

            @Override
            public String title() {
                return "Managers";
            }

            @Override
            public List<Column> columns() {
                return columns;
            }

            @Override
            public Section read(ReportContext ctx) {
                return managerSection(ctx);
            }
        });
    }

    // ---- The section -----------------------------------------------------------------------------------------------

    /** What one Manager's row is made of, so the totals use the same numbers. */
    private record Figures(
            ManagerQueries.ManagerRef ref,
            String zone,
            Map<String, Optional<Long>> shared,
            ManagerMonth month,
            Optional<BigDecimal> trainingDays,
            Optional<ExpenseQueries.ClaimedTotal> claimed,
            boolean expenseAvailable,
            boolean trainingAvailable) {}

    private Section managerSection(ReportContext ctx) {
        List<ManagerQueries.ManagerRef> refs = managersInScope(ctx);
        if (refs.isEmpty()) {
            return Section.of("managers", "Managers", columns, List.of(), null);
        }
        YearMonth month = ctx.month();
        Set<UUID> userIds = refs.stream().map(ManagerQueries.ManagerRef::userId).collect(Collectors.toSet());
        List<UUID> managerIds = refs.stream().map(ManagerQueries.ManagerRef::id).toList();

        Map<ManagerColumns, Map<UUID, Map<String, Long>>> bySource = new HashMap<>();
        Set<ManagerColumns> failed = new java.util.HashSet<>();
        TileContext tileContext = new ReportTileContext(ctx);
        for (ManagerColumns source : sources) {
            try {
                bySource.put(source, source.figures(tileContext, userIds));
            } catch (RuntimeException e) {
                LOG.warn("Manager column source {} could not be read", source.getClass().getSimpleName(), e);
                failed.add(source);
            }
        }
        Map<UUID, ManagerMonth> months = attendance.months(managerIds, month);
        Map<UUID, Map<LocalDate, BigDecimal>> trainingDays = null;
        try {
            trainingDays = training.attendedDaysOf(managerIds, month.atDay(1), month.atEndOfMonth());
        } catch (RuntimeException e) {
            LOG.warn("Training days could not be read", e);
        }
        Map<UUID, ExpenseQueries.ClaimedTotal> claimed = null;
        try {
            claimed = expenses.claimedBy(month.atDay(1), month.atEndOfMonth(), userIds);
        } catch (RuntimeException e) {
            LOG.warn("Expense claimed could not be read", e);
        }
        Map<UUID, String> zones = zonesOf(managerIds);

        List<Figures> all = new ArrayList<>();
        for (ManagerQueries.ManagerRef ref : refs) {
            Map<String, Optional<Long>> values = new LinkedHashMap<>();
            for (SharedColumn c : shared) {
                if (failed.contains(c.source())) {
                    values.put(c.column().key(), null);
                } else {
                    Long figure = bySource.getOrDefault(c.source(), Map.of()).getOrDefault(ref.userId(), Map.of()).get(c.column().key());
                    values.put(c.column().key(), Optional.ofNullable(figure));
                }
            }
            BigDecimal days = trainingDays == null
                    ? null
                    : trainingDays.getOrDefault(ref.id(), Map.of()).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            ExpenseQueries.ClaimedTotal total = claimed == null ? null : claimed.get(ref.userId());
            all.add(new Figures(
                    ref, zones.getOrDefault(ref.id(), "No Zone"), values, months.get(ref.id()), Optional.ofNullable(days),
                    Optional.ofNullable(total), claimed != null, trainingDays != null));
        }
        List<Row> rows = all.stream().map(this::row).toList();
        return Section.of("managers", "Managers", columns, rows, totals(all));
    }

    private Row row(Figures f) {
        List<Cell> cells = new ArrayList<>();
        cells.add(Cell.of(f.zone()));
        for (SharedColumn c : shared) {
            Optional<Long> value = f.shared().get(c.column().key());
            cells.add(value == null ? Cell.notAvailable() : value.map(Cell::of).orElseGet(Cell::noData));
        }
        if (f.month() instanceof ManagerMonth.Found m) {
            cells.add(percent(m.daysWorked(), m.workingDays()));
        } else {
            cells.add(Cell.noData());
        }
        cells.add(f.trainingAvailable() ? Cell.of(f.trainingDays().orElse(BigDecimal.ZERO)) : Cell.notAvailable());
        cells.add(f.expenseAvailable() ? Cell.of(f.claimed().map(ExpenseQueries.ClaimedTotal::submitted).orElse(BigDecimal.ZERO)) : Cell.notAvailable());
        cells.add(f.expenseAvailable() ? Cell.of(f.claimed().map(ExpenseQueries.ClaimedTotal::approved).orElse(BigDecimal.ZERO)) : Cell.notAvailable());
        return new Row(f.ref().id().toString(), f.ref().displayName(), cells);
    }

    /** Sums over the rows shown, so a Zone Manager's total covers only their own row. */
    private Row totals(List<Figures> all) {
        List<Cell> cells = new ArrayList<>();
        cells.add(Cell.of(""));
        for (SharedColumn c : shared) {
            boolean unavailable = false;
            long sum = 0;
            boolean any = false;
            for (Figures f : all) {
                Optional<Long> value = f.shared().get(c.column().key());
                if (value == null) {
                    unavailable = true;
                } else if (value.isPresent()) {
                    sum += value.get();
                    any = true;
                }
            }
            cells.add(unavailable ? Cell.notAvailable() : any ? Cell.of(sum) : Cell.noData());
        }
        BigDecimal worked = BigDecimal.ZERO;
        BigDecimal working = BigDecimal.ZERO;
        for (Figures f : all) {
            if (f.month() instanceof ManagerMonth.Found m) {
                worked = worked.add(m.daysWorked());
                working = working.add(m.workingDays());
            }
        }
        cells.add(percent(worked, working));
        cells.add(all.get(0).trainingAvailable()
                ? Cell.of(all.stream().map(f -> f.trainingDays().orElse(BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add))
                : Cell.notAvailable());
        cells.add(all.get(0).expenseAvailable()
                ? Cell.of(all.stream().map(f -> f.claimed().map(ExpenseQueries.ClaimedTotal::submitted).orElse(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                : Cell.notAvailable());
        cells.add(all.get(0).expenseAvailable()
                ? Cell.of(all.stream().map(f -> f.claimed().map(ExpenseQueries.ClaimedTotal::approved).orElse(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                : Cell.notAvailable());
        return new Row("total", "All Managers shown", cells);
    }

    static Cell percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) {
            return Cell.noData();
        }
        return Cell.of(part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, java.math.RoundingMode.HALF_UP));
    }

    /** The Zones of each Manager's current Schools, as text; "No Zone" when they have none. */
    private Map<UUID, String> zonesOf(List<UUID> managerIds) {
        Map<UUID, Set<UUID>> schoolIds = managers.currentSchoolIds(managerIds);
        Set<UUID> all = schoolIds.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        if (all.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> zoneOfSchool = schools.schools(all).stream()
                .filter(s -> s.zoneId() != null)
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::zoneId));
        Map<UUID, String> names = schools.zones(new java.util.HashSet<>(zoneOfSchool.values())).stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        Map<UUID, String> result = new HashMap<>();
        schoolIds.forEach((manager, schoolSet) -> {
            TreeSet<String> zoneNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (UUID school : schoolSet) {
                UUID zone = zoneOfSchool.get(school);
                if (zone != null && names.containsKey(zone)) {
                    zoneNames.add(names.get(zone));
                }
            }
            if (!zoneNames.isEmpty()) {
                result.put(manager, String.join(", ", zoneNames));
            }
        });
        return result;
    }

    // ---- Scope -----------------------------------------------------------------------------------------------------

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
        return allowed.stream()
                .sorted(Comparator.comparing(ManagerQueries.ManagerRef::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ---- Month state and default month -------------------------------------------------------------------------------

    @Override
    public MonthState monthState(ReportContext ctx) {
        List<ManagerQueries.ManagerRef> refs = managersInScope(ctx);
        if (refs.isEmpty()) {
            return MonthState.NONE;
        }
        Map<UUID, ManagerMonth> months = attendance.months(refs.stream().map(ManagerQueries.ManagerRef::id).toList(), ctx.month());
        boolean locked = months.values().stream().allMatch(m -> m instanceof ManagerMonth.Found f && f.locked());
        return locked ? MonthState.LOCKED : MonthState.OPEN;
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
        List<ManagerQueries.ManagerRef> refs = scope.orgWide()
                ? managers.allManagers().stream().filter(ManagerQueries.ManagerRef::active).toList()
                : managers.managerByUserId(userId).filter(ManagerQueries.ManagerRef::active).map(List::of).orElse(List.of());
        if (refs.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> ids = refs.stream().map(ManagerQueries.ManagerRef::id).toList();
        YearMonth month = YearMonth.now(java.time.ZoneId.of("Asia/Kolkata"));
        for (int i = 0; i < 12; i++, month = month.minusMonths(1)) {
            Map<UUID, ManagerMonth> months = attendance.months(ids, month);
            if (months.values().stream().allMatch(m -> m instanceof ManagerMonth.Found f && f.locked())) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }

    /** A {@link TileContext} for the same caller, month and Zone, so the dashboard sources can be asked for the report. */
    private record ReportTileContext(ReportContext ctx) implements TileContext {

        @Override
        public UUID userId() {
            return ctx.userId();
        }

        @Override
        public Set<Role> roles() {
            return ctx.roles();
        }

        @Override
        public LocalDate today() {
            return ctx.today();
        }

        @Override
        public YearMonth month() {
            return ctx.month();
        }

        @Override
        public Optional<UUID> zoneId() {
            return Optional.ofNullable(ctx.request().zoneId());
        }

        @Override
        public <T> T memo(String key, Supplier<T> compute) {
            return ctx.memo(key, compute);
        }
    }
}
