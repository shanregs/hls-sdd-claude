package com.hls.schoolbilling.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The follow-up tasks of overdue monthly dues, owned by {@code schoolbilling} and implemented by {@code tasks} on top of its
 * {@code TaskCreation} interface (spec 025), so billing does not depend on the task module (spec 022 research R8).
 */
public interface OverdueTasks {

    /** The calling record's source key; one open task per ({@code source}, {@code sourceKey}). */
    record Request(
            String title,
            String description,
            UUID assigneeUserId,
            LocalDate dueOn,
            int escalateAfterDays,
            String linkedType,
            UUID linkedId,
            String linkedLabel,
            String place,
            String source,
            String sourceKey) {}

    /** Creates the follow-up task, or returns the open one for the same key. Refuses an assignee who is not an active Manager. */
    UUID createFollowUp(Request request);

    /** Completes the open task for the key with a note; false when none is open. */
    boolean complete(String source, String sourceKey, String note, UUID completedByUserId);

    /** Cancels the open task for the key; false when none is open. */
    boolean cancel(String source, String sourceKey, String reason);

    /** Gives the open task for the key to another active Manager; false when none is open. */
    boolean reassign(String source, String sourceKey, UUID assigneeUserId);
}
