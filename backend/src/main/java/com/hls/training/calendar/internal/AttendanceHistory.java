package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One create or correction of an attendance entry (append-only in the database, spec 024 FR-018). */
@Entity
@Table(name = "training_attendance_history")
class AttendanceHistory {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "entry_id", nullable = false)
    UUID entryId;

    @Column(name = "action", nullable = false)
    String action;

    @Column(name = "outcome", nullable = false)
    String outcome;

    @Column(name = "day_value")
    BigDecimal dayValue;

    @Column(name = "reason")
    String reason;

    @Column(name = "changed_by", nullable = false)
    UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    Instant changedAt;

    protected AttendanceHistory() {}

    AttendanceHistory(UUID entryId, String action, String outcome, BigDecimal dayValue, String reason, UUID by, Instant at) {
        this.entryId = entryId;
        this.action = action;
        this.outcome = outcome;
        this.dayValue = dayValue;
        this.reason = reason;
        this.changedBy = by;
        this.changedAt = at;
    }
}
