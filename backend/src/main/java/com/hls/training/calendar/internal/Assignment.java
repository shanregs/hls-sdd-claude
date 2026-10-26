package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The obligation to attend a session: one row per person per session (spec 024 FR-010). */
@Entity
@Table(name = "training_assignment")
class Assignment {

    static final String REQUIRED = "REQUIRED";
    static final String REMOVED = "REMOVED";
    static final String TEACHER = "TEACHER";
    static final String MANAGER = "MANAGER";

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "person_kind", nullable = false)
    String personKind;

    @Column(name = "person_id", nullable = false)
    UUID personId;

    @Column(name = "source", nullable = false)
    String source;

    @Column(name = "group_kind")
    String groupKind;

    @Column(name = "group_ref")
    UUID groupRef;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "assigned_by", nullable = false)
    UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt;

    @Column(name = "removed_by")
    UUID removedBy;

    @Column(name = "removed_at")
    Instant removedAt;

    @Column(name = "removed_reason")
    String removedReason;

    @Column(name = "cannot_attend_note")
    String cannotAttendNote;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected Assignment() {}

    Assignment(UUID sessionId, String personKind, UUID personId, UUID by, Instant at) {
        this.sessionId = sessionId;
        this.personKind = personKind;
        this.personId = personId;
        this.status = REQUIRED;
        this.source = "INDIVIDUAL";
        this.assignedBy = by;
        this.assignedAt = at;
    }

    boolean isRequired() {
        return REQUIRED.equals(status);
    }
}
