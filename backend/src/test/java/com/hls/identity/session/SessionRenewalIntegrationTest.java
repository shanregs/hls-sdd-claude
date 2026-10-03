package com.hls.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.auth.RenewalCookies;
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
 * User Story 3 (spec 001-identity-access): access renews silently within the 14-day session;
 * replaying an already-used renewal credential revokes the whole chain; renewal after 14 days is
 * refused (Acceptance Scenarios 1-3, FR-009/FR-010).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionRenewalIntegrationTest {

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
    private SessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String signInAndGetRenewalCookie(String phone) {
        userAdminService.createUser("Renewal Tester " + phone, phone, Set.of(Role.TEACHER), null, "correct-horse-1");
        var result = client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, "correct-horse-1"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult();
        return result.getResponseCookies()
                .getFirst(RenewalCookies.COOKIE_NAME)
                .getValue();
    }

    @Test
    void renewalWithinTheWindowIssuesANewCredentialAndAccessToken() {
        String renewalCookie = signInAndGetRenewalCookie("9876510001");

        var result = client.post()
                .uri("/api/v1/auth/renew")
                .header("Cookie", RenewalCookies.COOKIE_NAME + "=" + renewalCookie)
                .exchange()
                .expectStatus()
                .isOk()
                .expectCookie()
                .exists(RenewalCookies.COOKIE_NAME)
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult();

        assertThat(result.getResponseBody()).isNotNull();
        assertThat(result.getResponseBody().accessToken()).isNotBlank();
        String newRenewalCookie =
                result.getResponseCookies().getFirst(RenewalCookies.COOKIE_NAME).getValue();
        assertThat(newRenewalCookie).isNotEqualTo(renewalCookie);
    }

    @Test
    void replayingAnAlreadyUsedRenewalCredentialRevokesTheWholeSession() {
        String renewalCookie = signInAndGetRenewalCookie("9876510002");

        // First use: succeeds and rotates the credential.
        client.post()
                .uri("/api/v1/auth/renew")
                .header("Cookie", RenewalCookies.COOKIE_NAME + "=" + renewalCookie)
                .exchange()
                .expectStatus()
                .isOk();

        // Replaying the now-used credential must be refused and revoke the session.
        client.post()
                .uri("/api/v1/auth/renew")
                .header("Cookie", RenewalCookies.COOKIE_NAME + "=" + renewalCookie)
                .exchange()
                .expectStatus()
                .isUnauthorized();

        boolean anyRevoked = sessionRepository.findAll().stream()
                .anyMatch(session -> session.getStatus() == SessionStatus.REVOKED);
        assertThat(anyRevoked).isTrue();
    }

    @Test
    void renewalAfterFourteenDaysIsRefused() {
        String renewalCookie = signInAndGetRenewalCookie("9876510003");

        ((MutableTestClock) clock).advance(Duration.ofDays(14).plusMinutes(1));

        client.post()
                .uri("/api/v1/auth/renew")
                .header("Cookie", RenewalCookies.COOKIE_NAME + "=" + renewalCookie)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
