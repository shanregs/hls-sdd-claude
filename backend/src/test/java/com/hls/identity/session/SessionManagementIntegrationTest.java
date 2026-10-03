package com.hls.identity.session;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 5 (spec 001-identity-access): a signed-in user sees only their own active sessions,
 * the caller's own is marked "this device," ending one signs it out, and a user can never end
 * another user's session (FR-015, Acceptance Scenarios 1-2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionManagementIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    private static final ParameterizedTypeReference<List<SessionController.SessionView>> SESSION_LIST =
            new ParameterizedTypeReference<>() {};

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String loginWithDevice(String phone, String device) {
        return client.post()
                .uri("/api/v1/auth/login")
                .header("User-Agent", device)
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-2"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    @Test
    void listsOnlyTheCallersOwnSessionsAndMarksTheCurrentDevice() {
        String phone = "9876530001";
        userAdminService.createUser("Session Lister", phone, Set.of(Role.TEACHER), null, "correct-horse-2");
        loginWithDevice(phone, "Device-One");
        String secondDeviceToken = loginWithDevice(phone, "Device-Two");

        List<SessionController.SessionView> sessions = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + secondDeviceToken)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(SESSION_LIST)
                .returnResult()
                .getResponseBody();

        assertThat(sessions).hasSize(2);
        assertThat(sessions.stream().filter(SessionController.SessionView::current)).hasSize(1);
        assertThat(sessions.stream()
                        .filter(SessionController.SessionView::current)
                        .findFirst()
                        .orElseThrow()
                        .deviceDescription())
                .isEqualTo("Device-Two");
    }

    @Test
    void endingAnotherOwnSessionSignsThatDeviceOut() {
        String phone = "9876530002";
        userAdminService.createUser("Session Ender", phone, Set.of(Role.TEACHER), null, "correct-horse-2");
        loginWithDevice(phone, "Device-One");
        String secondDeviceToken = loginWithDevice(phone, "Device-Two");

        List<SessionController.SessionView> sessions = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + secondDeviceToken)
                .exchange()
                .expectBody(SESSION_LIST)
                .returnResult()
                .getResponseBody();
        var otherSession =
                sessions.stream().filter(s -> !s.current()).findFirst().orElseThrow();

        client.delete()
                .uri("/api/v1/me/sessions/" + otherSession.id())
                .header("Authorization", "Bearer " + secondDeviceToken)
                .exchange()
                .expectStatus()
                .isNoContent();

        List<SessionController.SessionView> remaining = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + secondDeviceToken)
                .exchange()
                .expectBody(SESSION_LIST)
                .returnResult()
                .getResponseBody();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).current()).isTrue();
    }

    @Test
    void aUserCanNeverEndAnotherUsersSession() {
        userAdminService.createUser("User A", "9876530003", Set.of(Role.TEACHER), null, "correct-horse-2");
        userAdminService.createUser("User B", "9876530004", Set.of(Role.TEACHER), null, "correct-horse-2");
        String userAToken = loginWithDevice("9876530003", "Device-A");
        String userBToken = loginWithDevice("9876530004", "Device-B");

        List<SessionController.SessionView> userASessions = client.get()
                .uri("/api/v1/me/sessions")
                .header("Authorization", "Bearer " + userAToken)
                .exchange()
                .expectBody(SESSION_LIST)
                .returnResult()
                .getResponseBody();
        var userASession = userASessions.get(0);

        client.delete()
                .uri("/api/v1/me/sessions/" + userASession.id())
                .header("Authorization", "Bearer " + userBToken)
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
