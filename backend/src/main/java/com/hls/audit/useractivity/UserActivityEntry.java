package com.hls.audit.useractivity;

import com.hls.audit.support.ClientOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable User Activity entry (data-model.md): one account lifecycle action. Never updated
 * or deleted; a correction is a new entry (FR-004).
 */
@Entity
@Table(name = "user_activity_entry")
public class UserActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "affected_user_id", nullable = false)
    private UUID affectedUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "detail")
    private String detail;

    /** Where the action came from: client, app version and device location (spec 018). */
    @Embedded
    private ClientOrigin origin = ClientOrigin.from(null);

    protected UserActivityEntry() {
        // JPA
    }

    public UserActivityEntry(
            Instant occurredAt,
            UUID sourceEventId,
            UUID actorUserId,
            UUID affectedUserId,
            String action,
            String detail) {
        this.occurredAt = occurredAt;
        this.sourceEventId = sourceEventId;
        this.actorUserId = actorUserId;
        this.affectedUserId = affectedUserId;
        this.action = action;
        this.detail = detail;
    }

    public UserActivityEntry(
            Instant occurredAt,
            UUID sourceEventId,
            UUID actorUserId,
            UUID affectedUserId,
            String action,
            String detail,
            ClientOrigin origin) {
        this(occurredAt, sourceEventId, actorUserId, affectedUserId, action, detail);
        this.origin = origin;
    }

    public ClientOrigin getOrigin() {
        return origin;
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

    public UUID getAffectedUserId() {
        return affectedUserId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }
}
