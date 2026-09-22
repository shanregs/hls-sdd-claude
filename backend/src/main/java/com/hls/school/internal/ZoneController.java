package com.hls.school.internal;

import com.hls.school.api.ZoneAssignmentConflictException;
import com.hls.school.api.dto.CurrentSchoolZoneAssignment;
import com.hls.school.api.dto.PlaceView;
import com.hls.school.api.dto.SchoolZoneAnswer;
import com.hls.school.api.dto.ZoneView;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints per contracts/school-zone-api.yaml. Reads
 * {@code @AuthenticationPrincipal Jwt} directly, the same pattern every
 * other controller in this codebase uses — no {@code CurrentUserResolver}.
 * Every endpoint here is Director/Admin-only.
 */
@RestController
@RequestMapping("/api/v1/school")
public class ZoneController {

    private final ZoneService zoneService;
    private final PlaceService placeService;

    public ZoneController(ZoneService zoneService, PlaceService placeService) {
        this.zoneService = zoneService;
        this.placeService = placeService;
    }

    public record ZoneRequest(String name) {
    }

    public record AssignSchoolZoneRequest(UUID schoolId, UUID zoneId, UUID endsAssignmentId) {
    }

    public record PlaceRequest(UUID zoneId, String name, String pincode) {
    }

    public record ConflictError(String message) {
    }

    @PostMapping("/zones")
    public ZoneView createZone(@RequestBody ZoneRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        UUID zoneId = zoneService.createZone(request.name(), userId(jwt));
        return new ZoneView(zoneId, request.name());
    }

    @GetMapping("/zones/{zoneId}")
    public ResponseEntity<ZoneView> getZone(@PathVariable UUID zoneId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return zoneService.findById(zoneId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/zones/{zoneId}/schools")
    public List<UUID> getZoneSchools(@PathVariable UUID zoneId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return zoneService.currentSchoolsForZone(zoneId);
    }

    @PostMapping("/school-zone-assignments")
    public CurrentSchoolZoneAssignment assignSchoolZone(
            @RequestBody AssignSchoolZoneRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return zoneService.assignSchoolToZone(request.schoolId(), request.zoneId(), request.endsAssignmentId(), userId(jwt));
    }

    @GetMapping("/schools/{schoolId}/zone")
    public SchoolZoneAnswer getSchoolZone(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return zoneService.currentZoneForSchool(schoolId);
    }

    @PostMapping("/places")
    public PlaceView addPlace(@RequestBody PlaceRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return placeService.addPlace(request.zoneId(), request.name(), request.pincode(), userId(jwt));
    }

    @GetMapping("/places")
    public List<PlaceView> findPlaces(
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        if (pincode != null) {
            return placeService.findByPincode(pincode);
        }
        if (name != null) {
            return placeService.findByName(name);
        }
        return List.of();
    }

    @GetMapping("/zones/{zoneId}/places")
    public List<PlaceView> getZonePlaces(@PathVariable UUID zoneId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return placeService.findByZoneId(zoneId);
    }

    @ExceptionHandler(ZoneAssignmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ConflictError handleConflict(ZoneAssignmentConflictException e) {
        return new ConflictError("This School's Zone assignment was already changed by someone else. Refresh and retry.");
    }

    // ---- helpers -------------------------------------------------------------------------

    private void requireDirectorOrAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Set<String> roleSet = roles == null ? Set.of() : Set.copyOf(roles);
        if (!roleSet.contains("DIRECTOR") && !roleSet.contains("ADMIN")) {
            throw new AccessDeniedException("Only Director or Admin may perform this action");
        }
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
