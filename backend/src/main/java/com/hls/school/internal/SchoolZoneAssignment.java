package com.hls.school.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-003/FR-004/FR-005. One period during which a School belonged to a
 * particular Zone. Identical shape and invariants to
 * {@code organization.internal.SchoolAssignment} — {@code effectiveTo == null}
 * means this is the <em>current</em> Zone (data-model.md), enforced as unique
 * per {@code schoolId} by a partial DB index (research.md §1), not here. Rows
 * are never updated except to set {@code effectiveTo} exactly once, and never
 * deleted (Constitution Principle I).
 */
@Entity
@Table(name = "school_zone_assignment")
public class SchoolZoneAssignment {

    @Id
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected SchoolZoneAssignment() {
        // JPA
    }

    public SchoolZoneAssignment(UUID id, UUID schoolId, UUID zoneId, Instant effectiveFrom,
                                 UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.schoolId = schoolId;
        this.zoneId = zoneId;
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

    public UUID getZoneId() {
        return zoneId;
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

    /** Ends this assignment. Settable exactly once. */
    public void end(Instant when) {
        if (this.effectiveTo != null) {
            throw new IllegalStateException("School-Zone assignment " + id + " was already ended");
        }
        this.effectiveTo = when;
    }
}
