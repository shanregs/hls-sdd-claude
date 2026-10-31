package com.hls.schoolreports.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.SchoolAttendance;
import com.hls.attendance.api.SchoolAttendance.SchoolRollup;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.performance.api.PerformanceSummary;
import com.hls.performance.api.PerformanceSummary.Counts;
import com.hls.recruitment.api.SchoolStages;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.MonthState;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportScope;
import com.hls.reporting.api.ReportSource;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.api.ContractView;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.schoolbilling.api.SchoolReceivables.SchoolMonth;
import com.hls.substitution.api.SubstitutionCover;
import com.hls.substitution.api.SubstitutionCover.SchoolCover;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The School report (spec 014b US3): one row per School for the month with the Teachers placed, contract end, attendance,
 * substitution cover, observations, billing and prospect stage. Each figure comes from the module that owns it, read on its own,
 * so one source being down makes only its own columns "not available".
 */
@Component
class SchoolReportSource implements ReportSource {

    static final String KEY = "school";
    private static final Logger LOG = LoggerFactory.getLogger(SchoolReportSource.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String NOT_APPLICABLE = "Not applicable";

    private static final List<Column> COLUMNS = List.of(
            new Column("zone", "Zone", Unit.TEXT),
            new Column("placed", "Teachers placed", Unit.COUNT),
            new Column("contractEnd", "Contract end", Unit.TEXT),
            new Column("percent", "Attendance %", Unit.PERCENT),
            new Column("coverSubstitute", "Cover by substitute", Unit.DAYS),
            new Column("coverManager", "Cover by Manager", Unit.DAYS),
            new Column("coverOpen", "Cover open", Unit.DAYS),
            new Column("observations", "Observations done", Unit.COUNT),
            new Column("openIssues", "Open issues", Unit.COUNT),
            new Column("billed", "Billed", Unit.RUPEES),
            new Column("collected", "Collected", Unit.RUPEES),
            new Column("outstanding", "Outstanding", Unit.RUPEES),
            new Column("stage", "Stage", Unit.TEXT));

    private final TeacherDirectory teachers;
    private final SchoolContracts contracts;
    private final SchoolAttendance attendance;
    private final SubstitutionCover cover;
    private final PerformanceSummary performance;
    private final SchoolReceivables receivables;
    private final SchoolStages stages;
    private final AttendanceReadApi attendanceRead;
    private final SchoolScope scope;
    private final BusinessDate businessDate;

    SchoolReportSource(
            TeacherDirectory teachers,
            SchoolContracts contracts,
            SchoolAttendance attendance,
            SubstitutionCover cover,
            PerformanceSummary performance,
            SchoolReceivables receivables,
            SchoolStages stages,
            AttendanceReadApi attendanceRead,
            SchoolScope scope,
            BusinessDate businessDate) {
        this.teachers = teachers;
        this.contracts = contracts;
        this.attendance = attendance;
        this.cover = cover;
        this.performance = performance;
        this.receivables = receivables;
        this.stages = stages;
        this.attendanceRead = attendanceRead;
        this.scope = scope;
        this.businessDate = businessDate;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY, "School", PermissionModule.REPORT_SCHOOL, Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL), true, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(new ReportPart("schools", "Schools", COLUMNS, this::schools));
    }

    /** The result of an optional read, or null when it failed. */
    private <T> T read(String what, Supplier<T> source) {
        try {
            return scope.isolated(source);
        } catch (RuntimeException e) {
            LOG.warn("School report: {} could not be read", what, e);
            return null;
        }
    }

    private Section schools(ReportContext ctx) {
        List<SchoolInfo> list = scope.inScope(ctx);
        if (list.isEmpty()) {
            return Section.of("schools", "Schools", COLUMNS, List.of(), null);
        }
        YearMonth month = ctx.month();
        List<UUID> ids = list.stream().map(SchoolInfo::id).toList();
        Map<UUID, String> zoneNames = scope.zoneNames(ctx);

        Map<UUID, Integer> placed = read("placed teachers", () -> teachers.placedCountBySchool(ids, month.atEndOfMonth()));
        Map<UUID, ContractView> contractOf = read("contracts", () -> contracts.contractsOfAll(ids, month.atEndOfMonth()));
        Map<UUID, SchoolRollup> rollups = read("attendance", () -> attendance.rollupsBySchool(ids, month));
        Map<UUID, SchoolCover> covers = read("substitution", () -> cover.coverBySchool(ids, month.atDay(1), month.atEndOfMonth()));
        Map<UUID, Counts> counts = read("performance", () -> performance.bySchool(month.atDay(1), month.atEndOfMonth()));
        Map<UUID, List<SchoolMonth>> billing = read("billing", () -> receivables.schoolMonths(ids, month, month));
        Map<UUID, String> stageOf = read("stages", () -> stages.stageOf(ids));

        List<Row> rows = new ArrayList<>();
        for (SchoolInfo s : list) {
            List<Cell> cells = new ArrayList<>();
            cells.add(Cell.of(s.zoneId() == null ? "Not placed" : zoneNames.getOrDefault(s.zoneId(), "-")));
            cells.add(placed == null ? Cell.notAvailable() : Cell.of((long) placed.getOrDefault(s.id(), 0)));
            cells.add(contractEnd(contractOf, s.id()));
            cells.add(percent(rollups, s.id()));
            cells.add(coverCell(covers, s.id(), SchoolCover::bySubstitute));
            cells.add(coverCell(covers, s.id(), SchoolCover::byManager));
            cells.add(coverCell(covers, s.id(), SchoolCover::open));
            cells.add(countCell(counts, s.id(), Counts::observationsDone));
            cells.add(countCell(counts, s.id(), Counts::openIssues));
            cells.add(billingCell(billing, s.id(), SchoolMonth::owed));
            cells.add(billingCell(billing, s.id(), SchoolMonth::collected));
            cells.add(billingCell(billing, s.id(), m -> m.balance().max(BigDecimal.ZERO)));
            cells.add(stageCell(stageOf, s.id()));
            rows.add(new Row(s.id().toString(), s.name(), cells));
        }
        return Section.of("schools", "Schools", COLUMNS, rows, null);
    }

    private static Cell contractEnd(Map<UUID, ContractView> contractOf, UUID id) {
        if (contractOf == null) {
            return Cell.notAvailable();
        }
        ContractView c = contractOf.get(id);
        return c == null || c.endsOn() == null ? Cell.noData() : Cell.of(c.endsOn().toString());
    }

    private static Cell percent(Map<UUID, SchoolRollup> rollups, UUID id) {
        if (rollups == null) {
            return Cell.notAvailable();
        }
        SchoolRollup r = rollups.get(id);
        if (r == null || r.working() == null || r.working().signum() <= 0) {
            return Cell.noData();
        }
        return Cell.of(r.present().multiply(HUNDRED).divide(r.working(), 1, RoundingMode.HALF_UP));
    }

    private static Cell coverCell(Map<UUID, SchoolCover> covers, UUID id, java.util.function.Function<SchoolCover, BigDecimal> part) {
        if (covers == null) {
            return Cell.notAvailable();
        }
        SchoolCover c = covers.get(id);
        return Cell.of(c == null ? BigDecimal.ZERO : part.apply(c));
    }

    private static Cell countCell(Map<UUID, Counts> counts, UUID id, java.util.function.ToLongFunction<Counts> part) {
        if (counts == null) {
            return Cell.notAvailable();
        }
        Counts c = counts.get(id);
        return Cell.of(c == null ? 0L : part.applyAsLong(c));
    }

    private static Cell billingCell(Map<UUID, List<SchoolMonth>> billing, UUID id, java.util.function.Function<SchoolMonth, BigDecimal> part) {
        if (billing == null) {
            return Cell.notAvailable();
        }
        List<SchoolMonth> months = billing.get(id);
        return months == null || months.isEmpty() ? Cell.noData() : Cell.of(part.apply(months.get(0)));
    }

    private static Cell stageCell(Map<UUID, String> stageOf, UUID id) {
        if (stageOf == null) {
            return Cell.notAvailable();
        }
        String stage = stageOf.get(id);
        return Cell.of(stage == null ? NOT_APPLICABLE : stage);
    }

    // ---- Month state ----------------------------------------------------------------------------------------------

    @Override
    public MonthState monthState(ReportContext ctx) {
        try {
            return scope.isolated(
                    () -> PlacedTeachers.stateOf(teachers, attendanceRead, scope.visibleSchoolIds(ctx.scope()), ctx.month()));
        } catch (RuntimeException e) {
            LOG.warn("School report: month state could not be read", e);
            return MonthState.NONE;
        }
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return state == MonthState.OPEN
                ? List.of("This month is still open: figures can change until attendance is locked.")
                : List.of();
    }

    @Override
    public Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope reportScope) {
        YearMonth current = YearMonth.from(businessDate.today());
        try {
            return Optional.of(scope.isolated(() -> PlacedTeachers.lastLocked(
                            teachers, attendanceRead, scope.visibleSchoolIds(reportScope), current))
                    .orElse(current));
        } catch (RuntimeException e) {
            LOG.warn("School report: last final month could not be read", e);
            return Optional.of(current);
        }
    }
}
