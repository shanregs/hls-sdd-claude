package com.hls.identity.user;

import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.session.SessionService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal internal user-creation/deactivation capability (FR-021). Used by the bootstrap
 * initializer and by tests; no HTTP endpoint exists for it in this spec — screens are spec 004's.
 */
@Service
public class UserAdminService {

    private final AppUserRepository appUserRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UserAdminService(
            AppUserRepository appUserRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            PhoneNumberNormalizer phoneNumberNormalizer,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public AppUser createUser(
            String displayName, String rawPhone, Set<Role> roles, UUID linkedTeacherId, String initialPassword) {
        return createUser(displayName, rawPhone, roles, linkedTeacherId, initialPassword, null, null);
    }

    /**
     * @param username optional, 3-30 chars, unique case-insensitively (FR-025)
     * @param email optional, unique, used only for password-reset delivery (FR-026)
     */
    @Transactional
    public AppUser createUser(
            String displayName,
            String rawPhone,
            Set<Role> roles,
            UUID linkedTeacherId,
            String initialPassword,
            String username,
            String email) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("A user must hold at least one role (FR-003).");
        }
        String phone = phoneNumberNormalizer.normalize(rawPhone);
        if (appUserRepository.findByPhone(phone).isPresent()) {
            throw new DuplicatePhoneException(phone);
        }
        if (username != null && appUserRepository
                .findByUsernameLower(username.toLowerCase(java.util.Locale.ROOT))
                .isPresent()) {
            throw new DuplicateUsernameException(username);
        }
        if (email != null && appUserRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException(email);
        }
        String passwordHash = initialPassword != null ? passwordEncoder.encode(initialPassword) : null;
        AppUser user = appUserRepository.save(new AppUser(displayName, phone, passwordHash, linkedTeacherId));
        if (username != null) {
            user.setUsername(username);
        }
        if (email != null) {
            user.setEmail(email);
        }
        user = appUserRepository.save(user);
        for (Role role : roles) {
            roleAssignmentRepository.save(new RoleAssignment(user.getId(), role));
        }
        return user;
    }

    /** Deactivates a user and immediately revokes every active session of theirs (FR-018) so their
     * next request is refused rather than waiting for the access token to expire. */
    @Transactional
    public void deactivateUser(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No such user: " + userId));
        user.deactivate();
        appUserRepository.save(user);
        sessionService.revokeAllSessionsForUser(userId);
        eventPublisher.publishEvent(
                new AccountActivationChanged(UUID.randomUUID(), clock.instant(), null, userId, false));
    }

    public List<Role> rolesOf(UUID userId) {
        return roleAssignmentRepository.findByUserId(userId).stream().map(RoleAssignment::getRole).toList();
    }

    public static class DuplicatePhoneException extends RuntimeException {
        public DuplicatePhoneException(String phone) {
            super("A user with phone " + phone + " already exists.");
        }
    }

    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String username) {
            super("A user with username " + username + " already exists.");
        }
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String email) {
            super("A user with email " + email + " already exists.");
        }
    }
}
