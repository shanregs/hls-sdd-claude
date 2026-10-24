package com.hls.schoolbilling.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.schoolbilling.internal.MouApprovalQueries;
import com.hls.schoolbilling.internal.MouApprovalService;
import com.hls.schoolbilling.internal.MouDtos.AmendRequest;
import com.hls.schoolbilling.internal.MouDtos.AmendResponse;
import com.hls.schoolbilling.internal.MouDtos.ApprovalDto;
import com.hls.schoolbilling.internal.MouDtos.DecisionRequest;
import com.hls.schoolbilling.internal.MouDtos.PendingRow;
import com.hls.schoolbilling.internal.MouDtos.VersionRequest;
import com.hls.schoolbilling.internal.MouTermsService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Approval of a MoU (spec 031 US2 and US3): detail, submit, approve, reject, terms correction and the pending list. */
@RestController
@RequestMapping("/api/v1/school-contracts")
public class MouApprovalController {

    private final MouApprovalService approvals;
    private final MouTermsService terms;
    private final MouApprovalQueries queries;
    private final PermissionGuard guard;

    public MouApprovalController(
            MouApprovalService approvals, MouTermsService terms, MouApprovalQueries queries, PermissionGuard guard) {
        this.approvals = approvals;
        this.terms = terms;
        this.queries = queries;
        this.guard = guard;
    }

    @GetMapping("/contracts/{id}/approval")
    public ApprovalDto detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return queries.detail(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping("/contracts/{id}/submit")
    public ApprovalDto submit(@PathVariable UUID id, @RequestBody VersionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return approvals.submit(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.version());
    }

    @PostMapping("/contracts/{id}/approve")
    public ApprovalDto approve(@PathVariable UUID id, @RequestBody VersionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return approvals.approve(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.version());
    }

    @PostMapping("/contracts/{id}/reject")
    public ApprovalDto reject(@PathVariable UUID id, @RequestBody DecisionRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return approvals.reject(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.version(), request.reason());
    }

    @PatchMapping("/contracts/{id}/terms")
    public AmendResponse amend(@PathVariable UUID id, @RequestBody AmendRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return terms.amend(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @GetMapping("/approvals/pending")
    public PageResponse<PendingRow> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return queries.pending(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                Math.max(0, page),
                Math.min(100, Math.max(1, size)));
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SCHOOL_CONTRACTS, action);
    }
}
