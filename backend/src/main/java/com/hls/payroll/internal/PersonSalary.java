package com.hls.payroll.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * A Manager's or staff member's own monthly salary from an effective date (spec 013a amendment 2026-10-11). Insert-only:
 * a trigger refuses any change. A Teacher's salary is not here; it is spec 005's record.
 */
@Entity
@Immutable
@Table(name = "person_salary")
public class PersonSalary implements Dated {

    /** Whose salary it is: a Manager (keyed by Manager id) or a staff member (keyed by user id). */
    public enum Kind {
        MANAGER,
        STAFF
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_kind", nullable = false)
    private Kind personKind;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

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

    protected PersonSalary() {
        // JPA
    }

    PersonSalary(
            Kind personKind,
            UUID personId,
            BigDecimal amount,
            LocalDate effectiveOn,
            String note,
            UUID recordedBy,
            Instant now) {
        this.personKind = personKind;
        this.personId = personId;
        this.amount = amount;
        this.effectiveOn = effectiveOn;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    UUID getId() {
        return id;
    }

    Kind getPersonKind() {
        return personKind;
    }

    UUID getPersonId() {
        return personId;
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
