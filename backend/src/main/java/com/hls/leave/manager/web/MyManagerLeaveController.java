package com.hls.leave.manager.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.leave.manager.internal.ManagerLeaveService;
import com.hls.leave.manager.internal.ManagerLeaveService.LeaveView;
import com.hls.leave.manager.internal.ManagerLeaveService.RequestInput;
import com.hls.leave.manager.internal.ManagerLeaveService.TypeView;
import com.hls.school.api.CallerContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A Manager's own leave (spec 032 US3): the types, the requests, a new request and the cancellation of a pending one. */
@RestController
@RequestMapping("/api/v1/manager-attendance/me/leave")
public class MyManagerLeaveController {

    private final ManagerLeaveService leave;
    private final PermissionGuard guard;

    public MyManagerLeaveController(ManagerLeaveService leave, PermissionGuard guard) {
        this.leave = leave;
        this.guard = guard;
    }

    @GetMapping("/types")
    public List<TypeView> types(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return leave.activeTypes();
    }

    @GetMapping
    public List<LeaveView> mine(@AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return leave.mine(CallerContext.userId(jwt));
    }

    @PostMapping
    public ResponseEntity<LeaveView> request(@RequestBody RequestInput body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(leave.request(CallerContext.userId(jwt), body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.DELETE);
        leave.cancel(CallerContext.userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_MANAGER_ATTENDANCE, action);
    }
}
