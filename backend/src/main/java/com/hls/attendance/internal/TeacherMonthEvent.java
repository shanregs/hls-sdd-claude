package com.hls.attendance.internal;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only lock, reopen and relock history. */
@Entity
@Table(name = "attendance_teacher_month_event")
public class TeacherMonthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, updatable = false, length = 7)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, updatable = false)
    private MonthEventType event;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TeacherMonthEvent() {
        // JPA
    }

    public TeacherMonthEvent(
            UUID teacherId, String yearMonth, MonthEventType event, String reason, UUID actorUserId, Instant now) {
        this.teacherId = teacherId;
        this.yearMonth = yearMonth;
        this.event = event;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.occurredAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public MonthEventType getEvent() {
        return event;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
