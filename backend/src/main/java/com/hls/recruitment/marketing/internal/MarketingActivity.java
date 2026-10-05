package com.hls.recruitment.marketing.internal;

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

/** A visit, call, proposal meeting or follow-up, on a prospect or (an account visit) on a School with an MoU. */
@Entity
@Table(name = "marketing_activity")
public class MarketingActivity {

    @Id
    private UUID id;

    @Column(name = "prospect_id")
    private UUID prospectId;

    @Column(name = "school_id")
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ActivityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActivityStatus status = ActivityStatus.PLANNED;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "activity_attendee", joinColumns = @JoinColumn(name = "activity_id"))
    @Column(name = "user_id")
    private Set<UUID> attendees = new HashSet<>();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MarketingActivity() {}

    public MarketingActivity(
            UUID prospectId,
            UUID schoolId,
            ActivityType type,
            LocalDate date,
            String notes,
            Collection<UUID> attendees,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.prospectId = prospectId;
        this.schoolId = schoolId;
        this.type = type;
        this.activityDate = date;
        this.notes = notes;
        this.attendees.addAll(attendees);
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProspectId() {
        return prospectId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public ActivityType getType() {
        return type;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public String getNotes() {
        return notes;
    }

    public String getOutcome() {
        return outcome;
    }

    public LocalDate getFollowUpOn() {
        return followUpOn;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Set<UUID> getAttendees() {
        return attendees;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Long getVersion() {
        return version;
    }

    public void complete(String outcome, String notes, LocalDate followUpOn) {
        this.status = ActivityStatus.COMPLETED;
        this.outcome = outcome;
        if (notes != null) {
            this.notes = notes;
        }
        this.followUpOn = followUpOn;
    }

    public void reschedule(LocalDate newDate) {
        this.activityDate = newDate;
    }

    public void cancel(String reason) {
        this.status = ActivityStatus.CANCELLED;
        this.cancelReason = reason;
    }
}
