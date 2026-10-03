package com.hls.teacher.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One dated salary amount for a Teacher. Append-only: never updated or deleted (spec 005 FR-017). */
@Entity
@Table(name = "teacher_salary_history")
public class SalaryHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SalaryHistoryEntry() {
        // JPA
    }

    public SalaryHistoryEntry(UUID teacherId, BigDecimal amount, LocalDate effectiveOn, UUID recordedBy, Instant now) {
        this.teacherId = teacherId;
        this.amount = amount;
        this.effectiveOn = effectiveOn;
        this.recordedBy = recordedBy;
        this.createdAt = now;
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

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
