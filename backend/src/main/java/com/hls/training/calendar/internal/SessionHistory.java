package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One changed field of a session: the earlier value stays visible (append-only in the database). */
@Entity
@Table(name = "training_session_history")
class SessionHistory {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "field", nullable = false)
    String field;

    @Column(name = "before_value")
    String beforeValue;

    @Column(name = "after_value")
    String afterValue;

    @Column(name = "reason")
    String reason;

    @Column(name = "changed_by")
    UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    Instant changedAt;

    protected SessionHistory() {}

    SessionHistory(UUID sessionId, String field, String before, String after, String reason, UUID by, Instant at) {
        this.sessionId = sessionId;
        this.field = field;
        this.beforeValue = before;
        this.afterValue = after;
        this.reason = reason;
        this.changedBy = by;
        this.changedAt = at;
    }
}
