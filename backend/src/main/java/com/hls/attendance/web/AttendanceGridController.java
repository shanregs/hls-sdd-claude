package com.hls.attendance.web;

import com.hls.attendance.internal.AttendanceGridService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The organization-wide month grid for Admin and Director (spec 008 US4), with combinable filters. */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceGridController {

    private final AttendanceGridService grids;
    private final PermissionGuard guard;

    public AttendanceGridController(AttendanceGridService grids, PermissionGuard guard) {
        this.grids = grids;
        this.guard = guard;
    }

    @GetMapping("/grid")
    public AttendanceGridService.GridResponse grid(
            @RequestParam String month,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.VIEW);
        return grids.grid(
                CallerContext.userId(jwt),
                roles,
                MonthParam.parse(month),
                new AttendanceGridService.Filter(query, zoneId, schoolId, managerId, status),
                page,
                size);
    }
}
