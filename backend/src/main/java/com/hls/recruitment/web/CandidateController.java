package com.hls.recruitment.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.recruitment.internal.AssessmentService;
import com.hls.recruitment.internal.AssessmentService.AssessmentRequest;
import com.hls.recruitment.internal.CandidateService;
import com.hls.recruitment.internal.CandidateService.CandidateDto;
import com.hls.recruitment.internal.CandidateService.HistoryRow;
import com.hls.recruitment.internal.CandidateService.ImportResult;
import com.hls.recruitment.internal.CandidateService.NewCandidate;
import com.hls.recruitment.internal.CandidateService.OutcomeRequest;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

/** Candidates, outcomes and assessment (module {@code RECRUITMENT}); writes follow the own-drive rule. */
@RestController
@RequestMapping("/api/v1/recruitment")
public class CandidateController {

    private final CandidateService candidates;
    private final AssessmentService assessments;
    private final PermissionGuard guard;

    public CandidateController(CandidateService candidates, AssessmentService assessments, PermissionGuard guard) {
        this.candidates = candidates;
        this.assessments = assessments;
        this.guard = guard;
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.RECRUITMENT, action);
    }

    @GetMapping("/candidates")
    public List<CandidateDto> search(
            @RequestParam(name = "drive", required = false) UUID drive,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return candidates.search(drive, outcome, query);
    }

    @PostMapping("/drives/{id}/candidates")
    public ResponseEntity<CandidateDto> add(
            @PathVariable UUID id, @RequestBody NewCandidate request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(201)
                .body(candidates.add(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request));
    }

    @PostMapping("/drives/{id}/candidates/import")
    public ImportResult importCsv(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        try {
            return candidates.importCsv(CallerContext.userId(jwt), CallerContext.roles(jwt), id, file.getBytes());
        } catch (IOException e) {
            throw new InvalidInputException("The file could not be read.");
        }
    }

    @PostMapping("/candidates/{id}/outcome")
    public CandidateDto outcome(
            @PathVariable UUID id, @RequestBody OutcomeRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return candidates.setOutcome(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/candidates/{id}/assessment")
    public CandidateDto assess(
            @PathVariable UUID id, @RequestBody AssessmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return assessments.record(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @GetMapping("/candidates/{id}/history")
    public List<HistoryRow> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return candidates.historyOf(id);
    }
}
