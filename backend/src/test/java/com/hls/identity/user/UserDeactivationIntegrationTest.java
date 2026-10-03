package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** User Story 4 (FR-004/FR-005/FR-007) and T010a: deactivate/reactivate and token invalidation. */
class UserDeactivationIntegrationTest extends UserManagementTestBase {

    @Test
    void deactivatingEndsSessionsBlocksSignInAndKillsTheLiveAccessTokenImmediately() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "To Deactivate", Set.of(Role.MANAGER));
        UUID userId = UUID.fromString((String) user.get("id"));
        String phone = (String) user.get("phone");
        Signed first = signIn(userId, phone, PASSWORD);
        Signed second = signIn(userId, phone, PASSWORD);
        assertThat(get("/api/v1/me/access-model", first.token()).status()).isEqualTo(200);

        Resp deactivated = post("/api/v1/identity/users/" + userId + "/deactivate", adminToken, null);

        assertThat(deactivated.status()).isEqualTo(204);
        // T010a / FR-004: tokens issued before deactivation are refused on the very next request
        assertThat(get("/api/v1/me/access-model", first.token()).status()).isEqualTo(401);
        assertThat(get("/api/v1/me/access-model", second.token()).status()).isEqualTo(401);
        assertThat(loginStatus(phone, PASSWORD)).isEqualTo(401);
        assertAuditEntry(adminToken, userId, "ACCOUNT_DEACTIVATED");
    }

    @Test
    void reactivatingRestoresSignInAndExactlyThePriorRoles() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Comes Back", Set.of(Role.MANAGER, Role.DIRECTOR));
        UUID userId = UUID.fromString((String) user.get("id"));
        String phone = (String) user.get("phone");
        post("/api/v1/identity/users/" + userId + "/deactivate", adminToken, null);

        Resp reactivated = post("/api/v1/identity/users/" + userId + "/reactivate", adminToken, null);

        assertThat(reactivated.status()).isEqualTo(204);
        Signed back = signIn(userId, phone, PASSWORD);
        assertThat(get("/api/v1/me/access-model", back.token()).status()).isEqualTo(200);
        assertThat(userAdminService.rolesOf(userId)).containsExactlyInAnyOrder(Role.MANAGER, Role.DIRECTOR);
        assertAuditEntry(adminToken, userId, "ACCOUNT_REACTIVATED");
    }

    @Test
    void repeatedDeactivateAndReactivateAreNoOpsThatPublishNothingNew() {
        String adminToken = signInAs(Role.ADMIN).token();
        Map<String, Object> user = createUserViaApi(adminToken, "Idempotent", Set.of(Role.TEACHER));
        String userId = (String) user.get("id");

        assertThat(post("/api/v1/identity/users/" + userId + "/reactivate", adminToken, null)
                        .status())
                .isEqualTo(204);
        assertThat(post("/api/v1/identity/users/" + userId + "/deactivate", adminToken, null)
                        .status())
                .isEqualTo(204);
        assertThat(post("/api/v1/identity/users/" + userId + "/deactivate", adminToken, null)
                        .status())
                .isEqualTo(204);

        assertAuditEntry(adminToken, UUID.fromString(userId), "ACCOUNT_DEACTIVATED");
        Resp activity = get("/api/v1/audit/user-activity?affectedUserId=" + userId + "&action=ACCOUNT_DEACTIVATED", adminToken);
        assertThat(activity.body()).contains("\"totalElements\":1");
        Resp reactivations = get("/api/v1/audit/user-activity?affectedUserId=" + userId + "&action=ACCOUNT_REACTIVATED", adminToken);
        assertThat(reactivations.body()).contains("\"totalElements\":0");
    }

    @Test
    void deactivatingTheSoleActiveAdminIsRefusedButReactivationIsNeverRefused() {
        deactivateEveryExistingAdmin();
        Signed sole = signInAs(Role.ADMIN);
        String path = "/api/v1/identity/users/" + sole.userId();

        Resp refused = post(path + "/deactivate", sole.token(), null);
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("no active user able to administer the system as Admin");
        assertThat(appUserRepository.findById(sole.userId()).orElseThrow().isActive()).isTrue();

        Signed second = signInAs(Role.ADMIN);
        assertThat(post(path + "/deactivate", second.token(), null).status()).isEqualTo(204);
        assertThat(post(path + "/reactivate", second.token(), null).status()).isEqualTo(204);
    }

    @Test
    void anAdminMayDeactivateTheirOwnAccountWhenNotTheLastAdmin() {
        deactivateEveryExistingAdmin();
        Signed one = signInAs(Role.ADMIN);
        signInAs(Role.ADMIN);

        Resp resp = post("/api/v1/identity/users/" + one.userId() + "/deactivate", one.token(), null);

        assertThat(resp.status()).isEqualTo(204);
        assertThat(get("/api/v1/me/access-model", one.token()).status()).isEqualTo(401);
    }

    @Test
    void anEndedSessionsAccessTokenStopsWorkingImmediately() {
        Signed user = signInAs(Role.MANAGER);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + user.token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(List.class)
                .returnResult()
                .getResponseBody();
        String sessionId = (String) sessions.get(0).get("id");

        assertThat(send(HttpMethod.DELETE, "/api/v1/me/sessions/" + sessionId, user.token(), null)
                        .status())
                .isEqualTo(204);

        assertThat(get("/api/v1/me/access-model", user.token()).status()).isEqualTo(401);
    }

    @Test
    void directorManagerAndTeacherGet403OnDeactivateAndReactivate() {
        UUID target = signInAs(Role.TEACHER).userId();
        for (Role role : new Role[] {Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            String token = signInAs(role).token();
            assertThat(post("/api/v1/identity/users/" + target + "/deactivate", token, null)
                            .status())
                    .isEqualTo(403);
            assertThat(post("/api/v1/identity/users/" + target + "/reactivate", token, null)
                            .status())
                    .isEqualTo(403);
        }
        assertThat(appUserRepository.findById(target).orElseThrow().isActive()).isTrue();
    }
}
