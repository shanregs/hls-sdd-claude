package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** User Stories 1 and 2 (FR-001/FR-002/FR-009): create, list/search/filter, per-role authorization. */
class UserManagementControllerTest extends UserManagementTestBase {

    private UserManagementController.CreateUserRequest request(
            String name, String phone, Set<Role> roles, String username, String email) {
        return new UserManagementController.CreateUserRequest(name, phone, roles, username, email, PASSWORD);
    }

    @Test
    void adminAndSystemCanCreateAUserWhoCanThenSignInWithExactlyThoseRoles() {
        for (Role creatorRole : new Role[] {Role.ADMIN, Role.SYSTEM}) {
            String token = signInAs(creatorRole).token();
            String phone = nextPhone();

            Resp created = post(
                    "/api/v1/identity/users",
                    token,
                    request("New Manager", phone, Set.of(Role.MANAGER, Role.DIRECTOR), null, null));

            assertThat(created.status()).isEqualTo(201);
            assertThat(created.body()).contains("\"active\":true").contains("MANAGER").contains("DIRECTOR");
            var signedIn = client.post()
                    .uri("/api/v1/auth/login")
                    .body(new com.hls.identity.auth.AuthDtos.LoginRequest(phone, PASSWORD))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(com.hls.identity.auth.AuthDtos.AuthResponse.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(signedIn.user().roles()).containsExactlyInAnyOrder("MANAGER", "DIRECTOR");
        }
    }

    @Test
    void duplicatePhoneUsernameAndEmailAreRefusedNamingTheField() {
        String token = signInAs(Role.ADMIN).token();
        String phone = nextPhone();
        assertThat(post(
                                "/api/v1/identity/users",
                                token,
                                request("First", phone, Set.of(Role.TEACHER), "dup.user", "dup@example.com"))
                        .status())
                .isEqualTo(201);

        Resp dupPhone = post("/api/v1/identity/users", token, request("Second", phone, Set.of(Role.TEACHER), null, null));
        assertThat(dupPhone.status()).isEqualTo(409);
        assertThat(dupPhone.body()).contains("phone");

        Resp dupUsername = post(
                "/api/v1/identity/users",
                token,
                request("Third", nextPhone(), Set.of(Role.TEACHER), "DUP.user", null));
        assertThat(dupUsername.status()).isEqualTo(409);
        assertThat(dupUsername.body()).contains("username");

        Resp dupEmail = post(
                "/api/v1/identity/users",
                token,
                request("Fourth", nextPhone(), Set.of(Role.TEACHER), null, "dup@example.com"));
        assertThat(dupEmail.status()).isEqualTo(409);
        assertThat(dupEmail.body()).contains("email");
    }

    @Test
    void emptyRolesAndUnknownRoleValuesAreRefusedWith400() {
        String token = signInAs(Role.ADMIN).token();

        Resp noRoles = post("/api/v1/identity/users", token, request("No Roles", nextPhone(), Set.of(), null, null));
        assertThat(noRoles.status()).isEqualTo(400);

        Resp unknownRole = post(
                "/api/v1/identity/users",
                token,
                Map.of("displayName", "Bad Role", "phone", nextPhone(), "roles", java.util.List.of("SUPERUSER")));
        assertThat(unknownRole.status()).isEqualTo(400);
    }

    @Test
    void directorManagerAndTeacherAreRefusedOnEveryEndpoint() {
        UUID someUser = signInAs(Role.TEACHER).userId();
        for (Role role : new Role[] {Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/identity/users", token).status()).isEqualTo(403);
            assertThat(post(
                                    "/api/v1/identity/users",
                                    token,
                                    request("Nope", nextPhone(), Set.of(Role.TEACHER), null, null))
                            .status())
                    .isEqualTo(403);
            assertThat(put("/api/v1/identity/users/" + someUser + "/roles", token, Map.of("roles", Set.of("ADMIN")))
                            .status())
                    .isEqualTo(403);
            assertThat(post("/api/v1/identity/users/" + someUser + "/deactivate", token, null)
                            .status())
                    .isEqualTo(403);
            assertThat(post("/api/v1/identity/users/" + someUser + "/reactivate", token, null)
                            .status())
                    .isEqualTo(403);
            assertThat(post(
                                    "/api/v1/identity/users/" + someUser + "/reset-password",
                                    token,
                                    Map.of("newPassword", "a-brand-new-password"))
                            .status())
                    .isEqualTo(403);
        }
    }

    @Test
    void unauthenticatedCallersAreRefused() {
        assertThat(get("/api/v1/identity/users", null).status()).isEqualTo(401);
    }

    @Test
    void createRecordsUserCreatedInUserActivityThroughTheRealEndpoint() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> created = createUserViaApi(adminToken, "Audited New", Set.of(Role.MANAGER));

        assertAuditEntry(adminToken, UUID.fromString((String) created.get("id")), "USER_CREATED");
    }

    @Test
    void listSearchAndFilterWork() {
        String token = signInAs(Role.ADMIN).token();
        String marker = "Zzq" + System.nanoTime() % 100000;
        createUserViaApi(token, marker + " Alpha", Set.of(Role.MANAGER));
        Map<String, Object> beta = createUserViaApi(token, marker + " Beta", Set.of(Role.TEACHER));
        String betaId = (String) beta.get("id");
        assertThat(post("/api/v1/identity/users/" + betaId + "/deactivate", token, null)
                        .status())
                .isEqualTo(204);

        Resp byName = get("/api/v1/identity/users?query=" + marker.toLowerCase(), token);
        assertThat(byName.status()).isEqualTo(200);
        assertThat(byName.body()).contains(marker + " Alpha").contains(marker + " Beta").contains("\"totalElements\":2");

        Resp byPhone = get("/api/v1/identity/users?query=" + beta.get("phone"), token);
        assertThat(byPhone.body()).contains("\"totalElements\":1").contains(marker + " Beta");

        Resp byRole = get("/api/v1/identity/users?query=" + marker + "&role=MANAGER", token);
        assertThat(byRole.body()).contains("\"totalElements\":1").contains("Alpha");

        Resp inactive = get("/api/v1/identity/users?query=" + marker + "&active=false", token);
        assertThat(inactive.body()).contains("\"totalElements\":1").contains("Beta");

        Resp activeManager = get("/api/v1/identity/users?query=" + marker + "&active=true&role=TEACHER", token);
        assertThat(activeManager.body()).contains("\"totalElements\":0");

        Resp noMatch = get("/api/v1/identity/users?query=no-such-person-xyz", token);
        assertThat(noMatch.status()).isEqualTo(200);
        assertThat(noMatch.body()).contains("\"content\":[]").contains("\"totalElements\":0");

        assertThat(send(HttpMethod.GET, "/api/v1/identity/users?role=NOT_A_ROLE", token, null)
                        .status())
                .isEqualTo(400);
    }
}
