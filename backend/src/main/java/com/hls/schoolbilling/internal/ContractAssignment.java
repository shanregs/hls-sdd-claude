package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A dated link from a Teacher to a School and, once mapped, to a position of its contract. It replaces the
 * interim {@code TeacherPlacement} of spec 005 and keeps its rules: in effect on a date when ACTIVE and the
 * date falls between starts_on and ends_on; rows are never overwritten.
 */
@Entity
@Table(name = "contract_assignment")
public class ContractAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContractAssignment() {
        // JPA
    }

    public ContractAssignment(
            UUID teacherId, UUID schoolId, UUID positionId, LocalDate startsOn, UUID createdBy, Instant now) {
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.positionId = positionId;
        this.startsOn = startsOn;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getPositionId() {
        return positionId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public void setPositionId(UUID positionId) {
        this.positionId = positionId;
    }

    public boolean isInEffectOn(LocalDate date) {
        return status == AssignmentStatus.ACTIVE
                && !startsOn.isAfter(date)
                && (endsOn == null || !endsOn.isBefore(date));
    }
}
