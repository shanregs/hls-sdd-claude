package com.hls.leave.internal;

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
import java.util.UUID;

/** One Teacher's request for leave (spec 009). Status changes only through the transition methods. */
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "leave_type_id", nullable = false, updatable = false)
    private UUID leaveTypeId;

    @Column(name = "first_date", nullable = false, updatable = false)
    private LocalDate firstDate;

    @Column(name = "last_date", nullable = false, updatable = false)
    private LocalDate lastDate;

    @Column(name = "half_day_start", nullable = false, updatable = false)
    private boolean halfDayStart;

    @Column(name = "half_day_end", nullable = false, updatable = false)
    private boolean halfDayEnd;

    @Column(name = "working_days", nullable = false)
    private BigDecimal workingDays;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaveStatus status;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "cancelled_by_kind")
    private String cancelledByKind;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected LeaveRequest() {
        // JPA
    }

    public LeaveRequest(
            UUID teacherId,
            UUID schoolId,
            UUID leaveTypeId,
            LocalDate firstDate,
            LocalDate lastDate,
            boolean halfDayStart,
            boolean halfDayEnd,
            BigDecimal workingDays,
            String reason,
            UUID createdByUserId,
            Instant createdAt) {
        this.teacherId = teacherId;
        this.schoolId = schoolId;
        this.leaveTypeId = leaveTypeId;
        this.firstDate = firstDate;
        this.lastDate = lastDate;
        this.halfDayStart = halfDayStart;
        this.halfDayEnd = halfDayEnd;
        this.workingDays = workingDays;
        this.reason = reason;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.status = LeaveStatus.PENDING;
    }

    public void approve(UUID by, String note, BigDecimal workingDaysAtApproval, Instant now) {
        this.status = LeaveStatus.APPROVED;
        this.decidedByUserId = by;
        this.decidedAt = now;
        this.decisionNote = note;
        this.workingDays = workingDaysAtApproval;
    }

    public void reject(UUID by, String reason, Instant now) {
        this.status = LeaveStatus.REJECTED;
        this.decidedByUserId = by;
        this.decidedAt = now;
        this.decisionNote = reason;
    }

    /** Cancelled by the Teacher or revoked by a supervisor; {@code kind} is TEACHER or SUPERVISOR. */
    public void cancel(String kind, UUID by, String note, Instant now) {
        this.status = LeaveStatus.CANCELLED;
        this.cancelledByKind = kind;
        if ("SUPERVISOR".equals(kind)) {
            this.decidedByUserId = by;
            this.decidedAt = now;
            this.decisionNote = note;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public LocalDate getFirstDate() {
        return firstDate;
    }

    public LocalDate getLastDate() {
        return lastDate;
    }

    public boolean isHalfDayStart() {
        return halfDayStart;
    }

    public boolean isHalfDayEnd() {
        return halfDayEnd;
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public UUID getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getCancelledByKind() {
        return cancelledByKind;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
