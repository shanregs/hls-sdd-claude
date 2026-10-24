package com.hls.expenses.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of a claim's history. Append-only: the database refuses an update or delete. */
@Entity
@Table(name = "expense_claim_event")
public class ClaimEvent {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "event", nullable = false)
    private String event;

    @Column(name = "reason")
    private String reason;

    @Column(name = "detail")
    private String detail;

    protected ClaimEvent() {
        // JPA
    }

    ClaimEvent(UUID claimId, Instant at, UUID actorId, String event, String reason, String detail) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.at = at;
        this.actorId = actorId;
        this.event = event;
        this.reason = reason;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public Instant getAt() {
        return at;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getEvent() {
        return event;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
