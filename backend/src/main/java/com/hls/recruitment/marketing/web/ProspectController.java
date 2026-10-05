package com.hls.recruitment.marketing.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.ProspectDetailService;
import com.hls.recruitment.marketing.internal.ProspectService;
import com.hls.recruitment.marketing.internal.ProspectService.OwnerRequest;
import com.hls.recruitment.marketing.internal.ProspectService.ProspectDto;
import com.hls.recruitment.marketing.internal.ProspectService.ProspectRequest;
import com.hls.recruitment.marketing.internal.ProspectService.ProspectRow;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
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

/** Prospects (module {@code MARKETING}); a Zone Manager sees and changes only prospects of their Zones. */
@RestController
@RequestMapping("/api/v1/marketing/prospects")
public class ProspectController {

    private final ProspectService prospects;
    private final ProspectDetailService details;
    private final PermissionGuard guard;

    public ProspectController(ProspectService prospects, ProspectDetailService details, PermissionGuard guard) {
        this.prospects = prospects;
        this.details = details;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING, action);
    }

    @GetMapping
    public PageResponse<ProspectRow> list(
            @RequestParam(required = false) UUID zone,
            @RequestParam(required = false) UUID owner,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return prospects.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                zone,
                owner,
                stage,
                query,
                Math.max(0, page),
                Math.min(100, Math.max(1, size)));
    }

    @PostMapping
    public ResponseEntity<ProspectDto> create(@RequestBody ProspectRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(prospects.create(CallerContext.userId(jwt), CallerContext.roles(jwt), request));
    }

    @GetMapping("/{id}")
    public ProspectDetailService.ProspectDetail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return details.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PutMapping("/{id}")
    public ProspectDto update(@PathVariable UUID id, @RequestBody ProspectRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return prospects.update(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/{id}/owner")
    public ProspectDto owner(@PathVariable UUID id, @RequestBody OwnerRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return prospects.changeOwner(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }
}
