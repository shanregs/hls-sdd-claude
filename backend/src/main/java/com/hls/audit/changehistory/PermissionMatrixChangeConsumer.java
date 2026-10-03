package com.hls.audit.changehistory;

import com.hls.identity.permissions.PermissionMatrixChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code identity}'s {@link PermissionMatrixChanged} (research.md §1), mapping a
 * role→permission grant edit into a generic Change History entry so future modules' own change
 * events fit the same shape without a schema change (data-model.md).
 */
@Component
public class PermissionMatrixChangeConsumer {

    private final ChangeHistoryEntryRepository repository;

    public PermissionMatrixChangeConsumer(ChangeHistoryEntryRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(PermissionMatrixChanged event) {
        if (repository.findBySourceEventId(event.eventId()).isPresent()) {
            return;
        }
        String entityId = event.role() + "." + event.module() + "." + event.action();
        repository.save(new ChangeHistoryEntry(
                event.occurredAt(),
                event.eventId(),
                event.actorUserId(),
                "PERMISSION_MATRIX",
                entityId,
                "granted",
                String.valueOf(event.before()),
                String.valueOf(event.after())));
    }
}
