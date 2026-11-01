package com.hls.substitution.payout;

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

/** One day a substitute covers for one allocation (requirement and substitute). Never deleted (spec 017b FR-007). */
@Entity
@Table(name = "substitute_day")
public class SubstituteDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requirement_id", nullable = false)
    private UUID requirementId;

    @Column(name = "substitute_teacher_id", nullable = false)
    private UUID substituteTeacherId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "absent_teacher_id", nullable = false)
    private UUID absentTeacherId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "day_value", nullable = false)
    private BigDecimal dayValue;

    @Column(name = "live", nullable = false)
    private boolean live = true;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance", nullable = false)
    private DayAttendance attendance = DayAttendance.NOT_MARKED;

    @Column(name = "marked_by")
    private UUID markedBy;

    @Column(name = "marked_at")
    private Instant markedAt;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DayStatus status = DayStatus.ALLOCATED;

    @Column(name = "mismatch_reason")
    private String mismatchReason;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "run_month")
    private LocalDate runMonth;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Column(name = "correction_ref")
    private String correctionRef;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubstituteDay() {
        // JPA
    }

    public SubstituteDay(
            UUID requirementId,
            UUID substituteTeacherId,
            UUID schoolId,
            UUID zoneId,
            UUID absentTeacherId,
            LocalDate dayDate,
            BigDecimal dayValue,
            String source,
            Instant now) {
        this.requirementId = requirementId;
        this.substituteTeacherId = substituteTeacherId;
        this.schoolId = schoolId;
        this.zoneId = zoneId;
        this.absentTeacherId = absentTeacherId;
        this.dayDate = dayDate;
        this.dayValue = dayValue;
        this.source = source;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public UUID getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public UUID getAbsentTeacherId() {
        return absentTeacherId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public YearMonth month() {
        return YearMonth.from(dayDate);
    }

    public BigDecimal getDayValue() {
        return dayValue;
    }

    public boolean isLive() {
        return live;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public DayAttendance getAttendance() {
        return attendance;
    }

    public UUID getMarkedBy() {
        return markedBy;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public String getSource() {
        return source;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public DayStatus getStatus() {
        return status;
    }

    public String getMismatchReason() {
        return mismatchReason;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getRunId() {
        return runId;
    }

    public LocalDate getRunMonth() {
        return runMonth;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public String getCorrectionRef() {
        return correctionRef;
    }

    public Long getVersion() {
        return version;
    }

    // ---------------------------------------------------------------- changes

    public void reassign(UUID zoneId, UUID absentTeacherId, BigDecimal dayValue, Instant now) {
        this.zoneId = zoneId;
        this.absentTeacherId = absentTeacherId;
        this.dayValue = dayValue;
        this.live = true;
        this.cancelReason = null;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.live = false;
        this.cancelReason = reason == null ? null : reason.substring(0, Math.min(100, reason.length()));
        this.updatedAt = now;
    }

    public void mark(DayAttendance attendance, UUID by, Instant now) {
        this.attendance = attendance;
        this.markedBy = attendance == DayAttendance.NOT_MARKED ? null : by;
        this.markedAt = attendance == DayAttendance.NOT_MARKED ? null : now;
        this.updatedAt = now;
    }

    public void setStatus(DayStatus status, String mismatchReason, Instant now) {
        this.status = status;
        this.mismatchReason = mismatchReason;
        this.updatedAt = now;
    }

    public void setPricing(BigDecimal rate, BigDecimal amount) {
        this.rate = rate;
        this.amount = amount;
    }

    public void setRun(UUID runId, LocalDate runMonth, BigDecimal paidAmount) {
        this.runId = runId;
        this.runMonth = runMonth;
        this.paidAmount = paidAmount;
    }

    public void setCorrectionRef(String correctionRef) {
        this.correctionRef = correctionRef;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }
}
