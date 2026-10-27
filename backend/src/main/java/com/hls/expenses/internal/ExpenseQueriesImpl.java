package com.hls.expenses.internal;

import com.hls.expenses.api.ExpenseQueries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The read interface other modules use (spec 015 FR-026); it adds no rules, it reuses the services behind the HTTP views. */
@Service
@Transactional(readOnly = true)
class ExpenseQueriesImpl implements ExpenseQueries {

    private final JdbcTemplate jdbc;
    private final PendingQueries pending;
    private final CostMeasureService costs;
    private final BudgetQueries budgets;

    ExpenseQueriesImpl(JdbcTemplate jdbc, PendingQueries pending, CostMeasureService costs, BudgetQueries budgets) {
        this.jdbc = jdbc;
        this.pending = pending;
        this.costs = costs;
        this.budgets = budgets;
    }

    @Override
    public Map<UUID, ClaimedTotal> claimedBy(LocalDate from, LocalDate to, Collection<UUID> claimantUserIds) {
        if (claimantUserIds.isEmpty()) {
            return Map.of();
        }
        String marks = String.join(",", java.util.Collections.nCopies(claimantUserIds.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(from, to));
        args.addAll(claimantUserIds);
        Map<UUID, ClaimedTotal> result = new HashMap<>();
        jdbc.query(
                "select claimant_id, count(*) as n, coalesce(sum(amount), 0) as submitted,"
                        + " coalesce(sum(amount) filter (where status in ('APPROVED','PAID','PAID_BY_COMPANY')), 0) as approved"
                        + " from expense_claim where status <> 'DRAFT' and expense_date between ? and ?"
                        + " and claimant_id in (" + marks + ") group by claimant_id",
                rs -> {
                    result.put(
                            rs.getObject("claimant_id", UUID.class),
                            new ClaimedTotal(rs.getLong("n"), rs.getBigDecimal("submitted"), rs.getBigDecimal("approved")));
                },
                args.toArray());
        return result;
    }

    @Override
    public List<BudgetLine> budgetVsActual(YearMonth month) {
        return budgets.overview(month).areas().stream()
                .map(a -> new BudgetLine(
                        a.zoneId(),
                        a.areaName(),
                        a.budget() == null ? null : new BigDecimal(a.budget()),
                        new BigDecimal(a.actual()),
                        new BigDecimal(a.pending()),
                        a.remaining() == null ? null : new BigDecimal(a.remaining()),
                        a.over()))
                .toList();
    }

    @Override
    public List<OwedClaim> owedTo(UUID personUserId) {
        return jdbc.query(
                "select id, expense_date, category, amount from expense_claim where claimant_id = ? and status = 'APPROVED'"
                        + " and paid_by = 'CLAIMANT' order by expense_date, created_at",
                (rs, i) -> new OwedClaim(
                        rs.getObject("id", UUID.class),
                        rs.getObject("expense_date", LocalDate.class),
                        rs.getString("category"),
                        rs.getBigDecimal("amount")),
                personUserId);
    }

    @Override
    public List<SpendRow> approvedSpend(YearMonth from, YearMonth to, UUID zoneIdOrNull, String categoryOrNull) {
        List<Object> args = new ArrayList<>(List.of(from.atDay(1), to.atEndOfMonth()));
        String filter = "";
        if (zoneIdOrNull != null) {
            filter += " and zone_id = ?";
            args.add(zoneIdOrNull);
        }
        if (categoryOrNull != null && !categoryOrNull.isBlank()) {
            filter += " and category = ?";
            args.add(ExpenseInput.category(categoryOrNull).name());
        }
        return jdbc.query(
                "select zone_id, category, date_trunc('month', expense_date)::date as m, sum(amount) as total, count(*) as n"
                        + " from expense_claim where status in ('APPROVED','PAID','PAID_BY_COMPANY')"
                        + " and expense_date between ? and ?" + filter + " group by zone_id, category, m order by m, category",
                (rs, i) -> new SpendRow(
                        rs.getObject("zone_id", UUID.class),
                        rs.getString("category"),
                        YearMonth.from(rs.getObject("m", LocalDate.class)),
                        rs.getBigDecimal("total"),
                        rs.getLong("n")),
                args.toArray());
    }

    @Override
    public PendingSummary pending() {
        PendingQueries.Summary summary = pending.summary();
        List<AgeBucket> buckets = summary.buckets().stream()
                .map(b -> new AgeBucket(b.label(), b.count(), new BigDecimal(b.total())))
                .toList();
        return new PendingSummary(buckets, summary.count(), new BigDecimal(summary.total()));
    }

    @Override
    public CostMeasure costPerSchool(LocalDate from, LocalDate to, UUID zoneIdOrNull) {
        CostMeasureService.Measure m = costs.perSchool(from, to, zoneIdOrNull);
        return convert(m);
    }

    @Override
    public CostMeasure costPerTeacher(LocalDate from, LocalDate to, UUID batchIdOrNull) {
        return convert(costs.perTeacher(from, to, batchIdOrNull));
    }

    private static CostMeasure convert(CostMeasureService.Measure m) {
        return new CostMeasure(
                new BigDecimal(m.cost()),
                m.divisor(),
                m.perUnit() == null ? null : new BigDecimal(m.perUnit()),
                m.state(),
                m.claims().stream().map(ClaimViews.Row::id).toList());
    }
}
