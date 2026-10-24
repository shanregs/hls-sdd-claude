package com.hls.leave.manager.internal;

import com.hls.attendance.api.PendingForgottenCheckIns;
import com.hls.identity.permissions.PermissionAction;
import com.hls.leave.manager.internal.ManagerLeaveRequest.Status;
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
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code managers.onLeaveToday} and {@code managers.pendingRequests} (spec 028 US2). */
@Component
class ManagerLeaveTiles implements TileSource {

    private static final String ON_LEAVE = "managers.onLeaveToday";
    private static final String PENDING = "managers.pendingRequests";

    private final ManagerLeaveRequestRepository requests;
    private final PendingForgottenCheckIns forgotten;
    private final ManagerQueries managers;

    ManagerLeaveTiles(
            ManagerLeaveRequestRepository requests, PendingForgottenCheckIns forgotten, ManagerQueries managers) {
        this.requests = requests;
        this.forgotten = forgotten;
        this.managers = managers;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(
                new TileDefinition(ON_LEAVE, Area.MANAGERS, 8, "Managers on leave today", PeriodKind.TODAY, "MANAGER_LEAVE",
                        PermissionAction.VIEW, ScopeKind.ALL, true,
                        Link.to("/operations/manager-leave", "status", "APPROVED"), "MY_MANAGER_ATTENDANCE", PermissionAction.VIEW),
                new TileDefinition(PENDING, Area.MANAGERS, 9, "Manager requests awaiting a decision", PeriodKind.ALL_TIME,
                        "MANAGER_LEAVE", PermissionAction.VIEW, ScopeKind.ALL, true,
                        Link.to("/operations/manager-leave", "status", "PENDING"), "MY_MANAGER_ATTENDANCE", PermissionAction.VIEW));
    }

    private Set<UUID> allowed(TileContext ctx) {
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
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        Set<UUID> allowed = allowed(ctx);
        if (tile.key().equals(ON_LEAVE)) {
            long n = requests.findByStatusOrderByRequestedAtAsc(Status.APPROVED).stream()
                    .filter(r -> !r.getFirstDate().isAfter(ctx.today()) && !r.getLastDate().isBefore(ctx.today()))
                    .filter(r -> allowed == null || allowed.contains(r.getManagerId()))
                    .map(ManagerLeaveRequest::getManagerId)
                    .distinct()
                    .count();
            return TileResult.ok(ON_LEAVE, Value.count("On leave", n, tile.link()));
        }
        long leave = requests.findByStatusOrderByRequestedAtAsc(Status.PENDING).stream()
                .filter(r -> allowed == null || allowed.contains(r.getManagerId()))
                .count();
        long checkIns = forgotten.pendingByManager().entrySet().stream()
                .filter(e -> allowed == null || allowed.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        return TileResult.ok(PENDING,
                Value.count("Leave", leave, tile.link()),
                Value.count("Forgotten check-ins", checkIns, Link.to("/operations/manager-attendance", "view", "regularizations")));
    }
}
