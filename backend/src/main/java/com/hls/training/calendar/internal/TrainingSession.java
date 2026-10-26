package com.hls.training.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** One training session, a weekend refresher or a monthly session (spec 024 FR-001). Fields are package-private on purpose. */
@Entity
@Table(name = "training_session")
class TrainingSession {

    static final String DRAFT = "DRAFT";
    static final String PUBLISHED = "PUBLISHED";
    static final String HELD = "HELD";
    static final String CANCELLED = "CANCELLED";
    static final String SKIPPED = "SKIPPED";
    static final String WEEKEND = "WEEKEND";
    static final String MONTHLY = "MONTHLY";

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "session_type", nullable = false)
    String sessionType;

    @Column(name = "topic", nullable = false)
    String topic;

    @Column(name = "session_date", nullable = false)
    LocalDate sessionDate;

    @Column(name = "starts_at", nullable = false)
    LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    LocalTime endsAt;

    @Column(name = "trainer_name")
    String trainerName;

    @Column(name = "trainer_contact")
    String trainerContact;

    @Column(name = "venue_kind", nullable = false)
    String venueKind;

    @Column(name = "venue")
    String venue;

    @Column(name = "description")
    String description;

    @Column(name = "seat_limit")
    Integer seatLimit;

    @Column(name = "status", nullable = false)
    String status;

    @Column(name = "pattern_id")
    UUID patternId;

    @Column(name = "pattern_month")
    LocalDate patternMonth;

    @Column(name = "hand_edited", nullable = false)
    boolean handEdited;

    @Column(name = "skip_reason")
    String skipReason;

    @Column(name = "cancel_reason")
    String cancelReason;

    @Column(name = "held_at")
    Instant heldAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected TrainingSession() {}

    TrainingSession(UUID createdBy, Instant now) {
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    boolean isFinal() {
        return HELD.equals(status) || CANCELLED.equals(status);
    }
}
