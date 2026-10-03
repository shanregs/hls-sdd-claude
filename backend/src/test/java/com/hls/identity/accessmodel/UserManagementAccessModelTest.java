package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
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

/** Spec 004 (Constitution Principle IX): the seed and the navigation placement of User Management. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserManagementAccessModelTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private PermissionMatrixService permissionMatrixService;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private AccessModelDtos.AccessModelResponse accessModelFor(String phone, Role role) {
        userAdminService.createUser("UM Test " + phone, phone, Set.of(role), null, "correct-horse-4");
        String token = client.post()
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
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AccessModelDtos.AccessModelResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static AccessModelDtos.NavItemView userManagementItem(
            AccessModelDtos.AccessModelResponse response, String section) {
        return response.navigation().stream()
                .filter(s -> s.section().equals(section))
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals("User Management"))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminSeesUserManagementUnderSystemWithViewCreateEdit() {
        var item = userManagementItem(accessModelFor("9876560001", Role.ADMIN), "SYSTEM");

        assertThat(item).isNotNull();
        assertThat(item.route()).isEqualTo("/identity/users");
        assertThat(item.actions()).contains("VIEW", "CREATE", "EDIT");
    }

    @Test
    void systemSeesUserManagementUnderSystemConfiguration() {
        var response = accessModelFor("9876560002", Role.SYSTEM);

        var item = userManagementItem(response, "SYSTEM CONFIGURATION");
        assertThat(item).isNotNull();
        assertThat(item.actions()).contains("VIEW", "CREATE", "EDIT");
        assertThat(userManagementItem(response, "SYSTEM")).isNull();
    }

    @Test
    void directorManagerAndTeacherSeeNoUserManagementAnywhere() {
        String[] phones = {"9876560003", "9876560004", "9876560005"};
        Role[] roles = {Role.DIRECTOR, Role.MANAGER, Role.TEACHER};
        for (int i = 0; i < roles.length; i++) {
            var response = accessModelFor(phones[i], roles[i]);
            List<String> labels = response.navigation().stream()
                    .flatMap(s -> s.items().stream())
                    .map(AccessModelDtos.NavItemView::label)
                    .toList();
            assertThat(labels).doesNotContain("User Management");
        }
    }

    @Test
    void seededMatrixGrantsUserManagementToAdminAndSystemOnly() {
        for (Role role : Role.values()) {
            boolean expected = role == Role.ADMIN || role == Role.SYSTEM;
            for (PermissionAction action :
                    List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                assertThat(permissionMatrixService.isGranted(role, PermissionModule.USER_MANAGEMENT, action))
                        .as("%s %s", role, action)
                        .isEqualTo(expected);
            }
        }
    }
}
