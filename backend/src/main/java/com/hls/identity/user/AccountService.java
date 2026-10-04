package com.hls.identity.user;

import com.hls.identity.activity.PasswordChanged;
import com.hls.identity.activity.ProfileUpdated;
import com.hls.identity.auth.PasswordPolicy;
import com.hls.identity.session.SessionRepository;
import com.hls.identity.session.SessionStatus;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account settings: a user edits their own profile and changes their own password.
 * The phone number is read-only here because it is the sign-in and one-time-code identity.
 */
@Service
public class AccountService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

    public record ProfileView(
            UUID id, String displayName, String phone, String username, String email, List<String> roles) {}

    /** The current password was wrong, or the new profile values are invalid; maps to 400. */
    public static class InvalidAccountInputException extends RuntimeException {
        public InvalidAccountInputException(String message) {
            super(message);
        }
    }

    private final AppUserRepository users;
    private final UserAdminService userAdminService;
    private final PasswordEncoder passwordEncoder;
    private final SessionRepository sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountService(
            AppUserRepository users,
            UserAdminService userAdminService,
            PasswordEncoder passwordEncoder,
            SessionRepository sessions,
            ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.userAdminService = userAdminService;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId) {
        return view(require(userId));
    }

    @Transactional
    public ProfileView updateProfile(UUID userId, String displayName, String username, String email) {
        AppUser user = require(userId);
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            throw new InvalidAccountInputException("Your name is required.");
        }
        if (name.length() > 160) {
            throw new InvalidAccountInputException("Your name can be at most 160 characters.");
        }
        String newUsername = blankToNull(username);
        if (newUsername != null && !USERNAME.matcher(newUsername).matches()) {
            throw new InvalidAccountInputException(
                    "The username must be 3 to 30 letters, digits, dots, dashes or underscores.");
        }
        String newEmail = blankToNull(email);
        if (newEmail != null && (newEmail.length() > 255 || !EMAIL.matcher(newEmail).matches())) {
            throw new InvalidAccountInputException("Enter a valid email address.");
        }
        if (newUsername != null
                && users.findByUsernameLower(newUsername.toLowerCase(Locale.ROOT))
                        .filter(other -> !other.getId().equals(userId))
                        .isPresent()) {
            throw new UserAdminService.DuplicateUsernameException(newUsername);
        }
        if (newEmail != null
                && users.findByEmail(newEmail)
                        .filter(other -> !other.getId().equals(userId))
                        .isPresent()) {
            throw new UserAdminService.DuplicateEmailException(newEmail);
        }

        List<String> changed = new ArrayList<>();
        if (!name.equals(user.getDisplayName())) {
            user.setDisplayName(name);
            changed.add("name");
        }
        if (!java.util.Objects.equals(newUsername, user.getUsername())) {
            user.setUsername(newUsername);
            changed.add("username");
        }
        if (!java.util.Objects.equals(newEmail, user.getEmail())) {
            user.setEmail(newEmail);
            changed.add("email");
        }
        if (!changed.isEmpty()) {
            users.saveAndFlush(user);
            events.publishEvent(new ProfileUpdated(
                    UUID.randomUUID(), clock.instant(), userId, userId, String.join(", ", changed)));
        }
        return view(user);
    }

    /**
     * Changes the caller's password after checking the current one, and ends every other signed-in
     * session so a stolen session cannot outlive the change.
     */
    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        AppUser user = require(userId);
        if (currentPassword == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidAccountInputException("Your current password is incorrect.");
        }
        if (PasswordPolicy.isViolatedBy(newPassword, user.getPhone())) {
            throw new InvalidAccountInputException(PasswordPolicy.VIOLATION_MESSAGE);
        }
        if (newPassword.equals(currentPassword)) {
            throw new InvalidAccountInputException("Choose a password different from your current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        sessions.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).stream()
                .filter(s -> !s.getId().equals(currentSessionId))
                .forEach(s -> {
                    s.revoke();
                    sessions.save(s);
                });
        events.publishEvent(new PasswordChanged(UUID.randomUUID(), clock.instant(), userId, userId));
    }

    private AppUser require(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserAdminService.UnknownUserException(userId));
    }

    private ProfileView view(AppUser user) {
        return new ProfileView(
                user.getId(),
                user.getDisplayName(),
                user.getPhone(),
                user.getUsername(),
                user.getEmail(),
                userAdminService.rolesOf(user.getId()).stream().map(Enum::name).sorted().toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
