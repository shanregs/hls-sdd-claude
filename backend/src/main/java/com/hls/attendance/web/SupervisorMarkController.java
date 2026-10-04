package com.hls.attendance.web;

import com.hls.attendance.api.MarkView;
import com.hls.attendance.internal.AttendanceGridService;
import com.hls.attendance.internal.AttendanceScope;
import com.hls.attendance.internal.MarkHistoryService;
import com.hls.attendance.internal.MarkService;
import com.hls.attendance.internal.SetByKind;
import com.hls.attendance.internal.TeacherMonthViewService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor marking and the Manager grid (spec 008 US2). Check order on every route: permission
 * (403), then data scope (404), then business rules (409).
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class SupervisorMarkController {

    public record MarkRequest(String statusCode, BigDecimal dayValue, String note, Long version) {}

    private final AttendanceScope scope;
    private final MarkService markService;
    private final MarkHistoryService historyService;
    private final TeacherMonthViewService monthViews;
    private final AttendanceGridService grids;
    private final PermissionGuard guard;

    public SupervisorMarkController(
            AttendanceScope scope,
            MarkService markService,
            MarkHistoryService historyService,
            TeacherMonthViewService monthViews,
            AttendanceGridService grids,
            PermissionGuard guard) {
        this.scope = scope;
        this.markService = markService;
        this.historyService = historyService;
        this.monthViews = monthViews;
        this.grids = grids;
        this.guard = guard;
    }

    @GetMapping("/teachers/{teacherId}")
    public TeacherMonthViewService.TeacherMonthView month(
            @PathVariable UUID teacherId, @RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireEither(roles, PermissionAction.VIEW);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return monthViews.view(teacherId, MonthParam.parse(month), TeacherMonthViewService.Viewer.SUPERVISOR);
    }

    @PutMapping("/teachers/{teacherId}/marks/{date}")
    public MarkView mark(
            @PathVariable UUID teacherId,
            @PathVariable LocalDate date,
            @RequestBody MarkRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireEither(roles, PermissionAction.VIEW);
        requireEither(roles, markService.hasMark(teacherId, date) ? PermissionAction.EDIT : PermissionAction.CREATE);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return markService.setMark(
                CallerContext.userId(jwt),
                teacherId,
                date,
                request.statusCode(),
                request.dayValue(),
                request.note(),
                request.version(),
                SetByKind.SUPERVISOR);
    }

    @DeleteMapping("/teachers/{teacherId}/marks/{date}")
    public ResponseEntity<Void> clear(
            @PathVariable UUID teacherId, @PathVariable LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireClear(roles);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        markService.clearMark(CallerContext.userId(jwt), teacherId, date);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teachers/{teacherId}/marks/{date}/history")
    public List<MarkHistoryService.HistoryView> history(
            @PathVariable UUID teacherId, @PathVariable LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        requireEither(roles, PermissionAction.VIEW);
        scope.requireInScope(CallerContext.userId(jwt), roles, teacherId);
        return historyService.of(teacherId, date);
    }

    @GetMapping("/teacher-grid")
    public AttendanceGridService.GridResponse teacherGrid(
            @RequestParam String month,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        guard.require(roles, PermissionModule.TEACHER_ATTENDANCE, PermissionAction.VIEW);
        return grids.grid(
                CallerContext.userId(jwt),
                roles,
                MonthParam.parse(month),
                new AttendanceGridService.Filter(query, null, null, null, null),
                page,
                size);
    }

    /** Either attendance module may grant the action (Manager via TEACHER_ATTENDANCE, Admin/Director via ATTENDANCE). */
    private void requireEither(Set<Role> roles, PermissionAction action) {
        try {
            guard.require(roles, PermissionModule.TEACHER_ATTENDANCE, action);
        } catch (AccessDeniedException e) {
            guard.require(roles, PermissionModule.ATTENDANCE, action);
        }
    }

    /** Clearing a mark: Manager through TEACHER_ATTENDANCE.EDIT, Admin through ATTENDANCE.DELETE. */
    private void requireClear(Set<Role> roles) {
        try {
            guard.require(roles, PermissionModule.TEACHER_ATTENDANCE, PermissionAction.EDIT);
        } catch (AccessDeniedException e) {
            guard.require(roles, PermissionModule.ATTENDANCE, PermissionAction.DELETE);
        }
    }
}
