package com.hls.performance.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.performance.internal.Caller;
import com.hls.performance.internal.FeedbackService;
import com.hls.performance.internal.FeedbackService.CorrectRequest;
import com.hls.performance.internal.FeedbackService.FeedbackView;
import com.hls.performance.internal.FeedbackService.RecordRequest;
import com.hls.performance.internal.IssueService;
import com.hls.performance.internal.IssueService.IssueView;
import com.hls.performance.internal.PerformanceQueries;
import com.hls.performance.internal.PerformanceQueries.FeedbackRow;
import com.hls.performance.internal.PerformanceQueries.Filter;
import com.hls.performance.internal.PlanService;
import com.hls.performance.internal.PlanService.PlanView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
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

/** Principal feedback (contracts/performance-api.md), gated by the TEACHER_PERFORMANCE grants and the scope of the Teacher. */
@RestController
@RequestMapping("/api/v1/teacher-performance/feedback")
public class FeedbackController {

    private final FeedbackService service;
    private final IssueService issues;
    private final PlanService plans;
    private final PerformanceQueries queries;
    private final PermissionGuard guard;

    public FeedbackController(FeedbackService service, IssueService issues, PlanService plans, PerformanceQueries queries, PermissionGuard guard) {
        this.service = service;
        this.issues = issues;
        this.plans = plans;
        this.queries = queries;
        this.guard = guard;
    }

    public record WithdrawBody(String reason, Long version) {}

    public record PlanBody(LocalDate plannedOn) {}

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.TEACHER_PERFORMANCE, action);
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    @GetMapping
    public PageResponse<FeedbackRow> list(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean actionRequired,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.feedback(caller(jwt), new Filter(zoneId, schoolId, managerId, teacherId, category, status, from, to, null), actionRequired, page, size);
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean versions, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        if (versions) {
            return service.versions(caller(jwt), id);
        }
        return service.get(caller(jwt), id);
    }

    @PostMapping
    public ResponseEntity<FeedbackView> record(@RequestBody RecordRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(service.record(caller(jwt), body));
    }

    @PostMapping("/{id}/correct")
    public FeedbackView correct(@PathVariable UUID id, @RequestBody CorrectRequest body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.correct(caller(jwt), id, body);
    }

    @PostMapping("/{id}/withdraw")
    public FeedbackView withdraw(@PathVariable UUID id, @RequestBody WithdrawBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.withdraw(caller(jwt), id, body == null ? null : body.reason(), body == null ? null : body.version());
    }

    /** Opens an issue from this feedback, or adds it to the Teacher's open issue. */
    @PostMapping("/{id}/issue")
    public IssueView openIssue(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        Caller caller = caller(jwt);
        FeedbackView feedback = service.get(caller, id);
        return issues.openFromFeedback(caller, feedback.teacherId(), feedback.rootId());
    }

    /** Plans an observation from this feedback: reason School feedback, due the same day unless a date is given. */
    @PostMapping("/{id}/plan")
    public ResponseEntity<PlanView> plan(@PathVariable UUID id, @RequestBody(required = false) PlanBody body, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        Caller caller = caller(jwt);
        FeedbackView feedback = service.get(caller, id);
        PlanView view = plans.planFromFeedback(caller, feedback.teacherId(), feedback.rootId(), body == null ? null : body.plannedOn());
        return ResponseEntity.status(view.created() ? 201 : 200).body(view);
    }
}
