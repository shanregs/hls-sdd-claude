package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 3 (FR-003/FR-007): role changes, empty set, last-admin safeguard, audit. */
class UserRoleChangeIntegrationTest extends UserManagementTestBase {

    private Set<String> rolesAtNextSignIn(String phone) {
        return Set.copyOf(client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .user()
                .roles());
    }

    @Test
    void addingAndRemovingRolesIsReflectedOnTheNextSignInAndAudited() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Role Changer", Set.of(Role.MANAGER));
        String userId = (String) user.get("id");
        String phone = (String) user.get("phone");

        Resp added = put(
                "/api/v1/identity/users/" + userId + "/roles",
                adminToken,
                Map.of("roles", Set.of("MANAGER", "DIRECTOR")));
        assertThat(added.status()).isEqualTo(200);
        assertThat(rolesAtNextSignIn(phone)).containsExactlyInAnyOrder("MANAGER", "DIRECTOR");

        Resp removed = put("/api/v1/identity/users/" + userId + "/roles", adminToken, Map.of("roles", Set.of("DIRECTOR")));
        assertThat(removed.status()).isEqualTo(200);
        assertThat(rolesAtNextSignIn(phone)).containsExactly("DIRECTOR");

        assertAuditEntry(adminToken, UUID.fromString(userId), "ROLE_ASSIGNED");
        assertAuditEntry(adminToken, UUID.fromString(userId), "ROLE_REMOVED");
    }

    @Test
    void anEmptyRoleSetIsRefusedAndNothingChanges() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Keeps Role", Set.of(Role.TEACHER));
        String userId = (String) user.get("id");

        Resp resp = put("/api/v1/identity/users/" + userId + "/roles", adminToken, Map.of("roles", Set.of()));

        assertThat(resp.status()).isEqualTo(400);
        assertThat(resp.body()).contains("A user must hold at least one role.");
        assertThat(userAdminService.rolesOf(UUID.fromString(userId))).containsExactly(Role.TEACHER);
    }

    @Test
    void removingAdminFromTheSoleActiveAdminIsRefusedUntilASecondAdminExists() {
        deactivateEveryExistingAdmin();
        Signed sole = signInAs(Role.ADMIN, Role.MANAGER);
        String path = "/api/v1/identity/users/" + sole.userId() + "/roles";

        Resp refused = put(path, sole.token(), Map.of("roles", Set.of("MANAGER")));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("no active user able to administer the system as Admin");
        assertThat(userAdminService.rolesOf(sole.userId())).containsExactlyInAnyOrder(Role.ADMIN, Role.MANAGER);

        // removing Admin together with another role is refused as one unit, with no partial change
        Resp refusedCombined = put(path, sole.token(), Map.of("roles", Set.of("TEACHER")));
        assertThat(refusedCombined.status()).isEqualTo(409);
        assertThat(userAdminService.rolesOf(sole.userId())).containsExactlyInAnyOrder(Role.ADMIN, Role.MANAGER);

        signInAs(Role.ADMIN);
        Resp allowed = put(path, sole.token(), Map.of("roles", Set.of("MANAGER")));
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(userAdminService.rolesOf(sole.userId())).containsExactly(Role.MANAGER);
    }

    @Test
    void anUnknownUserIs404() {
        String adminToken = signInAs(Role.ADMIN).token();
        Resp resp =
                put("/api/v1/identity/users/" + UUID.randomUUID() + "/roles", adminToken, Map.of("roles", Set.of("TEACHER")));
        assertThat(resp.status()).isEqualTo(404);
    }

    @Test
    void directorManagerAndTeacherGet403OnTheRolesEndpoint() {
        UUID target = signInAs(Role.TEACHER).userId();
        for (Role role : new Role[] {Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            Resp resp = put(
                    "/api/v1/identity/users/" + target + "/roles", signInAs(role).token(), Map.of("roles", Set.of("ADMIN")));
            assertThat(resp.status()).isEqualTo(403);
        }
        assertThat(userAdminService.rolesOf(target)).containsExactly(Role.TEACHER);
    }
}
