package com.hls.payroll.internal;

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
import org.hibernate.annotations.Immutable;

/** A fixed monthly salary for one designation from an effective date. Insert-only: a trigger refuses any change. */
@Entity
@Immutable
@Table(name = "designation_salary")
public class DesignationSalary implements Dated {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "designation_id", nullable = false)
    private UUID designationId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected DesignationSalary() {
        // JPA
    }

    DesignationSalary(
            UUID designationId, BigDecimal amount, LocalDate effectiveOn, String note, UUID recordedBy, Instant now) {
        this.designationId = designationId;
        this.amount = amount;
        this.effectiveOn = effectiveOn;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getDesignationId() {
        return designationId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getNote() {
        return note;
    }

    UUID getRecordedBy() {
        return recordedBy;
    }

    @Override
    public LocalDate effectiveOn() {
        return effectiveOn;
    }

    @Override
    public Instant recordedAt() {
        return recordedAt;
    }
}
