package com.hls.audit.useractivity;

import com.hls.identity.activity.AccountActivationChanged;
import com.hls.identity.activity.AccountLockChanged;
import com.hls.identity.activity.PasswordResetCompleted;
import com.hls.identity.activity.PasswordResetRequested;
import com.hls.identity.activity.SessionEnded;
import java.time.Instant;
import java.util.UUID;
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
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_RESET_REQUESTED", null);
    }

    @ApplicationModuleListener
    void on(PasswordResetCompleted event) {
        save(event.eventId(), event.occurredAt(), event.actorUserId(), event.affectedUserId(), "PASSWORD_RESET_COMPLETED", null);
    }

    @ApplicationModuleListener
    void on(SessionEnded event) {
        save(
                event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                "SESSION_ENDED",
                "session " + event.sessionId());
    }

    @ApplicationModuleListener
    void on(AccountLockChanged event) {
        save(
                event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                event.locked() ? "ACCOUNT_LOCKED" : "ACCOUNT_UNLOCKED",
                null);
    }

    @ApplicationModuleListener
    void on(AccountActivationChanged event) {
        save(
                event.eventId(),
                event.occurredAt(),
                event.actorUserId(),
                event.affectedUserId(),
                event.active() ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED",
                null);
    }

    private void save(UUID eventId, Instant occurredAt, UUID actorUserId, UUID affectedUserId, String action, String detail) {
        if (repository.findBySourceEventId(eventId).isPresent()) {
            return;
        }
        repository.save(new UserActivityEntry(occurredAt, eventId, actorUserId, affectedUserId, action, detail));
    }
}
