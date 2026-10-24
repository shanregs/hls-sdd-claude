package com.hls.tasks.internal;

import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Overdue, awaiting verification and escalated task counts per Manager for the comparison table. */
@Component
class TaskManagerColumns implements ManagerColumns {

    private final TaskQueries queries;

    TaskManagerColumns(TaskQueries queries) {
        this.queries = queries;
    }

    @Override
    public List<Column> columns() {
        return List.of(
                new Column("tasks.overdue", "Overdue", "/operations/tasks?view=overdue&assigneeUserId={managerId}"),
                new Column("tasks.awaiting", "Awaiting verification", "/operations/tasks?view=awaiting&assigneeUserId={managerId}"),
                new Column("tasks.escalated", "Escalated", "/operations/tasks?view=escalated&assigneeUserId={managerId}"));
    }

    @Override
    public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        Map<UUID, TaskQueries.Summary> counted = queries.summariesByAssignee(managerUserIds);
        for (UUID id : managerUserIds) {
            TaskQueries.Summary s = counted.get(id);
            result.put(id, s == null
                    ? Map.of("tasks.overdue", 0L, "tasks.awaiting", 0L, "tasks.escalated", 0L)
                    : Map.of("tasks.overdue", s.overdue(), "tasks.awaiting", s.awaiting(), "tasks.escalated", s.escalated()));
        }
        return result;
    }
}
