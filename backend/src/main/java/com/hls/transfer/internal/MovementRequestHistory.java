package com.hls.transfer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One line of a request history (append only: a database trigger refuses updates and deletes). */
@Entity
@Table(name = "movement_request_history")
public class MovementRequestHistory {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected MovementRequestHistory() {
        // JPA
    }

    MovementRequestHistory(UUID requestId, String action, String fromStatus, String toStatus, UUID actorUserId, String note, Instant at) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.note = note;
        this.at = at;
    }

    public UUID getRequestId() { return requestId; }
    public String getAction() { return action; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public String getNote() { return note; }
    public Instant getAt() { return at; }
}
