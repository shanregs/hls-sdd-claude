package com.hls.reporting.web;

import com.hls.reporting.api.TileResult;
import com.hls.reporting.internal.DashboardService;
import com.hls.school.api.CallerContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The management Dashboard (contracts/dashboard-api.md). Any signed-in user may call it; what comes back is the tiles whose
 * grant the caller holds, scoped to the caller. Read-only and not audited (FR-020).
 */
@RestController("managementDashboardController")
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping({"", "/"})
    public DashboardService.Manifest manifest(@AuthenticationPrincipal Jwt jwt) {
        return service.manifest(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping("/areas/{area}")
    public DashboardService.AreaResponse area(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String area,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String period) {
        return service.area(CallerContext.userId(jwt), CallerContext.roles(jwt), area, zoneId, period);
    }

    @GetMapping("/areas/{area}/tiles/{key}")
    public TileResult tile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String area,
            @PathVariable String key,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String period) {
        return service.tile(CallerContext.userId(jwt), CallerContext.roles(jwt), area, key, zoneId, period);
    }
}
