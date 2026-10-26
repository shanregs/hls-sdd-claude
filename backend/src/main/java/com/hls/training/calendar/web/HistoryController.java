package com.hls.training.calendar.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.HistoryQueries;
import com.hls.training.calendar.internal.HistoryQueries.History;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Training completion history (spec 024 contracts/training-api.md, History and my training). */
@RestController("trainingHistoryController")
@RequestMapping("/api/v1/training")
public class HistoryController {

    private final HistoryQueries queries;
    private final PermissionGuard guard;

    public HistoryController(HistoryQueries queries, PermissionGuard guard) {
        this.queries = queries;
        this.guard = guard;
    }

    @GetMapping("/people/{kind}/{id}/history")
    public History person(
            @PathVariable String kind, @PathVariable UUID id, @RequestParam(required = false) Integer year, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING, PermissionAction.VIEW);
        return queries.of(caller(jwt), kind.toUpperCase(Locale.ROOT), id, year);
    }

    @GetMapping("/people/{kind}/{id}/history/export")
    public ResponseEntity<String> export(
            @PathVariable String kind, @PathVariable UUID id, @RequestParam(required = false) Integer year, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TRAINING, PermissionAction.EXPORT);
        String csv = queries.csv(queries.of(caller(jwt), kind.toUpperCase(Locale.ROOT), id, year));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"training-history.csv\"")
                .body(csv);
    }

    /** The caller's own history: a Teacher with TRAINING_REGISTRATION View, or a Manager with TRAINING View. */
    @GetMapping("/me/history")
    public History mine(@RequestParam(required = false) Integer year, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        boolean allowed = guard.isGranted(roles, PermissionModule.TRAINING_REGISTRATION, PermissionAction.VIEW)
                || guard.isGranted(roles, PermissionModule.TRAINING, PermissionAction.VIEW);
        if (!allowed) {
            throw new AccessDeniedException("Not authorized for training");
        }
        return queries.mine(caller(jwt), year);
    }

    private static Caller caller(Jwt jwt) {
        Set<Role> roles = CallerContext.roles(jwt);
        return new Caller(CallerContext.userId(jwt), roles);
    }
}
