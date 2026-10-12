package com.hls.attendance.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** The single row of settings: the cut-off after which a missing check-in is reported, and the late time. */
@Entity
@Table(name = "manager_attendance_setting")
public class ManagerAttendanceSettings {

    public static final int ROW = 1;

    @Id
    @Column(name = "id")
    private int id = ROW;

    @Column(name = "missed_cutoff", nullable = false)
    private LocalTime missedCutoff;

    @Column(name = "late_after", nullable = false)
    private LocalTime lateAfter;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ManagerAttendanceSettings() {
        // JPA
    }

    public void change(LocalTime missedCutoff, LocalTime lateAfter, UUID by, Instant at) {
        this.missedCutoff = missedCutoff;
        this.lateAfter = lateAfter;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public LocalTime getMissedCutoff() {
        return missedCutoff;
    }

    public LocalTime getLateAfter() {
        return lateAfter;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
