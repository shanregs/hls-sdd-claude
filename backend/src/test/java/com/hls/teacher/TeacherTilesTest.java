package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T032: the Teachers tile counts equal the Teachers list by status, within the caller's scope and Zone. */
class TeacherTilesTest extends MasterDataTestBase {

    private static final String TILE = "/api/v1/dashboard/areas/TEACHERS/tiles/teachers.byStatus";

    @SuppressWarnings("unchecked")
    private static long value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return ((Number) v.get("value")).longValue();
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    private long listed(String token, String status) {
        return ((Number) get("/api/v1/teachers?status=" + status + "&size=1", token).map().get("totalElements")).longValue();
    }

    @Test
    void adminTileEqualsTheListForEveryStatus() {
        String admin = signInAs(Role.ADMIN).token();
        teacher(admin);
        post("/api/v1/teachers", admin, Map.of("name", uniqueName("Trainee"), "phone", "9444444445", "status", "IN_TRAINING"));

        Resp tile = get(TILE, admin);

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(value(tile, "Active")).isEqualTo(listed(admin, "ACTIVE")).isGreaterThanOrEqualTo(1);
        assertThat(value(tile, "In training")).isEqualTo(listed(admin, "IN_TRAINING")).isGreaterThanOrEqualTo(1);
        assertThat(value(tile, "On leave")).isEqualTo(listed(admin, "ON_LEAVE"));
        assertThat(value(tile, "Exited")).isEqualTo(listed(admin, "EXITED"));
    }

    @Test
    void aZoneManagerAndTheZoneFilterSeeOnlyTeachersPlacedInTheZone() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        UUID[] other = schoolInNewZone(admin);
        UUID inside = teacher(admin);
        placeTeacher(admin, inside, mine[2]);
        UUID outside = teacher(admin);
        placeTeacher(admin, outside, other[2]);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());

        assertThat(value(get(TILE, manager.token()), "Active")).isEqualTo(1);
        assertThat(value(get(TILE + "?zoneId=" + mine[0], admin), "Active")).isEqualTo(1);
        assertThat(value(get(TILE + "?zoneId=" + other[0], admin), "Active")).isEqualTo(1);
        assertThat(get(TILE + "?zoneId=" + mine[0], manager.token()).map().get("organizationWide")).isEqualTo(false);
        assertThat(get(TILE + "?zoneId=" + other[0], manager.token()).status()).isEqualTo(404);
    }
}
