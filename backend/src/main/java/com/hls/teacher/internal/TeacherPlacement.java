package com.hls.teacher.internal;

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
 * An interim, dated link from a Teacher to a School (to be replaced by the future Teacher-School
 * contract). In effect on a date when ACTIVE and the date falls between starts_on and ends_on.
 */
@Entity
@Table(name = "teacher_placement")
public class TeacherPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlacementStatus status = PlacementStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TeacherPlacement() {
        // JPA
    }

    public TeacherPlacement(UUID teacherId, UUID schoolId, LocalDate startsOn, Instant now) {
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.startsOn = startsOn;
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

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public PlacementStatus getStatus() {
        return status;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public void setStatus(PlacementStatus status) {
        this.status = status;
    }

    public boolean isInEffectOn(LocalDate date) {
        return status == PlacementStatus.ACTIVE
                && !startsOn.isAfter(date)
                && (endsOn == null || !endsOn.isBefore(date));
    }
}
