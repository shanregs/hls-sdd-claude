package com.hls.content.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskCreation.TaskRef;
import com.hls.tasks.api.TaskProgress;
import com.hls.tasks.api.TaskType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps the optional Manager task of a content piece in step with it (spec 029 FR-003, FR-009). A task exists only when the
 * author is an active Manager and the content has a due date; every other content has none and nothing here runs.
 */
@Component
class ContentTaskBridge {

    static final String SOURCE = "CONTENT";

    private final TaskCreation tasks;
    private final TaskProgress progress;
    private final ManagerQueries managers;

    ContentTaskBridge(TaskCreation tasks, TaskProgress progress, ManagerQueries managers) {
        this.tasks = tasks;
        this.progress = progress;
        this.managers = managers;
    }

    private static String key(Content content) {
        return content.getId().toString();
    }

    boolean eligible(UUID authorUserId, LocalDate dueOn) {
        return dueOn != null && managers.managerByUserId(authorUserId).filter(ManagerQueries.ManagerRef::active).isPresent();
    }

    /** Creates the task when the content qualifies and has none yet; returns the task id, or null. */
    UUID ensureTask(Content content, UUID createdBy) {
        if (!eligible(content.getAuthorUserId(), content.getDueOn())) {
            return null;
        }
        TaskRef ref = tasks.createFollowUp(new FollowUpRequest(
                TaskType.CONTENT_SUBMISSION,
                "Submit content: " + content.getTitle(),
                content.getDescription(),
                content.getAuthorUserId(),
                content.getDueOn(),
                null,
                "CONTENT",
                content.getId(),
                content.getCode(),
                null,
                SOURCE,
                key(content),
                createdBy));
        return ref.id();
    }

    /** After an author or due date change: move, create or cancel the task so it follows the content. */
    UUID resync(Content content, UUID actor) {
        if (!eligible(content.getAuthorUserId(), content.getDueOn())) {
            tasks.cancel(SOURCE, key(content), "The content no longer needs a task");
            return null;
        }
        boolean moved = tasks.reassign(SOURCE, key(content), content.getAuthorUserId());
        boolean rescheduled = tasks.reschedule(SOURCE, key(content), content.getDueOn(), "Submit content: " + content.getTitle(), content.getDescription());
        if (!moved && !rescheduled) {
            return ensureTask(content, actor);
        }
        return content.getTaskId();
    }

    void submitted(Content content, int versionNumber, String fileName, UUID by) {
        tasks.complete(SOURCE, key(content), "Submitted version " + versionNumber + " (" + fileName + ") for review.", by);
    }

    void approved(Content content, UUID reviewer) {
        progress.verify(SOURCE, key(content), reviewer);
    }

    void returned(Content content, String comment, UUID reviewer) {
        progress.sendBack(SOURCE, key(content), comment, reviewer);
    }
}
