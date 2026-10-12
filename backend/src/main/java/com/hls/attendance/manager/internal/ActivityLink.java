package com.hls.attendance.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** A planned activity (contract C1) linked to a Field day, named by its kind and id. */
@Embeddable
public class ActivityLink {

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    protected ActivityLink() {
        // JPA
    }

    public ActivityLink(String kind, UUID activityId) {
        this.kind = kind;
        this.activityId = activityId;
    }

    public String getKind() {
        return kind;
    }

    public UUID getActivityId() {
        return activityId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActivityLink link && kind.equals(link.kind) && activityId.equals(link.activityId);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + activityId.hashCode();
    }
}
