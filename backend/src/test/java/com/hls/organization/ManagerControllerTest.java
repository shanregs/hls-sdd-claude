package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 3 (FR-007/FR-008): Manager records, Zone assignment, per-role authorization, audit. */
class ManagerControllerTest extends MasterDataTestBase {

    @Test
    void aManagerRecordRequiresAUserWithTheManagerRole() {
        String admin = signInAs(Role.ADMIN).token();
        Signed teacher = signInAs(Role.TEACHER);
        Signed manager = signInAs(Role.MANAGER);

        Resp notManager = post("/api/v1/managers", admin, Map.of("userId", teacher.userId()));
        assertThat(notManager.status()).isEqualTo(400);
        assertThat(notManager.body()).contains("Manager role");
        assertThat(post("/api/v1/managers", admin, Map.of("userId", UUID.randomUUID())).status())
                .isEqualTo(400);

        Resp created = post("/api/v1/managers", admin, Map.of("userId", manager.userId()));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.map()).containsEntry("active", true).containsEntry("schoolCount", 0);
        assertThat(post("/api/v1/managers", admin, Map.of("userId", manager.userId())).status())
                .isEqualTo(409);
        assertChangeRecorded(admin, "MANAGER", created.id(), "created");
    }

    @Test
    void zonesCanBeAssignedAndAZoneMayHaveSeveralManagers() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx first = newManager(admin, zoneA, zoneB);
        ManagerCtx second = newManager(admin, zoneA);

        Resp one = get("/api/v1/managers/" + first.managerId(), director);
        assertThat(one.status()).isEqualTo(200);
        assertThat(one.body()).contains(zoneA.toString(), zoneB.toString());
        Resp zone = get("/api/v1/zones?query=" + "Zone", admin);
        assertThat(zone.body()).contains("\"managerCount\":");
        assertThat(get("/api/v1/zones?size=100", admin).body()).contains("\"managerCount\":2");

        // replacing the set drops zone B and ends its row (history keeps it)
        assignZones(admin, first.managerId(), zoneA);
        Resp afterwards = get("/api/v1/managers/" + first.managerId(), admin);
        assertThat(afterwards.body()).contains("ZONE_MANAGER");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zones = (List<Map<String, Object>>) afterwards.map().get("zones");
        assertThat(zones).hasSize(1);
        assertChangeRecorded(admin, "ZONE_MANAGER_ASSIGNMENT", first.managerId() + "/" + zoneB, "assigned");
        assertThat(second.managerId()).isNotNull();
    }

    @Test
    void candidatesAreActiveManagerUsersWithoutARecordAndOnlyForThoseWhoCanCreate() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        Signed free = signInAs(Role.MANAGER);
        ManagerCtx taken = newManager(admin);

        Resp resp = get("/api/v1/managers/candidates", director);

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains(free.userId().toString()).doesNotContain(taken.signed().userId().toString());
        assertThat(get("/api/v1/managers/candidates", signInAs(Role.MANAGER).token()).status())
                .isEqualTo(403);
    }

    @Test
    void unknownZonesAreRefusedAndAStaleVersionIsA409() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);

        Resp unknown = assignZonesRaw(admin, manager.managerId(), UUID.randomUUID());
        assertThat(unknown.status()).isEqualTo(400);

        UUID zone = zone(admin);
        Resp stale = put(
                "/api/v1/managers/" + manager.managerId() + "/zones",
                admin,
                Map.of("zoneIds", List.of(zone), "version", 999));
        assertThat(stale.status()).isEqualTo(409);
    }

    @Test
    void listSearchesByNameAndPaginates() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        String phone = manager.signed().phone();

        Resp byPhone = get("/api/v1/managers?query=" + phone, admin);
        assertThat(byPhone.status()).isEqualTo(200);
        assertThat(total(byPhone)).isEqualTo(1);
        assertThat(content(get("/api/v1/managers?size=1&page=0", admin))).hasSize(1);
        assertThat(content(get("/api/v1/managers?query=zzz-nobody", admin))).isEmpty();
        assertThat(get("/api/v1/managers?size=500", admin).map()).containsEntry("size", 100);
    }

    @Test
    void managerTeacherAndSystemAreRefusedOnEveryManagerEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        UUID school = schoolInNewZone(admin)[2];
        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/managers", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/managers/" + manager.managerId(), token).status())
                    .isEqualTo(403);
            assertThat(post("/api/v1/managers", token, Map.of("userId", UUID.randomUUID()))
                            .status())
                    .isEqualTo(403);
            assertThat(put("/api/v1/managers/" + manager.managerId() + "/zones", token, Map.of("zoneIds", List.of(), "version", 0))
                            .status())
                    .isEqualTo(403);
            assertThat(assignSchoolManagerRaw(token, school, manager.managerId()).status())
                    .isEqualTo(403);
            assertThat(get("/api/v1/schools/" + school + "/manager-history", token).status())
                    .isEqualTo(403);
        }
        assertThat(get("/api/v1/managers", null).status()).isEqualTo(401);
    }
}
