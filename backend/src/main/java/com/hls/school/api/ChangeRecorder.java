package com.hls.school.api;

import com.hls.audit.api.EntityChanged;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link EntityChanged} for the master-data modules. Call it inside the same transaction
 * as the write so the audit event is only delivered if the change commits.
 */
@Component
public class ChangeRecorder {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public ChangeRecorder(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Publishes one event; does nothing when {@code before} and {@code after} are equal. */
    public void record(
            UUID actorUserId, String entityType, Object entityId, String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        publisher.publishEvent(new EntityChanged(
                UUID.randomUUID(),
                clock.instant(),
                actorUserId,
                entityType,
                String.valueOf(entityId),
                field,
                before == null ? null : String.valueOf(before),
                after == null ? null : String.valueOf(after)));
    }

    /** Records a creation (field {@code created}) or deletion (field {@code deleted}). */
    public void recordLifecycle(UUID actorUserId, String entityType, Object entityId, String field, Object detail) {
        publisher.publishEvent(new EntityChanged(
                UUID.randomUUID(),
                clock.instant(),
                actorUserId,
                entityType,
                String.valueOf(entityId),
                field,
                null,
                detail == null ? null : String.valueOf(detail)));
    }
}
