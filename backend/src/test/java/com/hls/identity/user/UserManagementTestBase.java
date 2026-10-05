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

    /** The Admins {@link #deactivateEveryExistingAdmin()} switched off, so they can be switched back on. */
    private final List<UUID> adminsSwitchedOff = new java.util.ArrayList<>();

    /**
     * Makes the "exactly one / exactly two active Admins" tests deterministic. The test database is shared by every
     * test class, so {@link #restoreSwitchedOffAdmins()} puts these Admins back afterwards; without that, later
     * classes that sign in as the demo Admin get a 401.
     */
    void deactivateEveryExistingAdmin() {
        for (RoleAssignment assignment : roleAssignmentRepository.findAll()) {
            if (assignment.getRole() != Role.ADMIN) {
                continue;
            }
            appUserRepository.findById(assignment.getUserId()).ifPresent(u -> {
                if (u.isActive()) {
                    adminsSwitchedOff.add(u.getId());
                }
                u.deactivate();
                appUserRepository.save(u);
            });
        }
    }

    @org.junit.jupiter.api.AfterEach
    void restoreSwitchedOffAdmins() {
        for (UUID id : adminsSwitchedOff) {
            appUserRepository.findById(id).ifPresent(u -> {
                u.reactivate();
                appUserRepository.save(u);
            });
        }
        adminsSwitchedOff.clear();
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
