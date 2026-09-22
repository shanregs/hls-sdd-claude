package com.hls.attendance.internal;

import com.hls.attendance.api.dto.LockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** FR-011-FR-014. The locked/unlocked state of one Teacher's attendance for one calendar month. */
@Entity
@Table(name = "attendance_teacher_month_lock")
public class AttendanceTeacherMonthLock {

    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "period", nullable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LockStatus status;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false)
    private UUID lockedBy;

    protected AttendanceTeacherMonthLock() {
        // JPA
    }

    public AttendanceTeacherMonthLock(UUID id, UUID teacherId, String period, LockStatus status, Instant lockedAt, UUID lockedBy) {
        this.id = id;
        this.teacherId = teacherId;
        this.period = period;
        this.status = status;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getPeriod() {
        return period;
    }

    public LockStatus getStatus() {
        return status;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    /** FR-011. Used for both the first lock and every subsequent re-lock. */
    public void lock(Instant lockedAt, UUID lockedBy) {
        this.status = LockStatus.LOCKED;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    /** FR-013. */
    public void reopen() {
        this.status = LockStatus.REOPENED;
    }
}
