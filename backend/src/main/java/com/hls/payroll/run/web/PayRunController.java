package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.ApprovalService;
import com.hls.payroll.run.internal.OwedService;
import com.hls.payroll.run.internal.RunService;
import com.hls.payroll.run.internal.RunViews.RunView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pay runs (spec 013b contracts/payroll-run-api.md). Every endpoint checks its {@code PAYROLL} permission on the server
 * before it does anything, and the actor always comes from the access token.
 */
@RestController
@RequestMapping("/api/v1/payroll/runs")
public class PayRunController {

    private final RunService service;
    private final ApprovalService approvals;
    private final OwedService owed;
    private final PermissionGuard guard;

    public PayRunController(
            RunService service, ApprovalService approvals, OwedService owed, PermissionGuard guard) {
        this.owed = owed;
        this.service = service;
        this.approvals = approvals;
        this.guard = guard;
    }

    public record StartRequest(String month, Boolean supplementary) {}

    public record ReasonRequest(String reason) {}

    @GetMapping
    public PageResponse<RunView> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return PageResponse.from(service.list(month, status, page, size));
    }

    @PostMapping
    public ResponseEntity<RunView> start(@RequestBody StartRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return ResponseEntity.accepted()
                .body(service.start(
                        CallerContext.userId(jwt), request.month(), Boolean.TRUE.equals(request.supplementary())));
    }

    @GetMapping("/{id}")
    public RunView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return service.get(id);
    }

    @GetMapping("/{id}/owed")
    public List<OwedService.OwedPerson> owed(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return owed.owedFor(id);
    }

    @PostMapping("/{id}/recompute")
    public ResponseEntity<RunView> recompute(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return ResponseEntity.accepted().body(service.recompute(CallerContext.userId(jwt), id));
    }

    @PostMapping("/{id}/submit")
    public RunView submit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return service.submit(CallerContext.userId(jwt), id);
    }

    @PostMapping("/{id}/return")
    public RunView returnToDraft(
            @PathVariable UUID id, @RequestBody(required = false) ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.APPROVE);
        return service.returnToDraft(CallerContext.userId(jwt), id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/approve")
    public RunView approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.APPROVE);
        return approvals.approve(CallerContext.userId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public RunView cancel(
            @PathVariable UUID id, @RequestBody(required = false) ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return service.cancel(CallerContext.userId(jwt), id, request == null ? null : request.reason());
    }
}
