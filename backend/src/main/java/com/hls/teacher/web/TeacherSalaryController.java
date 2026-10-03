package com.hls.teacher.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.teacher.internal.SalaryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salary endpoints (spec 005 US9): the only place salary leaves the server, guarded by their own
 * TEACHER_SALARY permission (Admin and Director by default) and by Teacher scope.
 */
@RestController
public class TeacherSalaryController {

    private final SalaryService salaryService;
    private final PermissionGuard permissionGuard;

    public TeacherSalaryController(SalaryService salaryService, PermissionGuard permissionGuard) {
        this.salaryService = salaryService;
        this.permissionGuard = permissionGuard;
    }

    public record SalaryRequest(BigDecimal amount, LocalDate effectiveOn) {}

    @GetMapping("/api/v1/teachers/{id}/salary")
    public ResponseEntity<?> salary(
            @PathVariable UUID id,
            @RequestParam(required = false) LocalDate asOf,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_SALARY, PermissionAction.VIEW);
        if (asOf != null) {
            return ResponseEntity.ok(salaryService.asOf(CallerContext.userId(jwt), CallerContext.roles(jwt), id, asOf));
        }
        return ResponseEntity.ok(salaryService.history(CallerContext.userId(jwt), CallerContext.roles(jwt), id));
    }

    @PostMapping("/api/v1/teachers/{id}/salary")
    public ResponseEntity<SalaryService.Entry> record(
            @PathVariable UUID id, @RequestBody SalaryRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_SALARY, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(salaryService.record(
                        CallerContext.userId(jwt),
                        CallerContext.roles(jwt),
                        id,
                        request.amount(),
                        request.effectiveOn()));
    }
}
