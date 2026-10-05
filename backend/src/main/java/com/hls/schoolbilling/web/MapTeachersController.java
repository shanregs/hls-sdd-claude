package com.hls.schoolbilling.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.schoolbilling.internal.RemapService;
import com.hls.schoolbilling.internal.RemapService.Entry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Re-maps a School's current Teachers to the positions of a new contract in one step (spec 012 FR-007). Mapping
 * keeps the permission the interim placement used ({@code TEACHERS} {@code EDIT}); a Zone Manager only for
 * a contract of one of their own Schools.
 */
@RestController
@RequestMapping("/api/v1/school-contracts")
public class MapTeachersController {

    private final RemapService service;
    private final PermissionGuard guard;

    public MapTeachersController(RemapService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping("/contracts/{id}/map-teachers")
    public Map<String, Integer> mapTeachers(
            @PathVariable UUID id, @RequestBody List<Entry> entries, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        int remapped = service.remap(CallerContext.userId(jwt), CallerContext.roles(jwt), id, entries);
        return Map.of("remapped", remapped);
    }
}
