package com.hls.expenses.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What other modules (payroll, reports, the dashboard, the Android app) may read about expenses, from one source
 * (spec 015 FR-026). Read-only; callers apply their own permission and scope.
 */
public interface ExpenseQueries {

    /** An approved, personally paid claim not yet paid, owed to a person. */
    record OwedClaim(UUID claimId, LocalDate expenseDate, String category, BigDecimal amount) {}

    /** Approved spend of one area, category and month; a null zone is Head office. */
    record SpendRow(UUID zoneId, String category, YearMonth month, BigDecimal amount, long claims) {}

    /** One age bucket of the claims waiting for a decision. */
    record AgeBucket(String label, long count, BigDecimal total) {}

    record PendingSummary(List<AgeBucket> buckets, long count, BigDecimal total) {}

    /** The underlying cost, the divisor, the cost per unit (null when there is no divisor) and the state (OK, NO_SCHOOLS, NO_TEACHERS). */
    record CostMeasure(BigDecimal cost, long divisor, BigDecimal perUnit, String state, List<UUID> claimIds) {}

    /**
     * What a claimant claimed with expense dates in a range (spec 014c A9): {@code claims} and {@code submitted} count every
     * claim that was submitted (Submitted, Approved, Rejected, Paid, Paid by company; Drafts are not claims yet);
     * {@code approved} is the sum of the Approved, Paid and Paid by company ones, the same claims My Expenses shows.
     */
    record ClaimedTotal(long claims, BigDecimal submitted, BigDecimal approved) {}

    /** One area of a month's budget against actual (spec 014c A10); a null zone is Head office, a null budget means none set. */
    record BudgetLine(
            UUID zoneId,
            String areaName,
            BigDecimal budget,
            BigDecimal actual,
            BigDecimal pending,
            BigDecimal remaining,
            boolean over) {}

    List<OwedClaim> owedTo(UUID personUserId);

    List<SpendRow> approvedSpend(YearMonth from, YearMonth to, UUID zoneIdOrNull, String categoryOrNull);

    /** Per claimant user id; claimants with no submitted claim in the range are absent. A fixed number of queries. */
    Map<UUID, ClaimedTotal> claimedBy(LocalDate from, LocalDate to, Collection<UUID> claimantUserIds);

    /** The budget screen's rows for the month (one per area, Head office first), without the "all areas" total. */
    List<BudgetLine> budgetVsActual(YearMonth month);

    PendingSummary pending();

    CostMeasure costPerSchool(LocalDate from, LocalDate to, UUID zoneIdOrNull);

    CostMeasure costPerTeacher(LocalDate from, LocalDate to, UUID batchIdOrNull);
}
