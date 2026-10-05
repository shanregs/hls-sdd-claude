package com.hls.recruitment.marketing.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.marketing.internal.PipelineService;
import com.hls.recruitment.marketing.internal.PipelineService.Board;
import com.hls.recruitment.marketing.internal.PipelineService.ReviewRequest;
import com.hls.recruitment.marketing.internal.PipelineService.StageRequest;
import com.hls.recruitment.marketing.internal.ProposalService;
import com.hls.recruitment.marketing.internal.ProposalService.ProposalDto;
import com.hls.recruitment.marketing.internal.ProposalService.ProposalRequest;
import com.hls.recruitment.marketing.internal.ProspectService.ProspectDto;
import com.hls.school.api.CallerContext;
import java.util.List;
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

/** The board, stage moves, the Final Stage review and proposals (module {@code MARKETING}). */
@RestController
@RequestMapping("/api/v1/marketing")
public class PipelineController {

    private final PipelineService pipeline;
    private final ProposalService proposals;
    private final PermissionGuard guard;

    public PipelineController(PipelineService pipeline, ProposalService proposals, PermissionGuard guard) {
        this.pipeline = pipeline;
        this.proposals = proposals;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MARKETING, action);
    }

    @GetMapping("/pipeline")
    public Board board(
            @RequestParam(required = false) UUID zone, @RequestParam(required = false) UUID owner, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return pipeline.board(CallerContext.userId(jwt), CallerContext.roles(jwt), zone, owner);
    }

    @PostMapping("/prospects/{id}/stage")
    public ProspectDto stage(@PathVariable UUID id, @RequestBody StageRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return pipeline.move(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/prospects/{id}/review")
    public ProspectDto review(@PathVariable UUID id, @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.APPROVE);
        return pipeline.review(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @GetMapping("/prospects/{id}/proposals")
    public List<ProposalDto> proposals(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return proposals.list(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping("/prospects/{id}/proposals")
    public ResponseEntity<ProposalDto> createProposal(
            @PathVariable UUID id, @RequestBody ProposalRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201).body(proposals.create(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request));
    }
}
