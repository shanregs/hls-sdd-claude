package com.hls.recruitment.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.internal.RecruitmentDashboardService;
import com.hls.recruitment.internal.RecruitmentDashboardService.DashboardDto;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The recruitment funnel (module {@code RECRUITMENT}): every role with VIEW sees counts for all drives. */
@RestController
@RequestMapping("/api/v1/recruitment")
public class DashboardController {

    private final RecruitmentDashboardService dashboard;
    private final PermissionGuard guard;

    public DashboardController(RecruitmentDashboardService dashboard, PermissionGuard guard) {
        this.dashboard = dashboard;
        this.guard = guard;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.RECRUITMENT, PermissionAction.VIEW);
        return dashboard.dashboard(CallerContext.userId(jwt), season, from, to, mine);
    }
}
