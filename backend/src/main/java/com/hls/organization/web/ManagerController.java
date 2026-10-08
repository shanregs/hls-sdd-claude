package com.hls.organization.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.organization.api.ManagerView;
import com.hls.organization.internal.ManagerEmploymentService;
import com.hls.organization.internal.ManagerService;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Manager and assignment endpoints (contracts/master-data-api.md), gated by MANAGERS.* grants. */
@RestController
public class ManagerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ManagerService managerService;
    private final ManagerEmploymentService employmentService;
    private final PermissionGuard permissionGuard;

    public ManagerController(
            ManagerService managerService,
            ManagerEmploymentService employmentService,
            PermissionGuard permissionGuard) {
        this.managerService = managerService;
        this.employmentService = employmentService;
        this.permissionGuard = permissionGuard;
    }

    public record CreateManagerRequest(UUID userId) {}

    public record ZonesRequest(List<UUID> zoneIds, Long version) {}

    public record SchoolManagerRequest(UUID managerId) {}

    public record EmploymentRequest(String employeeId, LocalDate joiningDate, LocalDate exitDate, Long version) {}

    public record DesignationRequest(UUID designationId, LocalDate effectiveOn) {}

    @GetMapping("/api/v1/managers")
    public PageResponse<ManagerView> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean missing,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.VIEW);
        return PageResponse.from(managerService.list(
                query, missing, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)))));
    }

    @GetMapping("/api/v1/managers/candidates")
    public List<ManagerService.Candidate> candidates(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.CREATE);
        return managerService.candidates();
    }

    @GetMapping("/api/v1/managers/{id}")
    public ManagerView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.VIEW);
        return managerService.get(id);
    }

    @PostMapping("/api/v1/managers")
    public ResponseEntity<ManagerView> create(
            @RequestBody CreateManagerRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(managerService.create(CallerContext.userId(jwt), request.userId()));
    }

    @PutMapping("/api/v1/managers/{id}/zones")
    public ManagerView setZones(
            @PathVariable UUID id, @RequestBody ZonesRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.EDIT);
        return managerService.setZones(CallerContext.userId(jwt), id, request.zoneIds(), request.version());
    }

    /** Employee id, joining and exit dates: needs DESIGNATIONS EDIT, not MANAGERS EDIT (spec 005a FR-011). */
    @PutMapping("/api/v1/managers/{id}/employment")
    public ManagerView updateEmployment(
            @PathVariable UUID id, @RequestBody EmploymentRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.EDIT);
        employmentService.updateEmployment(
                CallerContext.userId(jwt),
                id,
                request.employeeId(),
                request.joiningDate(),
                request.exitDate(),
                request.version());
        return managerService.get(id);
    }

    @PostMapping("/api/v1/managers/{id}/designation")
    public ManagerView recordDesignation(
            @PathVariable UUID id, @RequestBody DesignationRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.EDIT);
        employmentService.appendDesignation(
                CallerContext.userId(jwt), id, request.designationId(), request.effectiveOn());
        return managerService.get(id);
    }

    @PutMapping("/api/v1/schools/{schoolId}/manager")
    public ResponseEntity<Void> assignSchoolManager(
            @PathVariable UUID schoolId, @RequestBody SchoolManagerRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.EDIT);
        managerService.assignSchoolManager(CallerContext.userId(jwt), schoolId, request.managerId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/schools/{schoolId}/manager-history")
    public List<ManagerView.AssignmentRow> schoolHistory(
            @PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.MANAGERS, PermissionAction.VIEW);
        return managerService.schoolHistory(schoolId);
    }
}
