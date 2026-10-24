package com.hls.leave.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.reporting.api.Area;
import com.hls.reporting.api.Link;
import com.hls.reporting.api.PeriodKind;
import com.hls.reporting.api.ScopeKind;
import com.hls.reporting.api.TileContext;
import com.hls.reporting.api.TileDefinition;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.Value;
import com.hls.reporting.api.TileSource;
import com.hls.school.api.SchoolDirectory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code leave.pendingTeacher} (spec 028 US4): the same count as the Leave Management pending figure, in the caller's scope. */
@Component
class TeacherLeaveTiles implements TileSource {

    private static final String KEY = "leave.pendingTeacher";

    private final LeaveRequestRepository requests;
    private final LeaveScope scope;
    private final SchoolDirectory schools;

    TeacherLeaveTiles(LeaveRequestRepository requests, LeaveScope scope, SchoolDirectory schools) {
        this.requests = requests;
        this.scope = scope;
        this.schools = schools;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.ATTENDANCE_LEAVE, 2, "Teacher leave awaiting a decision", PeriodKind.ALL_TIME, "LEAVE_MANAGEMENT",
                PermissionAction.VIEW, ScopeKind.ALL, true, Link.to("/operations/leave", "status", "PENDING")));
    }

    @Override
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        LeaveScope.Scope s = scope.of(ctx.userId(), ctx.roles());
        long pending;
        if (ctx.zoneId().isPresent()) {
            Set<UUID> inZone = schools.schoolsInZone(ctx.zoneId().get()).stream()
                    .map(SchoolDirectory.SchoolInfo::id)
                    .collect(Collectors.toSet());
            pending = inZone.isEmpty() ? 0 : requests.countPendingInScopeAtSchools(s.orgWide(), s.teacherIds(), inZone);
        } else {
            pending = requests.countPendingInScope(s.orgWide(), s.teacherIds());
        }
        return TileResult.ok(KEY, Value.count("Pending", pending, tile.link()));
    }
}
