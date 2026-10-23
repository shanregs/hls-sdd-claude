package com.hls.transfer.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A transfer or replacement request (spec 027). APPLIED is derived, never stored. */
@Entity
@Table(name = "movement_request")
public class MovementRequest {

    @Id
    private UUID id;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "from_school_id", nullable = false)
    private UUID fromSchoolId;

    @Column(name = "to_school_id", nullable = false)
    private UUID toSchoolId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "replacement_teacher_id")
    private UUID replacementTeacherId;

    @Column(name = "outgoing_destination_school_id")
    private UUID outgoingDestinationSchoolId;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(name = "reason_category", nullable = false)
    private String reasonCategory;

    @Column(name = "note")
    private String note;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "applied_notified_at")
    private Instant appliedNotifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MovementRequest() {
        // JPA
    }

    MovementRequest(String kind, UUID teacherId, UUID fromSchoolId, UUID toSchoolId, UUID requestedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.teacherId = teacherId;
        this.fromSchoolId = fromSchoolId;
        this.toSchoolId = toSchoolId;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        this.status = "PENDING";
    }

    public UUID getId() { return id; }
    public String getKind() { return kind; }
    public UUID getTeacherId() { return teacherId; }
    public UUID getFromSchoolId() { return fromSchoolId; }
    public UUID getToSchoolId() { return toSchoolId; }
    public UUID getPositionId() { return positionId; }
    public UUID getReplacementTeacherId() { return replacementTeacherId; }
    public UUID getOutgoingDestinationSchoolId() { return outgoingDestinationSchoolId; }
    public LocalDate getEffectiveOn() { return effectiveOn; }
    public String getReasonCategory() { return reasonCategory; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getAppliedNotifiedAt() { return appliedNotifiedAt; }
    public long getVersion() { return version; }

    void setPositionId(UUID positionId) { this.positionId = positionId; }
    void setReplacementTeacherId(UUID id) { this.replacementTeacherId = id; }
    void setOutgoingDestinationSchoolId(UUID id) { this.outgoingDestinationSchoolId = id; }
    void setEffectiveOn(LocalDate date) { this.effectiveOn = date; }
    void setReasonCategory(String category) { this.reasonCategory = category; }
    void setNote(String note) { this.note = note; }
    void setAppliedNotifiedAt(Instant at) { this.appliedNotifiedAt = at; }

    void touch(Instant now) { this.updatedAt = now; }

    void decide(String status, UUID by, String reason, Instant now) {
        this.status = status;
        this.decidedBy = by;
        this.decisionReason = reason;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    void setStatus(String status) { this.status = status; }
}
