package com.hls.payroll.web;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.internal.SalaryStructureService;
import com.hls.payroll.internal.SalaryStructureService.Detail;
import com.hls.payroll.internal.SalaryStructureService.ListEntry;
import com.hls.payroll.internal.SalaryStructureService.SalaryRow;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
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

/**
 * Salary structures by designation (contracts/salary-structures-api.md), gated by the SALARY_STRUCTURES grants. The
 * check is on the server for every request, whatever the menu shows. No endpoint returns any person's own pay.
 */
@RestController
@RequestMapping("/api/v1/salary-structures")
public class SalaryStructureController {

    private final SalaryStructureService service;
    private final PermissionGuard permissionGuard;

    public SalaryStructureController(SalaryStructureService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record RecordSalaryRequest(BigDecimal amount, LocalDate effectiveOn, String note) {}

    @GetMapping("/designations")
    public List<ListEntry> list(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean includeRetired,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.list(parseKind(kind), includeRetired);
    }

    @GetMapping("/designations/{designationId}")
    public Detail detail(@PathVariable UUID designationId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.detail(designationId);
    }

    @PostMapping("/designations/{designationId}/salaries")
    public ResponseEntity<SalaryRow> record(
            @PathVariable UUID designationId,
            @RequestBody RecordSalaryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(service.record(
                        CallerContext.userId(jwt),
                        designationId,
                        request.amount(),
                        request.effectiveOn(),
                        request.note()));
    }

    private static Kind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        try {
            return Kind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Kind must be TEACHER or MANAGER.");
        }
    }
}
