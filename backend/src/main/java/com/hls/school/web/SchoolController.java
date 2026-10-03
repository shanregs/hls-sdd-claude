package com.hls.school.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolView;
import com.hls.school.internal.SchoolService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

/** School endpoints (contracts/master-data-api.md); permission and data scope are re-checked here. */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SchoolService schoolService;
    private final PermissionGuard permissionGuard;

    public SchoolController(SchoolService schoolService, PermissionGuard permissionGuard) {
        this.schoolService = schoolService;
        this.permissionGuard = permissionGuard;
    }

    public record CreateSchoolRequest(
            String name,
            UUID placeId,
            String address,
            String contactPerson,
            String contactPhone,
            String billingContact) {}

    public record UpdateSchoolRequest(
            String name,
            String address,
            String contactPerson,
            String contactPhone,
            String billingContact,
            Long version) {}

    public record ChangePlaceRequest(UUID placeId, Long version) {}

    @GetMapping
    public PageResponse<SchoolView> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID placeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.VIEW);
        return PageResponse.from(schoolService.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                query,
                zoneId,
                placeId,
                active,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), Sort.by("name"))));
    }

    @GetMapping("/{id}")
    public SchoolView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.VIEW);
        return schoolService.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping
    public ResponseEntity<SchoolView> create(
            @RequestBody CreateSchoolRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.CREATE);
        SchoolService.Profile profile = new SchoolService.Profile(
                request.name(),
                request.address(),
                request.contactPerson(),
                request.contactPhone(),
                request.billingContact(),
                null);
        return ResponseEntity.status(201)
                .body(schoolService.create(CallerContext.userId(jwt), request.placeId(), profile));
    }

    @PutMapping("/{id}")
    public SchoolView update(
            @PathVariable UUID id, @RequestBody UpdateSchoolRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.EDIT);
        SchoolService.Profile profile = new SchoolService.Profile(
                request.name(),
                request.address(),
                request.contactPerson(),
                request.contactPhone(),
                request.billingContact(),
                request.version());
        return schoolService.update(CallerContext.userId(jwt), CallerContext.roles(jwt), id, profile);
    }

    @PutMapping("/{id}/place")
    public SchoolView changePlace(
            @PathVariable UUID id, @RequestBody ChangePlaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.EDIT);
        return schoolService.changePlace(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.placeId(), request.version());
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.EDIT);
        schoolService.deactivate(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.EDIT);
        schoolService.reactivate(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
