package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 028 T034: the away tile equals the Teachers the attendance read API reports away; a day off is not zero. */
class TeacherAttendanceTilesTest extends SubstitutionTestBase {

    private static final String TILE = "/api/v1/dashboard/areas/ATTENDANCE_LEAVE/tiles/attendance.awayToday";

    @Autowired
    private AttendanceReadApi attendance;

    @Autowired
    private WorkingCalendar calendar;

    @SuppressWarnings("unchecked")
    private static long away(Resp tile) {
        return ((Number) ((List<Map<String, Object>>) tile.map().get("values")).get(0).get("value")).longValue();
    }

    @Test
    void theTileCountsAbsentTeachersInTheCallersScopeAndZone() {
        LocalDate today = today();
        assumeTrue(!calendar.workingDays(null, today, today).isEmpty(), "today is not a working day");
        World w = newWorld();
        Resp marked = markAbsent(w.managerA().token(), w.teacherA().teacherId(), today, 1);
        assumeTrue(marked.status() == 200, "today cannot be marked: " + marked.body());

        Resp admin = get(TILE, w.admin());
        assertThat(admin.status()).as(admin.body()).isEqualTo(200);
        assertThat(attendance.teachersAwayOn(Set.of(w.teacherA().teacherId(), w.teacherB().teacherId()), today))
                .containsExactly(w.teacherA().teacherId());
        assertThat(away(admin)).isGreaterThanOrEqualTo(1);
        assertThat(away(get(TILE + "?zoneId=" + w.zoneA(), w.admin()))).isEqualTo(1);
        assertThat(away(get(TILE + "?zoneId=" + w.zoneB(), w.admin()))).isEqualTo(0);
        // Managers see it through Teacher Attendance, limited to the Teachers of their own Schools
        assertThat(away(get(TILE, w.managerA().token()))).isEqualTo(1);
        assertThat(away(get(TILE, w.managerB().token()))).isEqualTo(0);
    }

    @Test
    void onADayOffTheTileSaysSo() {
        LocalDate today = today();
        assumeTrue(calendar.workingDays(null, today, today).isEmpty(), "today is a working day");
        Resp tile = get(TILE, signInAs(com.hls.identity.user.Role.ADMIN).token());
        assertThat(tile.map().get("state")).isEqualTo("NOT_WORKING_DAY");
    }
}
