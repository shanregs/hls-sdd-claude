package com.hls.identity.permissions;

import com.hls.identity.permissions.PermissionMatrixDtos.EntryView;
import com.hls.identity.permissions.PermissionMatrixDtos.GrantRequest;
import com.hls.identity.permissions.PermissionMatrixDtos.MatrixListResponse;
import com.hls.identity.permissions.PermissionMatrixDtos.RejectionResponse;
import com.hls.identity.user.Role;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * View/edit the permission matrix (contracts/access-model-api.md), restricted to
 * `ADMIN`/`DIRECTOR`/`SYSTEM` by {@link PermissionGuard} — re-checked here independently of
 * whatever the frontend would render (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/identity/permission-matrix")
public class PermissionMatrixController {

    private final PermissionMatrixService permissionMatrixService;
    private final PermissionGuard permissionGuard;

    public PermissionMatrixController(PermissionMatrixService permissionMatrixService, PermissionGuard permissionGuard) {
        this.permissionMatrixService = permissionMatrixService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ResponseEntity<MatrixListResponse> list(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.VIEW);
        var entries = permissionMatrixService.findAll().stream().map(EntryView::from).toList();
        return ResponseEntity.ok(new MatrixListResponse(entries));
    }

    @PutMapping("/{role}/{module}/{action}")
    public ResponseEntity<?> updateGrant(
            @PathVariable Role role,
            @PathVariable PermissionModule module,
            @PathVariable PermissionAction action,
            @RequestBody GrantRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.EDIT);

        UUID actorUserId = UUID.fromString(jwt.getSubject());
        PermissionMatrixService.UpdateResult result =
                permissionMatrixService.updateGrant(role, module, action, request.granted(), actorUserId);
        if (!result.success()) {
            return ResponseEntity.status(409).body(new RejectionResponse(result.rejectionReason()));
        }
        return ResponseEntity.ok(EntryView.from(result.entry()));
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }
}
