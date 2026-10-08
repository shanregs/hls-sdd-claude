package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A designation a Manager holds from an effective date. Insert-only: the database refuses updates and deletes. */
@Entity
@Table(name = "manager_designation")
public class ManagerDesignation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Insertion order, the tie-break when two rows share an effective date (read-only, set by the database). */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "manager_id", nullable = false, updatable = false)
    private UUID managerId;

    @Column(name = "designation_id", nullable = false, updatable = false)
    private UUID designationId;

    @Column(name = "effective_on", nullable = false, updatable = false)
    private LocalDate effectiveOn;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ManagerDesignation() {
        // JPA
    }

    public ManagerDesignation(
            UUID managerId, UUID designationId, LocalDate effectiveOn, UUID recordedBy, Instant recordedAt) {
        this.managerId = managerId;
        this.designationId = designationId;
        this.effectiveOn = effectiveOn;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public UUID getDesignationId() {
        return designationId;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
