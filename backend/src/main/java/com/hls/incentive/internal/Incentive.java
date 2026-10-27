package com.hls.incentive.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The incentive a Manager earns for one closed School (spec 030). There is at most one per School, ever (unique
 * {@code school_id}). The amount is frozen when it is created. State changes go through the methods below, which hold the
 * state machine; the services add the checks that need other modules.
 */
@Entity
@Table(name = "incentive")
public class Incentive {

    @Id
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "earner_manager_id", nullable = false, updatable = false)
    private UUID earnerManagerId;

    @Column(name = "earner_user_id", nullable = false, updatable = false)
    private UUID earnerUserId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "closure_date", nullable = false, updatable = false)
    private LocalDate closureDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncentiveStatus status;

    @Column(name = "mou_changed", nullable = false)
    private boolean mouChanged;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "approved_by_earner", nullable = false)
    private boolean approvedByEarner;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "run_month")
    private LocalDate runMonth;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incentive() {
        // JPA
    }

    Incentive(
            UUID schoolId,
            UUID contractId,
            UUID earnerManagerId,
            UUID earnerUserId,
            BigDecimal amount,
            LocalDate closureDate,
            UUID zoneId,
            Instant now) {
        this.id = UUID.randomUUID();
        this.schoolId = schoolId;
        this.contractId = contractId;
        this.earnerManagerId = earnerManagerId;
        this.earnerUserId = earnerUserId;
        this.amount = amount;
        this.closureDate = closureDate;
        this.zoneId = zoneId;
        this.status = IncentiveStatus.PENDING_APPROVAL;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------ transitions

    void approve(UUID actor, boolean byEarner, Instant now) {
        require(IncentiveStatus.PENDING_APPROVAL);
        this.status = IncentiveStatus.APPROVED;
        this.decidedBy = actor;
        this.decidedAt = now;
        this.approvedByEarner = byEarner;
        this.rejectReason = null;
        this.updatedAt = now;
    }

    void reject(UUID actor, String reason, Instant now) {
        require(IncentiveStatus.PENDING_APPROVAL);
        this.status = IncentiveStatus.REJECTED;
        this.decidedBy = actor;
        this.decidedAt = now;
        this.approvedByEarner = false;
        this.rejectReason = reason;
        this.updatedAt = now;
    }

    void reopen(Instant now) {
        require(IncentiveStatus.REJECTED);
        this.status = IncentiveStatus.PENDING_APPROVAL;
        this.decidedBy = null;
        this.decidedAt = null;
        this.approvedByEarner = false;
        this.rejectReason = null;
        this.updatedAt = now;
    }

    void hold(String reason, Instant now) {
        require(IncentiveStatus.APPROVED);
        this.status = IncentiveStatus.HELD;
        this.holdReason = reason;
        this.mouChanged = false;
        this.updatedAt = now;
    }

    void release(Instant now) {
        require(IncentiveStatus.HELD);
        this.status = IncentiveStatus.APPROVED;
        this.holdReason = null;
        this.mouChanged = false;
        this.updatedAt = now;
    }

    void flagMouChanged(boolean changed, Instant now) {
        if (this.mouChanged != changed) {
            this.mouChanged = changed;
            this.updatedAt = now;
        }
    }

    void markPaid(UUID runId, LocalDate runMonth, Instant now) {
        this.status = IncentiveStatus.PAID_IN_RUN;
        this.runId = runId;
        this.runMonth = runMonth;
        this.mouChanged = false;
        this.holdReason = null;
        this.updatedAt = now;
    }

    private void require(IncentiveStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Incentive " + id + " is " + status + ", not " + expected);
        }
    }

    // ------------------------------------------------------------------ accessors

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public UUID getEarnerManagerId() {
        return earnerManagerId;
    }

    public UUID getEarnerUserId() {
        return earnerUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getClosureDate() {
        return closureDate;
    }

    public IncentiveStatus getStatus() {
        return status;
    }

    public boolean isMouChanged() {
        return mouChanged;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public boolean isApprovedByEarner() {
        return approvedByEarner;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public UUID getRunId() {
        return runId;
    }

    public LocalDate getRunMonth() {
        return runMonth;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The reference payroll keeps for this incentive's adjustment. */
    public String sourceRef() {
        return "INCENTIVE:" + id;
    }
}
