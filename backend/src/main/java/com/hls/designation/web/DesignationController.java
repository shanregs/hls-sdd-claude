package com.hls.designation.web;

import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.designation.internal.DesignationService;
import com.hls.designation.internal.DesignationService.DesignationView;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Designations list (contracts/designations-api.md), gated by the DESIGNATIONS grants. */
@RestController
@RequestMapping("/api/v1/designations")
public class DesignationController {

    private final DesignationService service;
    private final PermissionGuard permissionGuard;

    public DesignationController(DesignationService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record CreateRequest(String name, Kind kind) {}

    public record UpdateRequest(String name, Kind kind, Boolean retired, Long version) {}

    @GetMapping
    public List<DesignationView> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.VIEW);
        Boolean retired = null;
        if (status != null && !status.isBlank()) {
            retired = switch (status.toUpperCase()) {
                case "ACTIVE" -> false;
                case "RETIRED" -> true;
                default -> throw new InvalidInputException("Status must be ACTIVE or RETIRED.");};
        }
        return service.list(parseKind(kind), retired);
    }

    @GetMapping("/summary")
    public DesignationService.Summary summary(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.VIEW);
        return service.summary();
    }

    @GetMapping("/options")
    public List<DesignationView> options(@RequestParam String kind, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.EDIT);
        return service.options(parseKind(kind));
    }

    @PostMapping
    public ResponseEntity<DesignationView> create(@RequestBody CreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(service.create(CallerContext.userId(jwt), request.name(), request.kind()));
    }

    @PutMapping("/{id}")
    public DesignationView update(
            @PathVariable UUID id, @RequestBody UpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.EDIT);
        return service.update(
                CallerContext.userId(jwt), id, request.name(), request.kind(), request.retired(), request.version());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.DESIGNATIONS, PermissionAction.EDIT);
        throw new ConflictException("A designation is never deleted. Retire it instead.");
    }

    private static Kind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        try {
            return Kind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Kind must be TEACHER or MANAGER.");
        }
    }
}
