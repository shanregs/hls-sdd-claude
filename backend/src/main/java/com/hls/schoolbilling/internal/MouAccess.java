package com.hls.schoolbilling.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.school.api.CallerContext;
import com.hls.school.api.NotFoundException;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Grant, role and scope checks shared by the MoU document and approval services (spec 031). */
@Component
class MouAccess {

    private final ContractRepository contracts;
    private final ScopeQueries scopeQueries;
    private final PermissionGuard guard;

    MouAccess(ContractRepository contracts, ScopeQueries scopeQueries, PermissionGuard guard) {
        this.contracts = contracts;
        this.scopeQueries = scopeQueries;
        this.guard = guard;
    }

    PermissionGuard guard() {
        return guard;
    }

    /** The contract, or not found when its School is outside the caller's scope (Constitution Principle III). */
    Contract contractInScope(UUID userId, Set<Role> roles, UUID contractId) {
        Contract contract = contracts.findById(contractId).orElseThrow(() -> new NotFoundException("Contract not found."));
        if (!CallerContext.isOrgWide(roles) && !scopeQueries.scopeOf(userId, roles).allowsSchool(contract.getSchoolId())) {
            throw new NotFoundException("Contract not found.");
        }
        return contract;
    }

    void requireGrant(Set<Role> roles, PermissionAction action) {
        guard.require(roles, PermissionModule.SCHOOL_CONTRACTS, action);
    }

    /** Upload, replace, remove and submit are for Admin and Director only, even when a Manager was granted Edit. */
    void requireStaffRole(Set<Role> roles) {
        if (!MouAllowedActions.isStaff(roles)) {
            throw new AccessDeniedException("Only Admin and Director can change a MoU document or submit it.");
        }
    }

    /** Approve, reject and amend are for the Director and the Zone Manager; never Admin. */
    void requireApproverRole(Set<Role> roles) {
        if (!MouAllowedActions.isApprover(roles)) {
            throw new AccessDeniedException("Only a Director or the Zone Manager can decide a MoU.");
        }
    }

    /** The role recorded on a history entry. */
    static String actorRole(Set<Role> roles) {
        if (roles.contains(Role.DIRECTOR)) {
            return Role.DIRECTOR.name();
        }
        if (roles.contains(Role.ADMIN)) {
            return Role.ADMIN.name();
        }
        return Role.MANAGER.name();
    }
}
