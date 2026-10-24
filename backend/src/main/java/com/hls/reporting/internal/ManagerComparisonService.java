package com.hls.reporting.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.organization.api.ManagerQueries;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Merges the {@link ManagerColumns} of every module into one Manager-by-Manager table (spec 028 US2) and offers it as the
 * {@code managers.comparison} tile. A fixed number of queries: each source answers for all Managers at once.
 */
@Component
public class ManagerComparisonService implements TileSource {

    public static final String KEY = "managers.comparison";

    private final List<ManagerColumns> sources;
    private final ManagerQueries managers;

    public ManagerComparisonService(List<ManagerColumns> sources, ManagerQueries managers) {
        this.sources = sources;
        this.managers = managers;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.MANAGERS, 100, "Managers compared", PeriodKind.MONTH, "TASKS", PermissionAction.VIEW,
                ScopeKind.ORG_WIDE_ONLY, true, Link.to("/operations/tasks")));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        return compare(ctx, null, null);
    }

    /** {@code sort} is a column key (or {@code name}); {@code dir} is {@code asc} or {@code desc}. */
    public TileResult compare(TileContext ctx, String sort, String dir) {
        List<ManagerColumns.Column> columns = new ArrayList<>();
        sources.forEach(s -> columns.addAll(s.columns()));
        List<ManagerQueries.ManagerRef> rows = ctx.zoneId().isPresent()
                ? managers.managersOfZone(ctx.zoneId().get())
                : managers.allManagers().stream().filter(ManagerQueries.ManagerRef::active).toList();
        if (rows.isEmpty()) {
            return TileResult.noData(KEY, "No Managers");
        }
        Set<UUID> userIds = rows.stream().map(ManagerQueries.ManagerRef::userId).collect(Collectors.toSet());
        Map<UUID, Map<String, Long>> figures = new HashMap<>();
        for (ManagerColumns source : sources) {
            source.figures(ctx, userIds)
                    .forEach((id, byKey) -> figures.computeIfAbsent(id, k -> new HashMap<>()).putAll(byKey));
        }
        Comparator<ManagerQueries.ManagerRef> order =
                Comparator.comparing(ManagerQueries.ManagerRef::displayName, String.CASE_INSENSITIVE_ORDER);
        if (sort != null && !sort.isBlank() && !sort.equals("name")) {
            if (columns.stream().noneMatch(c -> c.key().equals(sort))) {
                throw new DashboardInvalidInputException("Unknown sort column.");
            }
            Comparator<ManagerQueries.ManagerRef> byFigure =
                    Comparator.comparingLong(m -> figures.getOrDefault(m.userId(), Map.of()).getOrDefault(sort, 0L));
            order = "asc".equalsIgnoreCase(dir) ? byFigure.thenComparing(order) : byFigure.reversed().thenComparing(order);
        } else if ("desc".equalsIgnoreCase(dir)) {
            order = order.reversed();
        }
        List<List<Value>> out = new ArrayList<>();
        for (ManagerQueries.ManagerRef m : rows.stream().sorted(order).toList()) {
            List<Value> cells = new ArrayList<>();
            cells.add(Value.text("Manager", m.displayName(), null));
            for (ManagerColumns.Column c : columns) {
                Long figure = figures.getOrDefault(m.userId(), Map.of()).get(c.key());
                Link link = linkFor(c.linkTemplate(), m.userId());
                cells.add(figure == null ? Value.text(c.label(), "-", link) : Value.count(c.label(), figure, link));
            }
            out.add(cells);
        }
        List<String> labels = new ArrayList<>();
        labels.add("Manager");
        columns.forEach(c -> labels.add(c.label()));
        return TileResult.ok(KEY, new TileResult.Table(labels, out));
    }

    private static Link linkFor(String template, UUID managerUserId) {
        if (template == null) {
            return null;
        }
        String resolved = template.replace("{managerId}", managerUserId.toString());
        int q = resolved.indexOf('?');
        if (q < 0) {
            return Link.to(resolved);
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : resolved.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        return new Link(resolved.substring(0, q), query);
    }
}
