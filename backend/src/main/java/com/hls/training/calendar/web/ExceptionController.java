package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.ExceptionBoard;
import com.hls.training.calendar.internal.ExceptionBoard.CloseBody;
import com.hls.training.calendar.internal.ExceptionBoard.ExceptionRow;
import com.hls.training.calendar.internal.ExceptionBoard.Filter;
import com.hls.training.calendar.internal.ExceptionBoard.Page;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Non-compliance exceptions (spec 024 contracts/training-api.md, Exceptions): view, close (Edit) and export (Export). */
@RestController("trainingExceptionController")
@RequestMapping("/api/v1/training/exceptions")
public class ExceptionController {

    private final ExceptionBoard board;
    private final PermissionGuard guard;

    public ExceptionController(ExceptionBoard board, PermissionGuard guard) {
        this.board = board;
        this.guard = guard;
    }

    @GetMapping
    public Page list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return board.list(caller(jwt), new Filter(month, zoneId, managerId, role, status, page, size));
    }

    @PostMapping("/{id}/close")
    public ExceptionRow close(@PathVariable UUID id, @RequestBody CloseBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return board.close(caller(jwt), id, body);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EXPORT);
        String csv = board.exportCsv(caller(jwt), new Filter(month, zoneId, managerId, role, status, 0, Integer.MAX_VALUE));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"training-exceptions.csv\"")
                .body(csv);
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING, action);
    }

    private static Caller caller(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        return new Caller(CallerContext.userId(jwt), roles);
    }
}
