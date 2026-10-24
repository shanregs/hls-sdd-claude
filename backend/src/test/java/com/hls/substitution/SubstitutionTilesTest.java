package com.hls.substitution;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.SubstitutionTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T033: the substitution tile equals the Substitutions list for today by status; cancelled ones are not counted. */
class SubstitutionTilesTest extends SubstitutionTestBase {

    private static final String TILE = "/api/v1/dashboard/areas/TEACHERS/tiles/operations.substitutions";

    @SuppressWarnings("unchecked")
    private static long value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return ((Number) v.get("value")).longValue();
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    private void requirement(UUID teacher, UUID school, LocalDate day, String status, UUID substitute) {
        jdbc.update(
                "insert into substitution_requirement (id, teacher_id, school_id, duty_date, day_value, kind, status,"
                        + " substitute_teacher_id, cover_user_id, raised_at, updated_at, version)"
                        + " values (gen_random_uuid(), ?, ?, ?, 1.00, 'ABSENT', ?, ?, null, now(), now(), 0)",
                teacher, school, day, status, substitute);
    }

    private long listed(String token, String status, UUID zoneSchool) {
        String filter = zoneSchool == null ? "" : "&schoolId=" + zoneSchool;
        LocalDate t = today();
        return ((Number) get(SUBS + "?status=" + status + "&from=" + t + "&to=" + t + "&size=1" + filter, token)
                        .map().get("totalElements")).longValue();
    }

    @Test
    void theTileEqualsTheListForTodayAndIsScopedToAZoneManager() {
        World w = newWorld();
        TeacherCtx bench = benchTeacher(w);
        LocalDate t = today();
        requirement(w.teacherA().teacherId(), w.schoolA(), t, "OPEN", null);
        requirement(bench.teacherId(), w.schoolA(), t, "ASSIGNED", w.teacherB().teacherId());
        requirement(w.teacherB().teacherId(), w.schoolB(), t, "MANAGER_COVER", null);
        requirement(newTeacher(w.admin(), w.schoolA(), 60).teacherId(), w.schoolA(), t, "CANCELLED", null);

        Resp tile = get(TILE, w.admin());

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(value(tile, "Open")).isEqualTo(listed(w.admin(), "OPEN", null)).isGreaterThanOrEqualTo(1);
        assertThat(value(tile, "Assigned")).isEqualTo(listed(w.admin(), "ASSIGNED", null)).isGreaterThanOrEqualTo(1);
        assertThat(value(tile, "Manager cover")).isEqualTo(listed(w.admin(), "MANAGER_COVER", null)).isGreaterThanOrEqualTo(1);

        Resp mine = get(TILE, w.managerA().token());
        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(value(mine, "Open")).isEqualTo(1);
        assertThat(value(mine, "Assigned")).isEqualTo(1);
        assertThat(value(mine, "Manager cover")).isEqualTo(0);

        Resp zoneB = get(TILE + "?zoneId=" + w.zoneB(), w.admin());
        assertThat(value(zoneB, "Open")).isEqualTo(0);
        assertThat(value(zoneB, "Manager cover")).isEqualTo(1);
        assertThat(get(TILE + "?zoneId=" + w.zoneB(), w.managerA().token()).status()).isEqualTo(404);
    }
}
