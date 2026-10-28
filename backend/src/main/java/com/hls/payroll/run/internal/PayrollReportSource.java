package com.hls.payroll.run.internal;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.payroll.api.PayrollFigures;
import com.hls.payroll.api.PayrollFigures.PersonFigures;
import com.hls.payroll.api.PayrollFigures.RunSummary;
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
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Payroll cost report (spec 014a US2) for Admin and Director: gross, loss-of-pay deduction, reimbursements and net by kind
 * of person, Zone and School, the run state, and a month-by-month table of the twelve months ending with the chosen one. Every
 * figure comes from {@link PayrollFigures}, the stored run, so it equals the run total and the run export. A month with no run
 * is "no data", never 0, and a draft run is labelled.
 */
@Component
class PayrollReportSource implements ReportSource {

    static final String KEY = "payroll";
    private static final String NO_RUN = "No pay run for this month yet.";

    private static final List<Column> GROUP_COLUMNS = List.of(
            new Column("people", "People", Unit.COUNT),
            new Column("cannotPay", "Cannot be paid", Unit.COUNT),
            new Column("gross", "Gross", Unit.RUPEES),
            new Column("lop", "LOP deduction", Unit.RUPEES),
            new Column("reimbursements", "Reimbursements", Unit.RUPEES),
            new Column("net", "Net", Unit.RUPEES));

    private static final List<Column> CYCLE_COLUMNS = List.of(
            new Column("state", "Run state", Unit.TEXT),
            new Column("people", "People", Unit.COUNT),
            new Column("gross", "Gross", Unit.RUPEES),
            new Column("lop", "LOP deduction", Unit.RUPEES),
            new Column("reimbursements", "Reimbursements", Unit.RUPEES),
            new Column("net", "Net", Unit.RUPEES));

    private final PayrollFigures figures;
    private final SchoolDirectory schools;

    PayrollReportSource(PayrollFigures figures, SchoolDirectory schools) {
        this.figures = figures;
        this.schools = schools;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(
                KEY, "Payroll cost", PermissionModule.REPORT_PAYROLL, Set.of(ReportFilter.ZONE, ReportFilter.SCHOOL), true, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("kinds", "By kind of person", GROUP_COLUMNS, ctx -> groups(ctx, "kinds", "By kind of person", kindOf())),
                new Part("zones", "By Zone", GROUP_COLUMNS, ctx -> groups(ctx, "zones", "By Zone", zoneOf(ctx))),
                new Part("schools", "By School", GROUP_COLUMNS, ctx -> groups(ctx, "schools", "By School", schoolOf(ctx))),
                new Part("cycle", "Month by month", CYCLE_COLUMNS, this::cycle));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    /** A grouping of people: an id and a label for the group the person belongs to. */
    private record Group(String id, String label) {}

    private static Function<PersonFigures, Group> kindOf() {
        return p -> switch (p.kind()) {
            case TEACHER -> new Group("TEACHER", "Teachers");
            case MANAGER -> new Group("MANAGER", "Managers");
            case STAFF -> new Group("STAFF", "Other staff");
        };
    }

    private Function<PersonFigures, Group> zoneOf(ReportContext ctx) {
        Map<UUID, String> names = zoneNames(ctx);
        return p -> p.zoneId() == null
                ? new Group("none", "No Zone")
                : new Group(p.zoneId().toString(), names.getOrDefault(p.zoneId(), "Zone"));
    }

    private Function<PersonFigures, Group> schoolOf(ReportContext ctx) {
        Map<UUID, String> names = schoolNames(ctx);
        return p -> p.schoolId() == null
                ? new Group("none", "No School")
                : new Group(p.schoolId().toString(), names.getOrDefault(p.schoolId(), "School"));
    }

    private Map<UUID, String> zoneNames(ReportContext ctx) {
        return ctx.memo("payroll.zoneNames", () -> schools.allZones().stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name)));
    }

    private Map<UUID, String> schoolNames(ReportContext ctx) {
        return ctx.memo("payroll.schoolNames", () -> schools.allSchools().stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name)));
    }

    private void requireOrgWide(ReportContext ctx) {
        if (!ctx.scope().orgWide()) {
            throw new ReportOutOfScopeException("The payroll report is for the whole organization only.");
        }
    }

    /** The people of the month run that pass the Zone and School filters; memoised for the sections of one request. */
    private List<PersonFigures> people(ReportContext ctx) {
        requireOrgWide(ctx);
        return ctx.memo("payroll.people", () -> {
            UUID zone = ctx.request().zoneId();
            UUID school = ctx.request().schoolId();
            return figures.peopleOf(ctx.month()).stream()
                    .filter(p -> zone == null || zone.equals(p.zoneId()))
                    .filter(p -> school == null || school.equals(p.schoolId()))
                    .toList();
        });
    }

    private Optional<RunSummary> run(ReportContext ctx) {
        requireOrgWide(ctx);
        return ctx.memo("payroll.run", () -> figures.runOf(ctx.month()));
    }

    private Section groups(ReportContext ctx, String key, String title, Function<PersonFigures, Group> grouping) {
        if (run(ctx).isEmpty()) {
            List<Row> none = new ArrayList<>();
            if ("kinds".equals(key)) {
                for (String[] kind : new String[][] {{"TEACHER", "Teachers"}, {"MANAGER", "Managers"}, {"STAFF", "Other staff"}}) {
                    none.add(new Row(kind[0], kind[1], java.util.Collections.nCopies(GROUP_COLUMNS.size(), Cell.noData())));
                }
            }
            return Section.of(key, title, GROUP_COLUMNS, none, null);
        }
        List<PersonFigures> all = people(ctx);
        Map<Group, List<PersonFigures>> byGroup = new LinkedHashMap<>();
        all.forEach(p -> byGroup.computeIfAbsent(grouping.apply(p), g -> new ArrayList<>()).add(p));
        List<Row> rows = byGroup.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().label(), String.CASE_INSENSITIVE_ORDER))
                .map(e -> row(e.getKey().id(), e.getKey().label(), e.getValue()))
                .toList();
        Row total = all.isEmpty() ? null : row("total", "Total", all);
        return Section.of(key, title, GROUP_COLUMNS, rows, total);
    }

    private static Row row(String id, String label, List<PersonFigures> group) {
        List<PersonFigures> payable = group.stream().filter(PersonFigures::payable).toList();
        return new Row(
                id,
                label,
                List.of(
                        Cell.of((long) payable.size()),
                        Cell.of((long) (group.size() - payable.size())),
                        Cell.of(sum(payable, PersonFigures::gross)),
                        Cell.of(sum(payable, PersonFigures::lossOfPay)),
                        Cell.of(sum(payable, PersonFigures::reimbursements)),
                        Cell.of(sum(payable, PersonFigures::net))));
    }

    private static BigDecimal sum(List<PersonFigures> rows, Function<PersonFigures, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private Section cycle(ReportContext ctx) {
        requireOrgWide(ctx);
        YearMonth to = ctx.month();
        YearMonth from = to.minusMonths(11);
        Map<YearMonth, RunSummary> byMonth = figures.runsBetween(from, to).stream()
                .collect(Collectors.toMap(RunSummary::month, r -> r));
        List<Row> rows = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            RunSummary r = byMonth.get(m);
            if (r == null) {
                rows.add(new Row(m.toString(), m.toString(), List.of(
                        Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData())));
            } else {
                rows.add(new Row(m.toString(), m.toString(), List.of(
                        Cell.of(r.state().name()),
                        Cell.of((long) r.payable()),
                        Cell.of(r.gross()),
                        Cell.of(r.lossOfPay()),
                        Cell.of(r.reimbursements()),
                        Cell.of(r.net()))));
            }
        }
        return Section.of("cycle", "Month by month", CYCLE_COLUMNS, rows, null);
    }

    @Override
    public MonthState monthState(ReportContext ctx) {
        return run(ctx).map(r -> switch (r.state()) {
            case DRAFT, AWAITING_APPROVAL -> MonthState.DRAFT;
            case APPROVED -> MonthState.APPROVED;
            case PAID -> MonthState.PAID;
        }).orElse(MonthState.NONE);
    }

    @Override
    public List<String> notes(ReportContext ctx, MonthState state) {
        return switch (state) {
            case NONE -> List.of(NO_RUN);
            case DRAFT -> List.of("This run is not approved yet: figures can change until it is approved.");
            default -> List.of();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<YearMonth> lastFinalMonth(UUID userId, Set<Role> roles, ReportScope scope) {
        return figures.lastApprovedMonth();
    }
}
