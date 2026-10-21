package com.hls.activitycalendar.web;

import com.hls.activitycalendar.internal.CalendarResponse;
import com.hls.activitycalendar.internal.ActivityCalendarService;
import com.hls.activitycalendar.internal.ActivityCalendarService.Caller;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Activity Calendar (contracts/calendar-api.md). Read-only: GET mappings only, gated by the ACTIVITY_CALENDAR View
 * grant; Teacher and System are refused before any lookup, a Manager gets only their own items.
 */
@RestController
@RequestMapping("/api/v1/activity-calendar")
public class ActivityCalendarController {

    private final ActivityCalendarService calendar;
    private final PermissionGuard guard;
    private final BusinessDate businessDate;

    public ActivityCalendarController(ActivityCalendarService calendar, PermissionGuard guard, BusinessDate businessDate) {
        this.calendar = calendar;
        this.guard = guard;
        this.businessDate = businessDate;
    }

    /** One month of activities, the summary and the source report; the month defaults to the current business month. */
    @GetMapping({"", "/"})
    public CalendarResponse month(@RequestParam(required = false) String month, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW);
        YearMonth wanted = parse(month);
        return calendar.month(new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt)), wanted);
    }

    private YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(businessDate.today());
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-10.");
        }
    }
}
