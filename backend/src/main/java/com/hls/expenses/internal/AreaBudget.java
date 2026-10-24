package com.hls.expenses.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The monthly budget of one area: a Zone, or Head office when {@code zoneId} is null (spec 015 FR-021). */
@Entity
@Table(name = "expense_area_budget")
public class AreaBudget {

    @Id
    private UUID id;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    protected AreaBudget() {
        // JPA
    }

    AreaBudget(UUID zoneId, LocalDate month, BigDecimal amount, UUID by, Instant now) {
        this.id = UUID.randomUUID();
        this.zoneId = zoneId;
        this.month = month;
        this.amount = amount;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    void change(BigDecimal amount, UUID by, Instant now) {
        this.amount = amount;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public LocalDate getMonth() {
        return month;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
