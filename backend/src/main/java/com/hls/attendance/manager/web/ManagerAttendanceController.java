package com.hls.attendance.manager.web;

import com.hls.attendance.manager.internal.CheckInPhotoService;
import com.hls.attendance.manager.internal.CheckInService;
import com.hls.attendance.manager.internal.ManagerContext;
import com.hls.attendance.manager.internal.CorrectionService;
import com.hls.attendance.manager.internal.ManagerDayView;
import com.hls.attendance.manager.internal.ManagerGridService;
import com.hls.attendance.manager.internal.RegularizationService;
import com.hls.attendance.manager.internal.RegularizationService.RegularizationView;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin and Director endpoints over Manager attendance (spec 032): the grid, a Manager's month, corrections, the
 * completion of a missing check-out, the opening of a check-in photo and the decisions on forgotten check-ins. The
 * lock arrives with a later story.
 */
@RestController
@RequestMapping("/api/v1/manager-attendance")
public class ManagerAttendanceController {

    private final CheckInService checkIns;
    private final CheckInPhotoService photos;
    private final ManagerContext context;
    private final ManagerGridService grid;
    private final CorrectionService corrections;
    private final RegularizationService regularizations;
    private final PermissionGuard guard;

    public ManagerAttendanceController(
            CheckInService checkIns,
            CheckInPhotoService photos,
            ManagerContext context,
            ManagerGridService grid,
            CorrectionService corrections,
            RegularizationService regularizations,
            PermissionGuard guard) {
        this.checkIns = checkIns;
        this.photos = photos;
        this.grid = grid;
        this.corrections = corrections;
        this.regularizations = regularizations;
        this.context = context;
        this.guard = guard;
    }

    public record RejectRequest(String reason) {}

    @GetMapping("/grid")
    public ManagerGridService.Grid grid(
            @RequestParam String month,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_ATTENDANCE, PermissionAction.VIEW);
        return grid.grid(ManagerGridService.parseMonth(month), zoneId, q);
    }

    @GetMapping("/managers/{managerId}/months/{month}")
    public ManagerGridService.MonthView managerMonth(
            @PathVariable UUID managerId, @PathVariable String month, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_ATTENDANCE, PermissionAction.VIEW);
        return grid.managerMonth(managerId, ManagerGridService.parseMonth(month));
    }

    @PutMapping("/managers/{managerId}/days/{date}")
    public ManagerDayView correct(
            @PathVariable UUID managerId,
            @PathVariable LocalDate date,
            @RequestBody CorrectionService.Correction body,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_ATTENDANCE, PermissionAction.EDIT);
        return corrections.correct(CallerContext.userId(jwt), managerId, date, body);
    }

    @GetMapping("/regularizations")
    public List<RegularizationView> regularizations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID managerId,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_LEAVE, PermissionAction.VIEW);
        return regularizations.list(status, managerId);
    }

    @PostMapping("/regularizations/{id}/approve")
    public RegularizationView approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_LEAVE, PermissionAction.APPROVE);
        return regularizations.approve(CallerContext.userId(jwt), id);
    }

    @PostMapping("/regularizations/{id}/reject")
    public RegularizationView reject(
            @PathVariable UUID id, @RequestBody RejectRequest body, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_LEAVE, PermissionAction.APPROVE);
        return regularizations.reject(CallerContext.userId(jwt), id, body.reason());
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
