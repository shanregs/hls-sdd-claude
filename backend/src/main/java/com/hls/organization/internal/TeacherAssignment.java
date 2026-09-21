package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Identical shape and invariants to {@link SchoolAssignment}, with
 * {@code teacherId} in place of {@code schoolId}. Kept as a fully separate
 * table (FR-013) — School-level and Teacher-level accountability are
 * independently queryable and independently mutable by construction, not by
 * a lookup convention on a shared table.
 */
@Entity
@Table(name = "organization_teacher_assignment")
public class TeacherAssignment {

    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

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

    protected TeacherAssignment() {
        // JPA
    }

    public TeacherAssignment(UUID id, UUID teacherId, UUID managerId, Instant effectiveFrom,
                              UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.teacherId = teacherId;
        this.managerId = managerId;
        this.effectiveFrom = effectiveFrom;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
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

    public void end(Instant when) {
        if (this.effectiveTo != null) {
            throw new IllegalStateException("Assignment " + id + " was already ended");
        }
        this.effectiveTo = when;
    }
}
