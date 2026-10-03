package com.hls.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.session.Session;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.session.SessionStatus;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.util.List;
import java.util.Optional;
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
 * User Story 1 (spec 001-identity-access): staff password sign-in recognizes all held roles, and
 * every failure path returns the identical generic message (FR-008, Acceptance Scenarios 1-5).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PasswordAuthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private SessionRepository sessionRepository;

    private RestTestClient client;

    private static final String GENERIC_MESSAGE = "Your phone number/username or password is incorrect.";

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void correctCredentialsSignInAndShowAllHeldRoles() {
        userAdminService.createUser("Priya Manager", "9876500001", Set.of(Role.MANAGER), null, "correct-horse-battery");

        var result = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876500001", "correct-horse-battery"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectCookie()
                .exists(RenewalCookies.COOKIE_NAME)
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult();

        assertThat(result.getResponseBody()).isNotNull();
        assertThat(result.getResponseBody().user().displayName()).isEqualTo("Priya Manager");
        assertThat(result.getResponseBody().user().roles()).containsExactly("MANAGER");
    }

    @Test
    void signsInWithUsernameInsteadOfPhone() {
        userAdminService.createUser(
                "Priya Manager",
                "9876500011",
                Set.of(Role.MANAGER),
                null,
                "correct-horse-battery",
                "priya.manager",
                null);

        var body = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("Priya.Manager", "correct-horse-battery"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.user().displayName()).isEqualTo("Priya Manager");
        assertThat(body.user().roles()).containsExactly("MANAGER");
    }

    @Test
    void multiRoleUserSeesAllRolesWithNoPicker() {
        userAdminService.createUser(
                "Asha Admin-Director", "9876500002", Set.of(Role.ADMIN, Role.DIRECTOR), null, "another-correct-pw");

        var body = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876500002", "another-correct-pw"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.user().roles()).containsExactlyInAnyOrder("ADMIN", "DIRECTOR");
    }

    @Test
    void wrongPasswordGetsGenericMessage() {
        userAdminService.createUser("Ravi Wrong", "9876500003", Set.of(Role.ADMIN), null, "the-real-password");

        var body = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876500003", "not-the-password"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(AuthDtos.ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.message()).isEqualTo(GENERIC_MESSAGE);
    }

    @Test
    void unregisteredPhoneGetsTheIdenticalGenericMessage() {
        var body = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876599999", "anything"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(AuthDtos.ErrorResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.message()).isEqualTo(GENERIC_MESSAGE);
    }

    @Test
    void logoutEndsTheSessionImmediately() {
        userAdminService.createUser("Logout Tester", "9876500004", Set.of(Role.SYSTEM), null, "logout-password-1");
        String accessToken = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876500004", "logout-password-1"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();

        client.post()
                .uri("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus()
                .isNoContent();

        List<Session> sessions = sessionRepository.findAll();
        Optional<Session> endedSession =
                sessions.stream().filter(s -> s.getStatus() == SessionStatus.ENDED).findFirst();
        assertThat(endedSession).isPresent();
    }
}
