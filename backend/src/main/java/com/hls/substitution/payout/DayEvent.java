package com.hls.substitution.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One change to a substitute day. Append-only: a trigger refuses update and delete (spec 017b FR-010, Principle I). */
@Entity
@Immutable
@Table(name = "substitute_day_event")
public class DayEvent {

    /** What changed. */
    public enum Field {
        ATTENDANCE,
        LIVE,
        STATUS,
        RUN,
        LOCK,
        REOPEN,
        MIGRATED,
        CORRECTION
    }

    /** Why it changed. */
    public enum Cause {
        MARK,
        EVENT_ASSIGNED,
        EVENT_ENDED,
        LOCK,
        JOB,
        RUN_APPROVED,
        MIGRATION,
        CORRECTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "day_id", nullable = false)
    private UUID dayId;

    @Column(name = "at", nullable = false)
    private Instant at;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "field", nullable = false)
    private Field field;

    @Column(name = "from_value")
    private String fromValue;

    @Column(name = "to_value")
    private String toValue;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "cause", nullable = false)
    private Cause cause;

    @Column(name = "note")
    private String note;

    protected DayEvent() {
        // JPA
    }

    public DayEvent(
            UUID dayId,
            Instant at,
            Field field,
            String fromValue,
            String toValue,
            UUID actorUserId,
            Cause cause,
            String note) {
        this.dayId = dayId;
        this.at = at;
        this.field = field;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.actorUserId = actorUserId;
        this.cause = cause;
        this.note = note == null ? null : note.substring(0, Math.min(200, note.length()));
    }

    public UUID getDayId() {
        return dayId;
    }

    public Instant getAt() {
        return at;
    }

    public Field getField() {
        return field;
    }

    public String getFromValue() {
        return fromValue;
    }

    public String getToValue() {
        return toValue;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Cause getCause() {
        return cause;
    }

    public String getNote() {
        return note;
    }
}
