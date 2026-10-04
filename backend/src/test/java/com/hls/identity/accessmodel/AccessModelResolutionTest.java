package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 1 (spec 002-access-model-app-shell): for each of the five roles, and once for a
 * two-role combination, {@code GET /api/v1/me/access-model} returns exactly the seeded
 * Dashboard/Account items for that principal's union of grants, with no duplicates (Acceptance
 * Scenarios 1-2, 5).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AccessModelResolutionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private AccessModelDtos.AccessModelResponse accessModelFor(String phone, Set<Role> roles) {
        userAdminService.createUser("Test User " + phone, phone, roles, null, "correct-horse-4");
        String accessToken = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-4"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();

        return client.get()
                .uri("/api/v1/me/access-model")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AccessModelDtos.AccessModelResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<String> itemLabelsIn(AccessModelDtos.AccessModelResponse response, String section) {
        return response.navigation().stream()
                .filter(s -> s.section().equals(section))
                .findFirst()
                .map(s -> s.items().stream().map(AccessModelDtos.NavItemView::label).toList())
                .orElse(List.of());
    }

    @Test
    void teacherSeesOnlyDashboardMyProfileAndSettings() {
        var response = accessModelFor("9876550001", Set.of(Role.TEACHER));

        assertThat(response.roles()).containsExactly("TEACHER");
        assertThat(itemLabelsIn(response, "Dashboard")).containsExactly("Dashboard");
        assertThat(itemLabelsIn(response, "ACCOUNT")).containsExactly("My Profile", "Settings");
        assertThat(response.navigation().stream().map(AccessModelDtos.NavSection::section))
                .doesNotContain("SYSTEM", "SYSTEM CONFIGURATION", "SYSTEM DASHBOARD");
        assertThat(response.dataScope()).containsEntry("DASHBOARD", "OWN");
    }

    @Test
    void managerSeesAssignedScopeAndNoRolePermissionsItem() {
        var response = accessModelFor("9876550002", Set.of(Role.MANAGER));

        assertThat(itemLabelsIn(response, "Dashboard")).containsExactly("Dashboard");
        assertThat(itemLabelsIn(response, "ACCOUNT")).containsExactly("Profile", "Settings");
        assertThat(response.dataScope()).containsEntry("DASHBOARD", "ASSIGNED");
        boolean hasRolePermissions = response.navigation().stream()
                .flatMap(s -> s.items().stream())
                .anyMatch(i -> i.label().equals("Role & Permissions"));
        assertThat(hasRolePermissions).isFalse();
    }

    @Test
    void adminSeesRolePermissionsUnderSystemSectionAndOrgWideScope() {
        var response = accessModelFor("9876550003", Set.of(Role.ADMIN));

        assertThat(itemLabelsIn(response, "SYSTEM")).containsExactly("User Management", "Role & Permissions");
        assertThat(response.dataScope()).containsEntry("DASHBOARD", "ORG_WIDE");
    }

    @Test
    void directorSeesRolePermissionsUnderSystemSectionAndOrgWideScope() {
        var response = accessModelFor("9876550004", Set.of(Role.DIRECTOR));

        assertThat(itemLabelsIn(response, "SYSTEM")).containsExactly("Role & Permissions");
        assertThat(response.dataScope()).containsEntry("DASHBOARD", "ORG_WIDE");
    }

    @Test
    void systemRoleSeesItsOwnDashboardAndConfigurationSectionsOnly() {
        var response = accessModelFor("9876550005", Set.of(Role.SYSTEM));

        assertThat(itemLabelsIn(response, "SYSTEM DASHBOARD")).containsExactly("Dashboard");
        assertThat(itemLabelsIn(response, "SYSTEM CONFIGURATION"))
                .containsExactly("User Management", "Role & Permissions");
        assertThat(itemLabelsIn(response, "Dashboard")).isEmpty();
        assertThat(itemLabelsIn(response, "SYSTEM")).isEmpty();
        assertThat(response.dataScope()).containsEntry("DASHBOARD", "NONE");
    }

    @Test
    void adminAndDirectorComboCombinesGrantsWithNoDuplicateItems() {
        var response = accessModelFor("9876550006", Set.of(Role.ADMIN, Role.DIRECTOR));

        assertThat(response.roles()).containsExactly("ADMIN", "DIRECTOR");
        assertThat(itemLabelsIn(response, "Dashboard")).containsExactly("Dashboard");
        assertThat(itemLabelsIn(response, "SYSTEM")).containsExactly("User Management", "Role & Permissions");
        assertThat(itemLabelsIn(response, "ACCOUNT")).containsExactly("Profile", "Settings");
    }
}
