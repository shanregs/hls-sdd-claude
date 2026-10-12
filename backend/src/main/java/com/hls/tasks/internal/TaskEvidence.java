package com.hls.tasks.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Links a task to a file in the shared file store that shows the work was done. */
@Entity
@Table(name = "task_evidence")
public class TaskEvidence {

    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected TaskEvidence() {
        // JPA
    }

    TaskEvidence(UUID taskId, UUID fileId, UUID addedBy, Instant addedAt) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.fileId = fileId;
        this.addedBy = addedBy;
        this.addedAt = addedAt;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public UUID getAddedBy() {
        return addedBy;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
