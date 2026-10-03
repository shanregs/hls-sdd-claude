package com.hls.identity.user;

import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.activity.PasswordResetByAdmin;
import com.hls.identity.activity.UserCreated;
import com.hls.identity.activity.UserRoleChanged;
import com.hls.identity.auth.PasswordPolicy;
import com.hls.identity.session.SessionService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User creation, search, role changes, (de)activation and admin-triggered password reset. The
 * no-actor {@code createUser} overloads are used by the bootstrap initializer, dev seed and tests;
 * the actor-aware methods back spec 004's User Management endpoints.
 */
@Service
public class UserAdminService {

    private final AppUserRepository appUserRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final LastAdminGuard lastAdminGuard;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public UserAdminService(
            AppUserRepository appUserRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            LastAdminGuard lastAdminGuard,
            PhoneNumberNormalizer phoneNumberNormalizer,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.lastAdminGuard = lastAdminGuard;
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

    /**
     * Actor-aware creation for User Management (spec 004 research.md section 5): same work as the
     * overload above, plus a {@link UserCreated} event so the creation is audited.
     */
    @Transactional
    public AppUser createUser(
            UUID actorUserId,
            String displayName,
            String rawPhone,
            Set<Role> roles,
            UUID linkedTeacherId,
            String initialPassword,
            String username,
            String email) {
        AppUser user = createUser(displayName, rawPhone, roles, linkedTeacherId, initialPassword, username, email);
        eventPublisher.publishEvent(
                new UserCreated(UUID.randomUUID(), clock.instant(), actorUserId, user.getId(), EnumSet.copyOf(roles)));
        return user;
    }

    /** One page of users with their roles, filtered per FR-002. {@code term} may be blank. */
    @Transactional(readOnly = true)
    public Page<UserWithRoles> search(String term, Role role, Boolean active, Pageable pageable) {
        String normalizedTerm = term == null ? "" : term.trim();
        Page<AppUser> page = appUserRepository.search(
                normalizedTerm,
                active != null,
                active != null && active,
                role != null,
                role != null ? role : Role.TEACHER,
                pageable);
        return page.map(u -> new UserWithRoles(u, rolesOf(u.getId())));
    }

    /** Like {@link #get} but without throwing, so callers inside a transaction are not marked rollback-only. */
    @Transactional(readOnly = true)
    public java.util.Optional<UserWithRoles> find(UUID userId) {
        return appUserRepository.findById(userId).map(u -> new UserWithRoles(u, rolesOf(userId)));
    }

    @Transactional(readOnly = true)
    public UserWithRoles get(UUID userId) {
        AppUser user = requireUser(userId);
        return new UserWithRoles(user, rolesOf(userId));
    }

    /**
     * Replaces the user's full role set (FR-003). Refuses an empty set, and refuses as a single
     * unit any change that would remove the last active Admin (FR-007/FR-011); on refusal nothing
     * changes.
     */
    @Transactional
    public UserWithRoles updateRoles(UUID actorUserId, UUID targetUserId, Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new EmptyRoleSetException();
        }
        AppUser user = requireUser(targetUserId);
        Set<Role> current = EnumSet.noneOf(Role.class);
        current.addAll(rolesOf(targetUserId));

        Set<Role> removed = EnumSet.copyOf(current);
        removed.removeAll(newRoles);
        Set<Role> added = EnumSet.copyOf(newRoles);
        added.removeAll(current);

        if (removed.contains(Role.ADMIN) && lastAdminGuard.wouldLeaveNoActiveAdmin(targetUserId, removed, false)) {
            throw new LastAdminException();
        }
        for (Role role : removed) {
            roleAssignmentRepository.deleteByUserIdAndRole(targetUserId, role);
            eventPublisher.publishEvent(new UserRoleChanged(
                    UUID.randomUUID(), clock.instant(), actorUserId, targetUserId, role, false));
        }
        for (Role role : added) {
            roleAssignmentRepository.save(new RoleAssignment(targetUserId, role));
            eventPublisher.publishEvent(new UserRoleChanged(
                    UUID.randomUUID(), clock.instant(), actorUserId, targetUserId, role, true));
        }
        return new UserWithRoles(user, rolesOf(targetUserId));
    }

    /**
     * Deactivates a user and immediately revokes every active session of theirs (FR-018); the
     * session check on every request then refuses their still-unexpired access tokens too.
     * Refused if they are the last active Admin (FR-007). A no-op on an already-inactive user.
     */
    @Transactional
    public void deactivateUser(UUID actorUserId, UUID userId) {
        AppUser user = requireUser(userId);
        if (!user.isActive()) {
            return;
        }
        if (lastAdminGuard.wouldLeaveNoActiveAdmin(userId, Set.of(), true)) {
            throw new LastAdminException();
        }
        user.deactivate();
        appUserRepository.save(user);
        sessionService.revokeAllSessionsForUser(userId);
        eventPublisher.publishEvent(
                new AccountActivationChanged(UUID.randomUUID(), clock.instant(), actorUserId, userId, false));
    }

    /** Reactivates a user with exactly the roles they held (FR-005). Never consults the last-admin
     * guard, since it only ever increases the number of active Admins. A no-op if already active. */
    @Transactional
    public void reactivateUser(UUID actorUserId, UUID userId) {
        AppUser user = requireUser(userId);
        if (user.isActive()) {
            return;
        }
        user.reactivate();
        appUserRepository.save(user);
        eventPublisher.publishEvent(
                new AccountActivationChanged(UUID.randomUUID(), clock.instant(), actorUserId, userId, true));
    }

    /**
     * Sets another user's password directly (FR-006): same policy as self-service reset, ends all
     * their sessions and clears any lockout. The password is never logged or published.
     */
    @Transactional
    public void adminResetPassword(UUID actorUserId, UUID targetUserId, String newPassword) {
        AppUser user = requireUser(targetUserId);
        if (PasswordPolicy.isViolatedBy(newPassword, user.getPhone())) {
            throw new PasswordPolicyViolationException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttemptCount(0);
        user.setLockUntil(null);
        appUserRepository.save(user);
        sessionService.revokeAllSessionsForUser(targetUserId);
        eventPublisher.publishEvent(
                new PasswordResetByAdmin(UUID.randomUUID(), clock.instant(), actorUserId, targetUserId));
    }

    private AppUser requireUser(UUID userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new UnknownUserException(userId));
    }

    public List<Role> rolesOf(UUID userId) {
        return roleAssignmentRepository.findByUserId(userId).stream().map(RoleAssignment::getRole).toList();
    }

    public record UserWithRoles(AppUser user, List<Role> roles) {}

    public static class UnknownUserException extends RuntimeException {
        public UnknownUserException(UUID userId) {
            super("No such user: " + userId);
        }
    }

    public static class EmptyRoleSetException extends RuntimeException {
        public EmptyRoleSetException() {
            super("A user must hold at least one role.");
        }
    }

    public static class LastAdminException extends RuntimeException {
        public LastAdminException() {
            super(LastAdminGuard.REFUSAL_MESSAGE);
        }
    }

    public static class PasswordPolicyViolationException extends RuntimeException {
        public PasswordPolicyViolationException() {
            super(PasswordPolicy.VIOLATION_MESSAGE);
        }
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
