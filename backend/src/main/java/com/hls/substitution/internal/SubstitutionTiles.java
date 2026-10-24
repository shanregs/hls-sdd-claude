package com.hls.substitution.internal;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code operations.substitutions} (spec 028 US4): today's requirements still open, assigned and covered by a Manager, as the
 * Substitutions list shows them. Cancelled ones are not counted. The caller's School scope and the chosen Zone both apply.
 */
@Component
class SubstitutionTiles implements TileSource {

    private static final String KEY = "operations.substitutions";
    private static final UUID NOBODY = new UUID(0L, 0L);

    private final SubstitutionRepository requirements;
    private final SubstitutionScope scope;
    private final SchoolDirectory schools;

    SubstitutionTiles(SubstitutionRepository requirements, SubstitutionScope scope, SchoolDirectory schools) {
        this.requirements = requirements;
        this.scope = scope;
        this.schools = schools;
    }

    @Override
    public List<TileDefinition> tiles() {
        return List.of(new TileDefinition(
                KEY, Area.TEACHERS, 2, "Substitutions today", PeriodKind.TODAY, "SUBSTITUTION", PermissionAction.VIEW,
                ScopeKind.ALL, true, Link.to("/operations/substitutions", "date", "today")));
    }

    @Override
    @Transactional(readOnly = true)
    public TileResult read(TileDefinition tile, TileContext ctx) {
        SubstitutionScope.Scope allowed = scope.of(ctx.userId(), ctx.roles());
        boolean all = allowed.all();
        Set<UUID> schoolIds = new HashSet<>(allowed.schoolIds());
        if (ctx.zoneId().isPresent()) {
            Set<UUID> inZone = schools.schoolsInZone(ctx.zoneId().get()).stream()
                    .map(SchoolDirectory.SchoolInfo::id)
                    .collect(Collectors.toSet());
            if (all) {
                schoolIds = inZone;
                all = false;
            } else {
                schoolIds.retainAll(inZone);
            }
        }
        if (schoolIds.isEmpty()) {
            schoolIds.add(NOBODY);
        }
        Link link = tile.link();
        return TileResult.ok(KEY,
                Value.count("Open", count(RequirementStatus.OPEN, all, schoolIds, ctx), link),
                Value.count("Assigned", count(RequirementStatus.ASSIGNED, all, schoolIds, ctx), link),
                Value.count("Manager cover", count(RequirementStatus.MANAGER_COVER, all, schoolIds, ctx), link));
    }

    private long count(RequirementStatus status, boolean all, Set<UUID> schoolIds, TileContext ctx) {
        return requirements
                .search(all, schoolIds, false, NOBODY, Set.of(status), ctx.today(), ctx.today(), PageRequest.of(0, 1))
                .getTotalElements();
    }
}
