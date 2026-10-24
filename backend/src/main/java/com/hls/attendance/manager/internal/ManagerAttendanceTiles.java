package com.hls.attendance.manager.internal;

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
import com.hls.school.api.CallerContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * {@code managers.noCheckIn} (spec 028 US2) and the "days with no check-in" column of the comparison table. The tile's
 * figure is the Manager Attendance "missed" list for today; on a weekly off or holiday it says so instead of showing 0.
 */
@Component
class ManagerAttendanceTiles implements TileSource, ManagerColumns {

    static final String KEY = "managers.noCheckIn";

    private final MissedCheckInService missed;
    private final ManagerQueries managers;

    ManagerAttendanceTiles(MissedCheckInService missed, ManagerQueries managers) {
        this.missed = missed;
        this.managers = managers;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.MANAGERS, 7, "Managers with no check-in", PeriodKind.TODAY, "MANAGER_ATTENDANCE",
                PermissionAction.VIEW, ScopeKind.ALL, true,
                Link.to("/operations/manager-attendance", "filter", "missing"), "MY_MANAGER_ATTENDANCE", PermissionAction.VIEW));
    }

    @Override
    public TileResult read(TileDefinition tile, TileContext ctx) {
        if (missed.isNonWorkingDay(ctx.today())) {
            return TileResult.notWorkingDay(KEY, "Not a working day");
        }
        Set<UUID> allowed = allowedManagerIds(ctx);
        long count = missed.missedOn(ctx.today(), missed.now()).stream()
                .filter(m -> allowed == null || allowed.contains(m.managerId()))
                .count();
        return TileResult.ok(KEY, Value.count("No check-in", count, tile.link()));
    }

    /** Null means every Manager; otherwise the Manager ids the caller and the chosen Zone allow. */
    private Set<UUID> allowedManagerIds(TileContext ctx) {
        Set<UUID> allowed = null;
        if (!CallerContext.isOrgWide(ctx.roles())) {
            allowed = managers.managerByUserId(ctx.userId()).map(m -> Set.of(m.id())).orElse(Set.of());
        }
        if (ctx.zoneId().isPresent()) {
            Set<UUID> inZone = managers.managersOfZone(ctx.zoneId().get()).stream()
                    .map(ManagerQueries.ManagerRef::id)
                    .collect(Collectors.toSet());
            allowed = allowed == null ? inZone : allowed.stream().filter(inZone::contains).collect(Collectors.toSet());
        }
        return allowed;
    }

    @Override
    public List<Column> columns() {
        return List.of(new Column(
                "attendance.noCheckInDays", "Days with no check-in", "/operations/manager-attendance?managerId={managerId}"));
    }

    @Override
    public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
        Map<UUID, UUID> managerIdByUser = new HashMap<>();
        for (UUID userId : managerUserIds) {
            managers.managerByUserId(userId).ifPresent(m -> managerIdByUser.put(userId, m.id()));
        }
        Map<UUID, Long> days = missed.missedDaysInMonth(ctx.month(), managerIdByUser.values());
        Map<UUID, Map<String, Long>> result = new HashMap<>();
        managerIdByUser.forEach((userId, managerId) ->
                result.put(userId, Map.of("attendance.noCheckInDays", days.getOrDefault(managerId, 0L))));
        return result;
    }
}
