package com.hls.attendance.web;

import com.hls.attendance.internal.CalendarService;
import com.hls.attendance.internal.StatusCodeService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

/** Status codes and the non-working calendar (spec 008 US5). Code changes need ATTENDANCE_SETUP.EDIT; the calendar is readable by everyone and editable with HOLIDAY_CALENDAR.EDIT. */
@RestController
@RequestMapping("/api/v1/attendance")
public class SetupController {

    public record CreateCodeRequest(String shortCode, String name, String category, BigDecimal weight) {}

    public record UpdateCodeRequest(String name, BigDecimal weight, Boolean active, Long version) {}

    public record DefaultWeeklyOffRequest(List<String> weeklyOff, Long version) {}

    public record WeeklyOffRequest(List<String> weeklyOff) {}

    public record NonWorkingDateRequest(LocalDate onDate, String description) {}

    private static final List<PermissionModule> CODE_READERS = List.of(
            PermissionModule.ATTENDANCE,
            PermissionModule.TEACHER_ATTENDANCE,
            PermissionModule.MY_ATTENDANCE,
            PermissionModule.ATTENDANCE_SETUP);

    private final StatusCodeService codes;
    private final CalendarService calendar;
    private final PermissionGuard guard;

    public SetupController(StatusCodeService codes, CalendarService calendar, PermissionGuard guard) {
        this.codes = codes;
        this.calendar = calendar;
        this.guard = guard;
    }

    @GetMapping("/status-codes")
    public List<StatusCodeService.StatusCodeView> listCodes(
            @RequestParam(defaultValue = "true") boolean activeOnly, @AuthenticationPrincipal Jwt jwt) {
        requireAnyView(jwt);
        return codes.list(activeOnly);
    }

    @PostMapping("/status-codes")
    public ResponseEntity<StatusCodeService.StatusCodeView> createCode(
            @RequestBody CreateCodeRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireEdit(jwt);
        return ResponseEntity.status(201)
                .body(codes.create(
                        CallerContext.userId(jwt), request.shortCode(), request.name(), request.category(), request.weight()));
    }

    @PutMapping("/status-codes/{id}")
    public StatusCodeService.StatusCodeView updateCode(
            @PathVariable UUID id, @RequestBody UpdateCodeRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireEdit(jwt);
        return codes.update(
                CallerContext.userId(jwt), id, request.name(), request.weight(), request.active(), request.version());
    }

    @GetMapping("/calendar")
    public CalendarService.CalendarView getCalendar(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.HOLIDAY_CALENDAR, PermissionAction.VIEW);
        return calendar.calendar();
    }

    @PutMapping("/calendar/default")
    public CalendarService.CalendarView updateDefault(
            @RequestBody DefaultWeeklyOffRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireCalendarEdit(jwt);
        return calendar.updateDefault(CallerContext.userId(jwt), request.weeklyOff(), request.version());
    }

    @PutMapping("/calendar/schools/{schoolId}")
    public CalendarService.CalendarView putOverride(
            @PathVariable UUID schoolId, @RequestBody WeeklyOffRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireCalendarEdit(jwt);
        return calendar.putSchoolOverride(CallerContext.userId(jwt), schoolId, request.weeklyOff());
    }

    @DeleteMapping("/calendar/schools/{schoolId}")
    public CalendarService.CalendarView removeOverride(@PathVariable UUID schoolId, @AuthenticationPrincipal Jwt jwt) {
        requireCalendarEdit(jwt);
        return calendar.removeSchoolOverride(CallerContext.userId(jwt), schoolId);
    }

    @PostMapping("/calendar/non-working-dates")
    public ResponseEntity<CalendarService.CalendarView> addDate(
            @RequestBody NonWorkingDateRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireCalendarEdit(jwt);
        return ResponseEntity.status(201)
                .body(calendar.addNonWorkingDate(CallerContext.userId(jwt), request.onDate(), request.description()));
    }

    @DeleteMapping("/calendar/non-working-dates/{onDate}")
    public CalendarService.CalendarView removeDate(@PathVariable LocalDate onDate, @AuthenticationPrincipal Jwt jwt) {
        requireCalendarEdit(jwt);
        return calendar.removeNonWorkingDate(CallerContext.userId(jwt), onDate);
    }

    private void requireCalendarEdit(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.HOLIDAY_CALENDAR, PermissionAction.EDIT);
    }

    private void requireEdit(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ATTENDANCE_SETUP, PermissionAction.EDIT);
    }

    private void requireAnyView(Jwt jwt) {
        for (PermissionModule module : CODE_READERS) {
            try {
                guard.require(CallerContext.roles(jwt), module, PermissionAction.VIEW);
                return;
            } catch (AccessDeniedException ignored) {
                // try the next module
            }
        }
        throw new AccessDeniedException("Not authorized to read attendance status codes");
    }
}
