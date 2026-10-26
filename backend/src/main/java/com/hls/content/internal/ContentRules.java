package com.hls.content.internal;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.identity.user.RoleAssignment;
import com.hls.identity.user.RoleAssignmentRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Who may write content and who may review it (spec 029), decided from the permission matrix and not from role names, so
 * a later matrix change needs no code. An author holds Create on Content; a reviewer holds Approve on Content.
 */
@Component
class ContentRules {

    private final PermissionMatrixService matrix;
    private final RoleAssignmentRepository roleAssignments;
    private final AppUserRepository users;

    ContentRules(PermissionMatrixService matrix, RoleAssignmentRepository roleAssignments, AppUserRepository users) {
        this.matrix = matrix;
        this.roleAssignments = roleAssignments;
        this.users = users;
    }

    /** An active user who holds Create on Content through any of their roles. */
    boolean canAuthor(UUID userId) {
        return users.findById(userId).filter(AppUser::isActive).isPresent() && holds(userId, PermissionAction.CREATE);
    }

    private boolean holds(UUID userId, PermissionAction action) {
        List<RoleAssignment> assigned = roleAssignments.findByUserId(userId);
        for (RoleAssignment assignment : assigned) {
            if (matrix.isGranted(assignment.getRole(), PermissionModule.CONTENT, action)) {
                return true;
            }
        }
        return false;
    }

    /** Every active user who holds Approve on Content. */
    Set<UUID> reviewers() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Role role : Role.values()) {
            if (matrix.isGranted(role, PermissionModule.CONTENT, PermissionAction.APPROVE)) {
                ids.addAll(roleAssignments.userIdsWithRole(role));
            }
        }
        ids.removeIf(id -> users.findById(id).filter(AppUser::isActive).isEmpty());
        return ids;
    }

    /** Reviewers other than the author and the submitter of the version under review. */
    Set<UUID> eligibleReviewers(UUID authorUserId, UUID submitterUserId) {
        Set<UUID> ids = reviewers();
        ids.remove(authorUserId);
        ids.remove(submitterUserId);
        return ids;
    }
}
