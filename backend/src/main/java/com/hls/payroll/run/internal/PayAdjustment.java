package com.hls.payroll.run.internal;

import com.hls.payroll.api.PayRules.Kind;
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
import java.util.UUID;

/**
 * A signed amount against a person that waits for a run to take it (spec 013b US4). Never deleted; once applied or
 * withdrawn it never changes (trigger). The three kinds other than {@code CORRECTION} are reserved for later specs and
 * nothing here creates them.
 */
@Entity
@Table(name = "payroll_adjustment")
public class PayAdjustment {

    public enum AdjustmentKind {
        CORRECTION,
        /** Reserved for spec 030. */
        INCENTIVE,
        /** Reserved for spec 015. */
        REIMBURSEMENT,
        /** Reserved for spec 017b. */
        SUBSTITUTE_CREDIT
    }

    public enum Status {
        WAITING,
        APPLIED,
        WITHDRAWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_kind", nullable = false)
    private Kind personKind;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "person_name", nullable = false, length = 200)
    private String personName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AdjustmentKind kind;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "attached_run_id")
    private UUID attachedRunId;

    @Column(name = "applied_run_id")
    private UUID appliedRunId;

    @Column(name = "applied_line_id")
    private UUID appliedLineId;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

    protected PayAdjustment() {
        // JPA
    }

    PayAdjustment(
            Kind personKind,
            UUID personId,
            String personName,
            AdjustmentKind kind,
            BigDecimal amount,
            String reason,
            UUID createdBy,
            Instant now) {
        this.personKind = personKind;
        this.personId = personId;
        this.personName = personName;
        this.kind = kind;
        this.amount = amount;
        this.reason = reason;
        this.status = Status.WAITING;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Kind getPersonKind() {
        return personKind;
    }

    public UUID getPersonId() {
        return personId;
    }

    public String getPersonName() {
        return personName;
    }

    public AdjustmentKind getKind() {
        return kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getAttachedRunId() {
        return attachedRunId;
    }

    public UUID getAppliedRunId() {
        return appliedRunId;
    }

    public UUID getAppliedLineId() {
        return appliedLineId;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getWithdrawnBy() {
        return withdrawnBy;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public String getWithdrawReason() {
        return withdrawReason;
    }

    void attachTo(UUID runId) {
        this.attachedRunId = runId;
    }

    void detach() {
        this.attachedRunId = null;
    }

    void apply(UUID runId, UUID lineId) {
        this.status = Status.APPLIED;
        this.appliedRunId = runId;
        this.appliedLineId = lineId;
        this.attachedRunId = null;
    }

    void withdraw(UUID by, String reason, Instant now) {
        this.status = Status.WITHDRAWN;
        this.withdrawnBy = by;
        this.withdrawnAt = now;
        this.withdrawReason = reason;
        this.attachedRunId = null;
    }
}
