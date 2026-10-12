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
import java.time.YearMonth;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A lock or a reopen of a Manager's month, kept for ever. A reopen carries its reason. */
@Entity
@Immutable
@Table(name = "manager_month_event")
public class ManagerMonthEvent {

    public enum Event {
        LOCKED,
        REOPENED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false)
    private Event event;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor", nullable = false)
    private UUID actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ManagerMonthEvent() {
        // JPA
    }

    public ManagerMonthEvent(UUID managerId, YearMonth month, Event event, String reason, UUID actor, Instant at) {
        this.managerId = managerId;
        this.yearMonth = month.toString();
        this.event = event;
        this.reason = reason;
        this.actor = actor;
        this.occurredAt = at;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public YearMonth getMonth() {
        return YearMonth.parse(yearMonth);
    }

    public Event getEvent() {
        return event;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
