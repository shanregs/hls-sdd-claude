package com.hls.organization.internal;

import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives a caller's scope from the current assignments on every call (no cache), so assignment
 * changes apply on the next request. Manager scope is decided from the user's <em>current</em>
 * roles and state, not from the token, so losing the Manager role empties it immediately.
 */
@Service
@Transactional(readOnly = true)
public class ScopeService implements ScopeQueries {

    private final ManagerRepository managerRepository;
    private final ZoneManagerAssignmentRepository zoneAssignments;
    private final SchoolManagerAssignmentRepository schoolAssignments;
    private final UserAdminService userAdminService;

    public ScopeService(
            ManagerRepository managerRepository,
            ZoneManagerAssignmentRepository zoneAssignments,
            SchoolManagerAssignmentRepository schoolAssignments,
            UserAdminService userAdminService) {
        this.managerRepository = managerRepository;
        this.zoneAssignments = zoneAssignments;
        this.schoolAssignments = schoolAssignments;
        this.userAdminService = userAdminService;
    }

    @Override
    public ScopeView scopeOf(UUID userId, Set<Role> roles) {
        if (CallerContext.isOrgWide(roles)) {
            return ScopeView.everything();
        }
        if (!roles.contains(Role.MANAGER) || !isLiveManager(userId)) {
            return ScopeView.none();
        }
        return managerRepository
                .findByUserId(userId)
                .filter(Manager::isActive)
                .map(manager -> new ScopeView(
                        false,
                        zoneAssignments.findByManagerIdAndEndsOnIsNull(manager.getId()).stream()
                                .map(ZoneManagerAssignment::getZoneId)
                                .collect(Collectors.toSet()),
                        schoolAssignments.findByManagerIdAndEndsOnIsNull(manager.getId()).stream()
                                .map(SchoolManagerAssignment::getSchoolId)
                                .collect(Collectors.toSet())))
                .orElse(ScopeView.none());
    }

    private boolean isLiveManager(UUID userId) {
        return userAdminService
                .find(userId)
                .map(user -> user.user().isActive() && user.roles().contains(Role.MANAGER))
                .orElse(false);
    }
}
