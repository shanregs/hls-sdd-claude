package com.hls.tasks.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import com.hls.tasks.internal.TaskService.Caller;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The five Manager task tiles of the management Dashboard (spec 028 US2); every figure is the Tasks list count for the link's view. */
@Component
class TaskTiles implements TileSource {

    private static final String MODULE = "TASKS";

    private final TaskQueries queries;
    private final ManagerQueries managers;

    TaskTiles(TaskQueries queries, ManagerQueries managers) {
        this.queries = queries;
        this.managers = managers;
    }

    private static TileDefinition def(String suffix, int order, String label) {
        return new TileDefinition(
                "managers.tasks." + suffix, Area.MANAGERS, order, label, PeriodKind.TODAY, MODULE, PermissionAction.VIEW,
                ScopeKind.ALL, true, Link.to("/operations/tasks", "view", suffix));
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                def("today", 1, "Tasks due today"),
                def("upcoming", 2, "Upcoming tasks"),
                def("overdue", 3, "Overdue tasks"),
                def("awaiting", 4, "Tasks awaiting verification"),
                def("escalated", 5, "Escalated tasks"));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        TaskQueries.Summary s = ctx.memo("tasks.summary", () -> summary(ctx));
        long n =
                switch (tile.key()) {
                    case "managers.tasks.today" -> s.today();
                    case "managers.tasks.upcoming" -> s.upcoming();
                    case "managers.tasks.overdue" -> s.overdue();
                    case "managers.tasks.awaiting" -> s.awaiting();
                    case "managers.tasks.escalated" -> s.escalated();
                    default -> throw new IllegalArgumentException("Unknown tile " + tile.key());
                };
        return TileResult.ok(tile.key(), Value.count(tile.label(), n, tile.link()));
    }

    private TaskQueries.Summary summary(TileContext ctx) {
        Caller caller = new Caller(ctx.userId(), ctx.roles());
        if (ctx.zoneId().isEmpty()) {
            return queries.summary(caller, null);
        }
        Set<UUID> inZone = managers.managersOfZone(ctx.zoneId().get()).stream()
                .map(ManagerQueries.ManagerRef::userId)
                .collect(Collectors.toSet());
        return queries.summaryForAssignees(caller, inZone);
    }
}
