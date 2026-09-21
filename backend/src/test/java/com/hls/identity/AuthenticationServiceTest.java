package com.hls.identity;

import com.hls.identity.internal.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for FR-001/007/008/009/010/015/016/018 (User Stories 1, 2, 4).
 * Repositories are mocked; only {@link AuthenticationService}'s own logic is
 * under test here — the Testcontainers-backed flow lives in
 * {@code IdentityIntegrationTest}.
 */
class AuthenticationServiceTest {

    private UserRepository userRepository;
    private SessionRepository sessionRepository;
    private OtpChallengeRepository otpChallengeRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private PasswordEncoder passwordEncoder;
    private TokenService tokenService;
    private OtpSender otpSender;
    private AuthAuditLogger auditLogger;
    private IdentityProperties properties;
    private Clock clock;
    private AuthenticationService service;

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        sessionRepository = mock(SessionRepository.class);
        otpChallengeRepository = mock(OtpChallengeRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4); // low cost factor: fast tests
        tokenService = mock(TokenService.class);
        otpSender = mock(OtpSender.class);
        auditLogger = mock(AuthAuditLogger.class);
        properties = new IdentityProperties();
        properties.getLockout().setFailedAttemptThreshold(3);
        properties.getLockout().setLockoutDurationMinutes(30);
        properties.getOtp().setTtlSeconds(300);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new AuthenticationService(userRepository, sessionRepository, otpChallengeRepository,
                passwordResetTokenRepository, passwordEncoder, tokenService, otpSender, auditLogger, properties, clock);
    }

    private User activeUser(String rawPassword) {
        User user = new User(UUID.randomUUID(), "Test User", "+919800000001", Set.of(Role.MANAGER), NOW);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    // ---- FR-001/FR-007/FR-015: correct/incorrect password -----------------------------

    @Test
    void login_withCorrectPassword_returnsAuthenticatedAndIssuesTokens() {
        User user = activeUser("correct-password");
        when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));
        var issued = new TokenService.IssuedTokens("access", 900, "refresh", UUID.randomUUID());
        when(tokenService.issueForNewSession(user.getId(), user.getRoles(), Channel.WEB, null)).thenReturn(issued);

        LoginOutcome outcome = service.login(user.getPhoneNumber(), "correct-password", Channel.WEB, null, "req-1");

        assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
        assertThat(((LoginOutcome.Authenticated) outcome).tokens()).isEqualTo(issued);
        verify(auditLogger).loginSuccess(user.getId(), user.getRoles(), "req-1");
    }

    @Test
    void login_withWrongPassword_throwsGenericExceptionAndRecordsFailure() {
        User user = activeUser("correct-password");
        when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(user.getPhoneNumber(), "wrong-password", Channel.WEB, null, "req-2"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getFailedAttemptCount()).isEqualTo(1);
        verify(auditLogger).loginFailure(user.getId(), user.getPhoneNumber(), "req-2");
        verify(tokenService, never()).issueForNewSession(any(), any(), any(), any());
    }

    // FR-015: an unregistered phone number fails identically to a wrong password.
    @Test
    void login_withUnregisteredPhoneNumber_throwsSameGenericException() {
        when(userRepository.findByPhoneNumber("+919800000099")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("+919800000099", "anything", Channel.WEB, null, "req-3"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditLogger).loginFailure(null, "+919800000099", "req-3");
    }

    // ---- FR-010: lockout ----------------------------------------------------------------

    @Test
    void login_reachingFailureThreshold_locksAccountAndDeniesEvenCorrectPasswordNextTime() {
        User user = activeUser("correct-password");
        when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(user.getPhoneNumber(), "wrong", Channel.WEB, null, "req"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThat(user.getFailedAttemptCount()).isEqualTo(3);
        assertThat(user.isLocked(NOW)).isTrue();
        verify(auditLogger).accountLocked(user.getId(), "req");

        // FR-015: locked-account denial with correct credentials looks identical to wrong-password.
        assertThatThrownBy(() -> service.login(user.getPhoneNumber(), "correct-password", Channel.WEB, null, "req"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenService, never()).issueForNewSession(any(), any(), any(), any());
    }

    @Test
    void unlockAccount_clearsLockAndResetsFailedAttempts() {
        User user = activeUser("correct-password");
        user.incrementFailedAttempts();
        user.incrementFailedAttempts();
        user.incrementFailedAttempts();
        user.setLockedUntil(NOW.plusSeconds(600));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.unlockAccount(user.getId(), "req-unlock");

        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getFailedAttemptCount()).isZero();
        verify(auditLogger).accountUnlocked(user.getId(), "req-unlock");
    }

    // ---- FR-018: MFA ---------------------------------------------------------------------

    @Test
    void login_withMfaEnabled_returnsMfaRequiredInsteadOfTokens() {
        User user = activeUser("correct-password");
        user.setMfaEnabled(true);
        user.setMfaMethod(MfaMethod.SMS);
        when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));

        LoginOutcome outcome = service.login(user.getPhoneNumber(), "correct-password", Channel.WEB, null, "req-mfa");

        assertThat(outcome).isInstanceOf(LoginOutcome.MfaRequired.class);
        assertThat(((LoginOutcome.MfaRequired) outcome).method()).isEqualTo(MfaMethod.SMS);
        verify(otpChallengeRepository).save(any());
        verify(otpSender).sendCode(eq(user.getPhoneNumber()), any());
        verify(tokenService, never()).issueForNewSession(any(), any(), any(), any());
        // Not yet fully authenticated — no LOGIN_SUCCESS until the MFA step itself succeeds.
        verify(auditLogger, never()).loginSuccess(any(), any(), any());
    }

    // ---- FR-008/FR-009: OTP ---------------------------------------------------------------

    @Test
    void verifyOtp_withCorrectUnexpiredCode_issuesTokens() {
        String phoneNumber = "+919800000050";
        String code = "123456";
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID(), phoneNumber, passwordEncoder.encode(code), NOW.plusSeconds(60));
        when(otpChallengeRepository.findByIdentifierOrderByExpiresAtDesc(phoneNumber)).thenReturn(java.util.List.of(challenge));

        User teacher = new User(UUID.randomUUID(), "Teacher", phoneNumber, Set.of(Role.TEACHER), NOW);
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(teacher));
        var issued = new TokenService.IssuedTokens("access", 900, "refresh", UUID.randomUUID());
        when(tokenService.issueForNewSession(teacher.getId(), teacher.getRoles(), Channel.MOBILE, "phone-1")).thenReturn(issued);

        var tokens = service.verifyOtp(phoneNumber, code, Channel.MOBILE, "phone-1", "req-otp");

        assertThat(tokens).isEqualTo(issued);
        assertThat(challenge.isConsumed()).isTrue();
        verify(auditLogger).loginSuccess(teacher.getId(), teacher.getRoles(), "req-otp");
    }

    @Test
    void verifyOtp_withExpiredCode_throwsGenericException() {
        String phoneNumber = "+919800000051";
        OtpChallenge expired = new OtpChallenge(UUID.randomUUID(), phoneNumber, passwordEncoder.encode("123456"), NOW.minusSeconds(1));
        when(otpChallengeRepository.findByIdentifierOrderByExpiresAtDesc(phoneNumber)).thenReturn(java.util.List.of(expired));

        assertThatThrownBy(() -> service.verifyOtp(phoneNumber, "123456", Channel.MOBILE, null, "req"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void verifyOtp_withIncorrectCode_throwsGenericExceptionAndIncrementsAttempts() {
        String phoneNumber = "+919800000052";
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID(), phoneNumber, passwordEncoder.encode("123456"), NOW.plusSeconds(60));
        when(otpChallengeRepository.findByIdentifierOrderByExpiresAtDesc(phoneNumber)).thenReturn(java.util.List.of(challenge));

        assertThatThrownBy(() -> service.verifyOtp(phoneNumber, "000000", Channel.MOBILE, null, "req"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(challenge.isConsumed()).isFalse();
    }
}
