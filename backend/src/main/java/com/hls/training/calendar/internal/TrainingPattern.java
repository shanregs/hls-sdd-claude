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

/** A standing monthly rule that generates one session a month (spec 024 FR-004). Fields are package-private on purpose. */
@Entity
@Table(name = "training_pattern")
class TrainingPattern {

    @Id
    UUID id = UUID.randomUUID();

    @Column(name = "rule_kind", nullable = false)
    String ruleKind;

    @Column(name = "nth")
    Integer nth;

    @Column(name = "weekday")
    Integer weekday;

    @Column(name = "day_of_month")
    Integer dayOfMonth;

    @Column(name = "starts_at", nullable = false)
    LocalTime startsAt;

    @Column(name = "ends_at", nullable = false)
    LocalTime endsAt;

    @Column(name = "topic", nullable = false)
    String topic;

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

    @Column(name = "default_group_kind")
    String defaultGroupKind;

    @Column(name = "default_group_ref")
    UUID defaultGroupRef;

    @Column(name = "start_month", nullable = false)
    LocalDate startMonth;

    @Column(name = "end_month")
    LocalDate endMonth;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected TrainingPattern() {}

    TrainingPattern(UUID createdBy, Instant now) {
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    boolean isOpenEnded() {
        return endMonth == null;
    }
}
