package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A reschedule: the earlier date stays on record; insert-only. */
@Entity
@Table(name = "activity_date_history")
public class ActivityDateHistory {

    @Id
    private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "old_date", nullable = false)
    private LocalDate oldDate;

    @Column(name = "new_date", nullable = false)
    private LocalDate newDate;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ActivityDateHistory() {}

    public ActivityDateHistory(UUID activityId, LocalDate oldDate, LocalDate newDate, UUID by, Instant at) {
        this.id = UUID.randomUUID();
        this.activityId = activityId;
        this.oldDate = oldDate;
        this.newDate = newDate;
        this.changedBy = by;
        this.changedAt = at;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public LocalDate getOldDate() {
        return oldDate;
    }

    public LocalDate getNewDate() {
        return newDate;
    }
}
