package com.hls.payroll.run.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One pay run (spec 013b data-model.md). The month is stored as its first day. A trigger makes an approved run final;
 * the services are the first line of defence and this entity is only ever changed through them.
 */
@Entity
@Table(name = "payroll_run")
public class PayRun {

    public enum Kind {
        PRIMARY,
        SUPPLEMENTARY
    }

    public enum Status {
        DRAFT,
        AWAITING_APPROVAL,
        APPROVED,
        CANCELLED
    }

    /** Where the background computation is; not a lifecycle state. */
    public enum ComputeState {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "month", nullable = false)
    private LocalDate month;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "compute_state", nullable = false)
    private ComputeState computeState;

    @Column(name = "progress_done", nullable = false)
    private int progressDone;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy")
    private String policy;

    @Column(name = "total_earned", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalEarned = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_deduction", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalDeduction = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_adjustments", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAdjustments = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_net", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO.setScale(2);

    @Column(name = "payable_count", nullable = false)
    private int payableCount;

    @Column(name = "cannot_pay_count", nullable = false)
    private int cannotPayCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recompute_summary")
    private String recomputeSummary;

    @Column(name = "started_by", nullable = false)
    private UUID startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PayRun() {
        // JPA
    }

    PayRun(YearMonth month, Kind kind, int sequence, UUID startedBy, Instant now) {
        this.month = month.atDay(1);
        this.kind = kind;
        this.sequence = sequence;
        this.status = Status.DRAFT;
        this.computeState = ComputeState.QUEUED;
        this.startedBy = startedBy;
        this.startedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public YearMonth getMonth() {
        return YearMonth.from(month);
    }

    public Kind getKind() {
        return kind;
    }

    public int getSequence() {
        return sequence;
    }

    public Status getStatus() {
        return status;
    }

    public ComputeState getComputeState() {
        return computeState;
    }

    public int getProgressDone() {
        return progressDone;
    }

    public int getProgressTotal() {
        return progressTotal;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getPolicy() {
        return policy;
    }

    public BigDecimal getTotalEarned() {
        return totalEarned;
    }

    public BigDecimal getTotalDeduction() {
        return totalDeduction;
    }

    public BigDecimal getTotalAdjustments() {
        return totalAdjustments;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }

    public int getPayableCount() {
        return payableCount;
    }

    public int getCannotPayCount() {
        return cannotPayCount;
    }

    public String getRecomputeSummary() {
        return recomputeSummary;
    }

    public UUID getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public long getVersion() {
        return version;
    }

    // ------------------------------------------------------------------ changes (services only)

    void setComputeState(ComputeState state, Instant now) {
        this.computeState = state;
        this.heartbeatAt = now;
    }

    void setProgress(int done, int total, Instant now) {
        this.progressDone = done;
        this.progressTotal = total;
        this.heartbeatAt = now;
    }

    void setPolicy(String policyJson) {
        this.policy = policyJson;
    }

    void setRecomputeSummary(String summaryJson) {
        this.recomputeSummary = summaryJson;
    }

    void setTotals(
            BigDecimal earned, BigDecimal deduction, BigDecimal adjustments, BigDecimal net, int payable, int cannotPay) {
        this.totalEarned = earned;
        this.totalDeduction = deduction;
        this.totalAdjustments = adjustments;
        this.totalNet = net;
        this.payableCount = payable;
        this.cannotPayCount = cannotPay;
    }

    void submit(UUID by, Instant now) {
        this.status = Status.AWAITING_APPROVAL;
        this.submittedBy = by;
        this.submittedAt = now;
    }

    void returnToDraft() {
        this.status = Status.DRAFT;
    }

    void approve(UUID by, Instant now) {
        this.status = Status.APPROVED;
        this.approvedBy = by;
        this.approvedAt = now;
    }

    void cancel(UUID by, String reason, Instant now) {
        this.status = Status.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = now;
        this.cancelReason = reason;
    }
}
