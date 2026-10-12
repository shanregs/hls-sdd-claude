package com.hls.tasks.internal;

import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the two audit trails of a task change: the task's own append-only history (what the screen shows) and Change
 * History (the organization-wide audit). Call inside the transaction of the change.
 */
@Component
class TaskRecorder {

    /** Changes made by the system itself (the daily check, other modules) are attributed to this id in Change History. */
    static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final TaskHistoryRepository history;
    private final ChangeRecorder changes;
    private final Clock clock;

    TaskRecorder(TaskHistoryRepository history, ChangeRecorder changes, Clock clock) {
        this.history = history;
        this.changes = changes;
        this.clock = clock;
    }

    /** One history line; also one Change History entry for the field when the value moved. */
    void change(Task task, String event, UUID actor, String field, Object before, Object after, String note) {
        history.save(new TaskHistory(
                task.getId(),
                event,
                actor,
                clock.instant(),
                before == null ? null : String.valueOf(before),
                after == null ? null : String.valueOf(after),
                note));
        changes.record(actor == null ? SYSTEM_ACTOR : actor, "TASK", task.getId(), field, before, after);
    }

    /** A creation: one history line and one Change History lifecycle entry. */
    void created(Task task, UUID actor, String detail) {
        history.save(new TaskHistory(task.getId(), "CREATED", actor, clock.instant(), null, task.getStatus().name(), detail));
        changes.recordLifecycle(actor == null ? SYSTEM_ACTOR : actor, "TASK", task.getId(), "created", detail);
    }

    /** A history line that is not a field change (evidence, notices). */
    void note(Task task, String event, UUID actor, String detail) {
        history.save(new TaskHistory(task.getId(), event, actor, clock.instant(), null, null, detail));
        changes.recordLifecycle(actor == null ? SYSTEM_ACTOR : actor, "TASK", task.getId(), event.toLowerCase(), detail);
    }
}
