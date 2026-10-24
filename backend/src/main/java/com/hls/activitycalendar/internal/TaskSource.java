package com.hls.activitycalendar.internal;

import com.hls.tasks.api.TaskActivities;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Manager tasks (spec 025); every task type becomes kind TASK with the type kept as the sub-kind. */
@Component
public class TaskSource implements ActivitySource {

    private final TaskActivities tasks;

    public TaskSource(TaskActivities tasks) {
        this.tasks = tasks;
    }

    @Override
    public String group() {
        return TASKS;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<CalendarEntry> entries(LocalDate from, LocalDate to, UUID userIdOrNull) {
        return tasks.activitiesBetween(from, to, userIdOrNull).stream()
                .map(e -> new CalendarEntry(
                        "TASK",
                        e.kind(),
                        e.id(),
                        e.date(),
                        e.date(),
                        e.originalDate(),
                        e.ownerUserId(),
                        Set.of(),
                        e.place(),
                        null,
                        null,
                        e.linkedType(),
                        e.linkedId(),
                        e.status(),
                        e.completedOn()))
                .toList();
    }
}
