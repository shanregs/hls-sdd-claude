package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase.ManagerCtx;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 024 T080 (decided by the user on 2026-10-24): a Manager's attended training day counts as worked in the month and
 * therefore in payroll when there is no check-in that day. The figure is compared with a Manager who missed the same day.
 */
class PayRunTrainingDayTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private static BigDecimal amount(Object text) {
        return new BigDecimal((String) text);
    }

    /** A held session on the date with the Manager recorded present for the given part of the day. */
    private void attendedTraining(UUID managerId, LocalDate date, String dayValue) {
        UUID session = UUID.randomUUID();
        jdbc.update(
                "insert into training_session (id, session_type, topic, session_date, starts_at, ends_at, venue_kind, status, hand_edited,"
                        + " version, created_by, created_at, updated_at)"
                        + " values (?, 'MONTHLY', 'Payroll check', ?, '10:00', '13:00', 'PHYSICAL', 'HELD', false, 0, ?, now(), now())",
                session, date, UUID.randomUUID());
        jdbc.update(
                "insert into training_attendance_entry (id, session_id, person_kind, person_id, basis, outcome, day_value, on_leave,"
                        + " sync_state, recorded_by, recorded_at, version)"
                        + " values (?, ?, 'MANAGER', ?, 'ASSIGNED', 'PRESENT', ?::numeric, false, 'NOT_APPLICABLE', ?, now(), 0)",
                UUID.randomUUID(), session, managerId, dayValue, UUID.randomUUID());
    }

    private void workAllDaysExcept(UUID managerId, LocalDate missed) {
        for (LocalDate date : workingDays(null)) {
            if (!date.equals(missed)) {
                managerCheckIn(managerId, date);
            }
        }
    }

    private void lock(String admin, UUID managerId) {
        Resp locked = post("/api/v1/manager-attendance/months/" + month + "/lock", admin, Map.of("managerId", managerId.toString()));
        assertThat(locked.status()).as(locked.body()).isEqualTo(200);
    }

    @Test
    void aTrainingDayWithNoCheckInCountsAsWorkedInThePayrollFigure() {
        String admin = signInAs(Role.ADMIN).token();
        List<LocalDate> days = workingDays(null);
        LocalDate missed = days.get(3);

        ManagerCtx trained = paidManager(admin, "30000");
        workAllDaysExcept(trained.managerId(), missed);
        attendedTraining(trained.managerId(), missed, "1.00");
        lock(admin, trained.managerId());

        ManagerCtx absent = paidManager(admin, "30000");
        workAllDaysExcept(absent.managerId(), missed);
        lock(admin, absent.managerId());

        ManagerCtx half = paidManager(admin, "30000");
        workAllDaysExcept(half.managerId(), missed);
        attendedTraining(half.managerId(), missed, "0.50");
        lock(admin, half.managerId());

        UUID run = startAndCompute(admin);

        Map<String, Object> withTraining = lineOf(admin, run, trained.managerId());
        assertThat(withTraining.get("state")).as(String.valueOf(withTraining)).isEqualTo("PAYABLE");
        assertThat(amount(withTraining.get("deduction"))).isEqualByComparingTo("0.00");
        assertThat(amount(withTraining.get("net"))).isEqualByComparingTo("30000.00");

        Map<String, Object> without = lineOf(admin, run, absent.managerId());
        assertThat(amount(without.get("deduction"))).isGreaterThan(BigDecimal.ZERO);

        Map<String, Object> halfLine = lineOf(admin, run, half.managerId());
        assertThat(amount(halfLine.get("deduction")))
                .isGreaterThan(BigDecimal.ZERO)
                .isLessThan(amount(without.get("deduction")));
    }
}
