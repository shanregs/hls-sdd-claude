package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One stage move or review decision on a prospect; insert-only. */
@Entity
@Table(name = "prospect_stage_history")
public class StageHistory {

    public static final String STAGE = "STAGE";
    public static final String REVIEW_APPROVED = "REVIEW_APPROVED";
    public static final String REVIEW_REJECTED = "REVIEW_REJECTED";

    @Id
    private UUID id;

    @Column(name = "prospect_id", nullable = false)
    private UUID prospectId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage")
    private ProspectStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false)
    private ProspectStage toStage;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected StageHistory() {}

    public StageHistory(
            UUID prospectId, String kind, ProspectStage from, ProspectStage to, String reason, UUID by, Instant at) {
        this.id = UUID.randomUUID();
        this.prospectId = prospectId;
        this.kind = kind;
        this.fromStage = from;
        this.toStage = to;
        this.reason = reason;
        this.changedBy = by;
        this.changedAt = at;
    }

    public UUID getProspectId() {
        return prospectId;
    }

    public String getKind() {
        return kind;
    }

    public ProspectStage getFromStage() {
        return fromStage;
    }

    public ProspectStage getToStage() {
        return toStage;
    }

    public String getReason() {
        return reason;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
