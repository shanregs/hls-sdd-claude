package com.hls.attendance.web;

import com.hls.attendance.api.MarkView;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.internal.MarkService;
import com.hls.attendance.internal.SetByKind;
import com.hls.attendance.internal.TeacherMonthViewService;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A Teacher's own attendance (spec 008 US1, US7). The Teacher is always the caller's linked record. */
@RestController
@RequestMapping("/api/v1/attendance/me")
public class MyAttendanceController {

    public record MarkRequest(String statusCode, BigDecimal dayValue, String note, Long version) {}

    private final TeacherDirectory teachers;
    private final TeacherMonthViewService monthViews;
    private final MarkService markService;
    private final BusinessCalendar business;
    private final PermissionGuard guard;

    public MyAttendanceController(
            TeacherDirectory teachers,
            TeacherMonthViewService monthViews,
            MarkService markService,
            BusinessCalendar business,
            PermissionGuard guard) {
        this.teachers = teachers;
        this.monthViews = monthViews;
        this.markService = markService;
        this.business = business;
        this.guard = guard;
    }

    @GetMapping
    public TeacherMonthViewService.TeacherMonthView month(
            @RequestParam(required = false) String month, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_ATTENDANCE, PermissionAction.VIEW);
        UUID teacherId = ownTeacher(jwt);
        YearMonth ym = month == null ? YearMonth.from(business.today()) : MonthParam.parse(month);
        return monthViews.view(teacherId, ym, TeacherMonthViewService.Viewer.SELF);
    }

    @PutMapping("/marks/{date}")
    public MarkView mark(
            @PathVariable LocalDate date, @RequestBody MarkRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = ownTeacher(jwt);
        PermissionAction needed = markService.hasMark(teacherId, date) ? PermissionAction.EDIT : PermissionAction.CREATE;
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_ATTENDANCE, needed);
        return markService.setMark(
                CallerContext.userId(jwt),
                teacherId,
                date,
                request.statusCode(),
                request.dayValue(),
                request.note(),
                request.version(),
                SetByKind.SELF);
    }

    private UUID ownTeacher(Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.MY_ATTENDANCE, PermissionAction.VIEW);
        return teachers.teacherOfUser(CallerContext.userId(jwt))
                .orElseThrow(() -> new NotFoundException("Your profile has not been set up yet."))
                .id();
    }
}
