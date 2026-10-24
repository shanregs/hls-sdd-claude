package com.hls.expenses.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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

    List<OwedClaim> owedTo(UUID personUserId);

    List<SpendRow> approvedSpend(YearMonth from, YearMonth to, UUID zoneIdOrNull, String categoryOrNull);

    PendingSummary pending();

    CostMeasure costPerSchool(LocalDate from, LocalDate to, UUID zoneIdOrNull);

    CostMeasure costPerTeacher(LocalDate from, LocalDate to, UUID batchIdOrNull);
}
