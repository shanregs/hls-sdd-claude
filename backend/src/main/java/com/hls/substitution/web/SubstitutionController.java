package com.hls.substitution.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.substitution.internal.AvailabilityService.Candidate;
import com.hls.substitution.internal.RequirementStatus;
import com.hls.substitution.internal.SubstitutionService;
import com.hls.substitution.internal.SubstitutionService.DetailView;
import com.hls.substitution.internal.SubstitutionService.RequirementView;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

/**
 * Substitution requirements in the caller's scope (spec 017a). Check order on every route: permission (403), data scope
 * (404), business rules (409).
 */
@RestController
@RequestMapping("/api/v1/substitutions")
public class SubstitutionController {

    public record AssignRequest(UUID substituteTeacherId, String note, Long version) {}

    public record ReasonRequest(String reason, Long version) {}

    public record CoverRequest(String note, Long version) {}

    public record InformRequest(String contact, String method, String note, Long version) {}

    private final SubstitutionService service;
    private final PermissionGuard guard;

    public SubstitutionController(SubstitutionService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public PageResponse<RequirementView> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Boolean sameDay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.VIEW);
        return service.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                parseStatuses(status),
                schoolId,
                parseDate(from, "from"),
                parseDate(to, "to"),
                sameDay,
                page,
                size);
    }

    /** The Manager cover entries, apart from Teacher credits (spec 040). */
    @GetMapping("/manager-covers")
    public com.hls.school.api.PageResponse<SubstitutionService.ManagerCoverView> managerCovers(
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "false") boolean sameDay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.VIEW);
        return service.managerCovers(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                schoolId,
                parseDate(from, "from"),
                parseDate(to, "to"),
                sameDay,
                page,
                size);
    }

    @GetMapping("/{id}")
    public DetailView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.VIEW);
        return service.detail(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @GetMapping("/{id}/candidates")
    public List<Candidate> candidates(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.VIEW);
        return service.candidates(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping("/{id}/assign")
    public RequirementView assign(
            @PathVariable UUID id, @RequestBody AssignRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.EDIT);
        return service.assign(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                request.substituteTeacherId(),
                request.note(),
                request.version());
    }

    @PostMapping("/{id}/release")
    public RequirementView release(
            @PathVariable UUID id, @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.EDIT);
        return service.release(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.reason(), request.version());
    }

    @PostMapping("/{id}/cover")
    public RequirementView cover(
            @PathVariable UUID id, @RequestBody CoverRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.EDIT);
        return service.cover(CallerContext.userId(jwt), CallerContext.roles(jwt), id, request.note(), request.version());
    }

    @PostMapping("/{id}/inform")
    public RequirementView inform(
            @PathVariable UUID id, @RequestBody InformRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTION, PermissionAction.EDIT);
        return service.inform(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                request.contact(),
                request.method(),
                request.note(),
                request.version());
    }

    /** {@code null} or blank gives the default (open and assigned); {@code ALL} every status. */
    static Set<RequirementStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if ("ALL".equalsIgnoreCase(status.trim())) {
            return EnumSet.allOf(RequirementStatus.class);
        }
        EnumSet<RequirementStatus> result = EnumSet.noneOf(RequirementStatus.class);
        for (String part : status.split(",")) {
            try {
                result.add(RequirementStatus.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("The status must be OPEN, ASSIGNED, MANAGER_COVER, CANCELLED or ALL.");
            }
        }
        return result;
    }

    static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The " + name + " date must look like 2026-10-31.");
        }
    }
}
