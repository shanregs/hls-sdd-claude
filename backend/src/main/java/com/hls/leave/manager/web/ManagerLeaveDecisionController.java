package com.hls.leave.manager.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.leave.manager.internal.ManagerLeaveService;
import com.hls.leave.manager.internal.ManagerLeaveService.LeaveView;
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Admin and Director decisions on Managers' leave (spec 032 US3). */
@RestController
@RequestMapping("/api/v1/manager-attendance/leave")
public class ManagerLeaveDecisionController {

    public record ReasonRequest(String reason) {}

    private final ManagerLeaveService leave;
    private final PermissionGuard guard;

    public ManagerLeaveDecisionController(ManagerLeaveService leave, PermissionGuard guard) {
        this.leave = leave;
        this.guard = guard;
    }

    @GetMapping
    public List<LeaveView> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID managerId,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return leave.list(status, managerId);
    }

    @PostMapping("/{id}/approve")
    public LeaveView approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return leave.approve(CallerContext.userId(jwt), id);
    }

    @PostMapping("/{id}/reject")
    public LeaveView reject(
            @PathVariable UUID id, @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return leave.reject(CallerContext.userId(jwt), id, body.reason());
    }

    @PostMapping("/{id}/revoke")
    public LeaveView revoke(
            @PathVariable UUID id, @RequestBody ReasonRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return leave.revoke(CallerContext.userId(jwt), id, body.reason());
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MANAGER_LEAVE, action);
    }
}
