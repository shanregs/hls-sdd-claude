package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One change of a prospect's owner; insert-only. */
@Entity
@Table(name = "prospect_owner_history")
public class OwnerHistory {

    @Id
    private UUID id;

    @Column(name = "prospect_id", nullable = false)
    private UUID prospectId;

    @Column(name = "from_owner")
    private UUID fromOwner;

    @Column(name = "to_owner", nullable = false)
    private UUID toOwner;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected OwnerHistory() {}

    public OwnerHistory(UUID prospectId, UUID from, UUID to, UUID by, Instant at) {
        this.id = UUID.randomUUID();
        this.prospectId = prospectId;
        this.fromOwner = from;
        this.toOwner = to;
        this.changedBy = by;
        this.changedAt = at;
    }

    public UUID getFromOwner() {
        return fromOwner;
    }

    public UUID getToOwner() {
        return toOwner;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
