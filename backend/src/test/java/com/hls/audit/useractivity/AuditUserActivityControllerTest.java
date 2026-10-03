package com.hls.audit.useractivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

/** User Story 3 (FR-007/FR-010): Admin/System can list User Activity; other roles are refused;
 * export without a date range is refused (contracts/audit-api.md). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuditUserActivityControllerTest {

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
        String adminToken = signInAs("9876590001", Role.ADMIN);
        String systemToken = signInAs("9876590002", Role.SYSTEM);

        client.get()
                .uri("/api/v1/audit/user-activity")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectStatus()
                .isOk();
        client.get()
                .uri("/api/v1/audit/user-activity")
                .header("Authorization", "Bearer " + systemToken)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void directorManagerAndTeacherAreRefused() {
        String directorToken = signInAs("9876590003", Role.DIRECTOR);
        String managerToken = signInAs("9876590004", Role.MANAGER);
        String teacherToken = signInAs("9876590005", Role.TEACHER);

        for (String token : new String[] {directorToken, managerToken, teacherToken}) {
            client.get()
                    .uri("/api/v1/audit/user-activity")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus()
                    .isForbidden();
        }
    }

    /**
     * Regression test: ending a session through the real {@code DELETE /me/sessions/{id}} path
     * (not a direct, test-only call to {@code SessionController} or a manually-committed event
     * publish) must produce a {@code SESSION_ENDED} row. {@code SessionController.endSession} was
     * missing {@code @Transactional}, so {@code SessionEnded}'s {@code @ApplicationModuleListener}
     * (which only runs after-commit) never fired in production — the event publication registry
     * recorded the event but it stayed permanently incomplete, and no {@code user_activity_entry}
     * row was ever created, even though every sibling lifecycle event (lockout, password reset,
     * deactivation) worked, because their services are all {@code @Transactional}.
     */
    @Test
    void endingASessionThroughTheRealEndpointRecordsSessionEnded() {
        String phone = "9876590007";
        String adminToken = signInAs("9876590008", Role.ADMIN);
        String userToken = signInAs(phone, Role.MANAGER);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(List.class)
                .returnResult()
                .getResponseBody();
        String sessionId = (String) sessions.get(0).get("id");

        client.delete()
                .uri("/api/v1/me/sessions/" + sessionId)
                .header("Authorization", "Bearer " + userToken)
                .exchange()
                .expectStatus()
                .isNoContent();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> client.get()
                .uri("/api/v1/audit/user-activity?action=SESSION_ENDED")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"action\":\"SESSION_ENDED\"")));
    }

    @Test
    void exportWithoutDateRangeReturns400() {
        String adminToken = signInAs("9876590006", Role.ADMIN);

        var result = client.get()
                .uri("/api/v1/audit/user-activity/export")
                .header("Authorization", "Bearer " + adminToken)
                .exchange()
                .returnResult(String.class);
        assertThat(result.getStatus().value()).isEqualTo(400);
    }
}
