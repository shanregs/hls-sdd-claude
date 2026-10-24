package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 028 T019: the no-check-in tile equals the Manager Attendance missed list; a day off is never a missed check-in. */
class ManagerAttendanceTilesTest extends ManagerAttendanceTestBase {

    private static final String TILE = "/api/v1/dashboard/areas/MANAGERS/tiles/managers.noCheckIn";

    @SuppressWarnings("unchecked")
    private static long value(Resp tile) {
        return ((Number) ((List<Map<String, Object>>) tile.map().get("values")).get(0).get("value")).longValue();
    }

    @Test
    void theTileEqualsTheMissedListForTodayAndAManagerSeesOnlyThemself() {
        assumeTrue(!workingCalendar.workingDays(null, today(), today()).isEmpty(), "today is not a working day");
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx missing = longJoinedManager(admin);
        ManagerCtx here = longJoinedManager(admin);
        seedDay(here.managerId(), today(), instant(today(), 9, 0), null);
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '00:01', late_after = time '00:00' where id = 1");

        Resp tile = get(TILE, admin);

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        assertThat(value(tile)).isEqualTo(listOf(get(ATT + "/missed", admin)).size()).isGreaterThanOrEqualTo(1);
        // a Manager sees the tile through their own attendance, and only their own row
        assertThat(value(get(TILE, missing.token()))).isEqualTo(1);
        assertThat(value(get(TILE, here.token()))).isEqualTo(0);
    }

    @Test
    void beforeTheCutOffNobodyHasMissedYet() {
        assumeTrue(!workingCalendar.workingDays(null, today(), today()).isEmpty(), "today is not a working day");
        String admin = signInAs(Role.ADMIN).token();
        longJoinedManager(admin);
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59' where id = 1");

        assertThat(value(get(TILE, admin))).isEqualTo(0);
    }

    @Test
    void onADayOffTheTileSaysSoInsteadOfShowingZero() {
        assumeTrue(workingCalendar.workingDays(null, today(), today()).isEmpty(), "today is a working day");
        Resp tile = get(TILE, signInAs(Role.ADMIN).token());
        assertThat(tile.map().get("state")).isEqualTo("NOT_WORKING_DAY");
        assertThat((List<?>) tile.map().get("values")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void theComparisonColumnCountsPastWorkingDaysWithNoCheckIn() {
        String admin = signInAs(Role.ADMIN).token();
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59' where id = 1");
        LocalDate first = YearMonth.from(today()).atDay(1);
        List<LocalDate> past = workingCalendar.workingDays(null, first, today().minusDays(1));
        assumeTrue(!past.isEmpty(), "no past working day this month yet");
        longJoinedManager(admin);
        ManagerCtx present = longJoinedManager(admin);
        for (LocalDate d : past) {
            seedDay(present.managerId(), d, instant(d, 9, 0), instant(d, 18, 0));
        }

        Resp table = get("/api/v1/dashboard/managers?sort=attendance.noCheckInDays&dir=desc", admin);

        assertThat(table.status()).as(table.body()).isEqualTo(200);
        assertThat(table.body()).contains("Days with no check-in");
        List<List<Map<String, Object>>> rows =
                (List<List<Map<String, Object>>>) ((Map<String, Object>) table.map().get("table")).get("rows");
        int index = ((List<String>) ((Map<String, Object>) table.map().get("table")).get("columns")).indexOf("Days with no check-in");
        long max = rows.stream().mapToLong(r -> ((Number) r.get(index).get("value")).longValue()).max().orElse(-1);
        assertThat(max).isGreaterThanOrEqualTo(past.size());
    }
}
