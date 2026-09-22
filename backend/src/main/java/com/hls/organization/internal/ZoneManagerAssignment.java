package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-001/FR-002. One period during which a Manager was (or is) assigned to
 * cover a Zone. {@code effectiveTo == null} means this is a <em>current</em>
 * assignment — unlike {@link SchoolAssignment}/{@link TeacherAssignment},
 * more than one can be currently open for the same Zone at once (different
 * Managers), enforced as unique per {@code (zoneId, managerId)} by a partial
 * DB index (research.md §2), not here. Rows are never updated except to set
 * {@code effectiveTo} exactly once, and never deleted (Constitution
 * Principle I).
 */
@Entity
@Table(name = "organization_zone_manager_assignment")
public class ZoneManagerAssignment {

    @Id
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

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

    protected ZoneManagerAssignment() {
        // JPA
    }

    public ZoneManagerAssignment(UUID id, UUID zoneId, UUID managerId, Instant effectiveFrom,
                                  UUID assignedBy, Instant assignedAt) {
        this.id = id;
        this.zoneId = zoneId;
        this.managerId = managerId;
        this.effectiveFrom = effectiveFrom;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
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

    /** Ends this assignment. Settable exactly once. */
    public void end(Instant when) {
        if (this.effectiveTo != null) {
            throw new IllegalStateException("Zone-Manager assignment " + id + " was already ended");
        }
        this.effectiveTo = when;
    }
}
