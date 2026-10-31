package com.hls.incentive.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One row of an incentive's history. Append-only: the database refuses an update or delete (spec 030). */
@Entity
@Table(name = "incentive_event")
public class IncentiveEvent {

    @Id
    private UUID id;

    /** Insertion order, so entries written in the same instant keep their order. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "incentive_id", nullable = false, updatable = false)
    private UUID incentiveId;

    @Column(name = "kind", nullable = false, updatable = false)
    private String kind;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncentiveEvent() {
        // JPA
    }

    IncentiveEvent(UUID incentiveId, String kind, UUID actorId, String detail, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.incentiveId = incentiveId;
        this.kind = kind;
        this.actorId = actorId;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getIncentiveId() {
        return incentiveId;
    }

    public String getKind() {
        return kind;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
