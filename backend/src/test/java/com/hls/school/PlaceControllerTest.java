package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 1 (FR-002): Places, duplicates allowed, lookups, PIN validation, per-role authorization. */
class PlaceControllerTest extends MasterDataTestBase {

    @Test
    void addingPlacesAcceptsDuplicateNamesAndPinCodesAndLookupReturnsEveryMatch() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        String name = uniqueName("Madurantakam");
        String pin = nextPin();

        assertThat(post("/api/v1/zones/" + zoneA + "/places", admin, Map.of("name", name, "pinCode", pin))
                        .status())
                .isEqualTo(201);
        assertThat(post("/api/v1/zones/" + zoneB + "/places", admin, Map.of("name", name, "pinCode", nextPin()))
                        .status())
                .isEqualTo(201);
        assertThat(post("/api/v1/zones/" + zoneA + "/places", admin, Map.of("name", uniqueName("Other"), "pinCode", pin))
                        .status())
                .isEqualTo(201);

        Resp byName = get("/api/v1/places?name=" + name, admin);
        assertThat(byName.status()).isEqualTo(200);
        assertThat(byName.body()).contains(zoneA.toString(), zoneB.toString());
        Resp byPin = get("/api/v1/places?pinCode=" + pin, admin);
        assertThat(byPin.body()).contains(name, zoneA.toString());
        assertThat(get("/api/v1/places?pinCode=999999", admin).body()).isEqualTo("[]");
        assertThat(get("/api/v1/places", admin).status()).isEqualTo(400);
    }

    @Test
    void pinCodeMustBeExactlySixDigits() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        for (String bad : new String[] {"12345", "1234567", "12a456", ""}) {
            Resp resp = post(
                    "/api/v1/zones/" + zoneId + "/places", admin, Map.of("name", uniqueName("P"), "pinCode", bad));
            assertThat(resp.status()).as(bad).isEqualTo(400);
            assertThat(resp.body()).contains("PIN code must be six digits");
        }
    }

    @Test
    void placesCanBeListedEditedAndDeletedByAdminAndDirector() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID zoneId = zone(admin);
        UUID placeId = place(admin, zoneId);
        String pin = nextPin();

        Resp listed = get("/api/v1/zones/" + zoneId + "/places", director);
        assertThat(listed.status()).isEqualTo(200);
        assertThat(total(listed)).isEqualTo(1);

        Resp edit = put(
                "/api/v1/places/" + placeId,
                director,
                Map.of("name", "Renamed Place", "pinCode", pin, "zoneId", zoneId));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(edit.map()).containsEntry("name", "Renamed Place").containsEntry("pinCode", pin);
        assertChangeRecorded(admin, "PLACE", placeId, "name");

        assertThat(delete("/api/v1/places/" + placeId, director).status()).isEqualTo(204);
        assertThat(total(get("/api/v1/zones/" + zoneId + "/places", admin))).isZero();
    }

    @Test
    void anUnknownZoneIsA404() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = post(
                "/api/v1/zones/" + UUID.randomUUID() + "/places",
                admin,
                Map.of("name", "X", "pinCode", nextPin()));

        assertThat(resp.status()).isEqualTo(404);
    }

    @Test
    void managerTeacherAndSystemAreRefusedOnEveryPlaceEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        UUID placeId = place(admin, zoneId);
        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/zones/" + zoneId + "/places", token).status()).isEqualTo(403);
            assertThat(get("/api/v1/places?pinCode=123456", token).status()).isEqualTo(403);
            assertThat(post("/api/v1/zones/" + zoneId + "/places", token, Map.of("name", "X", "pinCode", "123456"))
                            .status())
                    .isEqualTo(403);
            assertThat(put("/api/v1/places/" + placeId, token, Map.of("name", "X", "pinCode", "123456"))
                            .status())
                    .isEqualTo(403);
            assertThat(delete("/api/v1/places/" + placeId, token).status()).isEqualTo(403);
        }
    }

    @Test
    void listingPaginatesAndAnEmptyZoneIsNotAnError() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        for (int i = 0; i < 3; i++) {
            place(admin, zoneId);
        }

        Resp page2 = get("/api/v1/zones/" + zoneId + "/places?size=2&page=1", admin);
        assertThat(content(page2)).hasSize(1);
        assertThat(total(page2)).isEqualTo(3);
        assertThat(content(get("/api/v1/zones/" + zoneId + "/places?query=nomatch", admin)))
                .isEmpty();
    }
}
