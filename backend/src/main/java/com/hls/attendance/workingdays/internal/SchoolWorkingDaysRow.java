package com.hls.attendance.workingdays.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The working days one School counts in one calendar month. Unique per School, year and month. */
@Entity
@Table(name = "school_working_days")
public class SchoolWorkingDaysRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "month", nullable = false)
    private int month;

    @Column(name = "working_days", nullable = false, precision = 3, scale = 1)
    private BigDecimal workingDays;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SchoolWorkingDaysRow() {
        // JPA
    }

    public SchoolWorkingDaysRow(UUID schoolId, int year, int month, BigDecimal workingDays, UUID actor, Instant now) {
        this.schoolId = schoolId;
        this.year = year;
        this.month = month;
        this.workingDays = workingDays;
        this.createdBy = actor;
        this.createdAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void change(BigDecimal workingDays, UUID actor, Instant now) {
        this.workingDays = workingDays;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public Long getVersion() {
        return version;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
