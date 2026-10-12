package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 032 T009: what the database itself refuses, whatever the application does. */
class ManagerAttendanceSchemaTest extends MasterDataTestBase {

    private UUID insertDay(UUID manager, LocalDate date, String lat, String lng, String leaveFraction) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, day_type, check_in_at, check_in_lat, check_in_lng,"
                        + " leave_fraction, version) values (?, ?, ?, 'OFFICE', now(), ?::numeric, ?::numeric,"
                        + " ?::numeric, 0)",
                id,
                manager,
                date,
                lat,
                lng,
                leaveFraction);
        return id;
    }

    private UUID insertHistory(UUID dayId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into manager_day_history (id, day_id, changed_by, changed_at, action) values (?, ?, ?, now(), 'CHECK_IN')",
                id,
                dayId,
                UUID.randomUUID());
        return id;
    }

    @Test
    void aSecondDayForTheSameManagerAndDateIsRefused() {
        UUID manager = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 3);
        insertDay(manager, date, "12.97", "77.59", null);
        assertThatThrownBy(() -> insertDay(manager, date, "12.97", "77.59", null)).isInstanceOf(DataAccessException.class);
        // another Manager, or another date, is fine
        insertDay(UUID.randomUUID(), date, "12.97", "77.59", null);
        insertDay(manager, date.plusDays(1), "12.97", "77.59", null);
    }

    @Test
    void anOutOfRangeLocationOrLeaveFractionIsRefused() {
        for (String[] bad : new String[][] {{"91", "77"}, {"-91", "77"}, {"12", "181"}, {"12", "-181"}}) {
            assertThatThrownBy(() -> insertDay(UUID.randomUUID(), LocalDate.of(2026, 11, 3), bad[0], bad[1], null))
                    .as(bad[0] + "," + bad[1])
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> insertDay(UUID.randomUUID(), LocalDate.of(2026, 11, 3), "12", "77", "0.7"))
                .isInstanceOf(DataAccessException.class);
        insertDay(UUID.randomUUID(), LocalDate.of(2026, 11, 3), "90", "180", "0.5");
    }

    @Test
    void aDayHistoryRowCannotBeUpdatedOrDeleted() {
        UUID id = insertHistory(insertDay(UUID.randomUUID(), LocalDate.of(2026, 11, 3), "12", "77", null));
        assertThatThrownBy(() -> jdbc.update("update manager_day_history set reason = 'x' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from manager_day_history where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void aMonthEventCannotBeUpdatedOrDeleted() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into manager_month_event (id, manager_id, year_month, event, actor, occurred_at)"
                        + " values (?, ?, '2026-11', 'LOCKED', ?, now())",
                id,
                UUID.randomUUID(),
                UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update manager_month_event set reason = 'x' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from manager_month_event where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void theSettingsRowIsSeededAndTheLateTimeMustBeBeforeTheCutOff() {
        var row = jdbc.queryForMap("select missed_cutoff, late_after from manager_attendance_setting where id = 1");
        assertThat(row.get("missed_cutoff").toString()).startsWith("10:30");
        assertThat(row.get("late_after").toString()).startsWith("09:30");
        assertThatThrownBy(() -> jdbc.update(
                        "update manager_attendance_setting set late_after = time '11:00' where id = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into manager_attendance_setting (id, missed_cutoff, late_after, updated_at) values (2, time '10:00', time '09:00', now())"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlyOnePendingForgottenCheckInPerManagerAndDate() {
        UUID manager = UUID.randomUUID();
        String sql = "insert into manager_regularization (id, manager_id, day_date, proposed_time, day_type, reason,"
                + " status, requested_at) values (?, ?, date '2026-11-03', time '09:15', 'OFFICE', 'forgot', ?, now())";
        jdbc.update(sql, UUID.randomUUID(), manager, "PENDING");
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), manager, "PENDING"))
                .isInstanceOf(DataAccessException.class);
        // a decided one does not count, and the same date can be asked for again
        jdbc.update(sql, UUID.randomUUID(), manager, "REJECTED");
    }

    @Test
    void theAlertKeyMakesAnAlertHappenOncePerManagerPerDay() {
        UUID manager = UUID.randomUUID();
        String sql = "insert into manager_missed_alert (manager_id, alert_date, created_at) values (?, date '2026-11-03', ?)";
        jdbc.update(sql, manager, java.sql.Timestamp.from(Instant.now()));
        assertThatThrownBy(() -> jdbc.update(sql, manager, java.sql.Timestamp.from(Instant.now())))
                .isInstanceOf(DataAccessException.class);
    }
}
