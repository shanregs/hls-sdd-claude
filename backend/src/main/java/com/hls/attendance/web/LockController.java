package com.hls.attendance.web;

import com.hls.attendance.api.RollupView;
import com.hls.attendance.internal.AttendanceScope;
import com.hls.attendance.internal.MonthLockService;
import com.hls.attendance.internal.TeacherMonthViewService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Month lock, reopen and relock (spec 008 US6). All need {@code ATTENDANCE.PROCESS}. */
@RestController
@RequestMapping("/api/v1/attendance")
public class LockController {

    public record ReopenRequest(String reason) {}

    private final MonthLockService locks;
    private final AttendanceScope scope;
    private final PermissionGuard guard;

    public LockController(MonthLockService locks, AttendanceScope scope, PermissionGuard guard) {
        this.locks = locks;
        this.scope = scope;
        this.guard = guard;
    }

    @PostMapping("/months/{yearMonth}/lock")
    public Map<String, Integer> lock(@PathVariable String yearMonth, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ATTENDANCE, PermissionAction.PROCESS);
        return Map.of("locked", locks.lockMonth(CallerContext.userId(jwt), MonthParam.parse(yearMonth)));
    }

    @PostMapping("/teachers/{teacherId}/months/{yearMonth}/reopen")
    public Map<String, String> reopen(
            @PathVariable UUID teacherId,
            @PathVariable String yearMonth,
            @RequestBody ReopenRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.PROCESS);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        locks.reopen(CallerContext.userId(jwt), teacherId, MonthParam.parse(yearMonth), request.reason());
        return Map.of("state", "OPEN");
    }

    @PostMapping("/teachers/{teacherId}/months/{yearMonth}/relock")
    public RollupView relock(
            @PathVariable UUID teacherId, @PathVariable String yearMonth, @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.PROCESS);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return TeacherMonthViewService.toView(
                locks.relock(CallerContext.userId(jwt), teacherId, MonthParam.parse(yearMonth)), true);
    }

    @GetMapping("/teachers/{teacherId}/months/{yearMonth}/events")
    public List<MonthLockService.EventView> events(
            @PathVariable UUID teacherId, @PathVariable String yearMonth, @AuthenticationPrincipal Jwt jwt) {
        var roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.VIEW);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return locks.events(teacherId, MonthParam.parse(yearMonth));
    }
}
