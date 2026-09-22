package com.hls.audit.internal;

import com.hls.audit.api.dto.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-002/FR-003/FR-005/FR-006. A single, permanent record of one change to one
 * financial- or attendance-affecting record from another module (data-model.md).
 * Every field is set exactly once, at construction — there is no setter for
 * any of them, and {@link AuditEntryRepository} exposes no update/delete
 * method at all (research.md §4), so nothing about this entity is ever
 * mutated or removed after insert.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    private UUID id;

    /**
     * Database-generated (BIGSERIAL), never set by application code — the
     * definitive ordering key (research.md §5). Populated whenever an entity
     * is loaded via a query; left at its Java default (0) on a
     * not-yet-persisted instance, which is never read before that instance is
     * saved and re-queried.
     */
    @Column(name = "sequence_no", insertable = false, updatable = false)
    private long sequenceNo;

    @Column(name = "source_module", nullable = false)
    private String sourceModule;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id")
    private String requestId;

    protected AuditEntry() {
        // JPA
    }

    public AuditEntry(UUID id, String sourceModule, String entityType, String entityId, AuditAction action,
                       String summary, String beforeValue, String afterValue, UUID actorUserId, String actorRole,
                       Instant occurredAt, String requestId) {
        this.id = id;
        this.sourceModule = sourceModule;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.summary = summary;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getSummary() {
        return summary;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRequestId() {
        return requestId;
    }
}
