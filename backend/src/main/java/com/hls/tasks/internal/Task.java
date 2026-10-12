package com.hls.tasks.internal;

import com.hls.tasks.api.TaskType;
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

/** A piece of work assigned to a Manager (spec 025). Overdue is never stored; it is derived from the due date. */
@Entity
@Table(name = "task")
public class Task {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TaskType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "assignee_user_id", nullable = false)
    private UUID assigneeUserId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "escalate_after_days", nullable = false)
    private int escalateAfterDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "linked_type")
    private String linkedType;

    @Column(name = "linked_id")
    private UUID linkedId;

    @Column(name = "linked_label")
    private String linkedLabel;

    @Column(name = "place")
    private String place;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_key")
    private String sourceKey;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(name = "overdue_reason")
    private String overdueReason;

    @Column(name = "completion_note")
    private String completionNote;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "overdue_notified_for")
    private LocalDate overdueNotifiedFor;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
        // JPA
    }

    Task(TaskType type, String title, UUID assigneeUserId, UUID createdBy, LocalDate dueOn, int escalateAfterDays, String source, Instant now) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.title = title;
        this.assigneeUserId = assigneeUserId;
        this.createdBy = createdBy;
        this.dueOn = dueOn;
        this.escalateAfterDays = escalateAfterDays;
        this.status = TaskStatus.ASSIGNED;
        this.source = source;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public TaskType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public int getEscalateAfterDays() {
        return escalateAfterDays;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getLinkedType() {
        return linkedType;
    }

    public UUID getLinkedId() {
        return linkedId;
    }

    public String getLinkedLabel() {
        return linkedLabel;
    }

    public String getPlace() {
        return place;
    }

    public String getSource() {
        return source;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public UUID getParentTaskId() {
        return parentTaskId;
    }

    public String getOverdueReason() {
        return overdueReason;
    }

    public String getCompletionNote() {
        return completionNote;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public LocalDate getOverdueNotifiedFor() {
        return overdueNotifiedFor;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True when the work is still open and the due date is before {@code today}. */
    public boolean isOverdueOn(LocalDate today) {
        return status.isOpen() && dueOn.isBefore(today);
    }

    void link(String linkedType, UUID linkedId, String linkedLabel, String place) {
        this.linkedType = linkedType;
        this.linkedId = linkedId;
        this.linkedLabel = linkedLabel;
        this.place = place;
    }

    void describe(String description) {
        this.description = description;
    }

    void fromSource(String sourceKey, UUID parentTaskId) {
        this.sourceKey = sourceKey;
        this.parentTaskId = parentTaskId;
    }

    void edit(String title, String description, UUID assigneeUserId, LocalDate dueOn, int escalateAfterDays, String place, Instant now) {
        this.title = title;
        this.description = description;
        this.assigneeUserId = assigneeUserId;
        this.dueOn = dueOn;
        this.escalateAfterDays = escalateAfterDays;
        this.place = place;
        this.updatedAt = now;
    }

    void moveTo(TaskStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    void recordOverdueReason(String reason, Instant now) {
        this.overdueReason = reason;
        this.updatedAt = now;
    }

    void complete(String note, Instant now) {
        this.completionNote = note;
        this.completedAt = now;
        this.status = TaskStatus.COMPLETED;
        this.updatedAt = now;
    }

    void sendBack(Instant now) {
        this.completedAt = null;
        this.status = TaskStatus.IN_PROGRESS;
        this.updatedAt = now;
    }

    void verify(UUID by, Instant now) {
        this.verifiedBy = by;
        this.verifiedAt = now;
        this.status = TaskStatus.VERIFIED;
        this.updatedAt = now;
    }

    void cancel(String reason, Instant now) {
        this.cancelReason = reason;
        this.status = TaskStatus.CANCELLED;
        this.updatedAt = now;
    }

    void markOverdueNotified(LocalDate dueOn) {
        this.overdueNotifiedFor = dueOn;
    }

    void markEscalated(Instant now) {
        this.escalatedAt = now;
    }
}
