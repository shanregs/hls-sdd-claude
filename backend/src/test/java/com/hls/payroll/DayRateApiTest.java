package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.DayRates;
import com.hls.support.DesignationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 017b T011: designation day rates, by role, validation, history and the rate on a date. */
class DayRateApiTest extends DesignationTestBase {

    private static final String RATES = "/api/v1/salary-structures/designation-day-rates";

    @Autowired
    private DayRates dayRates;

    private Resp record(String token, UUID designation, Object amount, LocalDate on) {
        return post(RATES + "/" + designation, token, Map.of("amount", amount, "effectiveOn", on.toString(), "note", "test"));
    }

    @Test
    void adminAndDirectorRecordARateAndEveryoneElseIsTurnedAway() {
        UUID designation = designation(signInAs(Role.ADMIN).token(), "TEACHER");

        assertThat(record(signInAs(Role.ADMIN).token(), designation, 800, LocalDate.of(2026, 9, 1)).status()).isEqualTo(201);
        assertThat(record(signInAs(Role.DIRECTOR).token(), designation, 900, LocalDate.of(2026, 10, 1)).status()).isEqualTo(201);
        assertThat(record(signInAs(Role.MANAGER).token(), designation, 1, LocalDate.of(2026, 9, 1)).status()).isEqualTo(403);
        assertThat(record(signInAs(Role.TEACHER).token(), designation, 1, LocalDate.of(2026, 9, 1)).status()).isEqualTo(403);
        assertThat(record(signInAs(Role.SYSTEM).token(), designation, 1, LocalDate.of(2026, 9, 1)).status()).isEqualTo(403);
        assertThat(get(RATES, signInAs(Role.MANAGER).token()).status()).isEqualTo(403);
        assertThat(get(RATES, signInAs(Role.DIRECTOR).token()).status()).isEqualTo(200);
    }

    @Test
    void theAmountAndDateAreChecked() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        LocalDate on = LocalDate.of(2026, 9, 1);

        assertThat(record(admin, designation, 0, on).status()).as("zero").isEqualTo(400);
        assertThat(record(admin, designation, -5, on).status()).as("negative").isEqualTo(400);
        assertThat(record(admin, designation, new BigDecimal("12.345"), on).status()).as("three decimals").isEqualTo(400);
        assertThat(post(RATES + "/" + designation, admin, Map.of("amount", 800)).status()).as("no date").isEqualTo(400);
        assertThat(record(admin, UUID.randomUUID(), 800, on).status()).as("unknown designation").isEqualTo(404);
    }

    @Test
    void theRateOnADateIsTheLatestEffectiveAndTheHistoryKeepsEveryRow() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        record(admin, designation, 800, LocalDate.of(2026, 9, 1));
        record(admin, designation, 1000, LocalDate.of(2026, 10, 10));

        assertThat(dayRates.rateOn(designation, LocalDate.of(2026, 8, 31))).isInstanceOf(DayRates.None.class);
        assertThat(((DayRates.Found) dayRates.rateOn(designation, LocalDate.of(2026, 10, 9))).perDay())
                .isEqualByComparingTo("800");
        assertThat(((DayRates.Found) dayRates.rateOn(designation, LocalDate.of(2026, 10, 10))).perDay())
                .isEqualByComparingTo("1000");
        assertThat(dayRates.rateOn(null, LocalDate.of(2026, 10, 10))).isInstanceOf(DayRates.None.class);

        Resp history = get(RATES + "/" + designation, admin);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("800.00").contains("1000.00");
        assertThat(history.body().indexOf("1000.00")).as("newest first").isLessThan(history.body().indexOf("800.00"));
    }

    @Test
    void aTieOnTheSameDateGoesToTheRowRecordedLast() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        LocalDate on = LocalDate.of(2026, 9, 1);
        record(admin, designation, 700, on);
        record(admin, designation, 750, on);

        assertThat(((DayRates.Found) dayRates.rateOn(designation, on)).perDay()).isEqualByComparingTo("750");
    }

    @Test
    void everyRecordedRateIsInTheChangeHistoryWithTheOldAndNewAmount() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "TEACHER");
        record(admin, designation, 800, LocalDate.of(2026, 9, 1));
        record(admin, designation, 900, LocalDate.of(2026, 9, 20));

        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .until(() -> jdbc.queryForObject(
                                "select count(*) from change_history_entry where entity_type = 'DESIGNATION_DAY_RATE' and entity_id = ?",
                                Integer.class, designation.toString())
                        >= 2);
        assertThat(jdbc.queryForList(
                        "select before_value, after_value from change_history_entry"
                                + " where entity_type = 'DESIGNATION_DAY_RATE' and entity_id = ? order by occurred_at",
                        designation.toString()))
                .extracting(r -> r.get("before_value") + "->" + r.get("after_value"))
                .containsExactly("null->800.00", "800.00->900.00");
    }
}
