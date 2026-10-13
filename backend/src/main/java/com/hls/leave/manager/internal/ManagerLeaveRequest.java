package com.hls.leave.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A Manager's request for leave (spec 032 data-model.md, manager_leave_request). */
@Entity
@Table(name = "manager_leave_request")
public class ManagerLeaveRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "first_date", nullable = false)
    private LocalDate firstDate;

    @Column(name = "last_date", nullable = false)
    private LocalDate lastDate;

    @Column(name = "first_half", nullable = false)
    private boolean firstHalf;

    @Column(name = "last_half", nullable = false)
    private boolean lastHalf;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ManagerLeaveRequest() {
        // JPA
    }

    public ManagerLeaveRequest(
            UUID managerId,
            UUID leaveTypeId,
            LocalDate firstDate,
            LocalDate lastDate,
            boolean firstHalf,
            boolean lastHalf,
            String reason,
            Instant requestedAt) {
        this.managerId = managerId;
        this.leaveTypeId = leaveTypeId;
        this.firstDate = firstDate;
        this.lastDate = lastDate;
        this.firstHalf = firstHalf;
        this.lastHalf = lastHalf;
        this.reason = reason;
        this.status = Status.PENDING;
        this.requestedAt = requestedAt;
    }

    /** Approval, rejection or revoke by an Admin or Director. */
    public void decide(Status newStatus, UUID by, Instant at, String decisionReason) {
        this.status = newStatus;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionReason = decisionReason;
    }

    /** The Manager withdrew a pending request. */
    public void cancel(Instant at) {
        this.status = Status.CANCELLED;
        this.decidedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getManagerId() {
        return managerId;
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

    public boolean isFirstHalf() {
        return firstHalf;
    }

    public boolean isLastHalf() {
        return lastHalf;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
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
}
