package com.hls.school.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.school.api.ZoneView;
import com.hls.school.internal.ZoneService;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

/** Zone endpoints (contracts/master-data-api.md); each re-checks its permission server-side. */
@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ZoneService zoneService;
    private final PermissionGuard permissionGuard;

    public ZoneController(ZoneService zoneService, PermissionGuard permissionGuard) {
        this.zoneService = zoneService;
        this.permissionGuard = permissionGuard;
    }

    public record ZoneRequest(String name, Long version) {}

    @GetMapping
    public PageResponse<ZoneView> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.VIEW);
        return PageResponse.from(zoneService.list(
                query,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), Sort.by("name"))));
    }

    @PostMapping
    public ResponseEntity<ZoneView> create(@RequestBody ZoneRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(zoneService.create(CallerContext.userId(jwt), request.name()));
    }

    @PutMapping("/{id}")
    public ZoneView rename(@PathVariable UUID id, @RequestBody ZoneRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.EDIT);
        return zoneService.rename(CallerContext.userId(jwt), id, request.name(), request.version());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.DELETE);
        zoneService.delete(CallerContext.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
