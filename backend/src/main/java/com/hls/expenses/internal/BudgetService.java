package com.hls.expenses.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.StaleVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets and changes the monthly budget of an area (spec 015 FR-021). Every change keeps the old and new amount, who and
 * when. A budget never blocks a claim, and a month closed in payroll does not matter.
 */
@Service
public class BudgetService {

    static final String ENTITY = "EXPENSE_BUDGET";

    /** The outcome: the budget id and whether this call created the budget (so the caller can pick Create or Edit). */
    public record Saved(UUID budgetId, boolean created) {}

    private final AreaBudgetRepository budgets;
    private final BudgetChangeRepository changes;
    private final SchoolDirectory zones;
    private final ChangeRecorder audit;
    private final Clock clock;

    BudgetService(
            AreaBudgetRepository budgets, BudgetChangeRepository changes, SchoolDirectory zones, ChangeRecorder audit, Clock clock) {
        this.budgets = budgets;
        this.changes = changes;
        this.zones = zones;
        this.audit = audit;
        this.clock = clock;
    }

    /** True when a budget already exists for the area and month, so the caller needs Edit rather than Create. */
    @Transactional(readOnly = true)
    public boolean exists(UUID zoneId, String month) {
        return find(zoneId, parseMonth(month).atDay(1)).isPresent();
    }

    @Transactional
    public Saved set(ExpenseCaller caller, UUID zoneId, String month, BigDecimal amount, Long version) {
        LocalDate first = parseMonth(month).atDay(1);
        BigDecimal checked = ExpenseInput.budget(amount);
        if (zoneId != null && zones.zone(zoneId).isEmpty()) {
            throw new InvalidInputException("Choose a Zone or Head office for the area.");
        }
        Instant now = clock.instant();
        Optional<AreaBudget> existing = find(zoneId, first);
        if (existing.isEmpty()) {
            AreaBudget created = budgets.saveAndFlush(new AreaBudget(zoneId, first, checked, caller.userId(), now));
            changes.save(new BudgetChange(created.getId(), now, caller.userId(), null, checked));
            audit.recordLifecycle(caller.userId(), ENTITY, created.getId(), "created", areaLabel(zoneId) + " " + first + " " + checked);
            return new Saved(created.getId(), true);
        }
        AreaBudget budget = existing.get();
        if (version != null) {
            StaleVersion.check(AreaBudget.class, budget.getId(), budget.getVersion(), version);
        }
        BigDecimal before = budget.getAmount();
        if (before.compareTo(checked) != 0) {
            budget.change(checked, caller.userId(), now);
            budgets.saveAndFlush(budget);
            changes.save(new BudgetChange(budget.getId(), now, caller.userId(), before, checked));
            audit.record(caller.userId(), ENTITY, budget.getId(), "amount", before, checked);
        }
        return new Saved(budget.getId(), false);
    }

    private Optional<AreaBudget> find(UUID zoneId, LocalDate first) {
        return zoneId == null ? budgets.findByZoneIdIsNullAndMonth(first) : budgets.findByZoneIdAndMonth(zoneId, first);
    }

    private static String areaLabel(UUID zoneId) {
        return zoneId == null ? ClaimViews.HEAD_OFFICE : "Zone " + zoneId;
    }

    /** Accepts 2026-10 or 2026-10-01. */
    static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new InvalidInputException("The month is required.");
        }
        String text = month.trim();
        try {
            return text.length() > 7 ? YearMonth.from(LocalDate.parse(text)) : YearMonth.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }
}
