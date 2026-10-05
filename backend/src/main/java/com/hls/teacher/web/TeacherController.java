package com.hls.teacher.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.PageResponse;
import com.hls.teacher.api.TeacherView;
import com.hls.teacher.internal.TeacherService;
import com.hls.teacher.internal.TeacherStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

/** Teacher endpoints (contracts/master-data-api.md); permission and scope are re-checked here. */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TeacherService teacherService;
    private final PermissionGuard permissionGuard;

    public TeacherController(
            TeacherService teacherService, PermissionGuard permissionGuard) {
        this.teacherService = teacherService;
        this.permissionGuard = permissionGuard;
    }

    public record CreateTeacherRequest(
            String name, String phone, String email, String address, String status, UUID userId) {}

    public record UpdateTeacherRequest(String name, String phone, String email, String address, Long version) {}

    public record StatusRequest(String status, LocalDate effectiveOn) {}

    public record UserLinkRequest(UUID userId) {}

    public record PlacementRequest(UUID schoolId, UUID positionId, LocalDate effectiveOn) {}

    @GetMapping
    public PageResponse<TeacherView> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.VIEW);
        return PageResponse.from(teacherService.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                query,
                parseStatus(status),
                schoolId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), Sort.by("name"))));
    }

    @GetMapping("/candidates")
    public java.util.List<TeacherService.AccountCandidate> candidates(@AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        requireOrgWide(jwt);
        return teacherService.accountCandidates();
    }

    @GetMapping("/{id}")
    public TeacherView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.VIEW);
        return teacherService.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
    }

    @PostMapping
    public ResponseEntity<TeacherView> create(
            @RequestBody CreateTeacherRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.CREATE);
        requireOrgWide(jwt);
        return ResponseEntity.status(201)
                .body(teacherService.create(
                        CallerContext.userId(jwt),
                        new TeacherService.NewTeacher(
                                request.name(),
                                request.phone(),
                                request.email(),
                                request.address(),
                                parseStatus(request.status()),
                                request.userId())));
    }

    @PutMapping("/{id}")
    public TeacherView update(
            @PathVariable UUID id, @RequestBody UpdateTeacherRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        return teacherService.update(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                new TeacherService.Contact(
                        request.name(), request.phone(), request.email(), request.address(), request.version()));
    }

    @PostMapping("/{id}/status")
    public TeacherView changeStatus(
            @PathVariable UUID id, @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        requireOrgWide(jwt);
        return teacherService.changeStatus(
                CallerContext.userId(jwt), id, parseStatus(request.status()), request.effectiveOn());
    }

    @PutMapping("/{id}/user")
    public TeacherView linkUser(
            @PathVariable UUID id, @RequestBody UserLinkRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        requireOrgWide(jwt);
        return teacherService.linkUser(CallerContext.userId(jwt), id, request.userId());
    }

    @PostMapping("/{id}/placements")
    public TeacherView place(
            @PathVariable UUID id, @RequestBody PlacementRequest request, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        return teacherService.assign(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                request.schoolId(),
                request.positionId(),
                request.effectiveOn());
    }

    @DeleteMapping("/{id}/placements/pending")
    public ResponseEntity<Void> cancelPending(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        permissionGuard.require(CallerContext.roles(jwt), PermissionModule.TEACHERS, PermissionAction.EDIT);
        teacherService.cancelScheduledMove(CallerContext.userId(jwt), CallerContext.roles(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static void requireOrgWide(Jwt jwt) {
        if (!CallerContext.isOrgWide(CallerContext.roles(jwt))) {
            throw new AccessDeniedException("Only Admin or Director may do this.");
        }
    }

    private static TeacherStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TeacherStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown status: " + status);
        }
    }
}
