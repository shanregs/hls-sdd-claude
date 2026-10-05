package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Prospects and the per-role, per-Zone matrix of contracts/marketing-api.md. */
class ProspectApiTest extends MarketingTestBase {

    @Test
    void aProspectIsAddedWithItsContactAndOwnerAndADuplicateIsRefusedWithTheExistingOne() {
        String admin = admin();
        UUID zone = zone(admin);
        String name = uniqueName("Green Valley");

        Resp created = post(M + "/prospects", admin, prospectBody(zone, name));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.body()).contains(name, "Mrs Rao", "PROSPECT", "\"phone\":\"9111111111\"");

        Resp duplicate = post(M + "/prospects", admin, prospectBody(zone, "  " + name.toUpperCase().replace(" ", "   ") + " "));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("existing prospect");
        // the same name in another Zone is a different prospect
        assertThat(post(M + "/prospects", admin, prospectBody(zone(admin), name)).status()).isEqualTo(201);
    }

    @Test
    void contactFieldsAreValidated() {
        String admin = admin();
        UUID zone = zone(admin);

        Map<String, Object> noName = prospectBody(zone, " ");
        assertThat(post(M + "/prospects", admin, noName).status()).isEqualTo(400);
        Map<String, Object> badEmail = prospectBody(zone, uniqueName("P"));
        badEmail.put("email", "not-an-email");
        assertThat(post(M + "/prospects", admin, badEmail).status()).isEqualTo(400);
        Map<String, Object> tooMany = prospectBody(zone, uniqueName("P"));
        tooMany.put("expectedTeachers", 501);
        assertThat(post(M + "/prospects", admin, tooMany).status()).isEqualTo(400);
        Map<String, Object> noZone = prospectBody(zone, uniqueName("P"));
        noZone.remove("zoneId");
        assertThat(post(M + "/prospects", admin, noZone).status()).isEqualTo(400);
    }

    @Test
    void aZoneManagerSeesAndChangesOnlyTheProspectsOfTheirZones() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID inA = prospect(admin, zoneA);
        UUID inB = prospect(admin, zoneB);

        Resp list = get(M + "/prospects?size=100", managerA.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(inA.toString()).doesNotContain(inB.toString());
        assertThat(get(M + "/prospects/" + inA, managerA.token()).status()).isEqualTo(200);
        assertThat(get(M + "/prospects/" + inB, managerA.token()).status()).isEqualTo(404);
        Map<String, Object> change = prospectBody(zoneB, "Renamed");
        change.put("version", 0);
        assertThat(put(M + "/prospects/" + inB, managerA.token(), change).status()).isEqualTo(404);
        // adding a prospect in another Zone is also not found
        assertThat(post(M + "/prospects", managerA.token(), prospectBody(zoneB, uniqueName("Sneaky"))).status()).isEqualTo(404);
        assertThat(post(M + "/prospects", managerA.token(), prospectBody(zoneA, uniqueName("Mine"))).status()).isEqualTo(201);
        // search by name, scoped
        assertThat(get(M + "/prospects?query=Prospect%20School&size=100", managerA.token()).body()).doesNotContain(inB.toString());
    }

    @Test
    void ownerChangesAreForAdminAndDirectorAndKeepAHistory() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID prospect = prospect(manager.token(), zone);
        Signed newOwner = signInAs(Role.MANAGER);

        assertThat(post(M + "/prospects/" + prospect + "/owner", manager.token(), Map.of("ownerUserId", newOwner.userId())).status()).isEqualTo(403);
        Resp changed = post(M + "/prospects/" + prospect + "/owner", directorToken(), Map.of("ownerUserId", newOwner.userId()));
        assertThat(changed.status()).as(changed.body()).isEqualTo(200);
        assertThat(changed.body()).contains("ownerHistory").contains(newOwner.userId().toString());
        assertThat(post(M + "/prospects/" + prospect + "/owner", admin, Map.of("ownerUserId", newOwner.userId())).status()).isEqualTo(409);
        assertThat(post(M + "/prospects/" + prospect + "/owner", admin, Map.of("ownerUserId", UUID.randomUUID())).status()).isEqualTo(400);
        assertChangeRecorded(admin, "PROSPECT", prospect, "owner");
    }

    @Test
    void editingNeedsTheCurrentVersion() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        Map<String, Object> change = prospectBody(zone, uniqueName("Renamed School"));

        change.put("version", 99);
        assertThat(put(M + "/prospects/" + prospect, admin, change).status()).isEqualTo(409);
        change.put("version", 0);
        Resp ok = put(M + "/prospects/" + prospect, admin, change);
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.body()).contains("Renamed School");
    }

    @Test
    void teacherAndSystemAreRefusedAndNoTokenIs401() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));

        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(M + "/prospects", token).status()).as(role + " list").isEqualTo(403);
            assertThat(get(M + "/prospects/" + prospect, token).status()).as(role + " get").isEqualTo(403);
            assertThat(post(M + "/prospects", token, prospectBody(zone(admin), "x")).status()).as(role + " create").isEqualTo(403);
            assertThat(get(M + "/activities", token).status()).as(role + " activities").isEqualTo(403);
        }
        assertThat(get(M + "/prospects", null).status()).isEqualTo(401);
    }
}
