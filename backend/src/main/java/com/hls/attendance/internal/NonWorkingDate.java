package com.hls.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** An organization-wide non-working date such as a declared holiday. */
@Entity
@Table(name = "attendance_non_working_date")
public class NonWorkingDate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected NonWorkingDate() {
        // JPA
    }

    public NonWorkingDate(LocalDate onDate, String description, UUID createdBy) {
        this.onDate = onDate;
        this.description = description;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getOnDate() {
        return onDate;
    }

    public String getDescription() {
        return description;
    }
}
