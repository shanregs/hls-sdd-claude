package com.hls.tasks.internal;

import com.hls.tasks.api.TaskActivities;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Contract C1: tasks as planned activities. */
@Component
class TaskActivitiesImpl implements TaskActivities {

    private final TaskRepository tasks;

    TaskActivitiesImpl(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannedActivity> plannedBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
        List<Task> found = ownerUserId == null
                ? tasks.findByDueOnBetweenAndStatusNot(from, to, TaskStatus.CANCELLED)
                : tasks.findByAssigneeUserIdAndDueOnBetweenAndStatusNot(ownerUserId, from, to, TaskStatus.CANCELLED);
        return found.stream()
                .sorted(java.util.Comparator.comparing(Task::getDueOn).thenComparing(Task::getCreatedAt))
                .map(t -> new PlannedActivity(
                        t.getType().name(), t.getId(), t.getAssigneeUserId(), t.getDueOn(), t.getPlace(), t.getStatus().name()))
                .toList();
    }
}
