package com.hls.audit.changehistory;

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

/** User Story 2 (FR-006/FR-010): Admin/System can list Change History; other roles are refused;
 * export without a date range is refused (contracts/audit-api.md). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuditChangeHistoryControllerTest {

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
        userAdminService.createUser("Audit Tester " + phone, phone, Set.of(role), null, "correct-horse-5");
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
    void adminAndSystemCanList() {
        String adminToken = signInAs("9876580001", Role.ADMIN);
        String systemToken = signInAs("9876580002", Role.SYSTEM);

        client.get()
                .uri("/api/v1/audit/change-history")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk();
        client.get()
                .uri("/api/v1/audit/change-history")
                .header("Authorization", "Bearer " + systemToken)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void directorManagerAndTeacherAreRefused() {
        String directorToken = signInAs("9876580003", Role.DIRECTOR);
        String managerToken = signInAs("9876580004", Role.MANAGER);
        String teacherToken = signInAs("9876580005", Role.TEACHER);

        for (String token : new String[] {directorToken, managerToken, teacherToken}) {
            client.get()
                    .uri("/api/v1/audit/change-history")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus()
                    .isForbidden();
        }
    }

    @Test
    void exportWithoutDateRangeReturns400() {
        String adminToken = signInAs("9876580006", Role.ADMIN);

        var result = client.get()
                .uri("/api/v1/audit/change-history/export")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .returnResult(String.class);
        assertThat(result.getStatus().value()).isEqualTo(400);
    }
}
