package com.hls.attendance.manager.web;

import com.hls.attendance.manager.internal.CheckInPhotoService;
import com.hls.attendance.manager.internal.CheckInService;
import com.hls.attendance.manager.internal.ManagerContext;
import com.hls.attendance.manager.internal.ManagerDayView;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin and Director endpoints over Manager attendance (spec 032). This slice holds the completion of a missing
 * check-out and the opening of a check-in photo; the grid, corrections and the lock arrive with later stories.
 */
@RestController
@RequestMapping("/api/v1/manager-attendance")
public class ManagerAttendanceController {

    private final CheckInService checkIns;
    private final CheckInPhotoService photos;
    private final ManagerContext context;
    private final PermissionGuard guard;

    public ManagerAttendanceController(
            CheckInService checkIns, CheckInPhotoService photos, ManagerContext context, PermissionGuard guard) {
        this.checkIns = checkIns;
        this.photos = photos;
        this.context = context;
        this.guard = guard;
    }

    @PostMapping("/managers/{managerId}/days/{date}/complete-check-out")
    public ManagerDayView completeCheckOut(
            @PathVariable UUID managerId,
            @PathVariable LocalDate date,
            @RequestBody MyManagerAttendanceController.CompleteRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_ATTENDANCE, PermissionAction.EDIT);
        context.byManagerId(managerId);
        return checkIns.completeCheckOut(
                CallerContext.userId(jwt),
                managerId,
                date,
                MyManagerAttendanceController.parseTime(body.time()),
                body.reason());
    }

    @GetMapping("/managers/{managerId}/days/{date}/photo")
    public ResponseEntity<byte[]> photo(
            @PathVariable UUID managerId, @PathVariable LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_ATTENDANCE, PermissionAction.VIEW);
        return MyManagerAttendanceController.download(photos.open(managerId, date));
    }
}
