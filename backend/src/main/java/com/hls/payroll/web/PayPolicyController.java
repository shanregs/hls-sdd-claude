package com.hls.payroll.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.internal.PayPolicyService;
import com.hls.payroll.internal.PayPolicyService.PolicyResponse;
import com.hls.payroll.internal.PayPolicyService.PolicyVersion;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The pay policy and its history (contracts/salary-structures-api.md), gated by the SALARY_STRUCTURES grants. */
@RestController
@RequestMapping("/api/v1/salary-structures/policy")
public class PayPolicyController {

    private final PayPolicyService service;
    private final PermissionGuard permissionGuard;

    public PayPolicyController(PayPolicyService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record RecordPolicyRequest(
            LocalDate effectiveOn,
            String lopDivisor,
            BigDecimal halfDayFraction,
            String rounding,
            String payMonth,
            String note) {}

    @GetMapping
    public PolicyResponse policy(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.policy();
    }

    @PostMapping
    public ResponseEntity<PolicyVersion> record(
            @RequestBody RecordPolicyRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(service.record(
                        CallerContext.userId(jwt),
                        request.effectiveOn(),
                        request.lopDivisor(),
                        request.halfDayFraction(),
                        request.rounding(),
                        request.payMonth(),
                        request.note()));
    }
}
