package com.hls.transfer.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.transfer.internal.Caller;
import com.hls.transfer.internal.MovementHistoryQueries;
import com.hls.transfer.internal.MovementHistoryQueries.MovementLine;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A Teacher's and a School's movement history; View grant plus the movement scope. */
@RestController
@RequestMapping("/api/v1/transfers")
public class MovementHistoryController {

    private final MovementHistoryQueries queries;
    private final PermissionGuard guard;

    public MovementHistoryController(MovementHistoryQueries queries, PermissionGuard guard) {
        this.queries = queries;
        this.guard = guard;
    }

    private Caller caller(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_TRANSFERS, PermissionAction.VIEW);
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt), false, false);
    }

    @GetMapping("/teachers/{teacherId}/movements")
    public List<MovementLine> teacher(@PathVariable UUID teacherId, @AuthenticationPrincipal Jwt jwt) {
        return queries.ofTeacher(caller(jwt), teacherId);
    }

    @GetMapping("/schools/{schoolId}/movements")
    public List<MovementLine> school(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        return queries.ofSchool(caller(jwt), schoolId);
    }
}
