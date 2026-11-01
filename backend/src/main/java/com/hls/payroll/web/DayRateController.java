package com.hls.payroll.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.internal.DayRateService;
import com.hls.payroll.internal.DayRateService.ListEntry;
import com.hls.payroll.internal.DayRateService.RateRow;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Designation day rates for Substitute Teachers (spec 017b FR-005), under the Salary Structures grants. */
@RestController
@RequestMapping("/api/v1/salary-structures/designation-day-rates")
public class DayRateController {

    private final DayRateService service;
    private final PermissionGuard permissionGuard;

    public DayRateController(DayRateService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record RecordDayRateRequest(BigDecimal amount, LocalDate effectiveOn, String note) {}

    @GetMapping
    public List<ListEntry> list(
            @RequestParam(defaultValue = "false") boolean includeRetired, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.list(includeRetired);
    }

    @GetMapping("/{designationId}")
    public List<RateRow> history(@PathVariable UUID designationId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.history(designationId);
    }

    @PostMapping("/{designationId}")
    public ResponseEntity<RateRow> record(
            @PathVariable UUID designationId,
            @RequestBody RecordDayRateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(service.record(
                        CallerContext.userId(jwt), designationId, request.amount(), request.effectiveOn(), request.note()));
    }
}
