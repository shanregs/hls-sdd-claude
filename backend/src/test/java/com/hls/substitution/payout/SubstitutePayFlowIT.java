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
}
