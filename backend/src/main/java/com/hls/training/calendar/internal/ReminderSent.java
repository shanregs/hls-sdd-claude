package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Marks that a reminder went out for a person, session and offset, so a second run sends nothing. */
@Entity
@Table(name = "training_reminder_sent")
class ReminderSent {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "person_kind", nullable = false)
    String personKind;

    @Column(name = "person_id", nullable = false)
    UUID personId;

    @Column(name = "days_before", nullable = false)
    int daysBefore;

    @Column(name = "sent_at", nullable = false)
    Instant sentAt;

    protected ReminderSent() {}

    ReminderSent(UUID sessionId, String personKind, UUID personId, int daysBefore, Instant at) {
        this.sessionId = sessionId;
        this.personKind = personKind;
        this.personId = personId;
        this.daysBefore = daysBefore;
        this.sentAt = at;
    }
}
