package com.hls.attendance.workingdays.internal;

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
import org.hibernate.annotations.Immutable;

/**
 * One change to a monthly figure, kept for ever. Keyed by School, year and month rather than the row id so the history
 * of a deleted row stays readable. A trigger refuses any update or delete.
 */
@Entity
@Immutable
@Table(name = "school_working_days_history")
public class WorkingDaysHistory {

    public enum Action {
        CREATE,
        EDIT,
        DELETE,
        COPY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "month", nullable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private Action action;

    @Column(name = "old_value", precision = 3, scale = 1)
    private BigDecimal oldValue;

    @Column(name = "new_value", precision = 3, scale = 1)
    private BigDecimal newValue;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_roles", nullable = false)
    private String actorRoles;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WorkingDaysHistory() {
        // JPA
    }

    public WorkingDaysHistory(
            UUID schoolId,
            int year,
            int month,
            Action action,
            BigDecimal oldValue,
            BigDecimal newValue,
            UUID actorId,
            String actorRoles,
            Instant occurredAt) {
        this.schoolId = schoolId;
        this.year = year;
        this.month = month;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actorId = actorId;
        this.actorRoles = actorRoles;
        this.occurredAt = occurredAt;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public Action getAction() {
        return action;
    }

    public BigDecimal getOldValue() {
        return oldValue;
    }

    public BigDecimal getNewValue() {
        return newValue;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRoles() {
        return actorRoles;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
