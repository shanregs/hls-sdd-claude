package com.hls.schoolbilling.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.PageResponse;
import com.hls.schoolbilling.internal.ContractDtos.ContractDto;
import com.hls.schoolbilling.internal.ContractDtos.ContractListRow;
import com.hls.schoolbilling.internal.ContractDtos.ContractStatus;
import com.hls.schoolbilling.internal.ContractDtos.EndRequest;
import com.hls.schoolbilling.internal.ContractDtos.MouRequest;
import com.hls.schoolbilling.internal.ContractDtos.NewContractRequest;
import com.hls.schoolbilling.internal.ContractDtos.SchoolContractsDto;
import com.hls.schoolbilling.internal.ContractDtos.SignatoryCandidate;
import com.hls.schoolbilling.internal.ContractListService;
import com.hls.schoolbilling.internal.ContractService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The School contracts (MoU) of spec 012. Every route is guarded by {@code SCHOOL_CONTRACTS}; a Zone Manager
 * only reads (and only their own Schools: another School is answered as not found).
 */
@RestController
@RequestMapping("/api/v1/school-contracts")
public class ContractController {

    private final ContractService service;
    private final ContractListService listService;
    private final PermissionGuard guard;

    public ContractController(ContractService service, ContractListService listService, PermissionGuard guard) {
        this.service = service;
        this.listService = listService;
        this.guard = guard;
    }

    @GetMapping
    public PageResponse<ContractListRow> list(
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return listService.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                status,
                managerId,
                Math.max(0, page),
                Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/schools/{schoolId}")
    public SchoolContractsDto school(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.schoolContracts(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId);
    }

    @GetMapping("/signatory-candidates")
    public List<SignatoryCandidate> candidates(@RequestParam UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.VIEW);
        return service.signatoryCandidates(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId);
    }

    @PostMapping("/schools/{schoolId}/contracts")
    public ResponseEntity<ContractDto> create(
            @PathVariable UUID schoolId, @RequestBody NewContractRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.CREATE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(CallerContext.userId(jwt), CallerContext.roles(jwt), schoolId, request));
    }

    @PutMapping("/contracts/{id}/mou")
    public ContractDto recordMou(
            @PathVariable UUID id, @RequestBody MouRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.recordMou(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request);
    }

    @PostMapping("/contracts/{id}/end")
    public ContractDto end(@PathVariable UUID id, @RequestBody EndRequest request, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.end(
                CallerContext.userId(jwt), CallerContext.roles(jwt), id, request == null ? null : request.endsOn());
    }

    @PostMapping("/contracts/{id}/cancel")
    public ContractDto cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        require(jwt, PermissionAction.EDIT);
        return service.cancel(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    private void require(Jwt jwt, PermissionAction action) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SCHOOL_CONTRACTS, action);
    }
}
