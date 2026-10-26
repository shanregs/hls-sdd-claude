package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.AttendanceRecordService;
import com.hls.training.calendar.internal.AttendanceRecordService.RosterRow;
import com.hls.training.calendar.internal.AttendanceRecordService.SaveBody;
import com.hls.training.calendar.internal.AttendanceRecordService.SaveResult;
import com.hls.training.calendar.internal.AttendanceRecordService.WalkInBody;
import com.hls.training.calendar.internal.Caller;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Attendance of a session (spec 024 contracts/training-api.md, Attendance); the {@code TRAINING_ATTENDANCE} grant is re-checked. */
@RestController("trainingAttendanceController")
@RequestMapping("/api/v1/training/sessions/{id}")
public class AttendanceController {

    private final AttendanceRecordService service;
    private final PermissionGuard guard;

    public AttendanceController(AttendanceRecordService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/attendance")
    public List<RosterRow> roster(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.roster(caller(jwt), id);
    }

    @PutMapping("/attendance")
    public SaveResult save(@PathVariable UUID id, @RequestBody SaveBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.save(caller(jwt), id, body);
    }

    @PostMapping("/walk-ins")
    public SaveResult walkIn(@PathVariable UUID id, @RequestBody WalkInBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.walkIn(caller(jwt), id, body);
    }

    @PostMapping("/attendance/{entryId}/sync")
    public SaveResult sync(@PathVariable UUID id, @PathVariable UUID entryId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.sync(caller(jwt), id, entryId);
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING_ATTENDANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        return new Caller(CallerContext.userId(jwt), roles);
    }
}
