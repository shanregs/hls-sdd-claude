package com.hls.school.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ContactDetails;
import com.hls.school.api.SchoolContactsView;
import com.hls.school.internal.SchoolContactService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Principal and accountant contacts of a School (amendment A8 to spec 005). */
@RestController
@RequestMapping("/api/v1/schools/{id}/contacts")
public class SchoolContactController {

    private final SchoolContactService service;
    private final PermissionGuard permissionGuard;

    public SchoolContactController(SchoolContactService service, PermissionGuard permissionGuard) {
        this.service = service;
        this.permissionGuard = permissionGuard;
    }

    public record ContactsRequest(ContactDetails principal, ContactDetails accountant) {}

    @GetMapping
    public SchoolContactsView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.VIEW);
        return service.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PutMapping
    public SchoolContactsView replace(
            @PathVariable UUID id, @RequestBody ContactsRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.SCHOOLS, PermissionAction.EDIT);
        return service.replace(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.principal(), request.accountant());
    }
}
