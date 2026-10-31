package com.hls.leave.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.leave.internal.LeaveDecisionService;
import com.hls.leave.internal.LeaveDecisionService.Detail;
import com.hls.leave.internal.LeaveDecisionService.ListResponse;
import com.hls.leave.internal.SameDayLeaveService;
import com.hls.leave.internal.LeaveViewFactory.LeaveView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Leave requests in the caller's scope (spec 009 US3, US4): list, view, approve, reject, revoke. */
@RestController
@RequestMapping("/api/v1/leave")
public class LeaveManagementController {

    public record ApproveRequest(String note, Long version) {}

    public record ReasonRequest(String reason, Long version) {}

    private final LeaveDecisionService service;
    private final SameDayLeaveService sameDay;
    private final PermissionGuard guard;

    public LeaveManagementController(
            LeaveDecisionService service, SameDayLeaveService sameDay, PermissionGuard guard) {
        this.service = service;
        this.sameDay = sameDay;
        this.guard = guard;
    }

    @GetMapping
    public ListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) Boolean sameDay,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.VIEW);
        return service.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                MyLeaveController.parseStatus(status),
                teacherId,
                schoolId,
                sameDay,
                parseMonth(month),
                page,
                size);
    }

    /** The leave types a supervisor can pick when recording same-day leave for a Teacher (spec 040). */
    @GetMapping("/types")
    public java.util.List<MyLeaveController.TypeView> types(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE);
        return sameDay.activeTypes().stream()
                .map(t -> new MyLeaveController.TypeView(t.getId(), t.getCode(), t.getName()))
                .toList();
    }

    /** Spec 040: record leave for a Teacher for today; it is Approved at once. Needs the right to approve leave. */
    @PostMapping("/same-day")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveView recordSameDay(@RequestBody SameDayLeaveService.RecordRequest body, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE);
        return sameDay.record(CallerContext.userId(jwt), CallerContext.roles(jwt), body);
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.VIEW);
        return service.detail(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping("/{id}/approve")
    public LeaveView approve(
            @PathVariable UUID id, @RequestBody ApproveRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE);
        return service.approve(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.note(), request.version());
    }

    @PostMapping("/{id}/reject")
    public LeaveView reject(
            @PathVariable UUID id, @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE);
        return service.reject(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.reason(), request.version());
    }

    @PostMapping("/{id}/revoke")
    public LeaveView revoke(
            @PathVariable UUID id, @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.LEAVE_MANAGEMENT, PermissionAction.APPROVE);
        return service.revoke(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.reason(), request.version());
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-01.");
        }
    }
}
