package com.hls.recruitment.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A scheduled visit to a college on one or more dates, with the HLS interviewers attending. */
@Entity
@Table(name = "campus_drive")
public class CampusDrive {

    @Id
    private UUID id;

    @Column(name = "college_id", nullable = false)
    private UUID collegeId;

    @Column(name = "season_label")
    private String seasonLabel;

    @Column(name = "venue")
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DriveStatus status = DriveStatus.PLANNED;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "scheduled_by", nullable = false)
    private UUID scheduledBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campus_drive_date", joinColumns = @JoinColumn(name = "drive_id"))
    @Column(name = "drive_date")
    private Set<LocalDate> dates = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campus_drive_interviewer", joinColumns = @JoinColumn(name = "drive_id"))
    @Column(name = "user_id")
    private Set<UUID> interviewers = new HashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CampusDrive() {}

    public CampusDrive(
            UUID collegeId,
            String seasonLabel,
            String venue,
            UUID scheduledBy,
            Collection<LocalDate> dates,
            Collection<UUID> interviewers,
            Instant now) {
        this.id = UUID.randomUUID();
        this.collegeId = collegeId;
        this.seasonLabel = seasonLabel;
        this.venue = venue;
        this.scheduledBy = scheduledBy;
        this.dates.addAll(dates);
        this.interviewers.addAll(interviewers);
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCollegeId() {
        return collegeId;
    }

    public String getSeasonLabel() {
        return seasonLabel;
    }

    public String getVenue() {
        return venue;
    }

    public DriveStatus getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public UUID getScheduledBy() {
        return scheduledBy;
    }

    public Set<LocalDate> getDates() {
        return dates;
    }

    public Set<UUID> getInterviewers() {
        return interviewers;
    }

    public Long getVersion() {
        return version;
    }

    public void change(String seasonLabel, String venue, Collection<LocalDate> newDates, Collection<UUID> newInterviewers) {
        this.seasonLabel = seasonLabel;
        this.venue = venue;
        this.dates.retainAll(newDates);
        this.dates.addAll(newDates);
        this.interviewers.retainAll(newInterviewers);
        this.interviewers.addAll(newInterviewers);
    }

    public void markHeld() {
        this.status = DriveStatus.HELD;
    }

    public void cancel(String reason) {
        this.status = DriveStatus.CANCELLED;
        this.cancelReason = reason;
    }
}
