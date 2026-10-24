package com.hls.recruitment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A drive date moved to another date (spec 035); the earlier date stays on record; insert-only (database trigger). */
@Entity
@Table(name = "drive_date_change")
public class DriveDateChange {

    @Id
    private UUID id;

    @Column(name = "drive_id", nullable = false)
    private UUID driveId;

    @Column(name = "old_date", nullable = false)
    private LocalDate oldDate;

    @Column(name = "new_date", nullable = false)
    private LocalDate newDate;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected DriveDateChange() {}

    public DriveDateChange(UUID driveId, LocalDate oldDate, LocalDate newDate, UUID changedBy, Instant changedAt) {
        this.id = UUID.randomUUID();
        this.driveId = driveId;
        this.oldDate = oldDate;
        this.newDate = newDate;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getDriveId() {
        return driveId;
    }

    public LocalDate getOldDate() {
        return oldDate;
    }

    public LocalDate getNewDate() {
        return newDate;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
