package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Spec 017b T005 and T007: the tables refuse what the rules forbid, whatever the application does. */
class PayoutSchemaTest extends MasterDataTestBase {

    private void insertDay(UUID requirement, UUID substitute, String value, String attendance, UUID markedBy) {
        jdbc.update(
                """
                insert into substitute_day (id, requirement_id, substitute_teacher_id, school_id, zone_id, absent_teacher_id,
                    day_date, day_value, live, attendance, marked_by, marked_at, source, status, version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::numeric, true, ?, ?, ?, 'ALLOCATION', 'ALLOCATED', 0, now(), now())
                """,
                UUID.randomUUID(), requirement, substitute, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 5), value, attendance, markedBy,
                markedBy == null ? null : java.sql.Timestamp.from(java.time.Instant.now()));
    }

    @Test
    void oneDayRecordPerAllocationAndTheDayValueIsHalfOrWhole() {
        UUID requirement = UUID.randomUUID();
        UUID substitute = UUID.randomUUID();
        insertDay(requirement, substitute, "1.00", "NOT_MARKED", null);

        assertThatThrownBy(() -> insertDay(requirement, substitute, "1.00", "NOT_MARKED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay(UUID.randomUUID(), substitute, "0.75", "NOT_MARKED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertDay(requirement, UUID.randomUUID(), "0.50", "NOT_MARKED", null);
    }

    @Test
    void aMarkNeedsWhoAndWhenAndOnlyThreeAttendanceValuesExist() {
        assertThatThrownBy(() -> insertDay(UUID.randomUUID(), UUID.randomUUID(), "1.00", "PRESENT", null))
                .as("a mark without who and when")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay(UUID.randomUUID(), UUID.randomUUID(), "1.00", "LATE", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertDay(UUID.randomUUID(), UUID.randomUUID(), "1.00", "PRESENT", UUID.randomUUID());
    }

    @Test
    void aMonthLockIsOnTheFirstOfTheMonthAndAReopenNeedsAReason() {
        UUID substitute = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                        "insert into substitute_month_lock (id, substitute_teacher_id, month, locked, version)"
                                + " values (?, ?, ?, true, 0)",
                        UUID.randomUUID(), substitute, LocalDate.of(2026, 9, 15)))
                .as("not the first of the month")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into substitute_month_lock (id, substitute_teacher_id, month, locked, version)"
                                + " values (?, ?, ?, false, 0)",
                        UUID.randomUUID(), substitute, LocalDate.of(2026, 9, 1)))
                .as("reopened without a reason")
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "insert into substitute_month_lock (id, substitute_teacher_id, month, locked, version)"
                        + " values (?, ?, ?, true, 0)",
                UUID.randomUUID(), substitute, LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> jdbc.update(
                        "insert into substitute_month_lock (id, substitute_teacher_id, month, locked, version)"
                                + " values (?, ?, ?, true, 0)",
                        UUID.randomUUID(), substitute, LocalDate.of(2026, 9, 1)))
                .as("one lock per substitute and month")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTeacherIsRegularUnlessMadeASubstituteAndNothingElseIsAllowed() {
        UUID teacher = UUID.randomUUID();
        jdbc.update(
                "insert into teacher (id, name, status, status_effective_on, version, created_at, updated_at)"
                        + " values (?, 'Defaulted', 'ACTIVE', current_date, 0, now(), now())",
                teacher);

        assertThat(jdbc.queryForObject("select engagement from teacher where id = ?", String.class, teacher))
                .isEqualTo("REGULAR");
        assertThatThrownBy(() -> jdbc.update("update teacher set engagement = 'CASUAL' where id = ?", teacher))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("update teacher set engagement = 'SUBSTITUTE' where id = ?", teacher)).isEqualTo(1);
    }

    @Test
    void aDayRateIsPositiveAndTheHistoryCannotBeRewritten() {
        UUID designation = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
                        "insert into designation_day_rate (id, designation_id, amount, effective_on, recorded_by, recorded_at)"
                                + " values (?, ?, 0, current_date, ?, now())",
                        UUID.randomUUID(), designation, UUID.randomUUID()))
                .as("a zero rate")
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID row = UUID.randomUUID();
        jdbc.update(
                "insert into designation_day_rate (id, designation_id, amount, effective_on, recorded_by, recorded_at)"
                        + " values (?, ?, 800, current_date, ?, now())",
                row, designation, UUID.randomUUID());
        Long first = jdbc.queryForObject("select seq from designation_day_rate where id = ?", Long.class, row);
        UUID second = UUID.randomUUID();
        jdbc.update(
                "insert into designation_day_rate (id, designation_id, amount, effective_on, recorded_by, recorded_at)"
                        + " values (?, ?, 900, current_date, ?, now())",
                second, designation, UUID.randomUUID());
        assertThat(jdbc.queryForObject("select seq from designation_day_rate where id = ?", Long.class, second))
                .as("the sequence breaks a tie on the same date")
                .isGreaterThan(first);

        assertThatThrownBy(() -> jdbc.update("update designation_day_rate set amount = 1 where id = ?", row))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from designation_day_rate where id = ?", row))
                .hasMessageContaining("append-only");
    }
}
