package com.hls.school.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.BulkImportResult;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.school.api.PlaceView;
import com.hls.school.internal.PlaceBulkImportService;
import com.hls.school.internal.PlaceService;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Place endpoints (contracts/master-data-api.md). Zone permissions cover Places. */
@RestController
public class PlaceController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlaceService placeService;
    private final PlaceBulkImportService bulkImportService;
    private final PermissionGuard permissionGuard;

    public PlaceController(
            PlaceService placeService, PlaceBulkImportService bulkImportService, PermissionGuard permissionGuard) {
        this.placeService = placeService;
        this.bulkImportService = bulkImportService;
        this.permissionGuard = permissionGuard;
    }

    public record PlaceRequest(String name, String pinCode, UUID zoneId) {}

    public record BulkImportRequest(UUID zoneId, List<PlaceBulkImportService.Row> rows) {}

    @GetMapping("/api/v1/zones/{zoneId}/places")
    public PageResponse<PlaceView> listInZone(
            @PathVariable UUID zoneId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.VIEW);
        return PageResponse.from(placeService.listInZone(
                zoneId,
                query,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), Sort.by("name"))));
    }

    @GetMapping("/api/v1/places")
    public List<PlaceView> lookup(
            @RequestParam(required = false) String pinCode,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.VIEW);
        return placeService.lookup(pinCode, name);
    }

    @PostMapping("/api/v1/zones/{zoneId}/places")
    public ResponseEntity<PlaceView> add(
            @PathVariable UUID zoneId, @RequestBody PlaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.EDIT);
        return ResponseEntity.status(201)
                .body(placeService.add(CallerContext.userId(jwt), zoneId, request.name(), request.pinCode()));
    }

    @PutMapping("/api/v1/places/{id}")
    public PlaceView edit(@PathVariable UUID id, @RequestBody PlaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.EDIT);
        return placeService.edit(CallerContext.userId(jwt), id, request.name(), request.pinCode(), request.zoneId());
    }

    @DeleteMapping("/api/v1/places/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.EDIT);
        placeService.delete(CallerContext.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/places/bulk-import")
    public BulkImportResult bulkImport(@RequestBody BulkImportRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.ZONES, PermissionAction.CREATE);
        return bulkImportService.importPlaces(CallerContext.userId(jwt), request.zoneId(), request.rows());
    }
}
