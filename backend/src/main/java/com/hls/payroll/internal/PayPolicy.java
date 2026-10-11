package com.hls.payroll.internal;

import com.hls.payroll.api.PayPolicyView;
import com.hls.payroll.api.PayPolicyView.LopDivisor;
import com.hls.payroll.api.PayPolicyView.PayMonth;
import com.hls.payroll.api.PayPolicyView.RoundingRule;
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

/** One dated version of the pay policy. Insert-only: a trigger refuses any change. */
@Entity
@Immutable
@Table(name = "pay_policy")
public class PayPolicy implements Dated {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "lop_divisor", nullable = false)
    private LopDivisor lopDivisor;

    @Column(name = "half_day_fraction", nullable = false, precision = 4, scale = 3)
    private BigDecimal halfDayFraction;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding", nullable = false)
    private RoundingRule rounding;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_month", nullable = false)
    private PayMonth payMonth;

    @Column(name = "note", length = 500)
    private String note;

    /** Null for the version the migration seeds. */
    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PayPolicy() {
        // JPA
    }

    PayPolicy(
            LocalDate effectiveOn,
            LopDivisor lopDivisor,
            BigDecimal halfDayFraction,
            RoundingRule rounding,
            PayMonth payMonth,
            String note,
            UUID recordedBy,
            Instant now) {
        this.effectiveOn = effectiveOn;
        this.lopDivisor = lopDivisor;
        this.halfDayFraction = halfDayFraction;
        this.rounding = rounding;
        this.payMonth = payMonth;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    UUID getId() {
        return id;
    }

    LopDivisor getLopDivisor() {
        return lopDivisor;
    }

    BigDecimal getHalfDayFraction() {
        return halfDayFraction;
    }

    RoundingRule getRounding() {
        return rounding;
    }

    PayMonth getPayMonth() {
        return payMonth;
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

    PayPolicyView toView() {
        return new PayPolicyView(id, effectiveOn, lopDivisor, halfDayFraction, rounding, payMonth);
    }
}
