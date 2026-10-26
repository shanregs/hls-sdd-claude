package com.hls.content.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One submitted version of a content piece. It never changes except to record that its file was removed (spec 029 FR-006). */
@Entity
@Table(name = "content_version")
public class ContentVersion {

    @Id
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "submitted_by_user_id", nullable = false)
    private UUID submittedByUserId;

    @Column(name = "note")
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason")
    private String removalReason;

    protected ContentVersion() {}

    ContentVersion(
            UUID contentId,
            int number,
            UUID fileId,
            String fileName,
            String contentType,
            long sizeBytes,
            UUID authorUserId,
            UUID submittedByUserId,
            String note,
            Instant submittedAt) {
        this.id = UUID.randomUUID();
        this.contentId = contentId;
        this.number = number;
        this.fileId = fileId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.authorUserId = authorUserId;
        this.submittedByUserId = submittedByUserId;
        this.note = note;
        this.submittedAt = submittedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContentId() {
        return contentId;
    }

    public int getNumber() {
        return number;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public UUID getSubmittedByUserId() {
        return submittedByUserId;
    }

    public String getNote() {
        return note;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getRemovedBy() {
        return removedBy;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public String getRemovalReason() {
        return removalReason;
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    void markRemoved(UUID by, String reason, Instant at) {
        this.removedBy = by;
        this.removalReason = reason;
        this.removedAt = at;
    }
}
