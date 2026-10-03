package com.hls.identity.user;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.UserAdminService.DuplicateEmailException;
import com.hls.identity.user.UserAdminService.DuplicatePhoneException;
import com.hls.identity.user.UserAdminService.DuplicateUsernameException;
import com.hls.identity.user.UserAdminService.EmptyRoleSetException;
import com.hls.identity.user.UserAdminService.LastAdminException;
import com.hls.identity.user.UserAdminService.PasswordPolicyViolationException;
import com.hls.identity.user.UserAdminService.UnknownUserException;
import com.hls.identity.user.UserAdminService.UserWithRoles;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Management endpoints (contracts/user-management-api.md). Every handler re-checks
 * {@code USER_MANAGEMENT.*} server-side via {@link PermissionGuard}, independent of what the
 * frontend renders (Constitution Principle X).
 */
@RestController
@RequestMapping("/api/v1/identity/users")
public class UserManagementController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserAdminService userAdminService;
    private final PermissionGuard permissionGuard;

    public UserManagementController(UserAdminService userAdminService, PermissionGuard permissionGuard) {
        this.userAdminService = userAdminService;
        this.permissionGuard = permissionGuard;
    }

    public record UserView(
            UUID id, String displayName, String phone, String username, String email, List<Role> roles, boolean active) {
        static UserView from(UserWithRoles u) {
            AppUser user = u.user();
            return new UserView(
                    user.getId(),
                    user.getDisplayName(),
                    user.getPhone(),
                    user.getUsername(),
                    user.getEmail(),
                    u.roles(),
                    user.isActive());
        }
    }

    public record UserPage(List<UserView> content, int page, int size, long totalElements) {}

    public record CreateUserRequest(
            String displayName, String phone, Set<Role> roles, String username, String email, String initialPassword) {}

    public record RolesRequest(Set<Role> roles) {}

    public record ResetPasswordRequest(String newPassword) {}

    public record Reason(String reason) {}

    @GetMapping
    public UserPage list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.VIEW);
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<UserWithRoles> result = userAdminService.search(
                query,
                role,
                active,
                PageRequest.of(Math.max(0, page), boundedSize, Sort.by("displayName").ascending()));
        return new UserPage(
                result.getContent().stream().map(UserView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @PostMapping
    public ResponseEntity<UserView> create(@RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.CREATE);
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new IllegalArgumentException("Display name is required.");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        AppUser created = userAdminService.createUser(
                actorOf(jwt),
                request.displayName().trim(),
                request.phone(),
                request.roles(),
                null,
                blankToNull(request.initialPassword()),
                blankToNull(request.username()),
                blankToNull(request.email()));
        return ResponseEntity.status(201).body(UserView.from(userAdminService.get(created.getId())));
    }

    @PutMapping("/{userId}/roles")
    public UserView updateRoles(
            @PathVariable UUID userId, @RequestBody RolesRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT);
        return UserView.from(userAdminService.updateRoles(actorOf(jwt), userId, request.roles()));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT);
        userAdminService.deactivateUser(actorOf(jwt), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT);
        userAdminService.reactivateUser(actorOf(jwt), userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId, @RequestBody ResetPasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(rolesOf(jwt), PermissionModule.USER_MANAGEMENT, PermissionAction.EDIT);
        userAdminService.adminResetPassword(actorOf(jwt), userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({DuplicatePhoneException.class, DuplicateUsernameException.class, DuplicateEmailException.class})
    ResponseEntity<Reason> conflict(RuntimeException e) {
        return ResponseEntity.status(409).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(LastAdminException.class)
    ResponseEntity<Reason> lastAdmin(LastAdminException e) {
        return ResponseEntity.status(409).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler({EmptyRoleSetException.class, PasswordPolicyViolationException.class, IllegalArgumentException.class})
    ResponseEntity<Reason> badRequest(RuntimeException e) {
        return ResponseEntity.status(400).body(new Reason(e.getMessage()));
    }

    @ExceptionHandler(UnknownUserException.class)
    ResponseEntity<Reason> notFound(UnknownUserException e) {
        return ResponseEntity.status(404).body(new Reason(e.getMessage()));
    }

    private static UUID actorOf(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Set<Role> rolesOf(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }
}
