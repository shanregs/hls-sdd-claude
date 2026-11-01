package com.hls.substitution.payout.web;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionGuard;
import com.hls.identity.permissions.PermissionModule;
import com.hls.school.api.CallerContext;
import com.hls.school.api.InvalidInputException;
import com.hls.substitution.payout.DayAttendance;
import com.hls.substitution.payout.DayEvent;
import com.hls.substitution.payout.DayEventRepository;
import com.hls.substitution.payout.DayQueryService;
import com.hls.substitution.payout.DayQueryService.DayView;
import com.hls.substitution.payout.DayQueryService.Filter;
import com.hls.substitution.payout.DayService;
import com.hls.substitution.payout.DayService.BulkLockResult;
import com.hls.substitution.payout.DayStatus;
import com.hls.substitution.payout.SubstituteDay;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Substitute day attendance and payouts (spec 017b contracts/payout-api.md). Check order on every route: permission
 * (403), data scope (404), business rules (409). A substitute has no account, so there is no "mine" endpoint.
 */
@RestController
@RequestMapping("/api/v1/substitute-payouts")
public class PayoutController {

    public record MarkRequest(String attendance, Long version) {}

    public record ReopenRequest(String reason) {}

    public record CorrectionRequest(java.math.BigDecimal amount, String reason) {}

    private final DayService service;
    private final DayQueryService queries;
    private final DayEventRepository events;
    private final PermissionGuard guard;

    public PayoutController(
            DayService service, DayQueryService queries, DayEventRepository events, PermissionGuard guard) {
        this.service = service;
        this.queries = queries;
        this.events = events;
        this.guard = guard;
    }

    @GetMapping("/days")
    public DayQueryService.Page days(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID substituteId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.VIEW);
        return queries.list(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                filter(month, from, to, substituteId, schoolId, zoneId, status),
                canEdit(jwt),
                page,
                Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/days/{id}")
    public DayDetail day(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.VIEW);
        DayView view = queries.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id, canEdit(jwt));
        List<DayEvent> history = queries.history(CallerContext.userId(jwt), CallerContext.roles(jwt), id, events);
        return new DayDetail(view, history.stream().map(PayoutController::event).toList());
    }

    public record DayDetail(DayView day, List<EventView> history) {}

    public record EventView(
            java.time.Instant at, String field, String from, String to, UUID actor, String cause, String note) {}

    private static EventView event(DayEvent e) {
        return new EventView(
                e.getAt(),
                e.getField().name(),
                e.getFromValue(),
                e.getToValue(),
                e.getActorUserId(),
                e.getCause().name(),
                e.getNote());
    }

    @GetMapping("/mismatches")
    public List<DayQueryService.MismatchView> mismatches(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID substituteId,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(required = false) UUID zoneId,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.VIEW);
        return queries.mismatches(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                filter(month, null, null, substituteId, schoolId, zoneId, null),
                canEdit(jwt));
    }

    /** An Admin raises a Correction for a day that changed after payment (payroll's own PROCESS right, FR-020). */
    @PostMapping("/days/{id}/correction")
    public DayView correction(
            @PathVariable UUID id, @RequestBody CorrectionRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.VIEW);
        guard.require(CallerContext.roles(jwt), PermissionModule.PAYROLL, PermissionAction.PROCESS);
        queries.get(CallerContext.userId(jwt), CallerContext.roles(jwt), id, false);
        SubstituteDay day = service.raiseCorrection(CallerContext.userId(jwt), id, request.amount(), request.reason());
        return queries.views(List.of(day), canEdit(jwt)).get(0);
    }

    @PutMapping("/days/{id}/attendance")
    public DayView mark(@PathVariable UUID id, @RequestBody MarkRequest request, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.EDIT);
        SubstituteDay day = service.mark(
                CallerContext.userId(jwt),
                CallerContext.roles(jwt),
                id,
                parseAttendance(request.attendance()),
                request.version());
        return queries.views(List.of(day), true).get(0);
    }

    @PostMapping("/months/{teacherId}/{month}/lock")
    public void lock(@PathVariable UUID teacherId, @PathVariable String month, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.APPROVE);
        service.lock(CallerContext.userId(jwt), teacherId, parseMonth(month));
    }

    @PostMapping("/months/{teacherId}/{month}/reopen")
    public void reopen(
            @PathVariable UUID teacherId,
            @PathVariable String month,
            @RequestBody ReopenRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.APPROVE);
        service.reopen(CallerContext.userId(jwt), teacherId, parseMonth(month), request.reason());
    }

    @PostMapping("/months/lock")
    public BulkLockResult lockAll(@RequestParam String month, @AuthenticationPrincipal Jwt jwt) {
        guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.APPROVE);
        return service.lockAll(CallerContext.userId(jwt), parseMonth(month));
    }

    // ---------------------------------------------------------------- helpers

    private boolean canEdit(Jwt jwt) {
        try {
            guard.require(CallerContext.roles(jwt), PermissionModule.SUBSTITUTE_PAYOUTS, PermissionAction.EDIT);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return false;
        }
    }

    private static Filter filter(
            String month, String from, String to, UUID substituteId, UUID schoolId, UUID zoneId, String status) {
        LocalDate start = parseDate(from, "from");
        LocalDate end = parseDate(to, "to");
        if (month != null && !month.isBlank()) {
            YearMonth m = parseMonth(month);
            start = m.atDay(1);
            end = m.atEndOfMonth();
        }
        Set<DayStatus> statuses = status == null || status.isBlank()
                ? Set.of()
                : Arrays.stream(status.split(","))
                        .map(String::trim)
                        .map(PayoutController::parseStatus)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayStatus.class)));
        return new Filter(start, end, substituteId, schoolId, zoneId, statuses);
    }

    static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidInputException("The month must look like 2026-09.");
        }
    }

    private static LocalDate parseDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The " + name + " date must look like 2026-09-30.");
        }
    }

    private static DayAttendance parseAttendance(String value) {
        try {
            return DayAttendance.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Attendance must be PRESENT, ABSENT or NOT_MARKED.");
        }
    }

    private static DayStatus parseStatus(String value) {
        try {
            return DayStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown status: " + value + ".");
        }
    }
}
