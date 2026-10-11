package com.hls.payroll.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.payroll.internal.PersonSalaryService;
import com.hls.payroll.internal.PersonSalaryService.Employment;
import com.hls.payroll.internal.PersonSalaryService.PersonDetail;
import com.hls.payroll.internal.PersonSalaryService.PersonKind;
import com.hls.payroll.internal.PersonSalaryService.PersonPage;
import com.hls.payroll.internal.PersonSalaryService.PersonSalaryRow;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Each person's own salary (contracts/salary-structures-api.md, "People"), gated by the SALARY_STRUCTURES grants. This
 * and the policy and designation endpoints are the only place an individual's pay leaves the server, and only to
 * Admin and Director by default.
 */
@RestController
@RequestMapping("/api/v1/salary-structures/people")
public class PersonSalaryController {

    private final PersonSalaryService service;
    private final PermissionGuard permissionGuard;

    public PersonSalaryController(PersonSalaryService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record RecordSalaryRequest(BigDecimal amount, LocalDate effectiveOn, String note) {}

    public record EmploymentRequest(LocalDate joiningDate, LocalDate exitDate) {}

    @GetMapping
    public PersonPage list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean withoutSalary,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        PersonKind parsed = kind == null || kind.isBlank() ? null : parseKind(kind);
        return service.list(parsed, q, withoutSalary, page, size);
    }

    @GetMapping("/{kind}/{personId}")
    public PersonDetail detail(
            @PathVariable String kind, @PathVariable UUID personId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.VIEW);
        return service.detail(parseKind(kind), personId);
    }

    @PostMapping("/{kind}/{personId}/salaries")
    public ResponseEntity<PersonSalaryRow> record(
            @PathVariable String kind,
            @PathVariable UUID personId,
            @RequestBody RecordSalaryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(service.record(
                        CallerContext.userId(jwt),
                        parseKind(kind),
                        personId,
                        request.amount(),
                        request.effectiveOn(),
                        request.note()));
    }

    @PutMapping("/{kind}/{personId}/employment")
    public Employment employment(
            @PathVariable String kind,
            @PathVariable UUID personId,
            @RequestBody EmploymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SALARY_STRUCTURES, PermissionAction.CREATE);
        if (parseKind(kind) != PersonKind.STAFF) {
            throw new InvalidInputException(
                    "Joining and exit dates are set here for staff only; a Manager's are on the Managers screen.");
        }
        return service.setEmployment(CallerContext.userId(jwt), personId, request.joiningDate(), request.exitDate());
    }

    private static PersonKind parseKind(String kind) {
        try {
            return PersonKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Kind must be TEACHER, MANAGER or STAFF.");
        }
    }
}
