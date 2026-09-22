package com.hls.attendance.internal;

import com.hls.attendance.api.AttendanceMonthLockedException;
import com.hls.attendance.api.AttendanceTeacherNotFoundException;
import com.hls.attendance.api.UnknownAttendanceStatusCodeException;
import com.hls.attendance.api.dto.AddNonWorkingDateRequest;
import com.hls.attendance.api.dto.AttendanceGridView;
import com.hls.attendance.api.dto.AttendanceMarkView;
import com.hls.attendance.api.dto.AttendanceStatusCodeView;
import com.hls.attendance.api.dto.CreateStatusCodeRequest;
import com.hls.attendance.api.dto.LockStatusView;
import com.hls.attendance.api.dto.MarkAttendanceRequest;
import com.hls.attendance.api.dto.MarkedByRole;
import com.hls.attendance.api.dto.MonthlyAttendanceRollupView;
import com.hls.attendance.api.dto.NonWorkingDateView;
import com.hls.identity.api.ManagerScopeQueries;
import com.hls.identity.api.TeacherScopeQueries;
import jakarta.validation.Valid;
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
 * REST endpoints per contracts/attendance-api.yaml. Reads
 * {@code @AuthenticationPrincipal Jwt} directly, the same pattern every other
 * controller in this codebase uses. Viewing (rollup/marks/export) is
 * role-scoped via {@link #canView}: Admin/Director unscoped, Manager via
 * {@code identity.api.ManagerScopeQueries} (FR-015), Teacher via {@code
 * identity.api.TeacherScopeQueries}. Marking on behalf is Manager-scoped or
 * Admin-unscoped (FR-002/FR-024). Lock/reopen are Director-only (FR-011/FR-014,
 * `/speckit-analyze` finding C1/I1). Status codes are Admin-or-Director
 * (FR-005); the Non-Working Calendar is Admin-only (FR-022).
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ManagerScopeQueries managerScopeQueries;
    private final TeacherScopeQueries teacherScopeQueries;

    public AttendanceController(AttendanceService attendanceService, ManagerScopeQueries managerScopeQueries, TeacherScopeQueries teacherScopeQueries) {
        this.attendanceService = attendanceService;
        this.managerScopeQueries = managerScopeQueries;
        this.teacherScopeQueries = teacherScopeQueries;
    }

    public record ReopenRequest(String reason) {
    }

    public record ErrorBody(String message) {
    }

    // ---- Status codes (FR-005) ----------------------------------------------------------

    @GetMapping("/status-codes")
    public List<AttendanceStatusCodeView> listStatusCodes() {
        return attendanceService.listActiveCodes();
    }

    @PostMapping("/status-codes")
    public AttendanceStatusCodeView createStatusCode(@Valid @RequestBody CreateStatusCodeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String role = requireAdminOrDirector(jwt);
        return attendanceService.createStatusCode(request, userId(jwt), role);
    }

    // ---- Non-Working Calendar (FR-022) -------------------------------------------------

    @GetMapping("/non-working-dates")
    public List<NonWorkingDateView> listNonWorkingDates(@RequestParam String period) {
        return attendanceService.datesForMonth(period);
    }

    @PostMapping("/non-working-dates")
    public NonWorkingDateView addNonWorkingDate(@Valid @RequestBody AddNonWorkingDateRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return attendanceService.addNonWorkingDate(request, userId(jwt));
    }

    @PostMapping("/non-working-dates/{id}/deactivate")
    public NonWorkingDateView deactivateNonWorkingDate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return attendanceService.deactivateNonWorkingDate(id, userId(jwt));
    }

    // ---- Marking (FR-001/FR-002/FR-023/FR-024) -----------------------------------------

    @PostMapping("/me/marks")
    public AttendanceMarkView markMyAttendance(@Valid @RequestBody MarkAttendanceRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = myTeacherId(jwt);
        return attendanceService.markAttendance(teacherId, request, userId(jwt), MarkedByRole.TEACHER);
    }

    @PostMapping("/teachers/{teacherId}/marks")
    public AttendanceMarkView markOnBehalf(@PathVariable UUID teacherId, @Valid @RequestBody MarkAttendanceRequest request, @AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = roleSet(jwt);
        UUID callerId = userId(jwt);
        if (roles.contains("ADMIN")) {
            return attendanceService.markAttendance(teacherId, request, callerId, MarkedByRole.ADMIN);
        }
        if (roles.contains("MANAGER")) {
            if (!managerScopeQueries.isAllowedForTeacher(callerId, teacherId)) {
                throw new AccessDeniedException("Teacher not currently accountable to this Manager");
            }
            return attendanceService.markAttendance(teacherId, request, callerId, MarkedByRole.MANAGER);
        }
        throw new AccessDeniedException("Only a Manager or Admin may mark attendance on behalf of a Teacher");
    }

    // ---- Rollup / marks viewing (FR-007/FR-010/FR-015) ---------------------------------

    @GetMapping("/teachers/{teacherId}/months/{period}")
    public MonthlyAttendanceRollupView getMonthlyRollup(@PathVariable UUID teacherId, @PathVariable String period, @AuthenticationPrincipal Jwt jwt) {
        requireCanView(jwt, teacherId);
        return attendanceService.rollupForMonth(teacherId, period);
    }

    @GetMapping("/teachers/{teacherId}/months/{period}/marks")
    public List<AttendanceMarkView> listMarksForMonth(@PathVariable UUID teacherId, @PathVariable String period, @AuthenticationPrincipal Jwt jwt) {
        requireCanView(jwt, teacherId);
        return attendanceService.marksForMonth(teacherId, period);
    }

    @GetMapping(value = "/teachers/{teacherId}/months/{period}/export", produces = "text/csv")
    public ResponseEntity<String> exportMonth(@PathVariable UUID teacherId, @PathVariable String period, @AuthenticationPrincipal Jwt jwt) {
        requireCanView(jwt, teacherId);
        List<AttendanceMarkView> marks = attendanceService.marksForMonth(teacherId, period);
        MonthlyAttendanceRollupView rollup = attendanceService.rollupForMonth(teacherId, period);

        StringBuilder csv = new StringBuilder("date,statusCode,fractionalValue,schoolId,markedByRole,markedAt\n");
        for (AttendanceMarkView mark : marks) {
            csv.append(mark.markDate()).append(',')
                    .append(mark.statusCode()).append(',')
                    .append(mark.fractionalValue()).append(',')
                    .append(mark.schoolId()).append(',')
                    .append(mark.markedByRole()).append(',')
                    .append(mark.markedAt()).append('\n');
        }
        csv.append("\nRollup Summary\n")
                .append("trainingDaysTotal,").append(rollup.trainingDaysTotal()).append('\n')
                .append("trainingDaysAttended,").append(rollup.trainingDaysAttended()).append('\n')
                .append("daysWorked,").append(rollup.daysWorked()).append('\n')
                .append("daysLeave,").append(rollup.daysLeave()).append('\n')
                .append("overallWorkingDays,").append(rollup.overallWorkingDays()).append('\n')
                .append("unmarkedDays,").append(rollup.unmarkedDays()).append('\n')
                .append("weightedAttendanceTotal,").append(rollup.weightedAttendanceTotal()).append('\n');

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"attendance-" + teacherId + "-" + period + ".csv\"")
                .body(csv.toString());
    }

    // ---- Lock / reopen (FR-011/FR-012/FR-013/FR-014) -----------------------------------

    @GetMapping("/teachers/{teacherId}/months/{period}/lock")
    public LockStatusView getLockStatus(@PathVariable UUID teacherId, @PathVariable String period, @AuthenticationPrincipal Jwt jwt) {
        requireCanView(jwt, teacherId);
        return attendanceService.lockStatus(teacherId, period);
    }

    @PostMapping("/teachers/{teacherId}/months/{period}/lock")
    public LockStatusView lockMonth(@PathVariable UUID teacherId, @PathVariable String period, @AuthenticationPrincipal Jwt jwt) {
        requireDirector(jwt);
        return attendanceService.lockMonth(teacherId, period, userId(jwt));
    }

    @PostMapping("/teachers/{teacherId}/months/{period}/reopen")
    public LockStatusView reopenMonth(@PathVariable UUID teacherId, @PathVariable String period, @RequestBody ReopenRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireDirector(jwt);
        return attendanceService.reopenMonth(teacherId, period, request.reason(), userId(jwt));
    }

    // ---- Grid (User Story 5, FR-019/FR-020/FR-021/FR-025) ------------------------------

    @GetMapping("/grid")
    public AttendanceGridView getAttendanceGrid(@RequestParam String period, @RequestParam(required = false) UUID managerId, @AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = roleSet(jwt);
        UUID callerId = userId(jwt);

        if (roles.contains("ADMIN")) {
            return managerId != null
                    ? attendanceService.gridForManager(managerId, period, true)
                    : attendanceService.gridForAllTeachers(period, true);
        }
        if (roles.contains("DIRECTOR")) {
            return managerId != null
                    ? attendanceService.gridForManager(managerId, period, false)
                    : attendanceService.gridForAllTeachers(period, false);
        }
        if (roles.contains("MANAGER")) {
            if (managerId != null && !managerId.equals(callerId)) {
                throw new AccessDeniedException("A Manager may only request their own portfolio's grid");
            }
            return attendanceService.gridForManager(callerId, period, true);
        }
        throw new AccessDeniedException("Teacher role is not supported for this endpoint");
    }

    // ---- Exception handling ---------------------------------------------------------------

    @ExceptionHandler(AttendanceTeacherNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleTeacherNotFound() {
    }

    @ExceptionHandler(UnknownAttendanceStatusCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody handleUnknownStatusCode(UnknownAttendanceStatusCodeException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(AttendanceMonthLockedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorBody handleMonthLocked(AttendanceMonthLockedException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorBody handleIllegalState(IllegalStateException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody handleIllegalArgument(IllegalArgumentException e) {
        return new ErrorBody(e.getMessage());
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

    private void requireCanView(Jwt jwt, UUID teacherId) {
        if (!canView(jwt, teacherId)) {
            throw new AccessDeniedException("Caller is not allowed to view this Teacher's attendance");
        }
    }

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

    private void requireDirector(Jwt jwt) {
        if (!roleSet(jwt).contains("DIRECTOR")) {
            throw new AccessDeniedException("Only Director may perform this action");
        }
    }

    /** @return whichever of "ADMIN"/"DIRECTOR" the caller has (FR-005) — recorded verbatim in the audit trail. */
    private String requireAdminOrDirector(Jwt jwt) {
        Set<String> roles = roleSet(jwt);
        if (roles.contains("ADMIN")) {
            return "ADMIN";
        }
        if (roles.contains("DIRECTOR")) {
            return "DIRECTOR";
        }
        throw new AccessDeniedException("Only Admin or Director may perform this action");
    }

    private UUID myTeacherId(Jwt jwt) {
        String teacherIdClaim = jwt.getClaim("teacherId");
        if (teacherIdClaim == null) {
            throw new AccessDeniedException("Caller has no linked teacher id");
        }
        return UUID.fromString(teacherIdClaim);
    }

    private Set<String> roleSet(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? Set.of() : Set.copyOf(roles);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
