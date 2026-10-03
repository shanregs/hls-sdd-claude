package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared harness for spec 004's integration tests (see {@link IntegrationTestBase}). */
abstract class UserManagementTestBase extends IntegrationTestBase {

    Map<String, Object> createUserViaApi(String adminToken, String name, Set<Role> roles) {
        String phone = nextPhone();
        var created = client.post()
                .uri("/api/v1/identity/users")
                .header("Authorization", "Bearer " + adminToken)
                .body(new UserManagementController.CreateUserRequest(name, phone, roles, null, null, PASSWORD))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = created;
        return result;
    }

    /** Makes the "exactly one / exactly two active Admins" tests deterministic. */
    void deactivateEveryExistingAdmin() {
        for (RoleAssignment assignment : roleAssignmentRepository.findAll()) {
            if (assignment.getRole() != Role.ADMIN) {
                continue;
            }
            appUserRepository.findById(assignment.getUserId()).ifPresent(u -> {
                u.deactivate();
                appUserRepository.save(u);
            });
        }
    }

    /** FR-008/SC-005: the entry reaches User Activity through the real path within 5 seconds. */
    void assertAuditEntry(String adminToken, UUID affectedUserId, String action) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp resp = get(
                    "/api/v1/audit/user-activity?affectedUserId=" + affectedUserId + "&action=" + action, adminToken);
            assertThat(resp.status()).isEqualTo(200);
            assertThat(resp.body()).contains("\"action\":\"" + action + "\"");
        });
    }

    static List<Role> rolesOf(Map<String, Object> user) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) user.get("roles");
        return names.stream().map(Role::valueOf).toList();
    }
}
