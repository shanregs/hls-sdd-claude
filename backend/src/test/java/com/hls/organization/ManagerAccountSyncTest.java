package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005 edge case: losing the Manager role or being deactivated empties scope, keeps history. */
class ManagerAccountSyncTest extends MasterDataTestBase {

    @Test
    void deactivatingTheUserEmptiesScopeMarksTheManagerInactiveAndFlagsTheirSchools() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        assertThat(total(get("/api/v1/schools", manager.token()))).isEqualTo(1);

        post("/api/v1/identity/users/" + manager.signed().userId() + "/deactivate", admin, null);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(get("/api/v1/managers/" + manager.managerId(), admin).body()).contains("\"active\":false");
            assertThat(get("/api/v1/schools/" + ids[2], admin).body()).contains("\"needsManager\":true");
        });
        // history is untouched
        assertThat(get("/api/v1/schools/" + ids[2] + "/manager-history", admin).body())
                .contains(manager.managerId().toString());

        post("/api/v1/identity/users/" + manager.signed().userId() + "/reactivate", admin, null);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        get("/api/v1/managers/" + manager.managerId(), admin).body())
                .contains("\"active\":true"));
        Signed back = signIn(manager.signed().userId(), manager.signed().phone(), PASSWORD);
        assertThat(total(get("/api/v1/schools", back.token()))).isEqualTo(1);
    }

    @Test
    void removingTheManagerRoleEmptiesScopeImmediatelyEvenWithAValidToken() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        // the Manager keeps the old token (still carrying the MANAGER role claim)
        assertThat(total(get("/api/v1/schools", manager.token()))).isEqualTo(1);

        Resp roles = put(
                "/api/v1/identity/users/" + manager.signed().userId() + "/roles",
                admin,
                Map.of("roles", Set.of("TEACHER")));
        assertThat(roles.status()).isEqualTo(200);

        // decided from current roles, not the token: no school is visible any more
        assertThat(total(get("/api/v1/schools", manager.token()))).isZero();
        assertThat(get("/api/v1/schools/" + ids[2], manager.token()).status()).isEqualTo(404);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        get("/api/v1/managers/" + manager.managerId(), admin).body())
                .contains("\"active\":false"));
    }
}
