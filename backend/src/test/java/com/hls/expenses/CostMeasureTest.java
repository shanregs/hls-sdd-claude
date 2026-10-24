package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 015 US6 (SC-007): cost per School acquired and per Teacher trained, against hand-computed fixtures. */
class CostMeasureTest extends ExpensesTestBase {

    private Signed claimant;
    private String admin;
    private String director;
    private UUID zone;
    private LocalDate from;
    private LocalDate to;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        claimant = admin();
        admin = admin().token();
        director = director().token();
        zone = zone(admin);
        to = today();
        from = to.minusDays(30);
        day = to.minusDays(2);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> perSchool(UUID area) {
        Resp resp = get(REVIEW + "/costs/per-school?from=" + from + "&to=" + to + (area == null ? "" : "&zoneId=" + area), director);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp.map();
    }

    @Test
    void costPerSchoolCountsMarketingAndAcquisitionTravelOnly() {
        UUID visit = (UUID) marketingVisit(claimant.token(), day)[0];
        approvedClaim(claimant.token(), director, zone, day, "MARKETING", "18000.00", null);
        approvedClaim(claimant.token(), director, zone, day, "TRAVEL_FUEL", "12000.00", linkTo("SCHOOL_VISIT", visit));
        // excluded: unlinked travel, office supplies, rent, other, and a Submitted marketing claim
        approvedClaim(claimant.token(), director, zone, day, "TRAVEL_FUEL", "5000.00", null);
        approvedClaim(claimant.token(), director, zone, day, "OFFICE_SUPPLIES", "4000.00", null);
        approvedClaim(claimant.token(), director, zone, day, "RENT_UTILITIES", "9000.00", null);
        approvedClaim(claimant.token(), director, zone, day, "OTHER", "700.00", null);
        Map<String, Object> pendingBody = claimBody(day, "999.00", "MARKETING");
        pendingBody.put("zoneId", zone.toString());
        UUID pending = newDraft(claimant.token(), pendingBody);
        uploadBill(claimant.token(), pending, "bill.jpg", JPG);
        submit(claimant.token(), pending);
        // outside the period
        approvedClaim(claimant.token(), director, zone, to.minusDays(40), "MARKETING", "111.00", null);
        // three Schools won in the period, one outside it
        insertWonProspect(zone, noonOn(from));
        insertWonProspect(zone, noonOn(day));
        insertWonProspect(zone, noonOn(to));
        insertWonProspect(zone, noonOn(from.minusDays(1)));
        insertWonProspect(zone(admin), noonOn(day)); // another Zone

        Map<String, Object> measure = perSchool(zone);
        assertThat(measure).containsEntry("cost", "30000.00").containsEntry("state", "OK").containsEntry("perUnit", "10000.00");
        assertThat(((Number) measure.get("divisor")).intValue()).isEqualTo(3);
        assertThat((List<?>) measure.get("claims")).hasSize(2);
    }

    @Test
    void noWinsGivesTheCostAndANoSchoolsStateNeverADivision() {
        approvedClaim(claimant.token(), director, zone, day, "MARKETING", "2500.00", null);
        Map<String, Object> measure = perSchool(zone);
        assertThat(measure).containsEntry("cost", "2500.00").containsEntry("state", "NO_SCHOOLS");
        assertThat(measure.get("perUnit")).isNull();
        assertThat(((Number) measure.get("divisor")).intValue()).isZero();
    }

    @Test
    void roundingIsHalfUpToTwoDecimals() {
        approvedClaim(claimant.token(), director, zone, day, "MARKETING", "100.00", null);
        insertWonProspect(zone, noonOn(day));
        insertWonProspect(zone, noonOn(day));
        insertWonProspect(zone, noonOn(day));
        assertThat(perSchool(zone)).containsEntry("perUnit", "33.33");
        approvedClaim(claimant.token(), director, zone, day, "MARKETING", "0.02", null);
        // 100.02 / 3 = 33.34
        assertThat(perSchool(zone)).containsEntry("perUnit", "33.34");
    }

    @Test
    void costPerTeacherDividesTrainingStayOfBatchesEndingInThePeriodByTheirCompleters() {
        UUID batch = insertBatch(uniqueName("Batch"), from.plusDays(1), to.minusDays(5));
        for (int i = 0; i < 12; i++) {
            insertCompleter(batch, from.plusDays(1), to.minusDays(5));
        }
        // a Teacher who did not complete does not count
        jdbc.update(
                "insert into induction_enrolment (id, batch_id, teacher_id, starts_on, ends_on, enrolled_by, enrolled_at, result)"
                        + " values (?, ?, ?, ?, ?, ?, now(), 'NOT_COMPLETED')",
                UUID.randomUUID(), batch, UUID.randomUUID(), from.plusDays(1), to.minusDays(5), UUID.randomUUID());
        approvedClaim(claimant.token(), director, zone, day, "TRAINING_STAY", "40000.00", linkTo("INDUCTION_BATCH", batch));
        approvedClaim(claimant.token(), director, zone, day, "TRAINING_STAY", "20000.00", linkTo("INDUCTION_BATCH", batch));
        // a batch that ended before the period does not take part
        UUID old = insertBatch(uniqueName("Old"), from.minusDays(60), from.minusDays(40));
        insertCompleter(old, from.minusDays(60), from.minusDays(40));
        approvedClaim(claimant.token(), director, zone, day, "TRAINING_STAY", "999.00", linkTo("INDUCTION_BATCH", old));

        Resp narrowed = get(REVIEW + "/costs/per-teacher?batchId=" + batch, director);
        assertThat(narrowed.status()).as(narrowed.body()).isEqualTo(200);
        assertThat(narrowed.map()).containsEntry("cost", "60000.00").containsEntry("perUnit", "5000.00").containsEntry("state", "OK");
        assertThat(((Number) narrowed.map().get("divisor")).intValue()).isEqualTo(12);
        assertThat((List<?>) narrowed.map().get("claims")).hasSize(2);

        Resp period = get(REVIEW + "/costs/per-teacher?from=" + from + "&to=" + to, director);
        assertThat(period.status()).isEqualTo(200);
        assertThat(((Number) period.map().get("divisor")).longValue()).isGreaterThanOrEqualTo(12);
    }

    @Test
    void noCompletersGivesANoTeachersState() {
        UUID batch = insertBatch(uniqueName("Empty"), from.plusDays(1), to.minusDays(3));
        approvedClaim(claimant.token(), director, zone, day, "TRAINING_STAY", "8000.00", linkTo("INDUCTION_BATCH", batch));
        Resp resp = get(REVIEW + "/costs/per-teacher?batchId=" + batch, director);
        assertThat(resp.map()).containsEntry("cost", "8000.00").containsEntry("state", "NO_TEACHERS");
        assertThat(resp.map().get("perUnit")).isNull();
    }

    @Test
    void anInvalidPeriodIsRefusedAndManagerIsRefused() {
        assertThat(get(REVIEW + "/costs/per-school?from=" + to + "&to=" + from, director).status()).isEqualTo(400);
        assertThat(get(REVIEW + "/costs/per-school?from=" + from + "&to=" + to, signInAs(Role.MANAGER).token()).status()).isEqualTo(403);
    }
}
