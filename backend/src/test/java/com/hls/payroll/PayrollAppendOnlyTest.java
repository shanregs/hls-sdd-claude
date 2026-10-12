package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 013a SC-002: the rows never change, even for a direct SQL statement that bypasses the application (T010). */
class PayrollAppendOnlyTest extends MasterDataTestBase {

    private UUID insertSalary(String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into designation_salary (id, designation_id, amount, effective_on, note, recorded_by, recorded_at)"
                        + " values (?, ?, ?::numeric, current_date, null, ?, ?)",
                id,
                UUID.randomUUID(),
                amount,
                UUID.randomUUID(),
                java.sql.Timestamp.from(Instant.now()));
        return id;
    }

    private void insertPolicy(String fraction, String rounding) {
        jdbc.update(
                "insert into pay_policy (id, effective_on, lop_divisor, half_day_fraction, rounding, pay_month, note,"
                        + " recorded_by, recorded_at) values (?, date '2099-12-31', 'WORKING_DAYS', ?::numeric, ?,"
                        + " 'CALENDAR_MONTH', null, null, now())",
                UUID.randomUUID(),
                fraction,
                rounding);
    }

    @Test
    void aSalaryRowCannotBeUpdatedOrDeleted() {
        UUID id = insertSalary("30000.00");
        assertThatThrownBy(() -> jdbc.update("update designation_salary set amount = 1 where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from designation_salary where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select amount from designation_salary where id = ?", java.math.BigDecimal.class, id))
                .isEqualByComparingTo("30000");
    }

    @Test
    void aPersonSalaryRowCannotBeUpdatedOrDeletedAndInvalidRowsAreRefused() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into person_salary (id, person_kind, person_id, amount, effective_on, note, recorded_by, recorded_at)"
                        + " values (?, 'MANAGER', ?, 50000, current_date, null, ?, now())",
                id,
                UUID.randomUUID(),
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update person_salary set amount = 1 where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from person_salary where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        for (String bad : new String[] {"0", "-5", "10000000"}) {
            assertThatThrownBy(() -> jdbc.update(
                            "insert into person_salary (id, person_kind, person_id, amount, effective_on, recorded_by,"
                                    + " recorded_at) values (?, 'STAFF', ?, ?::numeric, current_date, ?, now())",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            bad,
                            UUID.randomUUID()))
                    .as("amount " + bad)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update(
                        "insert into person_salary (id, person_kind, person_id, amount, effective_on, recorded_by,"
                                + " recorded_at) values (?, 'TEACHER', ?, 1000, current_date, ?, now())",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aPolicyRowCannotBeUpdatedOrDeleted() {
        assertThatThrownBy(() -> jdbc.update("update pay_policy set half_day_fraction = 0.25"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from pay_policy"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from pay_policy where effective_on = date '2000-01-01'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theCheckConstraintsRefuseAnInvalidPolicy() {
        for (String fraction : new String[] {"0", "1", "1.5", "-0.5"}) {
            assertThatThrownBy(() -> insertPolicy(fraction, "NEAREST_RUPEE"))
                    .as("fraction " + fraction)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> insertPolicy("0.5", "SIDEWAYS")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theCheckConstraintsRefuseAZeroOrNegativeSalary() {
        assertThatThrownBy(() -> insertSalary("0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertSalary("-1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertSalary("10000000")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theSeededVersionOneIsThereWithTheStandardRules() {
        var row = jdbc.queryForMap(
                "select lop_divisor, half_day_fraction, rounding, pay_month, recorded_by from pay_policy"
                        + " where effective_on = date '2000-01-01'");
        assertThat(row).containsEntry("lop_divisor", "WORKING_DAYS").containsEntry("rounding", "NEAREST_RUPEE");
        assertThat(row).containsEntry("pay_month", "CALENDAR_MONTH");
        assertThat((java.math.BigDecimal) row.get("half_day_fraction")).isEqualByComparingTo("0.5");
        assertThat(row.get("recorded_by")).isNull();
    }
}
