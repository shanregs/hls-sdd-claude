package com.hls.expenses.internal;

import com.hls.expenses.api.ExpenseQueries;
import com.hls.identity.permissions.PermissionModule;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage.Column;
import com.hls.reporting.api.ReportPage.Row;
import com.hls.reporting.api.ReportPage.Section;
import com.hls.reporting.api.ReportPage.Unit;
import com.hls.reporting.api.ReportSource;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The Expenses report (spec 014c US2): spend by area and category for the month, budget against actual with what remains,
 * the claims still waiting for a decision (as of today, not for the month) and the cost per School acquired and per Teacher
 * trained. Every figure comes from {@link ExpenseQueries}, the read contract behind the 015 screens, so the report equals
 * them. Available to Admin and Director only (the permission key is not grantable to a Manager or Teacher).
 */
@Component
class ExpensesReportSource implements ReportSource {

    static final String KEY = "expenses";
    static final String HEAD_OFFICE = "Head office";

    private static final List<Column> SPEND_COLUMNS = List.of(
            new Column("area", "Area", Unit.TEXT),
            new Column("category", "Category", Unit.TEXT),
            new Column("claims", "Claims", Unit.COUNT),
            new Column("amount", "Approved spend", Unit.RUPEES));

    private static final List<Column> BUDGET_COLUMNS = List.of(
            new Column("budget", "Budget", Unit.RUPEES),
            new Column("actual", "Actual", Unit.RUPEES),
            new Column("pending", "Pending", Unit.RUPEES),
            new Column("remaining", "Remaining", Unit.RUPEES),
            new Column("status", "Status", Unit.TEXT));

    private static final List<Column> PENDING_COLUMNS = List.of(
            new Column("claims", "Claims", Unit.COUNT),
            new Column("amount", "Amount", Unit.RUPEES));

    private static final List<Column> COST_COLUMNS = List.of(
            new Column("cost", "Cost", Unit.RUPEES),
            new Column("divisor", "Divided by", Unit.COUNT),
            new Column("perUnit", "Cost per unit", Unit.RUPEES),
            new Column("note", "Note", Unit.TEXT));

    private final ExpenseQueries queries;
    private final SchoolDirectory schools;

    ExpensesReportSource(ExpenseQueries queries, SchoolDirectory schools) {
        this.queries = queries;
        this.schools = schools;
    }

    @Override
    public ReportDefinition definition() {
        return new ReportDefinition(KEY, "Expenses", PermissionModule.REPORT_EXPENSES, Set.of(ReportFilter.ZONE), false, true);
    }

    @Override
    public List<SectionSource> sections() {
        return List.of(
                new Part("spend", "Spend by area and category", SPEND_COLUMNS, this::spend),
                new Part("budget", "Budget against actual", BUDGET_COLUMNS, this::budget),
                new Part("pending", "Claims waiting for a decision (as of today)", PENDING_COLUMNS, this::pending),
                new Part("costs", "Cost per School acquired and per Teacher trained", COST_COLUMNS, this::costs));
    }

    private record Part(String key, String title, List<Column> columns, Function<ReportContext, Section> reader)
            implements SectionSource {

        @Override
        public Section read(ReportContext ctx) {
            return reader.apply(ctx);
        }
    }

    // ---- Spend ------------------------------------------------------------------------------------------------------

    private Section spend(ReportContext ctx) {
        YearMonth month = ctx.month();
        UUID zone = ctx.request().zoneId();
        List<ExpenseQueries.SpendRow> found = queries.approvedSpend(month, month, zone, null);
        Map<UUID, String> names = zoneNames(found.stream().map(ExpenseQueries.SpendRow::zoneId).collect(Collectors.toSet()));
        List<Row> rows = new ArrayList<>();
        long claims = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseQueries.SpendRow r : found.stream()
                .sorted(Comparator.comparing((ExpenseQueries.SpendRow r) -> areaName(r.zoneId(), names), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ExpenseQueries.SpendRow::category))
                .toList()) {
            String area = areaName(r.zoneId(), names);
            rows.add(new Row(
                    (r.zoneId() == null ? "head-office" : r.zoneId().toString()) + "|" + r.category(),
                    area + " / " + label(r.category()),
                    List.of(Cell.of(area), Cell.of(label(r.category())), Cell.of(r.claims()), Cell.of(r.amount()))));
            claims += r.claims();
            total = total.add(r.amount());
        }
        Row totals = rows.isEmpty()
                ? null
                : new Row("total", "All areas", List.of(Cell.of(""), Cell.of(""), Cell.of(claims), Cell.of(total)));
        return Section.of("spend", "Spend by area and category", SPEND_COLUMNS, rows, totals);
    }

    // ---- Budget -----------------------------------------------------------------------------------------------------

    private Section budget(ReportContext ctx) {
        UUID zone = ctx.request().zoneId();
        List<Row> rows = new ArrayList<>();
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal budgetTotal = null;
        for (ExpenseQueries.BudgetLine line : queries.budgetVsActual(ctx.month())) {
            if (zone != null && !zone.equals(line.zoneId())) {
                continue;
            }
            rows.add(new Row(
                    line.zoneId() == null ? "head-office" : line.zoneId().toString(),
                    line.areaName(),
                    List.of(
                            line.budget() == null ? Cell.noData() : Cell.of(line.budget()),
                            Cell.of(line.actual()),
                            Cell.of(line.pending()),
                            line.remaining() == null ? Cell.noData() : Cell.of(line.remaining()),
                            Cell.of(line.budget() == null ? "No budget set" : line.over() ? "Over budget" : "Within budget"))));
            actual = actual.add(line.actual());
            pending = pending.add(line.pending());
            if (line.budget() != null) {
                budgetTotal = (budgetTotal == null ? BigDecimal.ZERO : budgetTotal).add(line.budget());
            }
        }
        Row totals = null;
        if (!rows.isEmpty()) {
            BigDecimal remaining = budgetTotal == null ? null : budgetTotal.subtract(actual);
            totals = new Row("total", "All areas", List.of(
                    budgetTotal == null ? Cell.noData() : Cell.of(budgetTotal),
                    Cell.of(actual),
                    Cell.of(pending),
                    remaining == null ? Cell.noData() : Cell.of(remaining),
                    Cell.of(budgetTotal == null ? "No budget set" : actual.compareTo(budgetTotal) > 0 ? "Over budget" : "Within budget")));
        }
        return Section.of("budget", "Budget against actual", BUDGET_COLUMNS, rows, totals);
    }

    // ---- Pending ----------------------------------------------------------------------------------------------------

    private Section pending(ReportContext ctx) {
        ExpenseQueries.PendingSummary summary = queries.pending();
        List<Row> rows = summary.buckets().stream()
                .map(b -> new Row(b.label(), b.label(), List.of(Cell.of(b.count()), Cell.of(b.total()))))
                .toList();
        Row totals = new Row("total", "All waiting claims", List.of(Cell.of(summary.count()), Cell.of(summary.total())));
        return Section.of("pending", "Claims waiting for a decision (as of today)", PENDING_COLUMNS, rows, totals);
    }

    // ---- Costs ------------------------------------------------------------------------------------------------------

    private Section costs(ReportContext ctx) {
        YearMonth month = ctx.month();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        ExpenseQueries.CostMeasure perSchool = queries.costPerSchool(from, to, ctx.request().zoneId());
        ExpenseQueries.CostMeasure perTeacher = queries.costPerTeacher(from, to, null);
        List<Row> rows = List.of(
                costRow("perSchool", "Cost per School acquired", perSchool, "No Schools won in this period"),
                costRow("perTeacher", "Cost per Teacher trained", perTeacher, "No Teachers trained in this period"));
        return Section.of("costs", "Cost per School acquired and per Teacher trained", COST_COLUMNS, rows, null);
    }

    private static Row costRow(String id, String label, ExpenseQueries.CostMeasure m, String emptyNote) {
        boolean ok = m.perUnit() != null;
        return new Row(id, label, List.of(
                Cell.of(m.cost()),
                Cell.of(m.divisor()),
                ok ? Cell.of(m.perUnit()) : Cell.noData(),
                Cell.of(ok ? "" : emptyNote)));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    private Map<UUID, String> zoneNames(Set<UUID> zoneIds) {
        Set<UUID> real = zoneIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (real.isEmpty()) {
            return Map.of();
        }
        return schools.zones(real).stream().collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
    }

    private static String areaName(UUID zoneId, Map<UUID, String> names) {
        return zoneId == null ? HEAD_OFFICE : names.getOrDefault(zoneId, "Unknown Zone");
    }

    private static String label(String category) {
        try {
            return ExpenseCategory.valueOf(category).label();
        } catch (IllegalArgumentException e) {
            return category;
        }
    }
}
