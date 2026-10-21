package com.hls.performance.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.performance.internal.Caller;
import com.hls.performance.internal.PerformanceQueries;
import com.hls.performance.internal.PerformanceQueries.Filter;
import com.hls.performance.internal.PlanService;
import com.hls.performance.internal.PlanService.PlanRequest;
import com.hls.performance.internal.PlanService.PlanView;
import com.hls.performance.internal.PlanService.UpdateRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Planned observations (contracts/performance-api.md): each is an Observation task of the Teacher's Manager. */
@RestController
@RequestMapping("/api/v1/teacher-performance/plans")
public class PlanController {

    private final PlanService service;
    private final PerformanceQueries queries;
    private final PermissionGuard guard;

    public PlanController(PlanService service, PerformanceQueries queries, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_PERFORMANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    /** {@code view} is "planned" (all open plans) or "overdue". */
    @GetMapping
    public List<PlanView> list(
            @RequestParam(defaultValue = "planned") String view,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID teacherId,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.planned(caller(jwt), view, new Filter(zoneId, schoolId, managerId, teacherId, null, null, null, null, null));
    }

    @GetMapping("/{id}")
    public PlanView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.get(caller(jwt), id);
    }

    /** 201 when a plan was made, 200 with the existing plan (changed if the date or reason differ) when one was already open. */
    @PostMapping
    public ResponseEntity<PlanView> plan(@RequestBody PlanRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        PlanView view = service.plan(caller(jwt), body);
        return ResponseEntity.status(view.created() ? 201 : 200).body(view);
    }

    @PutMapping("/{id}")
    public PlanView update(@PathVariable UUID id, @RequestBody UpdateRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.update(caller(jwt), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @RequestParam String reason, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        service.cancel(caller(jwt), id, reason);
        return ResponseEntity.noContent().build();
    }
}
