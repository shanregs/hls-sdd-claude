package com.hls.attendance.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One change to a Manager's day, kept for ever: the first row of a day is the original, every change after it is a new
 * row with who, when, why and the affected fields before and after. A trigger refuses any update or delete.
 */
@Entity
@Immutable
@Table(name = "manager_day_history")
public class ManagerDayHistory {

    public enum Action {
        CHECK_IN,
        CHECK_OUT,
        COMPLETE_CHECK_OUT,
        CORRECT,
        LEAVE_APPLIED,
        LEAVE_REMOVED,
        ADDED_LATER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "day_id", nullable = false)
    private UUID dayId;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "before_json")
    private String beforeJson;

    @Column(name = "after_json")
    private String afterJson;

    protected ManagerDayHistory() {
        // JPA
    }

    public ManagerDayHistory(
            UUID dayId, UUID changedBy, Instant changedAt, Action action, String reason, String beforeJson, String afterJson) {
        this.dayId = dayId;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.action = action;
        this.reason = reason;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDayId() {
        return dayId;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public Action getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }
}
