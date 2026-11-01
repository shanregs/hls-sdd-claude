package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 017b US1: a Substitute is paid for the month through the 013b run, one line, days times the designation's daily
 * rate, no salary and no deduction; the days become PAID when the run is approved.
 */
class SubstitutePayFlowIT extends PayrollRunTestBase {

    private static final String PAYOUTS = "/api/v1/substitute-payouts";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** An Active Substitute Teacher holding a Teacher designation. */
    private UUID substitute(String admin, UUID designation) {
        UUID teacher = teacher(admin);
        long version = jdbc.queryForObject("select version from teacher where id = ?", Long.class, teacher);
        Resp engaged = put("/api/v1/teachers/" + teacher + "/engagement", admin,
                Map.of("engagement", "SUBSTITUTE", "version", version));
        assertThat(engaged.status()).as(engaged.body()).isEqualTo(200);
        if (designation != null) {
            assertThat(teacherEmployment(admin, teacher, designation, null).status()).isEqualTo(200);
        }
        return teacher;
    }

    private void dayRate(String admin, UUID designation, String amount, LocalDate effectiveOn) {
        Resp resp = post("/api/v1/salary-structures/designation-day-rates/" + designation, admin,
                Map.of("amount", new BigDecimal(amount), "effectiveOn", effectiveOn.toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    private UUID day(UUID substitute, LocalDate date, String value, String attendance) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into substitute_day (id, requirement_id, substitute_teacher_id, school_id, zone_id, absent_teacher_id,
                    day_date, day_value, live, attendance, marked_by, marked_at, source, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::numeric, true, ?, ?, ?, 'ALLOCATION', 'ALLOCATED', 0, now(), now())
                """,
                id, UUID.randomUUID(), substitute, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), date, value,
                attendance, "NOT_MARKED".equals(attendance) ? null : UUID.randomUUID(),
                "NOT_MARKED".equals(attendance) ? null : java.sql.Timestamp.from(java.time.Instant.now()));
        return id;
    }

    private Resp lock(String token, UUID substitute) {
        return post(PAYOUTS + "/months/" + substitute + "/" + month + "/lock", token, Map.of());
    }

    private String statusOf(UUID day) {
        return jdbc.queryForObject("select status from substitute_day where id = ?", String.class, day);
    }

    @Test
    void aLockedMonthIsPaidAsOneDayRateLineAndTheDaysBecomePaid() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(2));
        UUID substitute = substitute(admin, designation);
        UUID a = day(substitute, month.atDay(3), "1.00", "PRESENT");
        UUID b = day(substitute, month.atDay(4), "1.00", "PRESENT");
        UUID half = day(substitute, month.atDay(5), "0.50", "PRESENT");
        UUID absent = day(substitute, month.atDay(6), "1.00", "ABSENT");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);
        assertThat(statusOf(a)).isEqualTo("LOCKED_WAITING_RUN");
        assertThat(statusOf(absent)).isEqualTo("DID_NOT_ATTEND");

        UUID run = startAndCompute(admin);

        Map<String, Object> line = lineOf(admin, run, substitute);
        assertThat(line).as("one line for the substitute").isNotNull();
        assertThat(line.get("state")).isEqualTo("PAYABLE");
        assertThat(new BigDecimal(line.get("earned").toString())).isEqualByComparingTo("2000.00");
        assertThat(new BigDecimal(line.get("deduction").toString())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(line.get("net").toString())).isEqualByComparingTo("2000.00");
        assertThat(allLines(admin, run).stream().filter(l -> substitute.toString().equals(l.get("personId"))).count())
                .isEqualTo(1);

        String director = signInAs(Role.DIRECTOR).token();
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(statusOf(a)).isEqualTo("IN_RUN_AWAITING_APPROVAL");
        Resp approved = approveRun(director, run);
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);

        assertThat(statusOf(a)).isEqualTo("PAID");
        assertThat(statusOf(b)).isEqualTo("PAID");
        assertThat(statusOf(half)).isEqualTo("PAID");
        assertThat(statusOf(absent)).isEqualTo("DID_NOT_ATTEND");
        assertThat(jdbc.queryForObject(
                        "select sum(paid_amount) from substitute_day where substitute_teacher_id = ?",
                        BigDecimal.class, substitute))
                .isEqualByComparingTo("2000.00");
        assertThat(jdbc.queryForObject(
                        "select count(*) from payroll_paid_month where person_id = ? and person_kind = 'TEACHER'",
                        Integer.class, substitute))
                .isEqualTo(1);
        // nothing for this Teacher in the salary tables: the Substitute has no salary row
        assertThat(jdbc.queryForObject("select count(*) from person_salary where person_id = ?", Integer.class, substitute))
                .isZero();
    }

    @Test
    void aRateChangeMidMonthPricesEachDayAtTheRateOfItsDate() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(2));
        dayRate(admin, designation, "1000", month.atDay(10));
        UUID substitute = substitute(admin, designation);
        day(substitute, month.atDay(3), "1.00", "PRESENT");
        day(substitute, month.atDay(12), "1.00", "PRESENT");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);

        UUID run = startAndCompute(admin);

        Map<String, Object> line = lineOf(admin, run, substitute);
        assertThat(new BigDecimal(line.get("earned").toString())).isEqualByComparingTo("1800.00");
    }

    @Test
    void anUnlockedMonthAndAMissingRateAreReportedNeverPaidAsZero() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        UUID unlocked = substitute(admin, designation);
        day(unlocked, month.atDay(3), "1.00", "PRESENT");
        UUID noRateDesignation = designation(admin, "TEACHER");
        UUID noRate = substitute(admin, noRateDesignation);
        day(noRate, month.atDay(3), "1.00", "PRESENT");
        assertThat(lock(admin, noRate).status()).isEqualTo(200);
        UUID noDesignation = substitute(admin, null);
        day(noDesignation, month.atDay(3), "1.00", "PRESENT");
        assertThat(lock(admin, noDesignation).status()).isEqualTo(200);

        UUID run = startAndCompute(admin);

        Map<String, Object> notReady = lineOf(admin, run, unlocked);
        assertThat(notReady.get("state")).isEqualTo("CANNOT_PAY");
        assertThat(notReady.get("reason").toString()).contains("not locked");
        assertThat(notReady.get("net")).isNull();
        Map<String, Object> missingRate = lineOf(admin, run, noRate);
        assertThat(missingRate.get("state")).isEqualTo("CANNOT_PAY");
        assertThat(missingRate.get("reason").toString()).contains("No day rate");
        Map<String, Object> missingDesignation = lineOf(admin, run, noDesignation);
        assertThat(missingDesignation.get("state")).isEqualTo("CANNOT_PAY");
        assertThat(missingDesignation.get("reason").toString()).contains("no designation");
    }

    @Test
    void aSubstituteWithNoPresentDayHasNoLineAndARunWithoutSubstitutesIsUnchanged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(2));
        UUID substitute = substitute(admin, designation);
        day(substitute, month.atDay(3), "1.00", "ABSENT");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);

        UUID run = startAndCompute(admin);

        assertThat(lineOf(admin, run, substitute)).isNull();
    }

    private UUID computedRunFor(String admin, java.time.YearMonth runMonth) {
        Resp started = post(RUNS, admin, Map.of("month", runMonth.toString()));
        assertThat(started.status()).as(started.body()).isEqualTo(202);
        Map<String, Object> done = awaitComputed(admin, started.id());
        assertThat(done.get("computeState")).as(done.toString()).isEqualTo("DONE");
        return started.id();
    }

    @Test
    void aDayAddedAfterTheMonthWasPaidIsACorrectionThatTheNextRunPays() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(3));
        UUID substitute = substitute(admin, designation);
        java.time.YearMonth first = month.minusMonths(1);
        UUID paid = day(substitute, first.atDay(3), "1.00", "PRESENT");
        assertThat(post(PAYOUTS + "/months/" + substitute + "/" + first + "/lock", admin, Map.of()).status()).isEqualTo(200);
        UUID run1 = computedRunFor(admin, first);
        submitAndApprove(admin, run1);
        assertThat(statusOf(paid)).isEqualTo("PAID");

        // a second day is recorded after the payment; a person is paid once a month, so it is a Correction
        UUID late = day(substitute, first.atDay(4), "1.00", "PRESENT");
        assertThat(post(PAYOUTS + "/months/" + substitute + "/" + first + "/reopen", admin, Map.of("reason", "Late day")).status())
                .isEqualTo(200);
        assertThat(post(PAYOUTS + "/months/" + substitute + "/" + first + "/lock", admin, Map.of()).status()).isEqualTo(200);
        assertThat(statusOf(paid)).isEqualTo("PAID");
        assertThat(statusOf(late)).isEqualTo("CORRECTION_NEEDED");
        Resp mismatches = get(PAYOUTS + "/mismatches?month=" + first, admin);
        assertThat(mismatches.status()).as(mismatches.body()).isEqualTo(200);
        assertThat(mismatches.body()).contains(late.toString()).contains("Ask an Admin").contains("800");

        String correction = PAYOUTS + "/days/" + late + "/correction";
        Map<String, Object> body = Map.of("amount", 800, "reason", "Added after the month was paid");
        assertThat(post(correction, director, body).status()).as("Director has no payroll PROCESS right").isEqualTo(403);
        assertThat(post(correction, admin, Map.of("amount", 0, "reason", "x")).status()).isEqualTo(400);
        Resp raised = post(correction, admin, body);
        assertThat(raised.status()).as(raised.body()).isEqualTo(200);
        assertThat(statusOf(late)).isEqualTo("CORRECTION_RAISED");
        assertThat(post(correction, admin, body).status()).as("raised once").isEqualTo(409);
        assertThat(statusOf(paid)).as("only the late day is flagged").isEqualTo("PAID");

        // the next month's run carries the Correction on a line with no days
        UUID run2 = computedRunFor(admin, month);
        Map<String, Object> line = lineOf(admin, run2, substitute);
        assertThat(line).isNotNull();
        assertThat(new BigDecimal(line.get("earned").toString())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(line.get("adjustments").toString())).isEqualByComparingTo("800.00");
        assertThat(new BigDecimal(line.get("net").toString())).isEqualByComparingTo("800.00");
        submitAndApprove(admin, run2);

        assertThat(statusOf(late)).isEqualTo("CORRECTED");
        assertThat(statusOf(paid)).isEqualTo("PAID");
    }

    @Test
    void aSubstituteMissingFromTheApprovedPrimaryRunIsPaidByASupplementaryRunForTheSameMonth() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(2));
        UUID substitute = substitute(admin, designation);
        UUID primary = computedRunFor(admin, month);
        submitAndApprove(admin, primary);

        // the days are marked and locked only after the month's run was approved
        UUID a = day(substitute, month.atDay(3), "1.00", "PRESENT");
        UUID b = day(substitute, month.atDay(4), "1.00", "PRESENT");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);
        assertThat(statusOf(a)).isEqualTo("BACKPAY_WAITING");
        Resp owed = get(RUNS + "/" + primary + "/owed", admin);
        assertThat(owed.status()).as(owed.body()).isEqualTo(200);
        assertThat(owed.body()).contains(substitute.toString());

        Resp started = post(RUNS, admin, Map.of("month", month.toString(), "supplementary", true));
        assertThat(started.status()).as(started.body()).isEqualTo(202);
        awaitComputed(admin, started.id());
        Map<String, Object> line = lineOf(admin, started.id(), substitute);
        assertThat(line).isNotNull();
        assertThat(new BigDecimal(line.get("earned").toString())).isEqualByComparingTo("1600.00");
        submitAndApprove(admin, started.id());

        assertThat(statusOf(a)).isEqualTo("PAID");
        assertThat(statusOf(b)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                        "select distinct run_month from substitute_day where id = ?", java.sql.Date.class, a)
                .toLocalDate())
                .as("the original month is kept")
                .isEqualTo(month.atDay(1));
    }

    private Resp markDay(String token, UUID day, String attendance) {
        long version = jdbc.queryForObject("select version from substitute_day where id = ?", Long.class, day);
        return put(PAYOUTS + "/days/" + day + "/attendance", token, Map.of("attendance", attendance, "version", version));
    }

    @Test
    void aDayChangedWhileTheRunIsADraftOrAwaitsApprovalIsDroppedByTheNextCompute() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        dayRate(admin, designation, "800", month.atDay(1).minusMonths(2));
        UUID substitute = substitute(admin, designation);
        UUID a = day(substitute, month.atDay(3), "1.00", "PRESENT");
        UUID b = day(substitute, month.atDay(4), "1.00", "PRESENT");
        UUID c = day(substitute, month.atDay(5), "1.00", "PRESENT");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);
        UUID run = startAndCompute(admin);
        assertThat(new BigDecimal(lineOf(admin, run, substitute).get("earned").toString())).isEqualByComparingTo("2400.00");
        assertThat(statusOf(a)).isEqualTo("IN_DRAFT_RUN");

        // while a draft: the month is reopened and one day becomes Absent; the next compute drops it
        String reopen = PAYOUTS + "/months/" + substitute + "/" + month + "/reopen";
        assertThat(post(reopen, admin, Map.of("reason", "b was absent")).status()).isEqualTo(200);
        assertThat(markDay(admin, b, "ABSENT").status()).isEqualTo(200);
        assertThat(statusOf(b)).isEqualTo("DID_NOT_ATTEND");
        assertThat(lock(admin, substitute).status()).isEqualTo(200);
        assertThat(post(RUNS + "/" + run + "/recompute", admin, Map.of()).status()).isEqualTo(202);
        awaitComputed(admin, run);
        assertThat(new BigDecimal(lineOf(admin, run, substitute).get("earned").toString())).isEqualByComparingTo("1600.00");

        // while awaiting approval: a change is flagged, and returning the run lets it be computed again
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(statusOf(c)).isEqualTo("IN_RUN_AWAITING_APPROVAL");
        assertThat(post(reopen, admin, Map.of("reason", "c was absent")).status()).isEqualTo(200);
        assertThat(markDay(admin, c, "ABSENT").status()).isEqualTo(200);
        assertThat(statusOf(c)).isEqualTo("CHANGED_RUN_AWAITING");
        assertThat(get(PAYOUTS + "/mismatches?month=" + month, admin).body()).contains(c.toString());
        String director = signInAs(Role.DIRECTOR).token();
        assertThat(returnRun(director, run, "A day changed").status()).isEqualTo(200);
        assertThat(lock(admin, substitute).status()).isEqualTo(200);
        assertThat(post(RUNS + "/" + run + "/recompute", admin, Map.of()).status()).isEqualTo(202);
        awaitComputed(admin, run);
        assertThat(new BigDecimal(lineOf(admin, run, substitute).get("earned").toString())).isEqualByComparingTo("800.00");

        // approved: only the days still Present are paid, and they are PAID; the dropped ones stay as recorded
        submitAndApprove(admin, run);
        assertThat(statusOf(a)).isEqualTo("PAID");
        assertThat(statusOf(b)).isEqualTo("DID_NOT_ATTEND");
        assertThat(statusOf(c)).isEqualTo("DID_NOT_ATTEND");
        assertThat(jdbc.queryForObject("select sum(paid_amount) from substitute_day where substitute_teacher_id = ?",
                        BigDecimal.class, substitute))
                .isEqualByComparingTo("800.00");
    }
}
