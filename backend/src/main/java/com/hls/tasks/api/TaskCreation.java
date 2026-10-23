package com.hls.tasks.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract C2: other modules (022 overdue invoice, 023 follow-up, 026 corrective action, 029 content) create a task for
 * a Manager, linked to one of their own records, with a due date and an escalation rule. Creation is idempotent per
 * ({@code source}, {@code sourceKey}) while a task for that key is still open.
 */
public interface TaskCreation {

    /**
     * @param escalateAfterDays days past the due date before the Director is told; null means the default (3)
     * @param source the calling module's key, for example {@code MARKETING_FOLLOW_UP}
     * @param sourceKey identifies the record the task is about; a second call with the same key returns the open task
     * @param createdByUserId the person behind the call, or null when the system acts
     */
    record FollowUpRequest(
            TaskType type,
            String title,
            String description,
            UUID assigneeUserId,
            LocalDate dueOn,
            Integer escalateAfterDays,
            String linkedType,
            UUID linkedId,
            String linkedLabel,
            String place,
            String source,
            String sourceKey,
            UUID createdByUserId) {}

    record TaskRef(UUID id, boolean created, String status) {}

    /** Creates the task, or returns the open one for the same source key. Refuses an assignee who is not an active Manager. */
    TaskRef createFollowUp(FollowUpRequest request);

    /**
     * Amendment for spec 026: the calling module changes the task it created, addressed by its source and key. Each
     * operation acts on a task that is still open (assigned, acknowledged or in progress), runs as the system, writes the
     * task history and Change History, and returns false when there is no such open task.
     */
    boolean reschedule(String source, String sourceKey, LocalDate dueOn, String title, String description);

    /** Completes the open task with a note (no acknowledgement or overdue reason is needed: the calling module did the work). */
    boolean complete(String source, String sourceKey, String note, UUID completedByUserId);

    boolean cancel(String source, String sourceKey, String reason);

    /**
     * Completes the assignee's open tasks of one type that are linked to a record (for example the Principal feedback task of
     * a Teacher) with a note. Returns how many were completed.
     */
    int completeLinked(TaskType type, String linkedType, UUID linkedId, UUID assigneeUserId, String note);

    /** Gives the open task to another active Manager (else {@code InvalidInputException}) and tells them. */
    boolean reassign(String source, String sourceKey, UUID assigneeUserId);
}
