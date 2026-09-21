package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-001/FR-004/FR-005/FR-006. One period during which a Manager was (or is)
 * accountable for a School. {@code effectiveTo == null} means this is the
 * <em>current</em> assignment (data-model.md) — enforced as unique per
 * {@code schoolId} by a partial DB index (research.md §1), not here.
 * Rows are never updated except to set {@code effectiveTo} exactly once, and
 * never deleted (Constitution Principle I).
 */
@Entity
@Table(name = "organization_school_assignment")
public class SchoolAssignment {

    @Id
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected SchoolAssignment() {
        // JPA
    }

    public SchoolAssignment(UUID id, UUID schoolId, UUID managerId, Instant effectiveFrom,
                             UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.schoolId = schoolId;
        this.managerId = managerId;
        this.effectiveFrom = effectiveFrom;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isCurrent() {
        return effectiveTo == null;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    /** FR-004/edge case 1: ends this assignment, with or without a replacement following. Settable exactly once. */
    public void end(Instant when) {
        if (this.effectiveTo != null) {
            throw new IllegalStateException("Assignment " + id + " was already ended");
        }
        this.effectiveTo = when;
    }
}
