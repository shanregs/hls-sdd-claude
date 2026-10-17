package com.hls.payroll.run.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.run.internal.PaymentService;
import com.hls.payroll.run.internal.PaymentService.PaymentView;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual payment records of a line of an approved run (spec 013b contracts/payroll-run-api.md). */
@RestController
@RequestMapping("/api/v1/payroll/runs/{runId}/lines/{lineId}/payment")
public class PayPaymentController {

    private final PaymentService service;
    private final PermissionGuard guard;

    public PayPaymentController(PaymentService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    public record PaymentRequest(Boolean paid, LocalDate paidOn, String mode, String reference) {}

    @PutMapping
    public PaymentView record(
            @PathVariable UUID runId,
            @PathVariable UUID lineId,
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        return service.record(
                CallerContext.userId(jwt), runId, lineId, request.paid(), request.paidOn(), request.mode(), request.reference());
    }

    @GetMapping
    public PaymentView get(@PathVariable UUID runId, @PathVariable UUID lineId, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.VIEW);
        return service.get(runId, lineId);
    }
}
