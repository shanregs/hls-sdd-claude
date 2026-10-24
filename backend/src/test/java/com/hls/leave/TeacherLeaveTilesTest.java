package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.AttendanceTestBase.World;
import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 028 T034: the pending Teacher leave tile equals the pending figure of Leave Management, in scope and Zone. */
class TeacherLeaveTilesTest extends SubstitutionTestBase {

    private static final String TILE = "/api/v1/dashboard/areas/ATTENDANCE_LEAVE/tiles/leave.pendingTeacher";

    @SuppressWarnings("unchecked")
    private static long pending(Resp tile) {
        return ((Number) ((List<Map<String, Object>>) tile.map().get("values")).get(0).get("value")).longValue();
    }

    @Test
    void theTileEqualsLeaveManagementsPendingCountForEachCaller() {
        World w = newWorld();
        LocalDate day = monday();
        apply(w.teacherA(), day, day);
        apply(w.teacherA(), day.plusDays(7), day.plusDays(7));
        apply(w.teacherB(), day, day);

        Resp admin = get(TILE, w.admin());
        assertThat(admin.status()).as(admin.body()).isEqualTo(200);
        long listed = ((Number) get(SUP + "?size=1", w.admin()).map().get("pendingCount")).longValue();
        assertThat(pending(admin)).isEqualTo(listed).isGreaterThanOrEqualTo(3);

        Resp mine = get(TILE, w.managerA().token());
        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(pending(mine))
                .isEqualTo(((Number) get(SUP + "?size=1", w.managerA().token()).map().get("pendingCount")).longValue())
                .isEqualTo(2);

        assertThat(pending(get(TILE + "?zoneId=" + w.zoneB(), w.admin()))).isEqualTo(1);
        assertThat(get(TILE + "?zoneId=" + w.zoneB(), w.managerA().token()).status()).isEqualTo(404);
    }
}
