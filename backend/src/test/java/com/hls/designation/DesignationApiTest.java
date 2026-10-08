package com.hls.designation;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.DesignationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005a US1: the list of designations, its rules, per-role access and audit (T009 to T011). */
class DesignationApiTest extends DesignationTestBase {

    @Test
    void adminAddsRenamesRetiresAndReactivatesAndEachChangeIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        String name = uniqueName("Primary Teacher");

        Resp created = post("/api/v1/designations", admin, Map.of("name", "  " + name + "  ", "kind", "TEACHER"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.map())
                .containsEntry("name", name)
                .containsEntry("retired", false)
                .containsEntry("holders", 0);
        UUID id = created.id();
        assertChangeRecorded(admin, "DESIGNATION", id, "created");

        long version = ((Number) created.map().get("version")).longValue();
        String renamed = uniqueName("Senior Teacher");
        Resp rename = put("/api/v1/designations/" + id, admin, Map.of("name", renamed, "version", version));
        assertThat(rename.status()).as(rename.body()).isEqualTo(200);
        assertThat(rename.map()).containsEntry("name", renamed);
        assertChangeRecorded(admin, "DESIGNATION", id, "name");

        long v2 = ((Number) rename.map().get("version")).longValue();
        Resp retire = put("/api/v1/designations/" + id, admin, Map.of("retired", true, "version", v2));
        assertThat(retire.map()).containsEntry("retired", true);
        assertChangeRecorded(admin, "DESIGNATION", id, "retired");
        assertThat(get("/api/v1/designations/options?kind=TEACHER", admin).body()).doesNotContain(id.toString());

        long v3 = ((Number) retire.map().get("version")).longValue();
        Resp back = put("/api/v1/designations/" + id, admin, Map.of("retired", false, "version", v3));
        assertThat(back.map()).containsEntry("retired", false);
        assertThat(get("/api/v1/designations/options?kind=TEACHER", admin).body()).contains(id.toString());
        assertThat(put("/api/v1/designations/" + id, admin, Map.of("name", "X", "version", version)).status())
                .isEqualTo(409);
    }

    @Test
    void namesAreCheckedPerKindIgnoringCapitalsAndExtraSpaces() {
        String admin = signInAs(Role.ADMIN).token();
        String name = uniqueName("Coordinator");
        assertThat(post("/api/v1/designations", admin, Map.of("name", name, "kind", "TEACHER")).status())
                .isEqualTo(201);

        Resp duplicate = post(
                "/api/v1/designations",
                admin,
                Map.of("name", " " + name.toUpperCase().replace(" ", "   ") + " ", "kind", "TEACHER"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("already a designation");
        assertThat(post("/api/v1/designations", admin, Map.of("name", name, "kind", "MANAGER")).status())
                .isEqualTo(201);

        assertThat(post("/api/v1/designations", admin, Map.of("name", "   ", "kind", "TEACHER")).status())
                .isEqualTo(400);
        assertThat(post("/api/v1/designations", admin, Map.of("name", "x".repeat(81), "kind", "TEACHER")).status())
                .isEqualTo(400);
        assertThat(post("/api/v1/designations", admin, Map.of("name", uniqueName("NoKind"))).status())
                .isEqualTo(400);
        String eighty = ("y".repeat(40) + uniqueName("").replaceAll("[^A-Za-z0-9]", "") + "z".repeat(40)).substring(0, 80);
        assertThat(post("/api/v1/designations", admin, Map.of("name", eighty, "kind", "TEACHER")).status())
                .isEqualTo(201);
    }

    @Test
    void theKindIsFixedOnceHeldAndADesignationIsNeverDeleted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID free = designation(admin, "TEACHER");
        UUID held = designation(admin, "TEACHER");
        UUID teacher = teacher(admin);
        assertThat(teacherEmployment(admin, teacher, held, null).status()).isEqualTo(200);

        assertThat(put("/api/v1/designations/" + free, admin, Map.of("kind", "MANAGER", "version", version(admin, free)))
                        .status())
                .isEqualTo(200);
        Resp refused =
                put("/api/v1/designations/" + held, admin, Map.of("kind", "MANAGER", "version", version(admin, held)));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("kind cannot change");

        assertThat(delete("/api/v1/designations/" + free, admin).status()).isEqualTo(409);
        assertThat(delete("/api/v1/designations/" + held, admin).status()).isEqualTo(409);
        Map<String, Object> row = rows(get("/api/v1/designations?kind=TEACHER", admin)).stream()
                .filter(r -> held.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(row).containsEntry("holders", 1);
    }

    @Test
    void everyEndpointIsRefusedOnTheServerForZoneManagerTeacherAndSystem() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID id = designation(admin, "TEACHER");
        assertThat(get("/api/v1/designations", director).status()).isEqualTo(200);
        assertThat(post("/api/v1/designations", director, Map.of("name", uniqueName("Dir"), "kind", "MANAGER")).status())
                .isEqualTo(201);
        assertThat(get("/api/v1/designations/summary", director).status()).isEqualTo(200);

        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/designations", token).status()).as("list " + role).isEqualTo(403);
            assertThat(get("/api/v1/designations/summary", token).status()).as("summary " + role).isEqualTo(403);
            assertThat(get("/api/v1/designations/options?kind=TEACHER", token).status())
                    .as("options " + role)
                    .isEqualTo(403);
            assertThat(post("/api/v1/designations", token, Map.of("name", "Nope", "kind", "TEACHER")).status())
                    .as("create " + role)
                    .isEqualTo(403);
            assertThat(put("/api/v1/designations/" + id, token, Map.of("name", "Nope", "version", 0)).status())
                    .as("update " + role)
                    .isEqualTo(403);
            assertThat(delete("/api/v1/designations/" + id, token).status()).as("delete " + role).isEqualTo(403);
        }
        assertThat(get("/api/v1/designations", null).status()).isEqualTo(401);
    }

    @Test
    void theMenuItemIsOnlyForAdminAndDirector() {
        assertThat(get("/api/v1/me/access-model", signInAs(Role.ADMIN).token()).body()).contains("Designations");
        assertThat(get("/api/v1/me/access-model", signInAs(Role.DIRECTOR).token()).body()).contains("Designations");
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            assertThat(get("/api/v1/me/access-model", signInAs(role).token()).body())
                    .as(role.name())
                    .doesNotContain("Designations");
        }
    }

    private long version(String token, UUID id) {
        return rows(get("/api/v1/designations", token)).stream()
                .filter(r -> id.toString().equals(r.get("id")))
                .map(r -> ((Number) r.get("version")).longValue())
                .findFirst()
                .orElseThrow();
    }
}
