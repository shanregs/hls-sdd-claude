package com.hls.recruitment.marketing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Links a stored file to a visit; the file itself lives in the shared files module. */
@Entity
@Table(name = "activity_attachment")
public class ActivityAttachment {

    @Id
    private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ActivityAttachment() {}

    public ActivityAttachment(UUID activityId, UUID fileId, UUID addedBy, Instant addedAt) {
        this.id = UUID.randomUUID();
        this.activityId = activityId;
        this.fileId = fileId;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public UUID getFileId() {
        return fileId;
    }
}
