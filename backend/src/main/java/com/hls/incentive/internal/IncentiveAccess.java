package com.hls.incentive.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Who may see and who may decide which incentive (spec 030 FR-006, FR-007, FR-016; Constitution Principle III).
 * Admin and Director see everything. A Manager sees the incentives they earned and those of the Schools in a Zone they
 * manage; a School's assigned Manager who is not its Zone Manager sees only their own. Only the Director and that School's Zone Manager decide (only the Director reopens); the Zone is read
 * live from the School, so a change of Zone Manager applies on the next call. Anything outside the scope is not found.
 */
@Component
public class IncentiveAccess {

    /** The signed-in user. */
    public record Caller(UUID userId, Set<Role> roles) {}

    private final ScopeQueries scopes;
    private final SchoolDirectory schools;
    private final IncentiveRepository incentives;

    IncentiveAccess(ScopeQueries scopes, SchoolDirectory schools, IncentiveRepository incentives) {
        this.scopes = scopes;
        this.schools = schools;
        this.incentives = incentives;
    }

    public boolean orgWide(Caller caller) {
        return CallerContext.isOrgWide(caller.roles());
    }

    boolean director(Caller caller) {
        return caller.roles().contains(Role.DIRECTOR);
    }

    ScopeView scope(Caller caller) {
        return scopes.scopeOf(caller.userId(), caller.roles());
    }

    /**
     * True when the caller is a Zone Manager of the School's current Zone. A Manager merely assigned to the School
     * (not to its Zone) is not: they see only what they earned themselves.
     */
    boolean zoneManagerOf(Caller caller, ScopeView scope, UUID schoolId, UUID zoneId) {
        if (!caller.roles().contains(Role.MANAGER)) {
            return false;
        }
        return zoneId != null && scope.zoneIds().contains(zoneId);
    }

    boolean zoneManagerOf(Caller caller, Incentive incentive) {
        ScopeView scope = scope(caller);
        return zoneManagerOf(caller, scope, incentive.getSchoolId(), liveZone(incentive));
    }

    /** The Director, or the Zone Manager of the School's Zone. Grants are checked by the caller of this method. */
    boolean canDecide(Caller caller, Incentive incentive) {
        return director(caller) || zoneManagerOf(caller, incentive);
    }

    boolean canSee(Caller caller, Incentive incentive) {
        return orgWide(caller) || incentive.getEarnerUserId().equals(caller.userId()) || zoneManagerOf(caller, incentive);
    }

    UUID liveZone(Incentive incentive) {
        return schools.school(incentive.getSchoolId()).map(SchoolInfo::zoneId).orElse(incentive.getZoneId());
    }

    /** The incentive, or not found when it is outside the caller's scope. */
    Incentive visible(Caller caller, UUID id) {
        Incentive incentive = incentives.findById(id).orElseThrow(() -> new NotFoundException("Incentive not found."));
        if (!canSee(caller, incentive)) {
            throw new NotFoundException("Incentive not found.");
        }
        return incentive;
    }

    /** The same rule as a query filter, for lists, summaries and exports. */
    Specification<Incentive> visibleTo(Caller caller) {
        if (orgWide(caller)) {
            return (root, query, cb) -> cb.conjunction();
        }
        ScopeView scope = scope(caller);
        // Zones are read live from the Schools (as canSee does), not from the Zone stored on the incentive.
        Set<UUID> liveSchoolIds = schoolIdsInZones(scope.zoneIds());
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> any = new ArrayList<>();
            any.add(cb.equal(root.get("earnerUserId"), caller.userId()));
            if (!liveSchoolIds.isEmpty()) {
                any.add(root.get("schoolId").in(liveSchoolIds));
            }
            return cb.or(any.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** The Schools that are in these Zones right now. */
    Set<UUID> schoolIdsInZones(java.util.Collection<UUID> zoneIds) {
        Set<UUID> result = new HashSet<>();
        for (UUID zoneId : zoneIds) {
            schools.schoolsInZone(zoneId).forEach(s -> result.add(s.id()));
        }
        return result;
    }

    Optional<SchoolInfo> school(UUID schoolId) {
        return schools.school(schoolId);
    }
}
