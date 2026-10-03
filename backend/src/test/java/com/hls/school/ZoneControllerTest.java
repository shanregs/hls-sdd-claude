package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 1 (FR-001): Zone CRUD, delete rules, per-role authorization, audit. */
class ZoneControllerTest extends MasterDataTestBase {

    @Test
    void adminAndDirectorCanCreateListAndRename() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        String name = uniqueName("Chengalpattu");

        Resp created = post("/api/v1/zones", admin, Map.of("name", name));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.map())
                .containsEntry("name", name)
                .containsEntry("placeCount", 0)
                .containsEntry("schoolCount", 0);
        UUID id = created.id();

        Resp listed = get("/api/v1/zones?query=" + name.substring(0, 12), director);
        assertThat(listed.status()).isEqualTo(200);
        assertThat(listed.body()).contains(name);

        Long version = ((Number) created.map().get("version")).longValue();
        String renamed = name + " North";
        Resp rename = put("/api/v1/zones/" + id, director, Map.of("name", renamed, "version", version));
        assertThat(rename.status()).isEqualTo(200);
        assertThat(rename.map()).containsEntry("name", renamed);
        assertChangeRecorded(admin, "ZONE", id, "name");
    }

    @Test
    void duplicateNamesAreRefusedCaseInsensitively() {
        String admin = signInAs(Role.ADMIN).token();
        String name = uniqueName("Dup");
        post("/api/v1/zones", admin, Map.of("name", name));

        Resp dup = post("/api/v1/zones", admin, Map.of("name", name.toUpperCase()));

        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.body()).contains("already exists");
    }

    @Test
    void aStaleVersionOnRenameIsA409() {
        String admin = signInAs(Role.ADMIN).token();
        Resp created = post("/api/v1/zones", admin, Map.of("name", uniqueName("Stale")));
        Long version = ((Number) created.map().get("version")).longValue();
        put("/api/v1/zones/" + created.id(), admin, Map.of("name", uniqueName("First"), "version", version));

        Resp stale = put("/api/v1/zones/" + created.id(), admin, Map.of("name", uniqueName("Second"), "version", version));

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body()).contains("changed by someone else");
    }

    @Test
    void onlyAdminMayDeleteAndOnlyWhenNothingDependsOnTheZone() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID empty = zone(admin);
        UUID withPlace = zone(admin);
        place(admin, withPlace);

        assertThat(delete("/api/v1/zones/" + empty, director).status()).isEqualTo(403);
        Resp refused = delete("/api/v1/zones/" + withPlace, admin);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("1 Place(s)");
        assertThat(delete("/api/v1/zones/" + empty, admin).status()).isEqualTo(204);
        assertThat(get("/api/v1/zones?query=" + empty, admin).body()).doesNotContain(empty.toString());
    }

    @Test
    void managerTeacherAndSystemAreRefusedOnEveryZoneEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneId = zone(admin);
        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/zones", token).status()).isEqualTo(403);
            assertThat(post("/api/v1/zones", token, Map.of("name", "X")).status()).isEqualTo(403);
            assertThat(put("/api/v1/zones/" + zoneId, token, Map.of("name", "X", "version", 0))
                            .status())
                    .isEqualTo(403);
            assertThat(delete("/api/v1/zones/" + zoneId, token).status()).isEqualTo(403);
        }
        assertThat(get("/api/v1/zones", null).status()).isEqualTo(401);
    }

    @Test
    void paginationIsCappedAndAnEmptyResultIsNotAnError() {
        String admin = signInAs(Role.ADMIN).token();
        for (int i = 0; i < 3; i++) {
            zone(admin);
        }

        Resp capped = get("/api/v1/zones?size=500", admin);
        assertThat(capped.status()).isEqualTo(200);
        assertThat(((Number) capped.map().get("size")).intValue()).isEqualTo(100);

        Resp secondPage = get("/api/v1/zones?size=1&page=1", admin);
        assertThat(content(secondPage)).hasSize(1);

        Resp none = get("/api/v1/zones?query=no-such-zone-xyz", admin);
        assertThat(none.status()).isEqualTo(200);
        assertThat(content(none)).isEmpty();
        assertThat(total(none)).isZero();
    }
}
