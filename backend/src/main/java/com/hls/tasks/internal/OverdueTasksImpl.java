package com.hls.tasks.internal;

import com.hls.schoolbilling.api.OverdueTasks;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskCreation.FollowUpRequest;
import com.hls.tasks.api.TaskType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Spec 022's follow-up tasks for overdue monthly dues, served by the task interface (contract C2). */
@Component
class OverdueTasksImpl implements OverdueTasks {

    private final TaskCreation tasks;

    OverdueTasksImpl(TaskCreation tasks) {
        this.tasks = tasks;
    }

    @Override
    public UUID createFollowUp(Request r) {
        return tasks.createFollowUp(new FollowUpRequest(
                        TaskType.FOLLOW_UP,
                        r.title(),
                        r.description(),
                        r.assigneeUserId(),
                        r.dueOn(),
                        r.escalateAfterDays(),
                        r.linkedType(),
                        r.linkedId(),
                        r.linkedLabel(),
                        r.place(),
                        r.source(),
                        r.sourceKey(),
                        null))
                .id();
    }

    @Override
    public boolean complete(String source, String sourceKey, String note, UUID completedByUserId) {
        return tasks.complete(source, sourceKey, note, completedByUserId);
    }

    @Override
    public boolean cancel(String source, String sourceKey, String reason) {
        return tasks.cancel(source, sourceKey, reason);
    }

    @Override
    public boolean reassign(String source, String sourceKey, UUID assigneeUserId) {
        return tasks.reassign(source, sourceKey, assigneeUserId);
    }
}
