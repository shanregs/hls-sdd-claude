package com.hls.payroll.run.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** The frozen copy of an adjustment inside a line (spec 013b data-model.md). */
@Entity
@Table(name = "payroll_run_line_adjustment")
public class LineAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(name = "adjustment_id", nullable = false)
    private UUID adjustmentId;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    protected LineAdjustment() {
        // JPA
    }

    LineAdjustment(UUID lineId, UUID adjustmentId, String kind, BigDecimal amount, String reason) {
        this.lineId = lineId;
        this.adjustmentId = adjustmentId;
        this.kind = kind;
        this.amount = amount;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLineId() {
        return lineId;
    }

    public UUID getAdjustmentId() {
        return adjustmentId;
    }

    public String getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }
}
