package com.hls.teacher.internal;

import com.hls.identity.api.ManagerScopeQueries;
import com.hls.identity.api.TeacherScopeQueries;
import com.hls.teacher.api.dto.CreateTeacherProfileRequest;
import com.hls.teacher.api.dto.SalaryAsOfAnswer;
import com.hls.teacher.api.dto.TeacherProfileView;
import com.hls.teacher.api.dto.TeacherSalaryHistoryView;
import com.hls.teacher.api.dto.TeacherStatus;
import com.hls.teacher.api.dto.UpdateTeacherProfileRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints per contracts/teacher-api.yaml. Reads
 * {@code @AuthenticationPrincipal Jwt} directly, the same pattern every
 * other controller in this codebase uses. Create/update/status-change are
 * Admin-only (FR-010) — narrower than every other write-endpoint precedent
 * in this codebase so far, which combine Director+Admin; a dedicated
 * {@code requireAdmin} helper (not {@code requireDirectorOrAdmin}) enforces
 * this. Viewing (GET /teachers/{id}, GET /teachers/me) is role-scoped:
 * Admin/Director unscoped, Manager via {@code identity.api.ManagerScopeQueries}
 * (FR-007), Teacher via {@code identity.api.TeacherScopeQueries} (FR-008/009)
 * — no new scoping logic invented here, `teacher` never depends on
 * `organization.api` directly (research.md §1/§2).
 */
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final TeacherService teacherService;
    private final ManagerScopeQueries managerScopeQueries;
    private final TeacherScopeQueries teacherScopeQueries;

    public TeacherController(
            TeacherService teacherService, ManagerScopeQueries managerScopeQueries, TeacherScopeQueries teacherScopeQueries) {
        this.teacherService = teacherService;
        this.managerScopeQueries = managerScopeQueries;
        this.teacherScopeQueries = teacherScopeQueries;
    }

    public record ChangeStatusRequest(TeacherStatus status) {
    }

    public record RecordSalaryChangeRequest(BigDecimal amount, LocalDate effectiveFrom) {
    }

    public record ErrorBody(String message) {
    }

    @PostMapping
    public TeacherProfileView createTeacher(@Valid @RequestBody CreateTeacherProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teacherService.create(request, userId(jwt));
    }

    @GetMapping("/{teacherId}")
    public ResponseEntity<TeacherProfileView> getTeacher(@PathVariable UUID teacherId, @AuthenticationPrincipal Jwt jwt) {
        if (!canView(jwt, teacherId)) {
            throw new AccessDeniedException("Caller is not allowed to view this teacher profile");
        }
        return teacherService.findById(teacherId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{teacherId}")
    public TeacherProfileView updateTeacher(
            @PathVariable UUID teacherId, @RequestBody UpdateTeacherProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teacherService.updateProfile(teacherId, request, userId(jwt));
    }

    @PostMapping("/{teacherId}/status")
    public TeacherProfileView changeStatus(
            @PathVariable UUID teacherId, @RequestBody ChangeStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teacherService.changeStatus(teacherId, request.status(), userId(jwt));
    }

    @PostMapping("/{teacherId}/salary")
    public TeacherSalaryHistoryView recordSalaryChange(
            @PathVariable UUID teacherId, @RequestBody RecordSalaryChangeRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return teacherService.recordSalaryChange(teacherId, request.amount(), request.effectiveFrom(), userId(jwt));
    }

    @GetMapping("/{teacherId}/salary")
    public SalaryAsOfAnswer getSalary(
            @PathVariable UUID teacherId,
            @RequestParam(required = false) LocalDate asOf,
            @AuthenticationPrincipal Jwt jwt) {
        if (!canView(jwt, teacherId)) {
            throw new AccessDeniedException("Caller is not allowed to view this teacher's salary");
        }
        if (!teacherService.exists(teacherId)) {
            throw new TeacherNotFoundException(teacherId);
        }
        return asOf != null ? teacherService.salaryAsOf(teacherId, asOf) : teacherService.currentSalary(teacherId);
    }

    @GetMapping("/me")
    public ResponseEntity<TeacherProfileView> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String teacherIdClaim = jwt.getClaim("teacherId");
        if (teacherIdClaim == null) {
            throw new AccessDeniedException("Caller has no linked teacher id");
        }
        UUID teacherId = UUID.fromString(teacherIdClaim);
        return teacherService.findById(teacherId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(TeacherNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody handleValidation(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField())
                .collect(Collectors.joining(", "));
        return new ErrorBody("Missing or invalid field(s): " + fields);
    }

    // ---- helpers -------------------------------------------------------------------------

    private boolean canView(Jwt jwt, UUID teacherId) {
        Set<String> roles = roleSet(jwt);
        UUID callerId = userId(jwt);
        if (roles.contains("ADMIN") || roles.contains("DIRECTOR")) {
            return true;
        }
        if (roles.contains("MANAGER")) {
            return managerScopeQueries.isAllowedForTeacher(callerId, teacherId);
        }
        if (roles.contains("TEACHER")) {
            return teacherScopeQueries.isAllowed(callerId, teacherId);
        }
        return false;
    }

    private void requireAdmin(Jwt jwt) {
        if (!roleSet(jwt).contains("ADMIN")) {
            throw new AccessDeniedException("Only Admin may perform this action");
        }
    }

    private Set<String> roleSet(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
