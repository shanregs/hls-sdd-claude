package com.hls.payroll.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A staff member's joining and exit dates (spec 013a). Dates, not pay, so the row can change; each change is audited. */
@Entity
@Table(name = "staff_employment")
public class StaffEmployment {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffEmployment() {
        // JPA
    }

    StaffEmployment(UUID userId, LocalDate joiningDate, LocalDate exitDate, UUID updatedBy, Instant now) {
        this.userId = userId;
        this.joiningDate = joiningDate;
        this.exitDate = exitDate;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    UUID getUserId() {
        return userId;
    }

    LocalDate getJoiningDate() {
        return joiningDate;
    }

    LocalDate getExitDate() {
        return exitDate;
    }

    void change(LocalDate joiningDate, LocalDate exitDate, UUID updatedBy, Instant now) {
        this.joiningDate = joiningDate;
        this.exitDate = exitDate;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }
}
