package com.hls.identity.auth;

import com.hls.identity.activity.AccountLockChanged;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.IdentifierResolver;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password sign-in for any role, identified by phone number or username (User Story 1,
 * Constitution v2.3.0). Every failure path — wrong password, unregistered identifier, an account
 * with no password set — returns the identical generic failure, never revealing which case
 * occurred (FR-008). Lockout after 5 consecutive failures (FR-012, User Story 4) is fixed
 * deployment configuration, not database-configurable (research.md §17 draws this distinction for
 * the newer OTP throttle only).
 */
@Service
public class PasswordAuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    private final IdentifierResolver identifierResolver;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUserRepository appUserRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PasswordAuthService(
            IdentifierResolver identifierResolver,
            RoleAssignmentRepository roleAssignmentRepository,
            PasswordEncoder passwordEncoder,
            AppUserRepository appUserRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.identifierResolver = identifierResolver;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.appUserRepository = appUserRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * @param identifier a phone number or a username, resolved by {@link IdentifierResolver}
     *     (research.md §13, FR-027).
     */
    @Transactional
    public Result authenticate(String identifier, String rawPassword) {
        IdentifierResolver.Resolution resolution = identifierResolver.resolve(identifier);
        String loggedIdentifier = resolution.normalizedIdentifier();
        Optional<AppUser> maybeUser = resolution.user();
        if (maybeUser.isEmpty()) {
            return Result.invalidCredentials(null, loggedIdentifier);
        }
        AppUser user = maybeUser.get();
        if (!user.isActive()) {
            return Result.invalidCredentials(user.getId(), loggedIdentifier);
        }

        Instant now = clock.instant();
        if (user.getLockUntil() != null && now.isBefore(user.getLockUntil())) {
            return Result.locked(user.getId(), loggedIdentifier, user.getLockUntil());
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int attempts = user.getFailedAttemptCount() + 1;
            user.setFailedAttemptCount(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                Instant lockUntil = now.plus(LOCKOUT_DURATION);
                user.setLockUntil(lockUntil);
                appUserRepository.save(user);
                eventPublisher.publishEvent(
                        new AccountLockChanged(UUID.randomUUID(), now, null, user.getId(), true));
                return Result.locked(user.getId(), loggedIdentifier, lockUntil);
            }
            appUserRepository.save(user);
            return Result.invalidCredentials(user.getId(), loggedIdentifier);
        }

        boolean wasLocked = user.getLockUntil() != null;
        user.setFailedAttemptCount(0);
        user.setLockUntil(null);
        appUserRepository.save(user);
        if (wasLocked) {
            eventPublisher.publishEvent(
                    new AccountLockChanged(UUID.randomUUID(), now, user.getId(), user.getId(), false));
        }

        List<Role> roles = roleAssignmentRepository.findByUserId(user.getId()).stream()
                .map(ra -> ra.getRole())
                .toList();
        return Result.success(user, roles, loggedIdentifier);
    }

    /** Outcome of an authentication attempt; {@code identifierNormalized} is used for login-history logging. */
    public record Result(
            Outcome outcome,
            AppUser user,
            List<Role> roles,
            UUID userId,
            String identifierNormalized,
            Instant lockUntil) {

        public enum Outcome {
            SUCCESS,
            INVALID_CREDENTIALS,
            LOCKED
        }

        public boolean success() {
            return outcome == Outcome.SUCCESS;
        }

        public boolean locked() {
            return outcome == Outcome.LOCKED;
        }

        public static Result success(AppUser user, List<Role> roles, String identifierNormalized) {
            return new Result(Outcome.SUCCESS, user, roles, user.getId(), identifierNormalized, null);
        }

        public static Result invalidCredentials(UUID userId, String identifierNormalized) {
            return new Result(Outcome.INVALID_CREDENTIALS, null, List.of(), userId, identifierNormalized, null);
        }

        public static Result locked(UUID userId, String identifierNormalized, Instant lockUntil) {
            return new Result(Outcome.LOCKED, null, List.of(), userId, identifierNormalized, lockUntil);
        }
    }
}
