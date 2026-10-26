package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A non-compliance exception: an assigned person recorded Absent (not on leave) at a session (spec 024 FR-019). */
@Entity
@Table(name = "training_exception")
class TrainingException {

    static final String OPEN = "OPEN";
    static final String CLOSED = "CLOSED";
    static final String WITHDRAWN = "WITHDRAWN";

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "person_kind", nullable = false)
    String personKind;

    @Column(name = "person_id", nullable = false)
    UUID personId;

    @Column(name = "entry_id", nullable = false)
    UUID entryId;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "closure_kind")
    String closureKind;

    @Column(name = "closure_note")
    String closureNote;

    @Column(name = "makeup_session_id")
    UUID makeupSessionId;

    @Column(name = "raised_at", nullable = false)
    Instant raisedAt;

    @Column(name = "closed_by")
    UUID closedBy;

    @Column(name = "closed_at")
    Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected TrainingException() {}

    TrainingException(UUID sessionId, String personKind, UUID personId, UUID entryId, Instant at) {
        this.sessionId = sessionId;
        this.personKind = personKind;
        this.personId = personId;
        this.entryId = entryId;
        this.status = OPEN;
        this.raisedAt = at;
    }
}
