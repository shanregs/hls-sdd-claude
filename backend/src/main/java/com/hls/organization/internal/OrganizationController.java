package com.hls.organization.internal;

import com.hls.organization.api.AssignmentConflictException;
import com.hls.organization.api.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST endpoints per contracts/organization-api.yaml. No {@code CurrentUserResolver}
 * (research.md §6) — reads {@code @AuthenticationPrincipal Jwt} directly, the same
 * pattern {@code identity.internal.AuthController} uses. Every endpoint here is
 * Director/Admin-only (Clarifications session 2026-09-21); the global
 * {@code SecurityFilterChain} (spec 002) already requires a valid bearer token
 * before any handler method runs — this class only adds the role check.
 */
@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {

    private final AccountabilityService accountabilityService;

    public OrganizationController(AccountabilityService accountabilityService) {
        this.accountabilityService = accountabilityService;
    }

    public record AssignmentRequest(UUID schoolId, UUID teacherId, UUID managerId, UUID endsAssignmentId) {
    }

    public record ConflictError(String message) {
    }

    @PostMapping("/school-assignments")
    public CurrentAssignment assignSchoolManager(@RequestBody AssignmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.assignSchoolManager(
                request.schoolId(), request.managerId(), request.endsAssignmentId(), userId(jwt));
    }

    @GetMapping("/schools/{schoolId}/accountable-manager")
    public AccountabilityAnswer getSchoolAccountableManager(
            @PathVariable UUID schoolId,
            @RequestParam(required = false) Instant asOf,
            @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return asOf == null
                ? accountabilityService.currentManagerForSchool(schoolId)
                : accountabilityService.managerForSchoolAsOf(schoolId, asOf);
    }

    @GetMapping("/schools/{schoolId}/assignment-history")
    public List<AssignmentHistoryEntry> getSchoolAssignmentHistory(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.schoolAssignmentHistory(schoolId);
    }

    @DeleteMapping("/school-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endSchoolAssignment(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        accountabilityService.endSchoolAssignment(assignmentId, userId(jwt));
    }

    @PostMapping("/teacher-assignments")
    public CurrentAssignment assignTeacherManager(@RequestBody AssignmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.assignTeacherManager(
                request.teacherId(), request.managerId(), request.endsAssignmentId(), userId(jwt));
    }

    @GetMapping("/teachers/{teacherId}/accountable-manager")
    public AccountabilityAnswer getTeacherAccountableManager(
            @PathVariable UUID teacherId,
            @RequestParam(required = false) Instant asOf,
            @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return asOf == null
                ? accountabilityService.currentManagerForTeacher(teacherId)
                : accountabilityService.managerForTeacherAsOf(teacherId, asOf);
    }

    @GetMapping("/teachers/{teacherId}/assignment-history")
    public List<AssignmentHistoryEntry> getTeacherAssignmentHistory(@PathVariable UUID teacherId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.teacherAssignmentHistory(teacherId);
    }

    @DeleteMapping("/teacher-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endTeacherAssignment(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        accountabilityService.endTeacherAssignment(assignmentId, userId(jwt));
    }

    @GetMapping("/managers/{managerId}/portfolio")
    public List<PortfolioItem> getManagerPortfolio(@PathVariable UUID managerId, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.portfolioForManager(managerId);
    }

    @GetMapping("/unassigned")
    public List<UnassignedItem> listUnassigned(@RequestParam(required = false) ItemType itemType, @AuthenticationPrincipal Jwt jwt) {
        requireDirectorOrAdmin(jwt);
        return accountabilityService.unassigned(itemType);
    }

    @ExceptionHandler(AssignmentConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ConflictError handleConflict(AssignmentConflictException e) {
        return new ConflictError("This assignment was already changed by someone else. Refresh and retry.");
    }

    // ---- helpers -------------------------------------------------------------------------

    /**
     * FR-001/002/004: Director-or-Admin check. Reads the JWT's {@code roles} claim
     * directly — generic Spring Security claim-reading, not Identity-specific logic
     * (research.md §6), duplicated here rather than shared across modules.
     */
    private void requireDirectorOrAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Set<String> roleSet = roles == null ? Set.of() : Set.copyOf(roles);
        if (!roleSet.contains("DIRECTOR") && !roleSet.contains("ADMIN")) {
            throw new AccessDeniedException("Only Director or Admin may perform this action");
        }
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
