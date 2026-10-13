package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US4: the missed check-in list and the Managers on leave, by date. */
class MissedListApiTest extends ManagerAttendanceTestBase {

    private LocalDate pastWorkingDay() {
        return workingDates(lastMonth()).get(5);
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> (String) r.get("managerId")).toList();
    }

    @Test
    void theMissedListHasTheManagersWithNoCheckInAndNoLeaveOnThatDate() {
        String admin = signInAs(Role.ADMIN).token();
        LocalDate date = pastWorkingDay();
        ManagerCtx missing = longJoinedManager(admin);
        ManagerCtx onLeave = longJoinedManager(admin);
        seedLeave(onLeave.managerId(), date, 1.0);
        ManagerCtx here = longJoinedManager(admin);
        seedDay(here.managerId(), date, instant(date, 9, 0), instant(date, 18, 0));

        Resp resp = get(ATT + "/missed?date=" + date, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> rows = listOf(resp);
        assertThat(ids(rows)).contains(missing.managerId().toString());
        assertThat(ids(rows)).doesNotContain(onLeave.managerId().toString(), here.managerId().toString());
        assertThat(rows.stream().filter(r -> missing.managerId().toString().equals(r.get("managerId"))).findFirst().orElseThrow())
                .containsKey("name");
    }

    @Test
    void aDayThatIsNotAWorkingDayOrIsInTheFutureHasNoMissedManagers() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        List<LocalDate> working = workingDates(lastMonth());
        LocalDate off = lastMonth().atDay(1);
        while (working.contains(off)) {
            off = off.plusDays(1);
        }

        assertThat(ids(listOf(get(ATT + "/missed?date=" + off, admin)))).doesNotContain(manager.managerId().toString());
        assertThat(listOf(get(ATT + "/missed?date=" + today().plusDays(3), admin))).isEmpty();
    }

    @Test
    void theDefaultDateIsTodayAndTodayCountsOnlyAfterTheCutOff() {
        assumeTrue(!workingCalendar.workingDays(null, today(), today()).isEmpty(), "today is not a working day");
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59' where id = 1");
        assertThat(ids(listOf(get(ATT + "/missed", admin)))).doesNotContain(manager.managerId().toString());

        jdbc.update("update manager_attendance_setting set missed_cutoff = time '00:01', late_after = time '00:00' where id = 1");

        assertThat(ids(listOf(get(ATT + "/missed", admin)))).contains(manager.managerId().toString());
    }

    @Test
    void theOnLeaveListHasTheManagersOnApprovedLeaveWithHowMuchOfTheDay() {
        String admin = signInAs(Role.ADMIN).token();
        LocalDate date = pastWorkingDay();
        ManagerCtx full = longJoinedManager(admin);
        seedLeave(full.managerId(), date, 1.0);
        ManagerCtx half = longJoinedManager(admin);
        seedLeave(half.managerId(), date, 0.5);
        ManagerCtx none = longJoinedManager(admin);

        List<Map<String, Object>> rows = listOf(get(ATT + "/on-leave?date=" + date, admin));

        assertThat(ids(rows)).contains(full.managerId().toString(), half.managerId().toString());
        assertThat(ids(rows)).doesNotContain(none.managerId().toString());
        Map<String, Object> halfRow = rows.stream()
                .filter(r -> half.managerId().toString().equals(r.get("managerId")))
                .findFirst()
                .orElseThrow();
        assertThat(((Number) halfRow.get("fraction")).doubleValue()).isEqualTo(0.5);
        assertThat(halfRow).containsEntry("unpaid", false).containsKey("name");
    }

    @Test
    void anAdminAndADirectorSeeTheListsAndEveryoneElseGets403() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        String date = pastWorkingDay().toString();

        for (String uri : new String[] {"/missed?date=" + date, "/on-leave?date=" + date}) {
            assertThat(get(ATT + uri, admin).status()).as("admin " + uri).isEqualTo(200);
            assertThat(get(ATT + uri, signInAs(Role.DIRECTOR).token()).status()).as("director " + uri).isEqualTo(200);
            assertThat(get(ATT + uri, manager.token()).status()).as("manager " + uri).isEqualTo(403);
            assertThat(get(ATT + uri, teacher().token()).status()).as("teacher " + uri).isEqualTo(403);
            assertThat(get(ATT + uri, signInAs(Role.SYSTEM).token()).status()).as("system " + uri).isEqualTo(403);
        }
        assertThat(get(ATT + "/missed?date=soon", admin).status()).isEqualTo(400);
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
