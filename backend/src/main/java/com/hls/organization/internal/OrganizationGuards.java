package com.hls.organization.internal;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.school.api.ConflictException;
import com.hls.school.api.SchoolChangeGuard;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolScopeProvider;
import com.hls.school.api.ZoneChangeGuard;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hooks {@code school} defines for its dependents (research.md sections 2 and 17), implemented
 * here: Manager scope, the Manager-covers-Zone veto on School moves, and the Zone delete veto.
 */
@Component
public class OrganizationGuards implements SchoolScopeProvider, SchoolChangeGuard, ZoneChangeGuard {

    private final ScopeQueries scopeQueries;
    private final ManagerService managerService;
    private final SchoolManagerAssignmentRepository schoolAssignments;
    private final ZoneManagerAssignmentRepository zoneAssignments;
    private final SchoolDirectory schoolDirectory;

    public OrganizationGuards(
            ScopeQueries scopeQueries,
            ManagerService managerService,
            SchoolManagerAssignmentRepository schoolAssignments,
            ZoneManagerAssignmentRepository zoneAssignments,
            SchoolDirectory schoolDirectory) {
        this.scopeQueries = scopeQueries;
        this.managerService = managerService;
        this.schoolAssignments = schoolAssignments;
        this.zoneAssignments = zoneAssignments;
        this.schoolDirectory = schoolDirectory;
    }

    @Override
    public Optional<Set<UUID>> visibleSchoolIds(UUID userId, Set<Role> roles) {
        if (!roles.contains(Role.MANAGER)) {
            return Optional.empty();
        }
        return Optional.of(scopeQueries.scopeOf(userId, roles).schoolIds());
    }

    @Override
    @Transactional
    public void checkPlaceChange(UUID schoolId, UUID newZoneId) {
        Optional<SchoolManagerAssignment> row = schoolAssignments.findBySchoolIdAndEndsOnIsNull(schoolId);
        if (row.isEmpty()) {
            return;
        }
        managerService.lock(List.of(row.get().getManagerId()));
        if (!zoneAssignments.existsByZoneIdAndManagerIdAndEndsOnIsNull(newZoneId, row.get().getManagerId())) {
            String zoneName =
                    schoolDirectory.zone(newZoneId).map(SchoolDirectory.ZoneInfo::name).orElse("?");
            throw new ConflictException("The School's Manager must cover the School's Zone, and does not cover "
                    + zoneName + ". Reassign the School's Manager first.");
        }
    }

    @Override
    public void checkDelete(UUID zoneId) {
        if (zoneAssignments.existsByZoneIdAndEndsOnIsNull(zoneId)) {
            throw new ConflictException("This Zone cannot be deleted: Managers are still assigned to it.");
        }
    }
}
