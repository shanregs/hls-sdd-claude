package com.hls.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** FR-013. One append-only row per reopen-and-correct cycle for a teacher-month. Never edited or deleted. */
@Entity
@Table(name = "attendance_reopen_record")
public class AttendanceReopenRecord {

    @Id
    private UUID id;

    @Column(name = "lock_id", nullable = false)
    private UUID lockId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "reopened_at", nullable = false)
    private Instant reopenedAt;

    @Column(name = "reopened_by", nullable = false)
    private UUID reopenedBy;

    @Column(name = "relocked_at")
    private Instant relockedAt;

    @Column(name = "relocked_by")
    private UUID relockedBy;

    protected AttendanceReopenRecord() {
        // JPA
    }

    public AttendanceReopenRecord(UUID id, UUID lockId, String reason, Instant reopenedAt, UUID reopenedBy) {
        this.id = id;
        this.lockId = lockId;
        this.reason = reason;
        this.reopenedAt = reopenedAt;
        this.reopenedBy = reopenedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLockId() {
        return lockId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public UUID getReopenedBy() {
        return reopenedBy;
    }

    public Instant getRelockedAt() {
        return relockedAt;
    }

    public UUID getRelockedBy() {
        return relockedBy;
    }

    public void relock(Instant relockedAt, UUID relockedBy) {
        this.relockedAt = relockedAt;
        this.relockedBy = relockedBy;
    }
}
