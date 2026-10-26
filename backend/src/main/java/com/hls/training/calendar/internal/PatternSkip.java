package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A month a pattern deliberately skips, with the reason (spec 024 FR-006). */
@Entity
@Table(name = "training_pattern_skip")
class PatternSkip {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "pattern_id", nullable = false)
    UUID patternId;

    @Column(name = "month", nullable = false)
    LocalDate month;

    @Column(name = "reason", nullable = false)
    String reason;

    @Column(name = "skipped_by", nullable = false)
    UUID skippedBy;

    @Column(name = "skipped_at", nullable = false)
    Instant skippedAt;

    protected PatternSkip() {}

    PatternSkip(UUID patternId, LocalDate month, String reason, UUID by, Instant at) {
        this.patternId = patternId;
        this.month = month;
        this.reason = reason;
        this.skippedBy = by;
        this.skippedAt = at;
    }
}
