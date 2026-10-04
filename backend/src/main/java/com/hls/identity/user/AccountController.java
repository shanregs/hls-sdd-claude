package com.hls.identity.user;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A signed-in user's own settings: profile and password. Always acts on the caller, never an id. */
@RestController
public class AccountController {

    public record UpdateProfileRequest(String displayName, String username, String email) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    private final AccountService accounts;
    private final PermissionGuard guard;

    public AccountController(AccountService accounts, PermissionGuard guard) {
        this.accounts = accounts;
        this.guard = guard;
    }

    @GetMapping("/api/v1/me/profile")
    public AccountService.ProfileView profile(@AuthenticationPrincipal Jwt jwt) {
        guard.require(roles(jwt), PermissionModule.ACCOUNT_PROFILE, PermissionAction.VIEW);
        return accounts.profile(userId(jwt));
    }

    @PutMapping("/api/v1/me/profile")
    public AccountService.ProfileView update(
            @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(roles(jwt), PermissionModule.ACCOUNT_PROFILE, PermissionAction.EDIT);
        return accounts.updateProfile(userId(jwt), request.displayName(), request.username(), request.email());
    }

    @PostMapping("/api/v1/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(roles(jwt), PermissionModule.ACCOUNT_PROFILE, PermissionAction.EDIT);
        accounts.changePassword(
                userId(jwt),
                UUID.fromString(jwt.getClaimAsString("sid")),
                request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({
        UserAdminService.DuplicateUsernameException.class,
        UserAdminService.DuplicateEmailException.class
    })
    ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(409).body(Map.of("reason", e.getMessage()));
    }

    @ExceptionHandler(AccountService.InvalidAccountInputException.class)
    ResponseEntity<Map<String, String>> invalid(RuntimeException e) {
        return ResponseEntity.status(400).body(Map.of("reason", e.getMessage()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static Set<Role> roles(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }
}
