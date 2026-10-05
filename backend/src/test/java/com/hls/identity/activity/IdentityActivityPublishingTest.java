package com.hls.identity.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.auth.PasswordAuthService;
import com.hls.identity.auth.PasswordResetService;
import com.hls.identity.session.SessionController;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.session.SessionStatus;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * User Story 3 (research.md §2): identity publishes each of the five new lifecycle events at the
 * right point — password reset requested ({@code issueResetToken}) and completed
 * ({@code complete}), lockout ({@code PasswordAuthService}, 5th consecutive failure) and unlock
 * (subsequent success), session end ({@code DELETE /api/v1/me/sessions/{id}}), and deactivation
 * ({@code deactivateUser}). Uses Spring Test's {@code ApplicationEvents} to observe raw published
 * events directly, independent of spec 003's audit consumer (covered separately in
 * {@code UserActivityEventConsumerTest}). {@code ApplicationEvents} only records events published
 * on the test's own thread, so the lockout/unlock/session-end cases call the underlying
 * service/controller methods directly rather than through a real HTTP round trip (which runs on a
 * separate Tomcat worker thread) — the password-reset case already does this naturally.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@RecordApplicationEvents
class IdentityActivityPublishingTest {

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
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordAuthService passwordAuthService;

    @Autowired
    private SessionController sessionController;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String signInAs(String phone, Role role, String password) {
        userAdminService.createUser("Activity Tester " + phone, phone, Set.of(role), null, password);
        return client.post()
                .uri("/api/v1/auth/login")
                .body(new AuthDtos.LoginRequest(phone, password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AuthDtos.AuthResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    @Test
    void passwordResetPublishesRequestedThenCompleted() {
        userAdminService.createUser("Reset Tester", "9876610001", Set.of(Role.MANAGER), null, "the-real-password-1");
        AppUser user = appUserRepository.findByPhone("9876610001").orElseThrow();

        String token = passwordResetService.issueResetToken(user);
        assertThat(applicationEvents.stream(PasswordResetRequested.class))
                .anyMatch(e -> e.affectedUserId().equals(user.getId()));

        passwordResetService.complete(token, "a-brand-new-password", "127.0.0.1", "test-agent");
        assertThat(applicationEvents.stream(PasswordResetCompleted.class))
                .anyMatch(e -> e.affectedUserId().equals(user.getId()));
    }

    @Test
    void fifthConsecutiveFailurePublishesAccountLockChangedLockedTrue() {
        String phone = "9876610002";
        userAdminService.createUser("Lock Tester", phone, Set.of(Role.MANAGER), null, "the-real-password-2");

        for (int i = 0; i < 5; i++) {
            passwordAuthService.authenticate(phone, "wrong-" + i);
        }

        assertThat(applicationEvents.stream(AccountLockChanged.class)).anyMatch(AccountLockChanged::locked);
    }

    @Test
    void successfulSignInAfterALockPublishesAccountLockChangedLockedFalse() {
        String phone = "9876610003";
        userAdminService.createUser("Unlock Tester", phone, Set.of(Role.MANAGER), null, "the-real-password-3");
        for (int i = 0; i < 5; i++) {
            passwordAuthService.authenticate(phone, "wrong-" + i);
        }
        AppUser user = appUserRepository.findByPhone(phone).orElseThrow();
        // Simulate the lock window having passed: lockUntil must stay non-null (so
        // PasswordAuthService still sees "this account was locked" and publishes the unlock
        // transition) but in the past (so the early "still locked" check does not refuse this
        // attempt).
        user.setLockUntil(Instant.now().minusSeconds(60));
        appUserRepository.save(user);

        var result = passwordAuthService.authenticate(phone, "the-real-password-3");
        assertThat(result.success()).isTrue();

        assertThat(applicationEvents.stream(AccountLockChanged.class)).anyMatch(e -> !e.locked());
    }

    @Test
    void endingASessionPublishesSessionEnded() {
        String phone = "9876610004";
        String token = signInAs(phone, Role.MANAGER, "the-real-password-4");
        AppUser user = appUserRepository.findByPhone(phone).orElseThrow();
        UUID sessionId = sessionRepository
                .findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)
                .get(0)
                .getId();

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", user.getId().toString())
                .claim("sid", sessionId.toString())
                .claim("roles", java.util.List.of("MANAGER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        sessionController.endSession(sessionId, jwt, new MockHttpServletRequest());

        assertThat(applicationEvents.stream(SessionEnded.class)).anyMatch(e -> e.sessionId().equals(sessionId));
    }

    @Test
    void deactivatingAUserPublishesAccountActivationChangedActiveFalse() {
        userAdminService.createUser("Deactivate Tester", "9876610005", Set.of(Role.MANAGER), null, "the-real-password-5");
        AppUser user = appUserRepository.findByPhone("9876610005").orElseThrow();

        userAdminService.deactivateUser(null, user.getId());

        assertThat(applicationEvents.stream(AccountActivationChanged.class))
                .anyMatch(e -> e.affectedUserId().equals(user.getId()) && !e.active());
    }
}
