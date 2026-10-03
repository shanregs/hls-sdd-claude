package com.hls.identity.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
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
 * User Story 3 (spec 002-access-model-app-shell): a valid edit applies (FR-003) end-to-end through
 * the real HTTP endpoints; an edit that would zero out every Admin/Director/System matrix-manager
 * grant is rejected with no partial change (FR-004); a Manager/Teacher caller is refused with no
 * matrix data returned (FR-002). The change-record event's exact fields (before/after) are
 * verified separately in {@link PermissionMatrixServiceUnitTest}, since Spring's
 * {@code ApplicationEvents} test support cannot observe an event published from the Tomcat
 * worker thread a real HTTP round-trip runs on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PermissionMatrixServiceTest {

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

    private String signInAs(String phone, Role role) {
        userAdminService.createUser("Matrix Tester " + phone, phone, Set.of(role), null, "correct-horse-5");
        return client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-5"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    @Test
    void validEditAppliesAndPublishesAChangeRecord() {
        String adminToken = signInAs("9876560001", Role.ADMIN);

        var updated = client.put()
                .uri("/api/v1/identity/permission-matrix/MANAGER/DASHBOARD/VIEW")
                .header("Authorization", "Bearer " + adminToken)
                .body(new PermissionMatrixDtos.GrantRequest(false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PermissionMatrixDtos.EntryView.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.granted()).isFalse();

        var listed = client.get()
                .uri("/api/v1/identity/permission-matrix")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PermissionMatrixDtos.MatrixListResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(listed.entries())
                .anyMatch(e -> e.role() == Role.MANAGER
                        && e.module() == PermissionModule.DASHBOARD
                        && e.action() == PermissionAction.VIEW
                        && !e.granted());
    }

    @Test
    void editThatWouldRemoveTheLastMatrixManagerIsRejectedWithNoPartialChange() {
        String adminToken = signInAs("9876560002", Role.ADMIN);

        // Strip DIRECTOR's and SYSTEM's matrix-manager grant first — both succeed since ADMIN
        // still holds it throughout.
        client.put()
                .uri("/api/v1/identity/permission-matrix/DIRECTOR/IDENTITY_PERMISSIONS/EDIT")
                .header("Authorization", "Bearer " + adminToken)
                .body(new PermissionMatrixDtos.GrantRequest(false))
                .exchange()
                .expectStatus()
                .isOk();
        client.put()
                .uri("/api/v1/identity/permission-matrix/SYSTEM/IDENTITY_PERMISSIONS/EDIT")
                .header("Authorization", "Bearer " + adminToken)
                .body(new PermissionMatrixDtos.GrantRequest(false))
                .exchange()
                .expectStatus()
                .isOk();

        // Now ADMIN is the only remaining matrix-manager: revoking it must be rejected.
        var rejection = client.put()
                .uri("/api/v1/identity/permission-matrix/ADMIN/IDENTITY_PERMISSIONS/EDIT")
                .header("Authorization", "Bearer " + adminToken)
                .body(new PermissionMatrixDtos.GrantRequest(false))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody(PermissionMatrixDtos.RejectionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(rejection.reason()).isNotBlank();

        // No partial change: ADMIN still holds it.
        var listed = client.get()
                .uri("/api/v1/identity/permission-matrix")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PermissionMatrixDtos.MatrixListResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(listed.entries())
                .anyMatch(e -> e.role() == Role.ADMIN
                        && e.module() == PermissionModule.IDENTITY_PERMISSIONS
                        && e.action() == PermissionAction.EDIT
                        && e.granted());
    }

    @Test
    void managerCallerIsRefusedWithNoMatrixDataReturned() {
        String managerToken = signInAs("9876560003", Role.MANAGER);

        client.get()
                .uri("/api/v1/identity/permission-matrix")
                .header("Authorization", "Bearer " + managerToken)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .isEmpty();

        client.put()
                .uri("/api/v1/identity/permission-matrix/MANAGER/DASHBOARD/VIEW")
                .header("Authorization", "Bearer " + managerToken)
                .body(new PermissionMatrixDtos.GrantRequest(false))
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
