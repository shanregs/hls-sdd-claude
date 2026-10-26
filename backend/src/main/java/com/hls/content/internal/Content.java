package com.hls.content.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A piece of content prepared by an author, submitted as numbered versions and reviewed (spec 029). */
@Entity
@Table(name = "content")
public class Content {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description")
    private String description;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContentStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "last_approved_version")
    private Integer lastApprovedVersion;

    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "no_reviewer_notified_version")
    private Integer noReviewerNotifiedVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Content() {}

    Content(
            UUID id,
            String code,
            String title,
            String category,
            String subject,
            String description,
            UUID authorUserId,
            UUID createdByUserId,
            LocalDate dueOn,
            ContentStatus status,
            Instant now) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.authorUserId = authorUserId;
        this.createdByUserId = createdByUserId;
        this.dueOn = dueOn;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public Integer getLastApprovedVersion() {
        return lastApprovedVersion;
    }

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public LocalDate getApprovedOn() {
        return approvedOn;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Integer getNoReviewerNotifiedVersion() {
        return noReviewerNotifiedVersion;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void edit(String title, String category, String subject, String description, UUID authorUserId, LocalDate dueOn, Instant now) {
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.authorUserId = authorUserId;
        this.dueOn = dueOn;
        this.updatedAt = now;
    }

    void linkTask(UUID taskId) {
        this.taskId = taskId;
    }

    void submitted(int versionNumber, Instant now) {
        this.currentVersion = versionNumber;
        this.status = ContentStatus.SUBMITTED;
        this.updatedAt = now;
    }

    void approved(UUID reviewer, LocalDate on, Instant now) {
        this.status = ContentStatus.APPROVED;
        this.lastApprovedVersion = currentVersion;
        this.reviewerUserId = reviewer;
        this.approvedOn = on;
        this.updatedAt = now;
    }

    void returned(UUID reviewer, Instant now) {
        this.status = ContentStatus.RETURNED;
        this.reviewerUserId = reviewer;
        this.updatedAt = now;
    }

    void noReviewerNoticeSent(int versionNumber) {
        this.noReviewerNotifiedVersion = versionNumber;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
