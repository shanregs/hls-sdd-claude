package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.RunService;
import com.hls.payroll.run.internal.RunViews.LineView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The lines of a pay run and the frozen working of one line (spec 013b contracts/payroll-run-api.md). */
@RestController
@RequestMapping("/api/v1/payroll/runs/{runId}/lines")
public class PayRunLineController {

    private final RunService service;
    private final PermissionGuard guard;

    public PayRunLineController(RunService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public PageResponse<LineView> lines(
            @PathVariable UUID runId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return PageResponse.from(service.lines(runId, state, kind, query, page, size));
    }

    @GetMapping("/{lineId}")
    public LineView line(@PathVariable UUID runId, @PathVariable UUID lineId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return service.line(runId, lineId);
    }
}
