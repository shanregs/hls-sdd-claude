package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerRegularizationDecided;
import com.hls.attendance.api.ManagerRegularizationRequested;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerContext.Acting;
import com.hls.attendance.manager.internal.ManagerDay.DayType;
import com.hls.attendance.manager.internal.ManagerDay.Location;
import com.hls.attendance.manager.internal.ManagerDayHistory.Action;
import com.hls.attendance.manager.internal.ManagerRegularization.Status;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager's request to add a forgotten check-in on a past day of an unlocked month, and the decision on it (spec
 * 032 US5, research.md section 8). Approval writes the day as worked at the proposed time, marked "added later", and
 * keeps the request as the record of why. A decision never writes into a locked month, and a user who is both the
 * Manager and an Admin or Director never decides their own request.
 */
@Service
public class RegularizationService {

    /** The request as the screens see it. */
    public record RegularizationView(
            UUID id,
            UUID managerId,
            String managerName,
            LocalDate date,
            LocalTime proposedTime,
            String dayType,
            String purpose,
            String reason,
            String status,
            Instant requestedAt,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason) {}

    /** What the Manager sends. */
    public record RequestInput(String date, String proposedTime, String dayType, String purpose, String reason) {}

    private final ManagerContext context;
    private final ManagerMonthGuard guard;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final ManagerRegularizationRepository requests;
    private final ManagerAttendanceSettingsRepository settings;
    private final ManagerQueries managers;
    private final BusinessCalendar calendar;
    private final ManagerAttendanceAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public RegularizationService(
            ManagerContext context,
            ManagerMonthGuard guard,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            ManagerRegularizationRepository requests,
            ManagerAttendanceSettingsRepository settings,
            ManagerQueries managers,
            BusinessCalendar calendar,
            ManagerAttendanceAudit audit,
            ApplicationEventPublisher events,
            Clock clock,
            JdbcTemplate jdbc) {
        this.context = context;
        this.guard = guard;
        this.days = days;
        this.history = history;
        this.requests = requests;
        this.settings = settings;
        this.managers = managers;
        this.calendar = calendar;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ the Manager

    @Transactional
    public RegularizationView request(UUID userId, RequestInput in) {
        Acting manager = context.resolve(userId);
        String reason = text(in.reason(), "A reason is required.");
        LocalDate date = parseDate(in.date());
        LocalTime time = parseTime(in.proposedTime());
        DayType type = parseType(in.dayType());
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        if (date.isAfter(today)) {
            throw new InvalidInputException("A forgotten check-in can only be added for today or a past day.");
        }
        Instant at = date.atTime(time).atZone(calendar.zone()).toInstant();
        if (at.isAfter(now)) {
            throw new InvalidInputException("The proposed time cannot be in the future.");
        }
        String purpose = null;
        if (type == DayType.FIELD) {
            purpose = CheckInService.requireFieldBasis(in.purpose(), 0);
        }
        context.requireEmployedOn(manager, date);

        CheckInService.lockDay(jdbc, manager.managerId(), date);
        guard.requireOpen(manager.managerId(), YearMonth.from(date));
        ManagerDay existing = days.findByManagerIdAndDayDate(manager.managerId(), date).orElse(null);
        if (existing != null && existing.hasCheckIn()) {
            throw new InvalidInputException("That day already has a check-in, so there is nothing to add.");
        }
        if (existing != null && existing.hasFullLeave()) {
            throw new InvalidInputException("You are on approved leave for the whole of that day.");
        }
        if (requests.findByManagerIdAndDayDateAndStatus(manager.managerId(), date, Status.PENDING).isPresent()) {
            throw new InvalidInputException("You already have a pending request for that day.");
        }
        ManagerRegularization saved =
                requests.saveAndFlush(new ManagerRegularization(manager.managerId(), date, time, type, purpose, reason, now));
        audit.created(userId, ManagerAttendanceAudit.REGULARIZATION, saved.getId(), date + " " + time + " " + type + " (" + reason + ")");
        events.publishEvent(new ManagerRegularizationRequested(saved.getId(), manager.managerId(), date));
        return view(saved, manager.name());
    }

    @Transactional(readOnly = true)
    public List<RegularizationView> mine(UUID userId) {
        Acting manager = context.resolve(userId);
        return requests.findByManagerIdOrderByRequestedAtDesc(manager.managerId()).stream()
                .map(r -> view(r, manager.name()))
                .toList();
    }

    @Transactional
    public void cancel(UUID userId, UUID id) {
        Acting manager = context.resolve(userId);
        ManagerRegularization request = requests.findById(id)
                .filter(r -> r.getManagerId().equals(manager.managerId()))
                .orElseThrow(() -> new NotFoundException("That request was not found."));
        if (request.getStatus() != Status.PENDING) {
            throw new ConflictException("Only a pending request can be cancelled.");
        }
        request.cancel(clock.instant());
        requests.saveAndFlush(request);
        audit.changed(userId, ManagerAttendanceAudit.REGULARIZATION, id, "status", Status.PENDING, Status.CANCELLED);
    }

    // ------------------------------------------------------------------ Admin and Director

    @Transactional(readOnly = true)
    public List<RegularizationView> list(String status, UUID managerId) {
        List<ManagerRegularization> found;
        if (status != null && !status.isBlank()) {
            Status wanted = parseStatus(status);
            found = managerId == null
                    ? requests.findByStatusOrderByRequestedAtAsc(wanted)
                    : requests.findByManagerIdAndStatusOrderByRequestedAtAsc(managerId, wanted);
        } else {
            found = managerId == null
                    ? requests.findAll(org.springframework.data.domain.Sort.by("requestedAt").descending())
                    : requests.findByManagerIdOrderByRequestedAtDesc(managerId);
        }
        Map<UUID, String> names = managers.allManagers().stream()
                .collect(Collectors.toMap(ManagerRef::id, ManagerRef::displayName, (a, b) -> a));
        return found.stream().map(r -> view(r, names.get(r.getManagerId()))).toList();
    }

    @Transactional
    public RegularizationView approve(UUID deciderUserId, UUID id) {
        ManagerRegularization request = requests.findById(id).orElseThrow(() -> new NotFoundException("That request was not found."));
        Acting manager = context.byManagerId(request.getManagerId());
        requirePendingAndNotOwn(request, manager, deciderUserId);
        Instant now = clock.instant();
        LocalDate date = request.getDayDate();

        CheckInService.lockDay(jdbc, manager.managerId(), date);
        guard.requireOpen(manager.managerId(), YearMonth.from(date));
        ManagerDay row = days.findByManagerIdAndDayDate(manager.managerId(), date)
                .orElseGet(() -> new ManagerDay(manager.managerId(), date));
        if (row.hasCheckIn()) {
            throw new ConflictException("That day already has a check-in, so the request cannot be approved.");
        }
        if (row.hasFullLeave()) {
            throw new ConflictException("The Manager is on approved leave for the whole of that day.");
        }
        Instant at = date.atTime(request.getProposedTime()).atZone(calendar.zone()).toInstant();
        boolean late = LocalTime.ofInstant(at, calendar.zone()).isAfter(lateAfter());
        boolean field = request.getDayType() == DayType.FIELD;
        String before = DayJson.snapshot(row);
        row.checkIn(request.getDayType(), field ? request.getPurpose() : null, field && request.getPurpose() != null, at, new Location(null, null, null), late);
        row.markAddedLater();
        ManagerDay saved = days.saveAndFlush(row);
        history.save(new ManagerDayHistory(
                saved.getId(), deciderUserId, now, Action.ADDED_LATER, request.getReason(), before, DayJson.snapshot(saved)));
        request.decide(Status.APPROVED, deciderUserId, now, null);
        requests.saveAndFlush(request);

        audit.changed(deciderUserId, ManagerAttendanceAudit.REGULARIZATION, id, "status", Status.PENDING, Status.APPROVED);
        audit.changed(deciderUserId, ManagerAttendanceAudit.DAY, saved.getId(), "addedLater", false, true);
        audit.changed(deciderUserId, ManagerAttendanceAudit.DAY, saved.getId(), "checkInAt", null, at);
        events.publishEvent(new ManagerRegularizationDecided(id, manager.managerId(), date, ManagerRegularizationDecided.APPROVED, null));
        return view(request, manager.name());
    }

    @Transactional
    public RegularizationView reject(UUID deciderUserId, UUID id, String reasonText) {
        String reason = text(reasonText, "A reason is required to reject a request.");
        ManagerRegularization request = requests.findById(id).orElseThrow(() -> new NotFoundException("That request was not found."));
        Acting manager = context.byManagerId(request.getManagerId());
        requirePendingAndNotOwn(request, manager, deciderUserId);
        request.decide(Status.REJECTED, deciderUserId, clock.instant(), reason);
        requests.saveAndFlush(request);
        audit.changed(deciderUserId, ManagerAttendanceAudit.REGULARIZATION, id, "status", Status.PENDING, Status.REJECTED);
        audit.changed(deciderUserId, ManagerAttendanceAudit.REGULARIZATION, id, "decisionReason", null, reason);
        events.publishEvent(new ManagerRegularizationDecided(
                id, manager.managerId(), request.getDayDate(), ManagerRegularizationDecided.REJECTED, reason));
        return view(request, manager.name());
    }

    // ------------------------------------------------------------------ helpers

    private void requirePendingAndNotOwn(ManagerRegularization request, Acting manager, UUID deciderUserId) {
        if (manager.userId() != null && manager.userId().equals(deciderUserId)) {
            throw new AccessDeniedException("You cannot decide your own request.");
        }
        if (request.getStatus() != Status.PENDING) {
            throw new ConflictException("That request has already been " + request.getStatus().name().toLowerCase() + ".");
        }
    }

    private LocalTime lateAfter() {
        return settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."))
                .getLateAfter();
    }

    private static RegularizationView view(ManagerRegularization r, String managerName) {
        return new RegularizationView(
                r.getId(),
                r.getManagerId(),
                managerName,
                r.getDayDate(),
                r.getProposedTime(),
                r.getDayType().name(),
                r.getPurpose(),
                r.getReason(),
                r.getStatus().name(),
                r.getRequestedAt(),
                r.getDecidedBy(),
                r.getDecidedAt(),
                r.getDecisionReason());
    }

    private static String text(String value, String missing) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInputException(missing);
        }
        if (trimmed.length() > CheckInService.MAX_TEXT) {
            throw new InvalidInputException("The text can be at most " + CheckInService.MAX_TEXT + " characters.");
        }
        return trimmed;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The date must look like 2026-11-03.");
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The proposed time must look like 09:15.");
        }
    }

    private static DayType parseType(String value) {
        try {
            return DayType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The day type must be OFFICE or FIELD.");
        }
    }

    private static Status parseStatus(String value) {
        try {
            return Status.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The status must be PENDING, APPROVED, REJECTED or CANCELLED.");
        }
    }
}
