package com.hls.settings.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.settings.internal.SettingKey.Group;
import com.hls.settings.internal.RuntimeSettingsService;
import com.hls.settings.internal.RuntimeSettingsService.GroupResponse;
import com.hls.settings.internal.RuntimeSettingsService.InvalidSettingsException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** System Settings and Security Settings (spec 011, contracts/settings-api.md). */
@RestController
@RequestMapping("/api/v1/settings")
public class RuntimeSettingsController {

    public record UpdateRequest(Map<String, String> values) {}

    public record ErrorBody(String message, Map<String, String> fields) {}

    private final RuntimeSettingsService service;
    private final PermissionGuard permissionGuard;

    public RuntimeSettingsController(RuntimeSettingsService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/{group}")
    public GroupResponse get(@PathVariable String group, @AuthenticationPrincipal Jwt jwt) {
        Group parsed = parse(group);
        permissionGuard.require(roles(jwt), module(parsed), PermissionAction.VIEW);
        return service.view(parsed);
    }

    @PutMapping("/{group}")
    public GroupResponse put(
            @PathVariable String group, @RequestBody UpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        Group parsed = parse(group);
        permissionGuard.require(roles(jwt), module(parsed), PermissionAction.EDIT);
        return service.update(UUID.fromString(jwt.getSubject()), parsed, request == null ? null : request.values());
    }

    @ExceptionHandler(InvalidSettingsException.class)
    ResponseEntity<ErrorBody> invalid(InvalidSettingsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorBody(e.getMessage(), e.fields()));
    }

    private static Group parse(String raw) {
        return Group.parse(raw).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    static PermissionModule module(Group group) {
        return group == Group.SYSTEM ? PermissionModule.SYSTEM_SETTINGS : PermissionModule.SECURITY_SETTINGS;
    }

    static Set<Role> roles(Jwt jwt) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String role : jwt.getClaimAsStringList("roles")) {
            roles.add(Role.valueOf(role));
        }
        return roles;
    }
}
