package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.AdjustmentService;
import com.hls.payroll.run.internal.AdjustmentService.AdjustmentView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.math.BigDecimal;
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

/** Payroll adjustments (spec 013b contracts/payroll-run-api.md): record, list and withdraw, gated by the PAYROLL grants. */
@RestController
@RequestMapping("/api/v1/payroll/adjustments")
public class PayAdjustmentController {

    private final AdjustmentService service;
    private final PermissionGuard guard;

    public PayAdjustmentController(AdjustmentService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    public record RecordRequest(String personKind, UUID personId, String kind, BigDecimal amount, String reason) {}

    public record WithdrawRequest(String reason) {}

    @GetMapping
    public PageResponse<AdjustmentView> list(
            @RequestParam(required = false) String personKind,
            @RequestParam(required = false) UUID personId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return PageResponse.from(service.list(personKind, personId, status, page, size));
    }

    @PostMapping
    public ResponseEntity<AdjustmentView> record(@RequestBody RecordRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return ResponseEntity.status(201)
                .body(service.record(
                        CallerContext.userId(jwt),
                        request.personKind(),
                        request.personId(),
                        request.kind(),
                        request.amount(),
                        request.reason()));
    }

    @PostMapping("/{id}/withdraw")
    public AdjustmentView withdraw(
            @PathVariable UUID id, @RequestBody(required = false) WithdrawRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return service.withdraw(CallerContext.userId(jwt), id, request == null ? null : request.reason());
    }
}
