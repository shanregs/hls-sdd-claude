package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One outcome decision on a candidate; insert-only, so earlier decisions are never lost. */
@Entity
@Table(name = "candidate_outcome_history")
public class CandidateOutcomeHistory {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private Outcome outcome;

    @Column(name = "note")
    private String note;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected CandidateOutcomeHistory() {}

    public CandidateOutcomeHistory(UUID candidateId, Outcome outcome, String note, UUID changedBy, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.candidateId = candidateId;
        this.outcome = outcome;
        this.note = note;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getNote() {
        return note;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
