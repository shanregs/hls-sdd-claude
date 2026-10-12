package com.hls.attendance.manager.internal;

import com.hls.attendance.manager.internal.ManagerDay.DayType;
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
import java.time.LocalTime;
import java.util.UUID;

/** A Manager's request to add a forgotten check-in on a past day (called a regularization in the design documents). */
@Entity
@Table(name = "manager_regularization")
public class ManagerRegularization {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "proposed_time", nullable = false)
    private LocalTime proposedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Column(name = "purpose", length = 500)
    private String purpose;

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

    protected ManagerRegularization() {
        // JPA
    }

    public ManagerRegularization(
            UUID managerId,
            LocalDate dayDate,
            LocalTime proposedTime,
            DayType dayType,
            String purpose,
            String reason,
            Instant requestedAt) {
        this.managerId = managerId;
        this.dayDate = dayDate;
        this.proposedTime = proposedTime;
        this.dayType = dayType;
        this.purpose = purpose;
        this.reason = reason;
        this.status = Status.PENDING;
        this.requestedAt = requestedAt;
    }

    public void decide(Status newStatus, UUID by, Instant at, String decisionReason) {
        this.status = newStatus;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionReason = decisionReason;
    }

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

    public LocalDate getDayDate() {
        return dayDate;
    }

    public LocalTime getProposedTime() {
        return proposedTime;
    }

    public DayType getDayType() {
        return dayType;
    }

    public String getPurpose() {
        return purpose;
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
