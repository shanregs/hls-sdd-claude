package com.hls.organization.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A Manager record for a user holding the Manager role; name and phone are read from identity. */
@Entity
@Table(name = "manager")
public class Manager {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Manager() {
        // JPA
    }

    public Manager(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setEmployment(String employeeId, LocalDate joiningDate, LocalDate exitDate, Instant now) {
        this.employeeId = employeeId;
        this.joiningDate = joiningDate;
        this.exitDate = exitDate;
        this.updatedAt = now;
    }

    /** Marks the record changed so its version moves on (used when its Zone set changes). */
    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
