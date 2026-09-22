package com.hls.teacher.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * FR-001/FR-003. One recorded salary amount for a teacher, effective from a
 * given calendar date. Never edited or deleted once recorded — enforced by
 * {@link TeacherSalaryHistoryRepository} exposing no delete/update-by-id
 * method at all (FR-004).
 */
@Entity
@Table(name = "teacher_salary_history")
public class TeacherSalaryHistory {

    @Id
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected TeacherSalaryHistory() {
        // JPA
    }

    public TeacherSalaryHistory(UUID id, UUID teacherId, BigDecimal amount, LocalDate effectiveFrom, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.teacherId = teacherId;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
