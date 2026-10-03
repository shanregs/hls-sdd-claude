package com.hls.audit.changehistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable Change History entry (data-model.md): one field-level change to a governed record.
 * Never updated or deleted; a correction is a new entry (FR-004).
 */
@Entity
@Table(name = "change_history_entry")
public class ChangeHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "field", nullable = false)
    private String field;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    protected ChangeHistoryEntry() {
        // JPA
    }

    public ChangeHistoryEntry(
            Instant occurredAt,
            UUID sourceEventId,
            UUID actorUserId,
            String entityType,
            String entityId,
            String field,
            String beforeValue,
            String afterValue) {
        this.occurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.actorUserId = actorUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.field = field;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getField() {
        return field;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }
}
