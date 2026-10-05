package com.hls.training.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An induction day the recruit was absent, with the reason; a present day is a training-day mark in attendance. */
@Entity
@Table(name = "induction_absence")
public class InductionAbsence {

    @Id
    private UUID id;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "absent_on", nullable = false)
    private LocalDate absentOn;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected InductionAbsence() {}

    public InductionAbsence(UUID enrolmentId, LocalDate absentOn, String reason, UUID recordedBy, Instant recordedAt) {
        this.id = UUID.randomUUID();
        this.enrolmentId = enrolmentId;
        this.absentOn = absentOn;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public UUID getEnrolmentId() {
        return enrolmentId;
    }

    public LocalDate getAbsentOn() {
        return absentOn;
    }

    public String getReason() {
        return reason;
    }
}
