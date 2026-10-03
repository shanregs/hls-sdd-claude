package com.hls.identity.permissions;

import com.hls.identity.user.Role;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The server-side re-check every protected endpoint calls independently of what the frontend
 * renders (Constitution Principle X, research.md §9): permission checks MUST fail closed here,
 * regardless of whether the caller's menu happened to hide the capability.
 */
@Component
public class PermissionGuard {

    private final PermissionMatrixService permissionMatrixService;

    public PermissionGuard(PermissionMatrixService permissionMatrixService) {
        this.permissionMatrixService = permissionMatrixService;
    }

    /**
     * @throws AccessDeniedException if none of {@code callerRoles} holds {@code module.action} —
     *     translated to an HTTP 403 by Spring Security's standard exception handling.
     */
    public void require(Set<Role> callerRoles, PermissionModule module, PermissionAction action) {
        boolean granted = callerRoles.stream().anyMatch(role -> permissionMatrixService.isGranted(role, module, action));
        if (!granted) {
            throw new AccessDeniedException("Not authorized for " + module + "." + action);
        }
    }
}
