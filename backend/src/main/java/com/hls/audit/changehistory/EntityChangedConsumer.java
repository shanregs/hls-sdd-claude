package com.hls.audit.changehistory;

import com.hls.audit.api.EntityChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Appends every {@link EntityChanged} published by the master-data modules to Change History,
 * deduplicating on the event id (spec 005 research.md section 1).
 */
@Component
public class EntityChangedConsumer {

    private final ChangeHistoryEntryRepository repository;

    public EntityChangedConsumer(ChangeHistoryEntryRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(EntityChanged event) {
        if (repository.findBySourceEventId(event.eventId()).isPresent()) {
            return;
        }
        repository.save(new ChangeHistoryEntry(
                event.occurredAt(),
                event.eventId(),
                event.actorUserId(),
                event.entityType(),
                event.entityId(),
                event.field(),
                event.beforeValue(),
                event.afterValue()));
    }
}
