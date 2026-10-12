package com.hls.attendance.web;

import com.hls.attendance.api.MarkView;
import com.hls.attendance.internal.AttendanceScope;
import com.hls.attendance.internal.PrincipalConfirmationService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Records the optional Principal confirmation of a day (amendment A2 to spec 008, D7). Same check order as the other
 * supervisor routes: permission (403), data scope (404), business rules (409). The Teacher cannot record it.
 */
@RestController
@RequestMapping("/api/v1/attendance/teachers/{teacherId}/marks/{date}/principal-confirmation")
public class PrincipalConfirmationController {

    public record ConfirmationRequest(Boolean confirmed, String principalName, String note) {}

    private final AttendanceScope scope;
    private final PrincipalConfirmationService service;
    private final PermissionGuard guard;

    public PrincipalConfirmationController(
            AttendanceScope scope, PrincipalConfirmationService service, PermissionGuard guard) {
        this.scope = scope;
        this.service = service;
        this.guard = guard;
    }

    @PutMapping
    public MarkView record(
            @PathVariable UUID teacherId,
            @PathVariable LocalDate date,
            @RequestBody ConfirmationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireEdit(roles);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return service.record(
                CallerContext.userId(jwt), teacherId, date, request.confirmed(), request.principalName(), request.note());
    }

    @DeleteMapping
    public MarkView clear(
            @PathVariable UUID teacherId, @PathVariable LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireEdit(roles);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return service.clear(CallerContext.userId(jwt), teacherId, date);
    }

    /** The Manager through TEACHER_ATTENDANCE, Admin and Director through ATTENDANCE. */
    private void requireEdit(Set<Role> roles) {
        try {
            guard.require(roles, PermissionModule.TEACHER_ATTENDANCE, PermissionAction.EDIT);
        } catch (AccessDeniedException e) {
            guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.EDIT);
        }
    }
}
