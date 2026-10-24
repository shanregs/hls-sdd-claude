package com.hls.teacher.internal;

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
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherScopeQueries;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code teachers.byStatus} (spec 028 US4): Teachers in training, active, on leave and exited by their stored status, within the
 * caller's Teacher scope. Under a Zone only Teachers placed today at a School of that Zone are counted.
 */
@Component
class TeacherTiles implements TileSource {

    private static final String KEY = "teachers.byStatus";

    private final TeacherRepository teachers;
    private final TeacherScopeQueries scopeQueries;
    private final TeacherPlacementSource placements;
    private final SchoolDirectory schools;

    TeacherTiles(
            TeacherRepository teachers,
            TeacherScopeQueries scopeQueries,
            TeacherPlacementSource placements,
            SchoolDirectory schools) {
        this.teachers = teachers;
        this.scopeQueries = scopeQueries;
        this.placements = placements;
        this.schools = schools;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.TEACHERS, 1, "Teachers by status", PeriodKind.ALL_TIME, "TEACHERS", PermissionAction.VIEW,
                ScopeKind.ALL, true, Link.to("/master-data/teachers")));
    }

    @Override
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        TeacherScopeQueries.TeacherScope scope = scopeQueries.teacherScope(ctx.userId(), ctx.roles());
        Set<UUID> inZone = null;
        if (ctx.zoneId().isPresent()) {
            Set<UUID> schoolIds = schools.schoolsInZone(ctx.zoneId().get()).stream()
                    .map(SchoolDirectory.SchoolInfo::id)
                    .collect(Collectors.toSet());
            inZone = schoolIds.isEmpty() ? Set.of() : placements.teacherIdsAtSchoolsOn(schoolIds, ctx.today());
        }
        Set<UUID> zoneIds = inZone;
        Map<TeacherStatus, Long> counts = new EnumMap<>(TeacherStatus.class);
        for (TeacherStatus s : TeacherStatus.values()) {
            counts.put(s, 0L);
        }
        for (Teacher t : teachers.findAll()) {
            if (scope.allows(t.getId()) && (zoneIds == null || zoneIds.contains(t.getId()))) {
                counts.merge(t.getStatus(), 1L, Long::sum);
            }
        }
        return TileResult.ok(KEY,
                value("In training", TeacherStatus.IN_TRAINING, counts),
                value("Active", TeacherStatus.ACTIVE, counts),
                value("On leave", TeacherStatus.ON_LEAVE, counts),
                value("Exited", TeacherStatus.EXITED, counts));
    }

    private static Value value(String label, TeacherStatus status, Map<TeacherStatus, Long> counts) {
        return Value.count(label, counts.get(status), Link.to("/master-data/teachers", "status", status.name()));
    }
}
