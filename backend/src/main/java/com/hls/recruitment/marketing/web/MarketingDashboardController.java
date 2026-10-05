package com.hls.recruitment.marketing.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.DashboardService;
import com.hls.recruitment.marketing.internal.DashboardService.DashboardDto;
import com.hls.school.api.CallerContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The marketing dashboard (module {@code MARKETING}); a Zone Manager's numbers cover only their Zones. */
@RestController
@RequestMapping("/api/v1/marketing/dashboard")
public class MarketingDashboardController {

    private final DashboardService dashboard;
    private final PermissionGuard guard;

    public MarketingDashboardController(DashboardService dashboard, PermissionGuard guard) {
        this.dashboard = dashboard;
        this.guard = guard;
    }

    @GetMapping
    public DashboardDto get(@RequestParam(required = false) String period, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING, PermissionAction.VIEW);
        return dashboard.dashboard(CallerContext.userId(jwt), CallerContext.roles(jwt), period);
    }
}
