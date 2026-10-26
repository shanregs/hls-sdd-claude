package com.hls.tasks.api;

import java.util.UUID;

/**
 * Amendment for spec 029: the calling module (content) moves a task it created through the review steps, addressed by its
 * source and key. Completing is already offered by {@link TaskCreation#complete}. Each operation runs as the person named, writes
 * the task history and Change History, and returns false when there is no task in the right state.
 */
public interface TaskProgress {

    /** Verifies a Completed task. The verifier is the reviewer of the calling module, never the task assignee. */
    boolean verify(String source, String sourceKey, UUID verifiedByUserId);

    /** Sends a Completed task back to In progress with a reason. */
    boolean sendBack(String source, String sourceKey, String reason, UUID sentBackByUserId);
}
