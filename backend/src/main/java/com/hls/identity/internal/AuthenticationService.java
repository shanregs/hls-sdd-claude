package com.hls.identity.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-001/003/007/008/009/010/012/015/016/018: password + OTP verification,
 * lockout, password reset, and session list/revoke. Deliberately one service —
 * these operations share the same "generic denial" (FR-015) and audit-trail
 * (FR-013) discipline, and splitting them would just duplicate both.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OtpSender otpSender;
    private final AuthAuditLogger auditLogger;
    private final IdentityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationService(UserRepository userRepository,
                                  SessionRepository sessionRepository,
                                  OtpChallengeRepository otpChallengeRepository,
                                  PasswordResetTokenRepository passwordResetTokenRepository,
                                  PasswordEncoder passwordEncoder,
                                  TokenService tokenService,
                                  OtpSender otpSender,
                                  AuthAuditLogger auditLogger,
                                  IdentityProperties properties,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.otpSender = otpSender;
        this.auditLogger = auditLogger;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- User Story 1 / User Story 4: password login, MFA, lockout ----------------

    @Transactional
    public LoginOutcome login(String phoneNumber, String rawPassword, Channel channel, String deviceLabel, String requestId) {
        Instant now = clock.instant();
        Optional<User> maybeUser = userRepository.findByPhoneNumber(phoneNumber);

        if (maybeUser.isEmpty()) {
            // FR-015: identical failure path whether or not the identifier is registered.
            auditLogger.loginFailure(null, phoneNumber, requestId);
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();

        if (user.isLocked(now)) {
            auditLogger.loginFailure(user.getId(), phoneNumber, requestId);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            recordFailedAttempt(user, requestId);
            throw new InvalidCredentialsException();
        }

        user.resetFailedAttempts();
        userRepository.save(user);

        if (user.isMfaEnabled()) {
            String identifier = user.getMfaMethod() == MfaMethod.EMAIL ? user.getEmail() : user.getPhoneNumber();
            UUID challengeId = issueOtpChallenge(identifier);
            return new LoginOutcome.MfaRequired(challengeId, user.getMfaMethod());
        }

        TokenService.IssuedTokens tokens = tokenService.issueForNewSession(user.getId(), user.getRoles(), channel, deviceLabel, user.getLinkedTeacherId());
        auditLogger.loginSuccess(user.getId(), user.getRoles(), requestId);
        return new LoginOutcome.Authenticated(tokens);
    }

    @Transactional
    public TokenService.IssuedTokens verifyMfa(UUID mfaChallengeId, String code, Channel channel, String deviceLabel, String requestId) {
        OtpChallenge challenge = consumeChallengeOrThrow(mfaChallengeId, code);
        User user = resolveUserByIdentifier(challenge.getIdentifier())
                .orElseThrow(InvalidCredentialsException::new);

        TokenService.IssuedTokens tokens = tokenService.issueForNewSession(user.getId(), user.getRoles(), channel, deviceLabel, user.getLinkedTeacherId());
        auditLogger.loginSuccess(user.getId(), user.getRoles(), requestId);
        return tokens;
    }

    private void recordFailedAttempt(User user, String requestId) {
        user.incrementFailedAttempts();
        boolean justLocked = user.getFailedAttemptCount() >= properties.getLockout().getFailedAttemptThreshold();
        if (justLocked) {
            user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(properties.getLockout().getLockoutDurationMinutes())));
        }
        userRepository.save(user);
        if (justLocked) {
            auditLogger.accountLocked(user.getId(), requestId);
        }
        auditLogger.loginFailure(user.getId(), user.getPhoneNumber(), requestId);
    }

    /** FR-010: Admin/Director-only — enforced by the controller's role check, not here. */
    @Transactional
    public void unlockAccount(UUID userId, String requestId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        user.setLockedUntil(null);
        user.resetFailedAttempts();
        userRepository.save(user);
        auditLogger.accountUnlocked(userId, requestId);
    }

    // ---- User Story 2: Teacher OTP login -------------------------------------------

    /** FR-009/FR-015: always completes without revealing whether phoneNumber is registered. */
    @Transactional
    public void requestOtp(String phoneNumber) {
        issueOtpChallenge(phoneNumber);
    }

    @Transactional
    public TokenService.IssuedTokens verifyOtp(String phoneNumber, String code, Channel channel, String deviceLabel, String requestId) {
        List<OtpChallenge> candidates = otpChallengeRepository.findByIdentifierOrderByExpiresAtDesc(phoneNumber);
        OtpChallenge current = candidates.stream()
                .filter(c -> c.isUsable(clock.instant()))
                .findFirst()
                .orElseThrow(InvalidCredentialsException::new);

        verifyChallengeCodeOrThrow(current, code);

        User user = userRepository.findByPhoneNumber(phoneNumber).orElseThrow(InvalidCredentialsException::new);
        TokenService.IssuedTokens tokens = tokenService.issueForNewSession(user.getId(), user.getRoles(), channel, deviceLabel, user.getLinkedTeacherId());
        auditLogger.loginSuccess(user.getId(), user.getRoles(), requestId);
        return tokens;
    }

    // ---- User Story 1: self-service password reset (FR-016) -----------------------

    @Transactional
    public void requestPasswordReset(String identifier, String requestId) {
        Optional<User> maybeUser = resolveUserByIdentifier(identifier);
        // FR-015: proceed identically whether or not the identifier resolves.
        maybeUser.ifPresent(user -> {
            String rawToken = randomToken();
            Instant expiry = clock.instant().plus(Duration.ofMinutes(30));
            passwordResetTokenRepository.save(new PasswordResetToken(UUID.randomUUID(), user.getId(), hash(rawToken), expiry));
            otpSender.sendCode(identifier, rawToken);
            auditLogger.passwordResetRequested(user.getId(), requestId);
        });
    }

    @Transactional
    public void confirmPasswordReset(String rawResetToken, String newPassword, String requestId) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash(rawResetToken))
                .filter(t -> t.isUsable(clock.instant()))
                .orElseThrow(InvalidCredentialsException::new);

        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidCredentialsException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.markConsumed();
        passwordResetTokenRepository.save(token);

        auditLogger.passwordResetCompleted(user.getId(), requestId);
    }

    // ---- User Story 1 (FR-011): silent renewal ---------------------------------------

    /**
     * The refresh endpoint is pre-authentication (no bearer token presented — that's
     * the point of a refresh), so the caller's identity comes from the session the
     * refresh token itself points to, never from a client-supplied claim.
     */
    @Transactional
    public TokenService.RefreshResult refresh(String rawRefreshToken) {
        Session session = sessionRepository.findByRefreshTokenHash(TokenHashing.sha256Base64(rawRefreshToken))
                .filter(s -> s.isUsable(clock.instant()))
                .orElseThrow(TokenService.InvalidSessionException::new);
        User user = userRepository.findById(session.getUserId()).orElseThrow(TokenService.InvalidSessionException::new);
        return tokenService.refresh(rawRefreshToken, user.getId(), user.getRoles(), user.getLinkedTeacherId());
    }

    // ---- User Story 5: view/revoke sessions ----------------------------------------

    public record SessionSummary(UUID id, Channel channel, String deviceLabel, Instant issuedAt, Instant lastActiveAt) {
    }

    public List<SessionSummary> listSessions(UUID requestingUserId, Set<Role> requestingRoles, UUID targetUserId) {
        UUID effectiveTarget = resolveTargetUserId(requestingUserId, requestingRoles, targetUserId);
        return sessionRepository.findByUserIdAndRevokedFalse(effectiveTarget).stream()
                .filter(s -> s.isUsable(clock.instant()))
                .map(s -> new SessionSummary(s.getId(), s.getChannel(), s.getDeviceLabel(), s.getIssuedAt(), s.getLastActiveAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID requestingUserId, Set<Role> requestingRoles, String requestId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow(InvalidCredentialsException::new);
        boolean isOwnSession = session.getUserId().equals(requestingUserId);
        boolean isPrivileged = requestingRoles.contains(Role.ADMIN) || requestingRoles.contains(Role.DIRECTOR);
        if (!isOwnSession && !isPrivileged) {
            throw new AccessDeniedException("Not authorized to revoke another user's session");
        }
        session.revoke(clock.instant());
        sessionRepository.save(session);
        auditLogger.sessionRevoked(session.getUserId(), requestId);
    }

    private UUID resolveTargetUserId(UUID requestingUserId, Set<Role> requestingRoles, UUID targetUserId) {
        if (targetUserId == null || targetUserId.equals(requestingUserId)) {
            return requestingUserId;
        }
        boolean isPrivileged = requestingRoles.contains(Role.ADMIN) || requestingRoles.contains(Role.DIRECTOR);
        if (!isPrivileged) {
            throw new AccessDeniedException("Not authorized to view another user's sessions");
        }
        return targetUserId;
    }

    // ---- Shared helpers --------------------------------------------------------------

    private UUID issueOtpChallenge(String identifier) {
        String code = randomNumericCode();
        Instant expiry = clock.instant().plus(Duration.ofSeconds(properties.getOtp().getTtlSeconds()));
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID(), identifier, hash(code), expiry);
        otpChallengeRepository.save(challenge);
        otpSender.sendCode(identifier, code);
        return challenge.getId();
    }

    private OtpChallenge consumeChallengeOrThrow(UUID challengeId, String code) {
        OtpChallenge challenge = otpChallengeRepository.findById(challengeId)
                .filter(c -> c.isUsable(clock.instant()))
                .orElseThrow(InvalidCredentialsException::new);
        verifyChallengeCodeOrThrow(challenge, code);
        return challenge;
    }

    private void verifyChallengeCodeOrThrow(OtpChallenge challenge, String code) {
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.incrementAttempts();
            otpChallengeRepository.save(challenge);
            throw new InvalidCredentialsException();
        }
        challenge.markConsumed();
        otpChallengeRepository.save(challenge);
    }

    private Optional<User> resolveUserByIdentifier(String identifier) {
        Optional<User> byPhone = userRepository.findByPhoneNumber(identifier);
        return byPhone.isPresent() ? byPhone : userRepository.findByEmail(identifier);
    }

    private String randomNumericCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        return passwordEncoder.encode(raw);
    }
}
