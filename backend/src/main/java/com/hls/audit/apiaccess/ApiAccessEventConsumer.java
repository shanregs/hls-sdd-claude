package com.hls.audit.apiaccess;

import com.hls.audit.support.ClientOrigin;
import com.hls.identity.clientcontext.ApiAccessRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code identity}'s {@link ApiAccessRecorded}: durable, at-least-once delivery via the
 * Spring Modulith event publication registry, deduplicated on {@code sourceEventId} so a redelivery
 * after a transient failure is a no-op, not a duplicate row.
 */
@Component
public class ApiAccessEventConsumer {

    private final ApiAccessEntryRepository repository;

    public ApiAccessEventConsumer(ApiAccessEntryRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(ApiAccessRecorded event) {
        if (repository.findBySourceEventId(event.eventId()).isPresent()) {
            return;
        }
        repository.save(new ApiAccessEntry(
                event.occurredAt(),
                event.eventId(),
                event.userId(),
                event.sessionId(),
                event.httpMethod(),
                event.routeTemplate(),
                event.statusCode(),
                ClientOrigin.from(event.clientContext())));
    }
}
