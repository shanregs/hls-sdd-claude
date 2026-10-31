package com.hls.tasks.internal;

import com.hls.tasks.api.TaskActivities;
import com.hls.designation.api.BusinessDate;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Contract C1: tasks as planned activities. */
@Component
class TaskActivitiesImpl implements TaskActivities {

    private final TaskRepository tasks;
    private final TaskHistoryRepository history;
    private final BusinessDate businessDate;

    TaskActivitiesImpl(TaskRepository tasks, TaskHistoryRepository history, BusinessDate businessDate) {
        this.tasks = tasks;
        this.history = history;
        this.businessDate = businessDate;
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

    /** Spec 035: every task due in the range in any status; the due-date history is read in one bulk query. */
    @Override
    @Transactional(readOnly = true)
    public List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID ownerUserId) {
        List<Task> found = ownerUserId == null
                ? tasks.findByDueOnBetween(from, to)
                : tasks.findByAssigneeUserIdAndDueOnBetween(ownerUserId, from, to);
        if (found.isEmpty()) {
            return List.of();
        }
        // the first planned date is the before value of the earliest DUE_CHANGED row; unparsable rows are ignored
        Map<UUID, LocalDate> original = new HashMap<>();
        for (TaskHistory h : history.findByTaskIdInAndEventOrderByAtAsc(found.stream().map(Task::getId).toList(), "DUE_CHANGED")) {
            if (original.containsKey(h.getTaskId()) || h.getBeforeValue() == null) {
                continue;
            }
            try {
                original.put(h.getTaskId(), LocalDate.parse(h.getBeforeValue()));
            } catch (java.time.format.DateTimeParseException ignored) {
                // not an ISO date: leave it out
            }
        }
        java.time.ZoneId zone = businessDate.zone();
        return found.stream()
                .sorted(java.util.Comparator.comparing(Task::getDueOn).thenComparing(Task::getCreatedAt))
                .map(t -> {
                    boolean done = t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.VERIFIED;
                    LocalDate completedOn = done && t.getCompletedAt() != null
                            ? t.getCompletedAt().atZone(zone).toLocalDate()
                            : null;
                    return new ActivityEntry(
                            t.getType().name(),
                            t.getId(),
                            t.getAssigneeUserId(),
                            t.getDueOn(),
                            original.get(t.getId()),
                            t.getPlace(),
                            t.getLinkedType(),
                            t.getLinkedId(),
                            t.getStatus().name(),
                            completedOn,
                            t.isSameDay());
                })
                .toList();
    }
}
