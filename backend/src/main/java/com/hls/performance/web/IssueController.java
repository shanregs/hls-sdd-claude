package com.hls.performance.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.performance.internal.Caller;
import com.hls.performance.internal.IssueService;
import com.hls.performance.internal.IssueService.CloseRequest;
import com.hls.performance.internal.IssueService.CorrectiveRequest;
import com.hls.performance.internal.IssueService.IssueView;
import com.hls.performance.internal.IssueService.ReferralRequest;
import com.hls.performance.internal.PerformanceQueries;
import com.hls.performance.internal.PerformanceQueries.Filter;
import com.hls.performance.internal.PerformanceQueries.IssueRow;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.time.LocalDate;
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

/** Performance issues, corrective actions and referrals (contracts/performance-api.md). */
@RestController
@RequestMapping("/api/v1/teacher-performance/issues")
public class IssueController {

    private final IssueService service;
    private final PerformanceQueries queries;
    private final PermissionGuard guard;

    public IssueController(IssueService service, PerformanceQueries queries, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.guard = guard;
    }

    public record NoteBody(String note) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_PERFORMANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    /** {@code view} is "open" (Open and Under correction) or "referred"; otherwise {@code status} filters. */
    @GetMapping
    public PageResponse<IssueRow> list(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.issues(caller(jwt), view, new Filter(zoneId, schoolId, managerId, teacherId, category, status, from, to, null), page, size);
    }

    @GetMapping("/{id}")
    public IssueView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.get(caller(jwt), id);
    }

    @PostMapping("/{id}/corrective-actions")
    public IssueView correctiveAction(@PathVariable UUID id, @RequestBody CorrectiveRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.recordCorrectiveAction(caller(jwt), id, body);
    }

    @PostMapping("/{id}/escalate")
    public IssueView escalate(@PathVariable UUID id, @RequestBody NoteBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.escalate(caller(jwt), id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/referral")
    public IssueView refer(@PathVariable UUID id, @RequestBody ReferralRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.refer(caller(jwt), id, body);
    }

    @PostMapping("/{id}/close")
    public IssueView close(@PathVariable UUID id, @RequestBody CloseRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.close(caller(jwt), id, body);
    }
}
