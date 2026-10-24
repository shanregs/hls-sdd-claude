package com.hls.expenses.internal;

import com.hls.expenses.internal.ClaimViews.Row;
import com.hls.recruitment.api.WonSchools;
import com.hls.school.api.InvalidInputException;
import com.hls.training.api.InductionBatchView;
import com.hls.training.api.InductionBatches;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two cost measures (spec 015 FR-024, SC-007). Cost per School acquired is approved Marketing claims plus Travel and
 * Fuel claims linked to marketing visits, meetings, calls or campus drives (by expense date in the period) over Schools won
 * in the period. Cost per Teacher trained is approved Training Stay claims of batches ending in the period over the
 * Teachers who completed them. A zero divisor gives a state, never a division; results are rounded half-up to two decimals.
 */
@Service
@Transactional(readOnly = true)
public class CostMeasureService {

    private static final String COUNTED = "('APPROVED','PAID','PAID_BY_COMPANY')";

    /** {@code cost} is the underlying spend, {@code divisor} the Schools or Teachers, {@code perUnit} null without a divisor. */
    public record Measure(String cost, long divisor, String perUnit, String state, List<Row> claims) {}

    private final JdbcTemplate jdbc;
    private final ExpenseClaimRepository claims;
    private final ClaimViews views;
    private final WonSchools won;
    private final InductionBatches batches;

    CostMeasureService(
            JdbcTemplate jdbc, ExpenseClaimRepository claims, ClaimViews views, WonSchools won, InductionBatches batches) {
        this.jdbc = jdbc;
        this.claims = claims;
        this.views = views;
        this.won = won;
        this.batches = batches;
    }

    /** Claim ids and amounts behind a measure, kept apart from the rows so the interface can reuse them. */
    record Behind(List<ExpenseClaim> claims, BigDecimal total) {}

    Behind acquisitionClaims(LocalDate from, LocalDate to, UUID zone) {
        String kinds = "'" + String.join("','", LinkKinds.ACQUISITION) + "'";
        List<UUID> ids = jdbc.query(
                "select id from expense_claim where status in " + COUNTED + " and expense_date between ? and ?"
                        + " and (category = 'MARKETING' or (category = 'TRAVEL_FUEL' and link_kind in (" + kinds + ")))"
                        + (zone == null ? "" : " and zone_id = ?") + " order by expense_date, created_at",
                (rs, i) -> rs.getObject("id", UUID.class),
                zone == null ? new Object[] {from, to} : new Object[] {from, to, zone});
        return behind(ids);
    }

    Behind trainingClaims(List<UUID> batchIds) {
        if (batchIds.isEmpty()) {
            return new Behind(List.of(), BigDecimal.ZERO);
        }
        String marks = String.join(",", batchIds.stream().map(b -> "?").toList());
        List<UUID> ids = jdbc.query(
                "select id from expense_claim where status in " + COUNTED + " and category = 'TRAINING_STAY'"
                        + " and link_kind = 'INDUCTION_BATCH' and link_ref in (" + marks + ") order by expense_date, created_at",
                (rs, i) -> rs.getObject("id", UUID.class),
                batchIds.toArray());
        return behind(ids);
    }

    private Behind behind(List<UUID> ids) {
        List<ExpenseClaim> found = claims.findAllById(ids).stream()
                .sorted(Comparator.comparing(ExpenseClaim::getExpenseDate).thenComparing(ExpenseClaim::getCreatedAt))
                .toList();
        BigDecimal total = found.stream().map(ExpenseClaim::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Behind(found, total);
    }

    long schoolsWon(LocalDate from, LocalDate to, UUID zone) {
        return won.wonBetween(from, to, zone).size();
    }

    long teachersTrained(List<UUID> batchIds) {
        return batchIds.stream().mapToLong(batches::completedTeachers).sum();
    }

    List<UUID> batchesFor(LocalDate from, LocalDate to, UUID batchId) {
        if (batchId != null) {
            return batches.batch(batchId).map(b -> List.of(b.id())).orElse(List.of());
        }
        return batches.endingBetween(from, to).stream().map(InductionBatchView::id).toList();
    }

    static BigDecimal perUnit(BigDecimal cost, long divisor) {
        return divisor == 0 ? null : cost.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new InvalidInputException("Choose a period with a start date and an end date that is not before it.");
        }
    }

    public Measure perSchool(LocalDate from, LocalDate to, UUID zone) {
        checkPeriod(from, to);
        Behind spend = acquisitionClaims(from, to, zone);
        long schools = schoolsWon(from, to, zone);
        return measure(spend, schools, "NO_SCHOOLS");
    }

    public Measure perTeacher(LocalDate from, LocalDate to, UUID batchId) {
        if (batchId == null) {
            checkPeriod(from, to);
        }
        List<UUID> ids = batchesFor(from, to, batchId);
        Behind spend = trainingClaims(ids);
        return measure(spend, teachersTrained(ids), "NO_TEACHERS");
    }

    private Measure measure(Behind spend, long divisor, String emptyState) {
        BigDecimal per = perUnit(spend.total(), divisor);
        List<Row> rows = new ArrayList<>(views.rows(new ExpenseCaller(null, java.util.Set.of()), spend.claims()));
        return new Measure(
                ClaimViews.money(spend.total()), divisor, per == null ? null : ClaimViews.money(per), divisor == 0 ? emptyState : "OK", rows);
    }
}
