package com.hls.expenses.internal;

import com.hls.expenses.internal.ClaimViews.Row;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.ZoneInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget versus actual (spec 015 US5, FR-021, FR-022). Actual counts Approved, Paid and Paid by company claims by
 * expense date in the month; pending counts Submitted claims; remaining is budget less actual (pending shown beside it,
 * not subtracted); over means actual is more than the budget. An area with no budget has {@code budget = null}, never
 * zero. Everything is a SQL aggregate, never a load of claims one by one.
 */
@Service
@Transactional(readOnly = true)
public class BudgetQueries {

    private static final String COUNTED = "('APPROVED','PAID','PAID_BY_COMPANY')";

    /** One area for a month; {@code zoneId} null is Head office; the total row has {@code total = true}. */
    public record AreaFigures(
            UUID zoneId,
            String areaName,
            boolean total,
            UUID budgetId,
            Long budgetVersion,
            String budget,
            String actual,
            String pending,
            String remaining,
            BigDecimal percentUsed,
            boolean over) {}

    public record Overview(String month, List<AreaFigures> areas, AreaFigures total) {}

    public record CategoryFigure(String category, String categoryLabel, long count, String total) {}

    public record ClaimantFigure(UUID claimantId, String claimantName, long count, String total) {}

    public record Detail(
            String month,
            String areaName,
            List<CategoryFigure> byCategory,
            List<ClaimantFigure> byClaimant,
            List<Row> claims) {}

    public record ChangeView(Instant at, UUID actorId, String actorName, String oldAmount, String newAmount) {}

    private final JdbcTemplate jdbc;
    private final AreaBudgetRepository budgets;
    private final BudgetChangeRepository changes;
    private final ExpenseClaimRepository claims;
    private final SchoolDirectory zones;
    private final ClaimViews views;

    BudgetQueries(
            JdbcTemplate jdbc,
            AreaBudgetRepository budgets,
            BudgetChangeRepository changes,
            ExpenseClaimRepository claims,
            SchoolDirectory zones,
            ClaimViews views) {
        this.jdbc = jdbc;
        this.budgets = budgets;
        this.changes = changes;
        this.claims = claims;
        this.zones = zones;
        this.views = views;
    }

    private record Sums(BigDecimal actual, BigDecimal pending) {}

    // ------------------------------------------------------------------ overview

    public Overview overview(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Map<Optional<UUID>, Sums> sums = new HashMap<>();
        jdbc.query(
                "select zone_id, coalesce(sum(amount) filter (where status in " + COUNTED + "), 0) as actual,"
                        + " coalesce(sum(amount) filter (where status = 'SUBMITTED'), 0) as pending"
                        + " from expense_claim where expense_date between ? and ? group by zone_id",
                rs -> {
                    sums.put(
                            Optional.ofNullable(rs.getObject("zone_id", UUID.class)),
                            new Sums(rs.getBigDecimal("actual"), rs.getBigDecimal("pending")));
                },
                first,
                last);
        Map<Optional<UUID>, AreaBudget> budgetByArea = new HashMap<>();
        for (AreaBudget b : budgets.findByMonth(first)) {
            budgetByArea.put(Optional.ofNullable(b.getZoneId()), b);
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (ZoneInfo z : zones.allZones()) {
            names.put(z.id(), z.name());
        }
        List<Optional<UUID>> areas = new ArrayList<>();
        areas.add(Optional.empty());
        names.keySet().forEach(id -> areas.add(Optional.of(id)));
        for (Optional<UUID> key : sums.keySet()) {
            if (!areas.contains(key)) {
                areas.add(key);
            }
        }
        for (Optional<UUID> key : budgetByArea.keySet()) {
            if (!areas.contains(key)) {
                areas.add(key);
            }
        }
        List<AreaFigures> rows = new ArrayList<>();
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;
        BigDecimal totalBudget = null;
        for (Optional<UUID> key : areas) {
            Sums s = sums.getOrDefault(key, new Sums(BigDecimal.ZERO, BigDecimal.ZERO));
            AreaBudget b = budgetByArea.get(key);
            String name = key.map(id -> names.getOrDefault(id, "Unknown Zone")).orElse(ClaimViews.HEAD_OFFICE);
            rows.add(figures(key.orElse(null), name, false, b, s.actual(), s.pending()));
            totalActual = totalActual.add(s.actual());
            totalPending = totalPending.add(s.pending());
            if (b != null) {
                totalBudget = (totalBudget == null ? BigDecimal.ZERO : totalBudget).add(b.getAmount());
            }
        }
        rows.sort(Comparator.comparing((AreaFigures r) -> r.zoneId() != null)
                .thenComparing(r -> r.areaName().toLowerCase(java.util.Locale.ROOT)));
        AreaFigures total = figuresOf(null, "All areas", true, null, null, totalBudget, totalActual, totalPending);
        return new Overview(month.toString(), rows, total);
    }

    private static AreaFigures figures(UUID zoneId, String name, boolean total, AreaBudget b, BigDecimal actual, BigDecimal pending) {
        return figuresOf(zoneId, name, total, b == null ? null : b.getId(), b == null ? null : b.getVersion(),
                b == null ? null : b.getAmount(), actual, pending);
    }

    private static AreaFigures figuresOf(
            UUID zoneId, String name, boolean total, UUID budgetId, Long version, BigDecimal budget, BigDecimal actual, BigDecimal pending) {
        BigDecimal remaining = budget == null ? null : budget.subtract(actual);
        BigDecimal percent = budget == null || budget.signum() == 0
                ? null
                : actual.multiply(BigDecimal.valueOf(100)).divide(budget, 1, RoundingMode.HALF_UP);
        return new AreaFigures(
                zoneId,
                name,
                total,
                budgetId,
                version,
                budget == null ? null : ClaimViews.money(budget),
                ClaimViews.money(actual),
                ClaimViews.money(pending),
                remaining == null ? null : ClaimViews.money(remaining),
                percent,
                budget != null && actual.compareTo(budget) > 0);
    }

    // ------------------------------------------------------------------ remaining for the review list

    /** The area remaining budget for a month, or null when no budget is set. */
    public String remaining(UUID zoneId, YearMonth month) {
        Optional<AreaBudget> budget = zoneId == null
                ? budgets.findByZoneIdIsNullAndMonth(month.atDay(1))
                : budgets.findByZoneIdAndMonth(zoneId, month.atDay(1));
        if (budget.isEmpty()) {
            return null;
        }
        BigDecimal actual = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from expense_claim where status in " + COUNTED
                        + " and expense_date between ? and ? and zone_id is not distinct from ?::uuid",
                BigDecimal.class,
                month.atDay(1),
                month.atEndOfMonth(),
                zoneId);
        return ClaimViews.money(budget.get().getAmount().subtract(actual == null ? BigDecimal.ZERO : actual));
    }

    // ------------------------------------------------------------------ detail

    /** The claims behind actual for a month, by category and by claimant; {@code allAreas} true ignores the zone. */
    public Detail detail(YearMonth month, boolean allAreas, UUID zoneId) {
        String areaFilter = allAreas ? "" : " and zone_id is not distinct from ?::uuid";
        Object[] args = allAreas
                ? new Object[] {month.atDay(1), month.atEndOfMonth()}
                : new Object[] {month.atDay(1), month.atEndOfMonth(), zoneId};
        String base = "from expense_claim where status in " + COUNTED + " and expense_date between ? and ?" + areaFilter;
        List<CategoryFigure> byCategory = jdbc.query(
                "select category, count(*) as n, sum(amount) as total " + base + " group by category order by category",
                (rs, i) -> {
                    ExpenseCategory category = ExpenseCategory.valueOf(rs.getString("category"));
                    return new CategoryFigure(category.name(), category.label(), rs.getLong("n"), ClaimViews.money(rs.getBigDecimal("total")));
                },
                args);
        List<Object[]> byClaimantRaw = jdbc.query(
                "select claimant_id, count(*) as n, sum(amount) as total " + base + " group by claimant_id order by sum(amount) desc",
                (rs, i) -> new Object[] {rs.getObject("claimant_id", UUID.class), rs.getLong("n"), rs.getBigDecimal("total")},
                args);
        Map<UUID, String> names = views.names(byClaimantRaw.stream().map(r -> (UUID) r[0]).collect(Collectors.toSet()));
        List<ClaimantFigure> byClaimant = byClaimantRaw.stream()
                .map(r -> new ClaimantFigure((UUID) r[0], names.getOrDefault((UUID) r[0], "Unknown user"), (Long) r[1], ClaimViews.money((BigDecimal) r[2])))
                .toList();
        List<UUID> ids = jdbc.query(
                "select id " + base + " order by expense_date, created_at", (rs, i) -> rs.getObject("id", UUID.class), args);
        List<ExpenseClaim> found = claims.findAllById(ids).stream()
                .sorted(Comparator.comparing(ExpenseClaim::getExpenseDate).thenComparing(ExpenseClaim::getCreatedAt))
                .toList();
        String area = allAreas
                ? "All areas"
                : zoneId == null ? ClaimViews.HEAD_OFFICE : zones.zone(zoneId).map(ZoneInfo::name).orElse("Unknown Zone");
        return new Detail(month.toString(), area, byCategory, byClaimant, views.rows(new ExpenseCaller(null, java.util.Set.of()), found));
    }

    // ------------------------------------------------------------------ history

    public List<ChangeView> history(UUID budgetId) {
        if (!budgets.existsById(budgetId)) {
            throw new NotFoundException("Budget not found.");
        }
        List<BudgetChange> rows = changes.findByBudgetIdOrderByAtDescIdAsc(budgetId);
        Map<UUID, String> names = views.names(rows.stream().map(BudgetChange::getActorId).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> new ChangeView(
                        c.getAt(),
                        c.getActorId(),
                        names.getOrDefault(c.getActorId(), "Unknown user"),
                        c.getOldAmount() == null ? null : ClaimViews.money(c.getOldAmount()),
                        ClaimViews.money(c.getNewAmount())))
                .toList();
    }
}
