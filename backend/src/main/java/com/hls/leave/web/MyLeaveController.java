package com.hls.leave.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.leave.internal.LeaveRequestService;
import com.hls.leave.internal.LeaveRequestService.Draft;
import com.hls.leave.internal.LeaveRequestService.Preview;
import com.hls.leave.internal.LeaveStatus;
import com.hls.leave.internal.LeaveViewFactory.LeaveView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import java.time.LocalDate;
import java.util.List;
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

/** A Teacher's own leave (spec 009 US1, US2). The Teacher is always the caller's linked record. */
@RestController
@RequestMapping("/api/v1/me/leave")
public class MyLeaveController {

    public record DraftRequest(
            UUID leaveTypeId, LocalDate firstDate, LocalDate lastDate, Boolean halfDayStart, Boolean halfDayEnd, String reason) {

        Draft toDraft() {
            return new Draft(
                    leaveTypeId,
                    firstDate,
                    lastDate,
                    Boolean.TRUE.equals(halfDayStart),
                    Boolean.TRUE.equals(halfDayEnd),
                    reason);
        }
    }

    public record TypeView(UUID id, String code, String name) {}

    private final LeaveRequestService service;
    private final PermissionGuard guard;

    public MyLeaveController(LeaveRequestService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/types")
    public List<TypeView> types(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_LEAVE, PermissionAction.VIEW);
        return service.activeTypes().stream().map(t -> new TypeView(t.getId(), t.getCode(), t.getName())).toList();
    }

    @PostMapping("/preview")
    public Preview preview(@RequestBody DraftRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_LEAVE, PermissionAction.CREATE);
        return service.preview(CallerContext.userId(jwt), request.toDraft());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveView submit(@RequestBody DraftRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_LEAVE, PermissionAction.CREATE);
        return service.submit(CallerContext.userId(jwt), request.toDraft());
    }

    @GetMapping
    public PageResponse<LeaveView> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_LEAVE, PermissionAction.VIEW);
        return service.listOwn(CallerContext.userId(jwt), parseStatus(status), page, size);
    }

    @PostMapping("/{id}/cancel")
    public LeaveView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_LEAVE, PermissionAction.DELETE);
        return service.cancelOwn(CallerContext.userId(jwt), id);
    }

    static LeaveStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return LeaveStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown status " + status + ".");
        }
    }
}
