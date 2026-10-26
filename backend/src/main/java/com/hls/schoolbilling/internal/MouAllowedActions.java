package com.hls.schoolbilling.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The actions the caller may take on a MoU, from grants, role, contract state and approval status (spec 031 contracts).
 * The server checks the same rules again on every action; this list only says which buttons to show. Scope is not
 * repeated here: a caller who can read the MoU at all is already in scope.
 */
final class MouAllowedActions {

    static final String UPLOAD = "UPLOAD";
    static final String REMOVE_DOCUMENT = "REMOVE_DOCUMENT";
    static final String SUBMIT = "SUBMIT";
    static final String APPROVE = "APPROVE";
    static final String REJECT = "REJECT";
    static final String AMEND_TERMS = "AMEND_TERMS";

    private MouAllowedActions() {}

    /** Admin and Director upload, replace, remove and submit; a Manager never does, whatever is granted. */
    static boolean isStaff(Set<Role> roles) {
        return roles.contains(Role.ADMIN) || roles.contains(Role.DIRECTOR);
    }

    /** Director and Zone Manager decide; Admin never does. */
    static boolean isApprover(Set<Role> roles) {
        return roles.contains(Role.DIRECTOR) || roles.contains(Role.MANAGER);
    }

    static List<String> of(
            Set<Role> roles,
            PermissionGuard guard,
            ContractState contract,
            MouApprovalStatus status,
            boolean hasDocument) {
        List<String> actions = new ArrayList<>();
        if (contract == ContractState.RATE_PENDING || status == null && contract != ContractState.CANCELLED) {
            return actions;
        }
        boolean edit = isStaff(roles) && guard.isGranted(roles, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.EDIT);
        if (edit) {
            actions.add(UPLOAD);
            if (hasDocument) {
                actions.add(REMOVE_DOCUMENT);
            }
        }
        if (contract == ContractState.CANCELLED) {
            return actions;
        }
        if (edit && (status == MouApprovalStatus.DRAFT || status == MouApprovalStatus.REJECTED)) {
            actions.add(SUBMIT);
        }
        boolean decide = isApprover(roles)
                && guard.isGranted(roles, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE);
        if (decide && status == MouApprovalStatus.PENDING) {
            actions.add(APPROVE);
            actions.add(REJECT);
            actions.add(AMEND_TERMS);
        }
        return actions;
    }
}
