package com.hls.expenses.internal;

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
 * One expense claim (spec 015). A paid claim never changes in its money facts (database trigger). The link columns are
 * a snapshot taken at submission so that they survive the activity being cancelled (FR-011).
 */
@Entity
@Table(name = "expense_claim")
public class ExpenseClaim {

    @Id
    private UUID id;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "client_request_id", nullable = false)
    private UUID clientRequestId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ExpenseCategory category;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_by", nullable = false)
    private PaidBy paidBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "no_bill_reason")
    private String noBillReason;

    @Column(name = "link_kind")
    private String linkKind;

    @Column(name = "link_ref")
    private UUID linkRef;

    @Column(name = "link_date")
    private LocalDate linkDate;

    @Column(name = "link_label")
    private String linkLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_how")
    private PaidHow paidHow;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_mode")
    private PaidMode paidMode;

    @Column(name = "paid_reference")
    private String paidReference;

    @Column(name = "paid_by_user")
    private UUID paidByUser;

    @Column(name = "payroll_run_id")
    private UUID payrollRunId;

    @Column(name = "payroll_month")
    private LocalDate payrollMonth;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExpenseClaim() {
        // JPA
    }

    ExpenseClaim(UUID claimantId, UUID clientRequestId, Instant now) {
        this.id = UUID.randomUUID();
        this.claimantId = claimantId;
        this.clientRequestId = clientRequestId;
        this.createdBy = claimantId;
        this.createdAt = now;
        this.updatedAt = now;
        this.status = ClaimStatus.DRAFT;
    }

    /** Sets every field the claimant may edit. */
    void fill(
            LocalDate expenseDate,
            BigDecimal amount,
            ExpenseCategory category,
            UUID zoneId,
            String note,
            PaidBy paidBy,
            String reason,
            String noBillReason,
            Instant now) {
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.category = category;
        this.zoneId = zoneId;
        this.note = note;
        this.paidBy = paidBy;
        this.reason = reason;
        this.noBillReason = noBillReason;
        this.updatedAt = now;
    }

    void snapshotLink(String kind, UUID ref, LocalDate date, String label) {
        this.linkKind = kind;
        this.linkRef = ref;
        this.linkDate = date;
        this.linkLabel = label;
    }

    void submit(Instant now) {
        this.status = ClaimStatus.SUBMITTED;
        this.submittedAt = now;
        this.decidedBy = null;
        this.decidedAt = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    void withdraw(Instant now) {
        this.status = ClaimStatus.DRAFT;
        this.submittedAt = null;
        this.updatedAt = now;
    }

    void approve(UUID by, Instant now, boolean companyPaid) {
        this.status = companyPaid ? ClaimStatus.PAID_BY_COMPANY : ClaimStatus.APPROVED;
        this.decidedBy = by;
        this.decidedAt = now;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    void reject(UUID by, String reason, Instant now) {
        this.status = ClaimStatus.REJECTED;
        this.decidedBy = by;
        this.decidedAt = now;
        this.decisionReason = reason;
        this.updatedAt = now;
    }

    /** A reversed approval goes back to Submitted; the earlier decision stays in the history. */
    void reverse(Instant now) {
        this.status = ClaimStatus.SUBMITTED;
        this.decidedBy = null;
        this.decidedAt = null;
        this.decisionReason = null;
        this.updatedAt = now;
    }

    void markPaidOutside(UUID by, LocalDate on, PaidMode mode, String reference, Instant now) {
        this.status = ClaimStatus.PAID;
        this.paidHow = PaidHow.OUTSIDE;
        this.paidOn = on;
        this.paidMode = mode;
        this.paidReference = reference;
        this.paidByUser = by;
        this.updatedAt = now;
    }

    void markPaidWithPayroll(UUID runId, LocalDate month, Instant now) {
        this.status = ClaimStatus.PAID;
        this.paidHow = PaidHow.PAYROLL;
        this.payrollRunId = runId;
        this.payrollMonth = month;
        this.paidOn = month;
        this.updatedAt = now;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimantId() {
        return claimantId;
    }

    public UUID getClientRequestId() {
        return clientRequestId;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getNote() {
        return note;
    }

    public PaidBy getPaidBy() {
        return paidBy;
    }

    public String getReason() {
        return reason;
    }

    public String getNoBillReason() {
        return noBillReason;
    }

    public String getLinkKind() {
        return linkKind;
    }

    public UUID getLinkRef() {
        return linkRef;
    }

    public LocalDate getLinkDate() {
        return linkDate;
    }

    public String getLinkLabel() {
        return linkLabel;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public PaidHow getPaidHow() {
        return paidHow;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public PaidMode getPaidMode() {
        return paidMode;
    }

    public String getPaidReference() {
        return paidReference;
    }

    public UUID getPaidByUser() {
        return paidByUser;
    }

    public UUID getPayrollRunId() {
        return payrollRunId;
    }

    public LocalDate getPayrollMonth() {
        return payrollMonth;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
