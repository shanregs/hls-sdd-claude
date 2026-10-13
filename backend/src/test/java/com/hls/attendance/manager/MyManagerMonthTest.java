package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US5: a Manager's own month, with a status per day, the times, the flags and the month figures. */
class MyManagerMonthTest extends ManagerAttendanceTestBase {

    private static String uri(YearMonth month) {
        return ME + "/months/" + month;
    }

    @Test
    void eachDayCarriesItsStatusTimesPurposeAndFlagsAndTheMonthHasItsFigures() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        YearMonth month = lastMonth();
        List<LocalDate> wd = workingDates(month);
        LocalDate holiday = wd.get(5);
        LocalDate weeklyOff = month.atDay(1);
        while (wd.contains(weeklyOff)) {
            weeklyOff = weeklyOff.plusDays(1);
        }
        UUID worked = seedDay(manager.managerId(), wd.get(0), instant(wd.get(0), 9, 0), instant(wd.get(0), 18, 0));
        UUID field = seedFieldDay(
                manager.managerId(), wd.get(1), "Visit to St Mary's", instant(wd.get(1), 9, 40), instant(wd.get(1), 17, 0));
        jdbc.update("update manager_day set late = true where id = ?", field);
        UUID drive = UUID.randomUUID();
        jdbc.update("insert into manager_day_activity (day_id, kind, activity_id) values (?, 'DRIVE', ?)", field, drive);
        seedLeave(manager.managerId(), wd.get(2), 1.0);
        seedDay(manager.managerId(), wd.get(3), instant(wd.get(3), 9, 0), null);
        addHoliday(admin, holiday);
        try {
            Resp resp = get(uri(month), manager.token());

            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
            assertThat(resp.map()).containsEntry("month", month.toString()).containsEntry("locked", false);
            assertThat(dayOf(resp, wd.get(0))).containsEntry("status", "WORKED").containsEntry("id", worked.toString());
            assertThat(dayOf(resp, wd.get(0)).get("checkInAt")).isNotNull();
            assertThat(dayOf(resp, wd.get(0)).get("checkOutAt")).isNotNull();
            Map<String, Object> fieldDay = dayOf(resp, wd.get(1));
            assertThat(fieldDay).containsEntry("status", "FIELD").containsEntry("purpose", "Visit to St Mary's");
            assertThat((List<?>) fieldDay.get("activities")).hasSize(1);
            assertThat(dayOf(resp, wd.get(2))).containsEntry("status", "LEAVE");
            assertThat(dayOf(resp, wd.get(3))).containsEntry("status", "NO_CHECK_OUT");
            assertThat((List<Object>) dayOf(resp, wd.get(3)).get("flags")).contains("NO_CHECK_OUT");
            assertThat(dayOf(resp, wd.get(4))).containsEntry("status", "ABSENT");
            assertThat(dayOf(resp, holiday)).containsEntry("status", "HOLIDAY");
            assertThat(dayOf(resp, weeklyOff)).containsEntry("status", "WEEKLY_OFF");

            @SuppressWarnings("unchecked")
            Map<String, Object> figures = (Map<String, Object>) resp.map().get("figures");
            int workingDays = wd.size() - 1;
            assertThat(((Number) figures.get("workingDays")).doubleValue()).isEqualTo(workingDays);
            assertThat(((Number) figures.get("daysWorked")).doubleValue()).isEqualTo(3.0);
            assertThat(((Number) figures.get("fieldDays")).doubleValue()).isEqualTo(1.0);
            assertThat(((Number) figures.get("leavePaid")).doubleValue()).isEqualTo(1.0);
            assertThat(((Number) figures.get("unpaid")).doubleValue()).isEqualTo(workingDays - 4.0);
        } finally {
            removeHoliday(admin, holiday);
        }
    }

    @Test
    void theLocationAppearsOnTheManagersOwnDaysButNeverAnotherManagers() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx mine = longJoinedManager(admin);
        ManagerCtx other = longJoinedManager(admin);
        YearMonth month = lastMonth();
        LocalDate day = workingDates(month).get(0);
        seedDay(mine.managerId(), day, instant(day, 9, 0), instant(day, 18, 0));
        seedDay(other.managerId(), day, instant(day, 9, 0), instant(day, 18, 0));

        Resp resp = get(uri(month), mine.token());

        @SuppressWarnings("unchecked")
        Map<String, Object> place = (Map<String, Object>) dayOf(resp, day).get("checkInPlace");
        assertThat(place).isNotNull();
        assertThat(((Number) place.get("latitude")).doubleValue()).isEqualTo(12.97);
        // the only way to read the other Manager's days is the Admin and Director endpoint
        assertThat(get(ATT + "/managers/" + other.managerId() + "/months/" + month, mine.token()).status()).isEqualTo(403);
        assertThat(resp.body()).doesNotContain(other.managerId().toString());
    }

    @Test
    void todayIsPendingBeforeTheCutOffAndMissedAfterItWithNoCheckIn() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        assumeTrue(!workingDates(YearMonth.from(today())).isEmpty() && workingCalendar.workingDays(null, today(), today()).size() == 1);
        jdbc.update("update manager_attendance_setting set late_after = time '23:58', missed_cutoff = time '23:59' where id = 1");
        assertThat(dayOf(get(uri(YearMonth.from(today())), manager.token()), today())).containsEntry("status", "PENDING");

        jdbc.update("update manager_attendance_setting set late_after = time '00:00', missed_cutoff = time '00:01' where id = 1");
        assertThat(dayOf(get(uri(YearMonth.from(today())), manager.token()), today())).containsEntry("status", "MISSED");

        checkIn(manager.token(), "OFFICE");
        Map<String, Object> checked = dayOf(get(uri(YearMonth.from(today())), manager.token()), today());
        assertThat(checked.get("status")).isIn("WORKED", "FIELD");
    }

    @Test
    void aManagerWithNoJoiningDateGetsNoFiguresRatherThanZeroUnpaidDays() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);

        Resp resp = get(uri(lastMonth()), manager.token());

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.map().get("figures")).isNull();
        assertThat((String) resp.map().get("figuresUnavailable")).contains("joining date");
    }

    @Test
    void aBadMonthIs400AndAnotherRoleIs403() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        assertThat(get(ME + "/months/2026-13", manager.token()).status()).isEqualTo(400);
        assertThat(get(ME + "/months/nonsense", manager.token()).status()).isEqualTo(400);
        assertThat(get(uri(lastMonth()), admin).status()).isEqualTo(403);
        assertThat(get(uri(lastMonth()), teacher().token()).status()).isEqualTo(403);
    }
}
