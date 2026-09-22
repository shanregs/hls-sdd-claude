package com.hls.teacher.internal;

import com.hls.teacher.api.dto.TeacherStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-001-005. One record per teacher. Never deleted — enforced by
 * {@link TeacherProfileRepository} exposing no delete method at all
 * (FR-004). No bank-detail fields (out of scope, spec.md Assumptions). No
 * salary field — salary is tracked as history in {@link TeacherSalaryHistory}
 * (specs/009-teacher-salary-history, research.md §2).
 */
@Entity
@Table(name = "teacher_profile")
public class TeacherProfile {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TeacherStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected TeacherProfile() {
        // JPA
    }

    public TeacherProfile(UUID id, String name, String phone, String email, TeacherStatus status, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public TeacherStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    /** FR-002. Only non-null arguments are applied — the caller passes only what changed. */
    public void updateContact(String name, String phone, String email) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (email != null) {
            this.email = email;
        }
    }

    /** FR-003. Every transition allowed, including reactivation after EXITED (data-model.md). */
    public void changeStatus(TeacherStatus newStatus) {
        this.status = newStatus;
    }
}
