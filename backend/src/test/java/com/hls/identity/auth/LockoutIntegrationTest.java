package com.hls.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.support.MutableTestClock;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 4 (spec 001-identity-access): 5 consecutive wrong passwords lock the account for 30
 * minutes with the unlock time shown; a correct password during the lock is still refused; the
 * count resets after a success; sign-in succeeds once the lock window passes (Acceptance Scenarios
 * 1-4, FR-012). Uses a settable {@link Clock} instead of a real 30-minute wait.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LockoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableTestClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private Clock clock;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private void attempt(String phone, String password) {
        client.post().uri("/api/v1/auth/login").body(new AuthDtos.LoginRequest(phone, password)).exchange();
    }

    @Test
    void fifthWrongPasswordLocksForThirtyMinutesWithUnlockTimeShown() {
        String phone = "9876520001";
        userAdminService.createUser("Lockout Tester", phone, Set.of(Role.MANAGER), null, "the-real-password");

        for (int i = 0; i < 4; i++) {
            attempt(phone, "wrong-" + i);
        }

        var body = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "wrong-5th"))
                .exchange()
                .expectStatus()
                .isEqualTo(423)
                .expectBody(AuthController.LockedResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.message()).startsWith("Account locked until");
        assertThat(body.unlockAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
    }

    @Test
    void correctPasswordDuringLockIsStillRefused() {
        String phone = "9876520002";
        userAdminService.createUser("Lockout Tester 2", phone, Set.of(Role.MANAGER), null, "the-real-password");

        for (int i = 0; i < 5; i++) {
            attempt(phone, "wrong-" + i);
        }

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "the-real-password"))
                .exchange()
                .expectStatus()
                .isEqualTo(423);
    }

    @Test
    void successfulSignInResetsTheFailureCountToZero() {
        String phone = "9876520003";
        userAdminService.createUser("Lockout Tester 3", phone, Set.of(Role.MANAGER), null, "the-real-password");

        attempt(phone, "wrong-1");
        attempt(phone, "wrong-2");
        attempt(phone, "wrong-3");

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "the-real-password"))
                .exchange()
                .expectStatus()
                .isOk();

        // Another 4 wrong attempts (not 5) after the reset must NOT lock the account.
        for (int i = 0; i < 4; i++) {
            attempt(phone, "still-wrong-" + i);
        }
        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "the-real-password"))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void signInSucceedsOnceTheLockoutWindowPasses() {
        String phone = "9876520004";
        userAdminService.createUser("Lockout Tester 4", phone, Set.of(Role.MANAGER), null, "the-real-password");

        for (int i = 0; i < 5; i++) {
            attempt(phone, "wrong-" + i);
        }
        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "the-real-password"))
                .exchange()
                .expectStatus()
                .isEqualTo(423);

        ((MutableTestClock) clock).advance(Duration.ofMinutes(30).plusSeconds(1));

        client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "the-real-password"))
                .exchange()
                .expectStatus()
                .isOk();
    }
}
