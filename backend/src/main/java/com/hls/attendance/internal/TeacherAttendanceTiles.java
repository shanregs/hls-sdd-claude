package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.WorkingCalendar;
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
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherScopeQueries;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code attendance.awayToday} (spec 028 US4): active Teachers in the caller's scope with an absence or approved leave mark
 * today, via {@link AttendanceReadApi#teachersAwayOn}. On a weekly off or holiday it says so instead of showing 0.
 */
@Component
class TeacherAttendanceTiles implements TileSource {

    private static final String KEY = "attendance.awayToday";

    private final AttendanceReadApi attendance;
    private final WorkingCalendar calendar;
    private final TeacherDirectory teachers;
    private final TeacherScopeQueries scopeQueries;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;

    TeacherAttendanceTiles(
            AttendanceReadApi attendance,
            WorkingCalendar calendar,
            TeacherDirectory teachers,
            TeacherScopeQueries scopeQueries,
            TeacherPlacementSource placements,
            SchoolDirectory schools) {
        this.attendance = attendance;
        this.calendar = calendar;
        this.teachers = teachers;
        this.scopeQueries = scopeQueries;
        this.placements = placements;
        this.schools = schools;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.ATTENDANCE_LEAVE, 1, "Teachers away today", PeriodKind.TODAY, "ATTENDANCE", PermissionAction.VIEW,
                ScopeKind.ALL, true, Link.to("/operations/attendance", "filter", "away")));
    }

    @Override
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        if (calendar.workingDays(null, ctx.today(), ctx.today()).isEmpty()) {
            return TileResult.notWorkingDay(KEY, "Not a working day");
        }
        TeacherScopeQueries.TeacherScope scope = scopeQueries.teacherScope(ctx.userId(), ctx.roles());
        Set<UUID> inZone = null;
        if (ctx.zoneId().isPresent()) {
            Set<UUID> schoolIds = schools.schoolsInZone(ctx.zoneId().get()).stream()
                    .map(SchoolDirectory.SchoolInfo::id)
                    .collect(Collectors.toSet());
            inZone = schoolIds.isEmpty() ? Set.of() : placements.teacherIdsAtSchoolsOn(schoolIds, ctx.today());
        }
        Set<UUID> zoneIds = inZone;
        Set<UUID> candidates = teachers.activeTeachers().stream()
                .map(TeacherDirectory.TeacherInfo::id)
                .filter(scope::allows)
                .filter(id -> zoneIds == null || zoneIds.contains(id))
                .collect(Collectors.toSet());
        long away = candidates.isEmpty() ? 0 : attendance.teachersAwayOn(candidates, ctx.today()).size();
        return TileResult.ok(KEY, Value.count("Away", away, tile.link()));
    }
}
