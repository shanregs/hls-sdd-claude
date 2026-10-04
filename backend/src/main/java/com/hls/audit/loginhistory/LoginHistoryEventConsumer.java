package com.hls.audit.loginhistory;

import com.hls.audit.support.ClientOrigin;
import com.hls.identity.loginhistory.LoginHistoryRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code identity}'s {@link LoginHistoryRecorded} (research.md §1): durable,
 * at-least-once delivery via Spring Modulith's event publication registry. Dedups on
 * {@code sourceEventId} so redelivery after a transient failure is a no-op, not a duplicate row
 * (data-model.md's validation rule).
 */
@Component
public class LoginHistoryEventConsumer {

    private final LoginHistoryEntryRepository repository;

    public LoginHistoryEventConsumer(LoginHistoryEntryRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(LoginHistoryRecorded event) {
        if (repository.findBySourceEventId(event.eventId()).isPresent()) {
            return;
        }
        repository.save(new LoginHistoryEntry(
                event.occurredAt(),
                event.eventId(),
                event.userId(),
                event.phoneMasked(),
                event.method().name(),
                event.eventType().name(),
                event.outcome(),
                ClientOrigin.from(event.clientContext()),
                event.clientContext().deviceRooted()));
    }
}
