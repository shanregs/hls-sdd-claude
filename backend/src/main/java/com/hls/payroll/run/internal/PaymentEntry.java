package com.hls.payroll.run.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One manual payment record of a line (spec 013b US7). Append-only: the current state of a line is its newest entry and
 * a correction is a new entry, so the earlier details stay.
 */
@Entity
@Table(name = "payroll_payment_entry")
public class PaymentEntry {

    public enum Mode {
        BANK_TRANSFER,
        CASH,
        UPI,
        CHEQUE,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private Mode mode;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "entered_by", nullable = false)
    private UUID enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    protected PaymentEntry() {
        // JPA
    }

    PaymentEntry(UUID lineId, boolean paid, LocalDate paidOn, Mode mode, String reference, UUID enteredBy, Instant now) {
        this.lineId = lineId;
        this.paid = paid;
        this.paidOn = paidOn;
        this.mode = mode;
        this.reference = reference;
        this.enteredBy = enteredBy;
        this.enteredAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLineId() {
        return lineId;
    }

    public boolean isPaid() {
        return paid;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Mode getMode() {
        return mode;
    }

    public String getReference() {
        return reference;
    }

    public UUID getEnteredBy() {
        return enteredBy;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }
}
