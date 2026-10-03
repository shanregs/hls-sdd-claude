package com.hls.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.session.Session;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.session.SessionStatus;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
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
 * User Story 6 (spec 001-identity-access): bootstrap Admin/System users exist on first
 * deployment and are not duplicated on restart (FR-020, Acceptance Scenarios 1-2); a deactivated
 * user's session ends and no further sign-in succeeds (Acceptance Scenario 4, FR-018). Acceptance
 * Scenario 3 (missing configuration) is covered separately in
 * {@link BootstrapUserInitializerUnitTest}, without a Spring context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "hls.bootstrap.admin.display-name=Bootstrap Admin",
            "hls.bootstrap.admin.phone=9876540001",
            "hls.bootstrap.admin.password=bootstrap-admin-pw",
            "hls.bootstrap.system.display-name=Bootstrap System",
            "hls.bootstrap.system.phone=9876540002",
            "hls.bootstrap.system.password=bootstrap-system-pw"
        })
@Testcontainers
class BootstrapAndDeactivationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private BootstrapUserInitializer bootstrapUserInitializer;

    @Autowired
    private SessionRepository sessionRepository;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void bootstrapAdminAndSystemExistAndCanSignIn() {
        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876540001", "bootstrap-admin-pw"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .value(body -> assertThat(body.user().roles()).containsExactly("ADMIN"));

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876540002", "bootstrap-system-pw"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .value(body -> assertThat(body.user().roles()).containsExactly("SYSTEM"));
    }

    @Test
    void restartingDoesNotDuplicateBootstrapUsersOrOverwritePasswords() {
        // The initializer already ran once during context startup; running it again simulates a
        // restart against an existing database (Acceptance Scenario 2).
        bootstrapUserInitializer.run(null);

        assertThat(appUserRepository.findByPhone("9876540001")).isPresent();

        // Still exactly one user for that phone, and the original password still works.
        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest("9876540001", "bootstrap-admin-pw"))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void deactivatingASignedInUserEndsTheirSessionAndBlocksFurtherSignIn() {
        String phone = "9876540099";
        userAdminService.createUser("Soon Deactivated", phone, Set.of(Role.TEACHER), null, "correct-horse-3");
        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-3"))
                .exchange()
                .expectStatus()
                .isOk();

        AppUser user = appUserRepository.findByPhone(phone).orElseThrow();
        Session theirSession = sessionRepository
                .findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)
                .get(0);

        userAdminService.deactivateUser(null, user.getId());

        Session reloaded = sessionRepository.findById(theirSession.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.REVOKED);

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-3"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
