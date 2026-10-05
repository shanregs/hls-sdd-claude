package com.hls.training.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A one-month induction for new recruits: dates, trainer, venue and a seat limit. */
@Entity
@Table(name = "induction_batch")
public class InductionBatch {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "trainer")
    private String trainer;

    @Column(name = "venue_type", nullable = false)
    private String venueType;

    @Column(name = "venue")
    private String venue;

    @Column(name = "seat_limit", nullable = false)
    private int seatLimit;

    /** PLANNED or CANCELLED; RUNNING and COMPLETED are derived from the dates. */
    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InductionBatch() {}

    public InductionBatch(
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String trainer,
            String venueType,
            String venue,
            int seatLimit,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.trainer = trainer;
        this.venueType = venueType;
        this.venue = venue;
        this.seatLimit = seatLimit;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getTrainer() {
        return trainer;
    }

    public String getVenueType() {
        return venueType;
    }

    public String getVenue() {
        return venue;
    }

    public int getSeatLimit() {
        return seatLimit;
    }

    public String getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}
