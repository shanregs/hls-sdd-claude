package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A Teacher's place in an open weekend session; cancelling flips the same row (spec 024 FR-009). */
@Entity
@Table(name = "training_registration")
class Registration {

    static final String REGISTERED = "REGISTERED";
    static final String CANCELLED = "CANCELLED";

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "teacher_id", nullable = false)
    UUID teacherId;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "registered_by", nullable = false)
    UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    Instant registeredAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "cancelled_by")
    UUID cancelledBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected Registration() {}

    Registration(UUID sessionId, UUID teacherId, UUID by, Instant at) {
        this.sessionId = sessionId;
        this.teacherId = teacherId;
        this.status = REGISTERED;
        this.registeredBy = by;
        this.registeredAt = at;
    }

    boolean isRegistered() {
        return REGISTERED.equals(status);
    }
}
