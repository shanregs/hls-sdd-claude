package com.hls.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-022. An Admin-configured, organization-wide calendar of non-working
 * dates, applied automatically to every Teacher's rollup/grid unless
 * overridden by an explicit mark (research.md §10). {@code date}/{@code label}
 * are immutable after creation — a correction is a new entry plus
 * deactivating the old one, never an edit in place.
 */
@Entity
@Table(name = "attendance_non_working_date")
public class AttendanceNonWorkingDate {

    @Id
    private UUID id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected AttendanceNonWorkingDate() {
        // JPA
    }

    public AttendanceNonWorkingDate(UUID id, LocalDate date, String label, boolean active, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.date = date;
        this.label = label;
        this.active = active;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void deactivate() {
        this.active = false;
    }
}
