package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 SC-004: a Manager sees only own Zone figures and own row, and never the comparison, payroll or inbox. */
class ManagerScopeTest extends TaskTestBase {

    private static final String BASE = "/api/v1/dashboard";

    @SuppressWarnings("unchecked")
    private static long overdue(Resp tile) {
        return ((Number) ((List<Map<String, Object>>) tile.map().get("values")).get(0).get("value")).longValue();
    }

    @Test
    void aManagerSeesOnlyOwnZoneAndOwnTasksAndNothingOrgWide() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone1 = zone(admin);
        UUID zone2 = zone(admin);
        ManagerCtx a = newManager(admin, zone1);
        ManagerCtx b = newManager(admin, zone2);
        UUID late = newTask(admin, a.signed().userId(), today().plusDays(1));
        moveDueDate(late, today().minusDays(1));
        UUID lateB = newTask(admin, b.signed().userId(), today().plusDays(1));
        moveDueDate(lateB, today().minusDays(1));
        moveDueDate(newTask(admin, b.signed().userId(), today().plusDays(1)), today().minusDays(2));

        Resp manifest = get(BASE, a.token());
        assertThat(manifest.status()).as(manifest.body()).isEqualTo(200);
        assertThat(manifest.body()).doesNotContain("managers.comparison").doesNotContain("PAYROLL").doesNotContain(zone2.toString());
        assertThat(manifest.map().get("inbox")).isEqualTo(false);

        assertThat(overdue(get(BASE + "/areas/MANAGERS/tiles/managers.tasks.overdue", a.token()))).isEqualTo(1);
        assertThat(overdue(get(BASE + "/areas/MANAGERS/tiles/managers.tasks.overdue?zoneId=" + zone1, a.token()))).isEqualTo(1);
        assertThat(get(BASE + "/areas/MANAGERS/tiles/managers.tasks.overdue?zoneId=" + zone2, a.token()).status()).isEqualTo(404);

        assertThat(get(BASE + "/managers", a.token()).status()).isEqualTo(403);
        assertThat(get(BASE + "/inbox", a.token()).status()).isEqualTo(403);
        assertThat(get(BASE + "/areas/PAYROLL", a.token()).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/MANAGERS/tiles/managers.comparison", a.token()).status()).isEqualTo(404);
    }

    @Test
    void aUserWithManagerAndDirectorRolesSeesTheDirectorsTilesOnce() {
        Signed both = signInAs(Role.MANAGER, Role.DIRECTOR);

        Resp manifest = get(BASE, both.token());

        assertThat(manifest.status()).as(manifest.body()).isEqualTo(200);
        assertThat(manifest.body()).contains("managers.comparison");
        assertThat(manifest.body().split("\"managers.comparison\"", -1).length - 1).isEqualTo(1);
        assertThat(manifest.body().split("\"managers.tasks.overdue\"", -1).length - 1).isEqualTo(1);
        assertThat(get(BASE + "/managers", both.token()).status()).isEqualTo(200);
    }

    @Test
    void teacherAndSystemGetAnEmptyManifest() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            Resp manifest = get(BASE, signInAs(role).token());
            assertThat(manifest.status()).isEqualTo(200);
            assertThat((List<?>) manifest.map().get("areas")).as(role.name()).isEmpty();
            assertThat(manifest.map().get("inbox")).isEqualTo(false);
        }
    }
}
