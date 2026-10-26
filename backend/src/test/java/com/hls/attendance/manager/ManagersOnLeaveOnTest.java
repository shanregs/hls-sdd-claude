package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T044: which Managers are on leave for the whole of a date (amends the read contract of spec 032). */
class ManagersOnLeaveOnTest extends ManagerAttendanceTestBase {

    @Autowired
    private ManagerAttendanceReadApi readApi;

    private void leave(UUID managerId, LocalDate date, String fraction) {
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, leave_request_id, leave_fraction, leave_unpaid, version)"
                        + " values (?, ?, ?, ?, ?, false, 0)",
                UUID.randomUUID(),
                managerId,
                date,
                UUID.randomUUID(),
                new BigDecimal(fraction));
    }

    @Test
    void onlyAFullDayOfApprovedLeaveCounts() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx onLeave = newManager(admin);
        ManagerCtx halfDay = newManager(admin);
        ManagerCtx working = newManager(admin);
        ManagerCtx otherDay = newManager(admin);
        LocalDate date = LocalDate.now().minusDays(3);
        leave(onLeave.managerId(), date, "1.0");
        leave(halfDay.managerId(), date, "0.5");
        leave(otherDay.managerId(), date.minusDays(1), "1.0");

        var found = readApi.managersOnLeaveOn(
                List.of(onLeave.managerId(), halfDay.managerId(), working.managerId(), otherDay.managerId(), UUID.randomUUID()), date);

        assertThat(found).containsExactly(onLeave.managerId());
        assertThat(readApi.managersOnLeaveOn(List.of(), date)).isEmpty();
    }
}
