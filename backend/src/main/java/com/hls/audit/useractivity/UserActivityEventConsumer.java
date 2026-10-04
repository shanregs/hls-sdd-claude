package com.hls.audit.useractivity;

import com.hls.audit.support.ClientOrigin;
import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.activity.AccountLockChanged;
import com.hls.identity.activity.PasswordChanged;
import com.hls.identity.activity.PasswordResetByAdmin;
import com.hls.identity.activity.PasswordResetCompleted;
import com.hls.identity.activity.PasswordResetRequested;
import com.hls.identity.activity.ProfileUpdated;
import com.hls.identity.activity.SessionEnded;
import com.hls.identity.activity.UserCreated;
import com.hls.identity.activity.UserRoleChanged;
import com.hls.identity.clientcontext.ClientContext;
import com.hls.identity.user.Role;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code identity}'s five lifecycle events (research.md §2), one
 * {@code @ApplicationModuleListener} method per type, each dedup-ing on {@code sourceEventId}.
 */
@Component
public class UserActivityEventConsumer {

    private final UserActivityEntryRepository repository;

    public UserActivityEventConsumer(UserActivityEntryRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(PasswordResetRequested event) {
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_RESET_REQUESTED", null, event.clientContext());
    }

    @ApplicationModuleListener
    void on(PasswordResetCompleted event) {
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_RESET_COMPLETED", null, event.clientContext());
    }

    @ApplicationModuleListener
    void on(PasswordChanged event) {
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_CHANGED", null, event.clientContext());
    }

    @ApplicationModuleListener
    void on(ProfileUpdated event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                "PROFILE_UPDATED",
                event.changedFields(), event.clientContext());
    }

    @ApplicationModuleListener
    void on(PasswordChanged event) {
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_CHANGED", null);
    }

    @ApplicationModuleListener
    void on(ProfileUpdated event) {
        save(
                event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                "PROFILE_UPDATED",
                event.changedFields());
    }

    @ApplicationModuleListener
    void on(SessionEnded event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                "SESSION_ENDED",
                "session " + event.sessionId(), event.clientContext());
    }

    @ApplicationModuleListener
    void on(AccountLockChanged event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                event.locked() ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED",
                null, event.clientContext());
    }

    @ApplicationModuleListener
    void on(AccountActivationChanged event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                event.active() ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED",
                null, event.clientContext());
    }

    @ApplicationModuleListener
    void on(UserCreated event) {
        String roles = event.roles().stream().sorted().map(Role::name).collect(Collectors.joining(", "));
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.newUserId(), "USER_CREATED", roles, event.clientContext());
    }

    @ApplicationModuleListener
    void on(UserRoleChanged event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                event.added() ? "ROLE_ASSIGNED" : "ROLE_REMOVED",
                event.role().name(), event.clientContext());
    }

    @ApplicationModuleListener
    void on(PasswordResetByAdmin event) {
        save(event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                "PASSWORD_RESET_BY_ADMIN",
                null, event.clientContext());
    }

    private void save(
            UUID eventId,
            Instant occurredAt,
            UUID actorUserId,
            UUID affectedUserId,
            String action,
            String detail,
            ClientContext clientContext) {
        if (repository.findBySourceEventId(eventId).isPresent()) {
            return;
        }
        repository.save(new UserActivityEntry(
                occurredAt, eventId, actorUserId, affectedUserId, action, detail, ClientOrigin.from(clientContext)));
    }
}
