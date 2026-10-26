package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The recorded outcome of one person at one session: exactly one row per person (spec 024 FR-016). */
@Entity
@Table(name = "training_attendance_entry")
class AttendanceEntry {

    static final String PRESENT = "PRESENT";
    static final String ABSENT = "ABSENT";
    static final String EXCUSED = "EXCUSED";

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "person_kind", nullable = false)
    String personKind;

    @Column(name = "person_id", nullable = false)
    UUID personId;

    @Column(name = "basis", nullable = false)
    String basis;

    @Column(name = "outcome", nullable = false)
    String outcome;

    @Column(name = "day_value")
    BigDecimal dayValue;

    @Column(name = "reason")
    String reason;

    @Column(name = "on_leave", nullable = false)
    boolean onLeave;

    @Column(name = "certificate_note")
    String certificateNote;

    @Column(name = "internal_remark")
    String internalRemark;

    @Column(name = "sync_state", nullable = false)
    String syncState;

    @Column(name = "clash_note")
    String clashNote;

    @Column(name = "recorded_by", nullable = false)
    UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    protected AttendanceEntry() {}

    AttendanceEntry(UUID sessionId, String personKind, UUID personId, UUID by, Instant at) {
        this.sessionId = sessionId;
        this.personKind = personKind;
        this.personId = personId;
        this.recordedBy = by;
        this.recordedAt = at;
    }
}
