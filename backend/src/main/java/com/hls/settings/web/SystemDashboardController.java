package com.hls.settings.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.settings.internal.SystemDashboardService;
import com.hls.settings.internal.SystemDashboardService.Dashboard;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The System dashboard (spec 011, contracts/settings-api.md). */
@RestController
@RequestMapping("/api/v1/system/dashboard")
public class SystemDashboardController {

    private final SystemDashboardService service;
    private final PermissionGuard permissionGuard;

    public SystemDashboardController(SystemDashboardService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = RuntimeSettingsController.roles(jwt);
        permissionGuard.require(roles, PermissionModule.SYSTEM_DASHBOARD, PermissionAction.VIEW);
        return service.dashboard(roles);
    }
}
