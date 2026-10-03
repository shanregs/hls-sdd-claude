package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 2 (FR-004/FR-005/FR-005a): Schools in Places, Zone derived, guards, per-role authorization. */
class SchoolControllerTest extends MasterDataTestBase {

    private Map<String, Object> profile(String name, Long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("address", "2 Station Road");
        body.put("contactPerson", "Head Teacher");
        body.put("contactPhone", "9111111111");
        body.put("billingContact", "billing@example.com");
        body.put("version", version);
        return body;
    }

    @Test
    void aSchoolRequiresAPlaceAndItsZoneIsThePlacesZone() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        UUID placeId = place(admin, zoneId);

        Resp noPlace = post("/api/v1/schools", admin, Map.of("name", "No Place", "address", "x"));
        assertThat(noPlace.status()).isEqualTo(400);
        assertThat(noPlace.body()).contains("must be located in a Place");

        Resp created = post(
                "/api/v1/schools",
                admin,
                Map.of("name", uniqueName("St Marys"), "placeId", placeId, "address", "1 Main Road"));
        assertThat(created.status()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> zone = (Map<String, Object>) created.map().get("zone");
        @SuppressWarnings("unchecked")
        Map<String, Object> place = (Map<String, Object>) created.map().get("place");
        assertThat(zone).containsEntry("id", zoneId.toString());
        assertThat(place).containsEntry("id", placeId.toString());
        assertThat(created.map()).containsEntry("active", true);
        assertChangeRecorded(admin, "SCHOOL", created.id(), "created");
    }

    @Test
    void adminAndDirectorEditAllProfileFieldsAndMoveToAnotherZone() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID[] first = schoolInNewZone(admin);
        UUID otherZone = zone(admin);
        UUID otherPlace = place(admin, otherZone);
        Resp current = get("/api/v1/schools/" + first[2], director);
        Long version = ((Number) current.map().get("version")).longValue();

        Resp edit = put("/api/v1/schools/" + first[2], director, profile("Renamed School", version));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(edit.map())
                .containsEntry("name", "Renamed School")
                .containsEntry("billingContact", "billing@example.com");
        Long next = ((Number) edit.map().get("version")).longValue();

        Resp moved = put("/api/v1/schools/" + first[2] + "/place", admin, Map.of("placeId", otherPlace, "version", next));
        assertThat(moved.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> zone = (Map<String, Object>) moved.map().get("zone");
        assertThat(zone).containsEntry("id", otherZone.toString());
        assertChangeRecorded(admin, "SCHOOL", first[2], "place");
        assertChangeRecorded(admin, "SCHOOL", first[2], "name");
    }

    @Test
    void aStaleVersionIsA409() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        Long version = ((Number) get("/api/v1/schools/" + ids[2], admin).map().get("version")).longValue();
        put("/api/v1/schools/" + ids[2], admin, profile("First", version));

        Resp stale = put("/api/v1/schools/" + ids[2], admin, profile("Second", version));

        assertThat(stale.status()).isEqualTo(409);
    }

    @Test
    void adminAndDirectorCanDeactivateAndReactivateButAManagerCannot() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        String manager = signInAs(Role.MANAGER).token();
        UUID school = schoolInNewZone(admin)[2];

        assertThat(post("/api/v1/schools/" + school + "/deactivate", manager, null).status())
                .isEqualTo(403);
        assertThat(post("/api/v1/schools/" + school + "/deactivate", director, null).status())
                .isEqualTo(204);
        assertThat(get("/api/v1/schools/" + school, admin).map()).containsEntry("active", false);
        assertThat(post("/api/v1/schools/" + school + "/reactivate", admin, null).status())
                .isEqualTo(204);
        assertThat(get("/api/v1/schools/" + school, admin).map()).containsEntry("active", true);
        assertChangeRecorded(admin, "SCHOOL", school, "active");
    }

    @Test
    void aPlaceWithSchoolsCannotBeDeletedOrMovedToAnotherZone() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        UUID otherZone = zone(admin);

        assertThat(delete("/api/v1/places/" + ids[1], admin).status()).isEqualTo(409);
        Resp move = put(
                "/api/v1/places/" + ids[1],
                admin,
                Map.of("name", "Same", "pinCode", nextPin(), "zoneId", otherZone));
        assertThat(move.status()).isEqualTo(409);
        assertThat(move.body()).contains("Schools are located in it");
        assertThat(delete("/api/v1/zones/" + ids[0], admin).status()).isEqualTo(409);
    }

    @Test
    void teacherAndSystemAreRefusedAndAnUnassignedManagerSeesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/schools", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/schools/" + school, token).status()).isEqualTo(403);
            assertThat(post("/api/v1/schools", token, Map.of("name", "X")).status()).isEqualTo(403);
        }
        String manager = signInAs(Role.MANAGER).token();
        assertThat(content(get("/api/v1/schools", manager))).isEmpty();
        assertThat(get("/api/v1/schools/" + school, manager).status()).isEqualTo(404);
        assertThat(get("/api/v1/schools/" + UUID.randomUUID(), manager).status()).isEqualTo(404);
        assertThat(get("/api/v1/schools", null).status()).isEqualTo(401);
    }

    @Test
    void listFiltersByTextZoneAndActiveAndPaginates() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        UUID placeId = place(admin, zoneId);
        UUID a = school(admin, placeId);
        UUID b = school(admin, placeId);
        school(admin, placeId);
        post("/api/v1/schools/" + b + "/deactivate", admin, null);

        Resp byZone = get("/api/v1/schools?zoneId=" + zoneId, admin);
        assertThat(total(byZone)).isEqualTo(3);
        assertThat(total(get("/api/v1/schools?zoneId=" + zoneId + "&active=false", admin)))
                .isEqualTo(1);
        assertThat(content(get("/api/v1/schools?zoneId=" + zoneId + "&size=2&page=1", admin)))
                .hasSize(1);
        assertThat(get("/api/v1/schools?zoneId=" + zoneId + "&size=500", admin).map())
                .containsEntry("size", 100);
        assertThat(total(get("/api/v1/schools?query=zzz-no-such-school", admin))).isZero();
        assertThat(get("/api/v1/schools/" + a, admin).status()).isEqualTo(200);
    }
}
