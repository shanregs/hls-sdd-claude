package com.hls.activitycalendar.web;

import com.hls.activitycalendar.internal.CalendarResponse;
import com.hls.activitycalendar.internal.ActivityCalendarService;
import com.hls.activitycalendar.internal.ActivityCalendarService.AttentionResponse;
import com.hls.activitycalendar.internal.ActivityCalendarService.Caller;
import com.hls.activitycalendar.internal.ActivityCalendarService.FilterChoices;
import com.hls.activitycalendar.internal.ActivityCalendarService.Filters;
import com.hls.designation.api.BusinessDate;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
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
    public CalendarResponse month(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String managerId,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW);
        YearMonth wanted = parse(month);
        return calendar.month(caller(jwt), wanted, filters(managerId, zoneId, kind, status));
    }

    /** Missed and rescheduled items for the period (up to 93 days; default the current month), grouped by Manager. */
    @GetMapping("/needs-attention")
    public AttentionResponse needsAttention(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String managerId,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String kind,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW);
        YearMonth current = YearMonth.from(businessDate.today());
        LocalDate start = date(from, current.atDay(1));
        LocalDate end = date(to, current.atEndOfMonth());
        return calendar.needsAttention(caller(jwt), start, end, filters(managerId, zoneId, kind, null));
    }

    /** The choices for the filter bar. */
    @GetMapping("/filters")
    public FilterChoices filters(@AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW);
        return calendar.filters(caller(jwt));
    }

    private static Caller caller(Jwt jwt) {
        return new Caller(CallerContext.userId(jwt), CallerContext.roles(jwt));
    }

    private static Filters filters(String managerId, String zoneId, String kind, String status) {
        return new Filters(uuid(managerId, "managerId"), uuid(zoneId, "zoneId"), blankToNull(kind), blankToNull(status));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID uuid(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The " + name + " is not valid.");
        }
    }

    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Dates must look like 2026-10-31.");
        }
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
