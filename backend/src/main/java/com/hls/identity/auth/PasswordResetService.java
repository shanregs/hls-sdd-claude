package com.hls.identity.auth;

import com.hls.identity.activity.PasswordResetCompleted;
import com.hls.identity.activity.PasswordResetRequested;
import com.hls.identity.loginhistory.LoginEventType;
import com.hls.identity.loginhistory.LoginHistoryPublisher;
import com.hls.identity.loginhistory.LoginMethod;
import com.hls.identity.otp.OtpChannel;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.session.SessionService;
import com.hls.identity.session.SessionStatus;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.IdentifierResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes a password reset after {@code OtpController} has verified a code (FR-016,
 * Constitution v2.3.0): resolves which channels an identifier can use, and turns a verified OTP
 * into a short-lived reset token, then a new password.
 */
@Service
public class PasswordResetService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final IdentifierResolver identifierResolver;
    private final AppUserRepository appUserRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final LoginHistoryPublisher loginHistoryPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ConcurrentHashMap<String, ResetTokenEntry> resetTokens = new ConcurrentHashMap<>();

    public PasswordResetService(
            IdentifierResolver identifierResolver,
            AppUserRepository appUserRepository,
            SessionRepository sessionRepository,
            SessionService sessionService,
            PasswordEncoder passwordEncoder,
            LoginHistoryPublisher loginHistoryPublisher,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.identifierResolver = identifierResolver;
        this.appUserRepository = appUserRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.loginHistoryPublisher = loginHistoryPublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Never reveals whether {@code identifier} is registered (FR-016). */
    public Set<OtpChannel> availableChannels(String identifier) {
        var resolution = identifierResolver.resolve(identifier);
        Set<OtpChannel> channels = EnumSet.of(OtpChannel.SMS);
        resolution.user().filter(u -> u.getEmail() != null).ifPresent(u -> channels.add(OtpChannel.EMAIL));
        return channels;
    }

    @Transactional
    public String issueResetToken(AppUser user) {
        String token = UUID.randomUUID().toString();
        resetTokens.put(token, new ResetTokenEntry(user.getId(), clock.instant().plus(RESET_TOKEN_TTL)));
        eventPublisher.publishEvent(
                new PasswordResetRequested(UUID.randomUUID(), clock.instant(), user.getId(), user.getId()));
        return token;
    }

    public enum CompleteOutcome {
        SUCCESS,
        INVALID_OR_EXPIRED_TOKEN,
        PASSWORD_POLICY_VIOLATION
    }

    @Transactional
    public CompleteOutcome complete(
            String resetToken, String newPassword, String clientIp, String deviceDescription) {
        ResetTokenEntry entry = resetTokens.remove(resetToken);
        if (entry == null || clock.instant().isAfter(entry.expiresAt())) {
            return CompleteOutcome.INVALID_OR_EXPIRED_TOKEN;
        }
        AppUser user = appUserRepository.findById(entry.userId()).orElse(null);
        if (user == null) {
            return CompleteOutcome.INVALID_OR_EXPIRED_TOKEN;
        }
        if (newPassword == null
                || newPassword.length() < MIN_PASSWORD_LENGTH
                || newPassword.equals(user.getPhone())) {
            return CompleteOutcome.PASSWORD_POLICY_VIOLATION;
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttemptCount(0);
        user.setLockUntil(null);
        appUserRepository.save(user);

        sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)
                .forEach(session -> sessionService.endSession(session.getId()));

        loginHistoryPublisher.record(
                user.getId(),
                user.getPhone(),
                LoginMethod.OTP,
                LoginEventType.PASSWORD_RESET,
                "Password reset completed",
                clientIp,
                deviceDescription);
        eventPublisher.publishEvent(
                new PasswordResetCompleted(UUID.randomUUID(), clock.instant(), user.getId(), user.getId()));

        return CompleteOutcome.SUCCESS;
    }

    private record ResetTokenEntry(UUID userId, Instant expiresAt) {}
}
