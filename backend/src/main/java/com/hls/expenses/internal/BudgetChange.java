package com.hls.expenses.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One change of a budget: old and new amount, who and when. Append-only. */
@Entity
@Table(name = "expense_budget_change")
public class BudgetChange {

    @Id
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "old_amount", precision = 14, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal newAmount;

    protected BudgetChange() {
        // JPA
    }

    BudgetChange(UUID budgetId, Instant at, UUID actorId, BigDecimal oldAmount, BigDecimal newAmount) {
        this.id = UUID.randomUUID();
        this.budgetId = budgetId;
        this.at = at;
        this.actorId = actorId;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public Instant getAt() {
        return at;
    }

    public UUID getActorId() {
        return actorId;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }
}
