package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The approval state of one MoU (spec 031): one row per contract that has real terms. */
@Entity
@Table(name = "mou_approval")
public class MouApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MouApprovalStatus status;

    @Column(name = "round", nullable = false)
    private int round;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MouApproval() {
        // JPA
    }

    public static MouApproval draft(UUID contractId, Instant now) {
        MouApproval a = new MouApproval();
        a.contractId = contractId;
        a.status = MouApprovalStatus.DRAFT;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }

    /** Starts a new round: first submission, resubmission after a rejection, or reset by a replaced document. */
    public void submit(UUID by, Instant now) {
        this.status = MouApprovalStatus.PENDING;
        this.round = round + 1;
        this.submittedBy = by;
        this.submittedAt = now;
        this.decidedBy = null;
        this.decidedAt = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    public void approve(UUID by, Instant now) {
        this.status = MouApprovalStatus.APPROVED;
        this.decidedBy = by;
        this.decidedAt = now;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    public void reject(UUID by, String reason, Instant now) {
        this.status = MouApprovalStatus.REJECTED;
        this.decidedBy = by;
        this.decidedAt = now;
        this.decisionReason = reason;
        this.updatedAt = now;
    }

    /** Moves the timestamp so the row is written and its version bumps (a term edit), telling a decider on an old screen. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public MouApprovalStatus getStatus() {
        return status;
    }

    public int getRound() {
        return round;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public long getVersion() {
        return version;
    }

    /** True when the decider is also the submitter of the current round. */
    public boolean isSelfDecided() {
        return decidedBy != null && decidedBy.equals(submittedBy);
    }
}
