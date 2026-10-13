package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerDayCorrected;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerContext.Acting;
import com.hls.attendance.manager.internal.ManagerDay.DayType;
import com.hls.attendance.manager.internal.ManagerDay.Location;
import com.hls.attendance.manager.internal.ManagerDayHistory.Action;
import com.hls.school.api.ConflictException;
import com.hls.attendance.manager.internal.PlannedActivities.Planned;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager's check-in, check-out and the completion of a missing check-out (spec 032 US1). The time always comes from
 * the server clock in the business time zone, never from the device. Each write keeps a history row and a Change
 * History entry in the same transaction.
 */
@Service
public class CheckInService {

    static final int MAX_TEXT = 500;
    private static final BigDecimal MAX_LAT = new BigDecimal("90");
    private static final BigDecimal MAX_LNG = new BigDecimal("180");
    private static final BigDecimal MAX_ACCURACY = new BigDecimal("999999");
    private static final int LOOKBACK_DAYS = 31;

    /** A device location as sent by the client. */
    public record LocationInput(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {}

    /** Today's status for the check-in screen. */
    public record Today(
            LocalDate date,
            boolean workingDay,
            LocalTime missedCutoff,
            LocalTime lateAfter,
            ManagerDayView day,
            ManagerDayView.Leave leave,
            List<LocalDate> missingCheckOutDates,
            List<Planned> plannedActivities) {}

    private final ManagerContext context;
    private final ManagerMonthGuard guard;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final ManagerAttendanceSettingsRepository settings;
    private final BusinessCalendar calendar;
    private final WorkingCalendar workingCalendar;
    private final ManagerAttendanceAudit audit;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final PlannedActivities planned;
    private final ApplicationEventPublisher events;

    public CheckInService(
            ManagerContext context,
            ManagerMonthGuard guard,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            ManagerAttendanceSettingsRepository settings,
            BusinessCalendar calendar,
            WorkingCalendar workingCalendar,
            ManagerAttendanceAudit audit,
            Clock clock,
            JdbcTemplate jdbc,
            PlannedActivities planned,
            ApplicationEventPublisher events) {
        this.context = context;
        this.guard = guard;
        this.days = days;
        this.history = history;
        this.settings = settings;
        this.calendar = calendar;
        this.workingCalendar = workingCalendar;
        this.audit = audit;
        this.clock = clock;
        this.jdbc = jdbc;
        this.planned = planned;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Today today(UUID userId) {
        Acting manager = context.resolve(userId);
        LocalDate today = calendar.today();
        ManagerAttendanceSettings s = settings();
        ManagerDay row = days.findByManagerIdAndDayDate(manager.managerId(), today).orElse(null);
        List<LocalDate> missing = days
                .findByManagerIdAndDayDateBetweenOrderByDayDate(
                        manager.managerId(), today.minusDays(LOOKBACK_DAYS), today.minusDays(1))
                .stream()
                .filter(d -> d.hasCheckIn() && d.getCheckOutAt() == null)
                .map(ManagerDay::getDayDate)
                .toList();
        ManagerDayView view = row == null ? null : ManagerDayView.of(row, today);
        return new Today(
                today,
                !workingCalendar.workingDays(null, today, today).isEmpty(),
                s.getMissedCutoff(),
                s.getLateAfter(),
                view,
                view == null ? null : view.leave(),
                missing,
                planned.forDay(userId, today));
    }

    @Transactional
    public ManagerDayView checkIn(
            UUID userId, String dayType, String purposeText, List<ActivityLink> requested, LocationInput where) {
        DayType type = parseType(dayType);
        Location location = location(where, "check in");
        Acting manager = context.resolve(userId);
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        context.requireEmployedOn(manager, today);

        lock(manager.managerId(), today);
        guard.requireOpen(manager.managerId(), YearMonth.from(today));
        ManagerDay row = days.findByManagerIdAndDayDate(manager.managerId(), today)
                .orElseGet(() -> new ManagerDay(manager.managerId(), today));
        if (row.hasCheckIn()) {
            throw new ConflictException("You have already checked in today.");
        }
        if (row.hasFullLeave()) {
            throw new InvalidInputException("You are on approved leave for the whole of today, so you cannot check in.");
        }
        boolean late = localTime(now).isAfter(settings().getLateAfter());
        String purpose = null;
        Set<ActivityLink> links = new LinkedHashSet<>();
        if (type == DayType.FIELD) {
            if (requested != null) {
                links.addAll(requested);
            }
            purpose = requireFieldBasis(purposeText, links.size());
            requireOwnActivities(userId, today, links);
        }
        row.checkIn(type, purpose, purpose != null && links.isEmpty(), now, location, late);
        row.replaceActivities(links);
        ManagerDay saved = days.saveAndFlush(row);
        history.save(new ManagerDayHistory(saved.getId(), userId, now, Action.CHECK_IN, null, null, checkInJson(saved)));
        audit.changed(userId, ManagerAttendanceAudit.DAY, saved.getId(), "checkIn", null, type + " at " + now);
        return ManagerDayView.of(saved, today);
    }

    @Transactional
    public ManagerDayView checkOut(UUID userId, LocationInput where) {
        Location location = location(where, "check out");
        Acting manager = context.resolve(userId);
        Instant now = clock.instant();
        LocalDate today = calendar.today();

        lock(manager.managerId(), today);
        guard.requireOpen(manager.managerId(), YearMonth.from(today));
        ManagerDay row = days.findByManagerIdAndDayDate(manager.managerId(), today)
                .filter(ManagerDay::hasCheckIn)
                .orElseThrow(() -> new InvalidInputException("You have not checked in today, so there is nothing to check out of."));
        if (row.getCheckOutAt() != null) {
            throw new ConflictException("You have already checked out today.");
        }
        Instant at = now.isBefore(row.getCheckInAt()) ? row.getCheckInAt() : now;
        row.checkOut(at, location);
        ManagerDay saved = days.saveAndFlush(row);
        history.save(new ManagerDayHistory(
                saved.getId(), userId, now, Action.CHECK_OUT, null, null, "{\"checkOutAt\":\"" + at + "\"}"));
        audit.changed(userId, ManagerAttendanceAudit.DAY, saved.getId(), "checkOut", null, String.valueOf(at));
        return ManagerDayView.of(saved, today);
    }

    /**
     * Adds the missing check-out of a past day, with a time on that day and a reason. The Manager completes their own
     * day; an Admin or Director completes any Manager's (the caller has already checked the permission).
     */
    @Transactional
    public ManagerDayView completeCheckOut(UUID actorUserId, UUID managerId, LocalDate date, LocalTime time, String reason) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new InvalidInputException("A reason is required to complete a check-out.");
        }
        if (cleanReason.length() > MAX_TEXT) {
            throw new InvalidInputException("The reason can be at most " + MAX_TEXT + " characters.");
        }
        if (time == null) {
            throw new InvalidInputException("The check-out time is required.");
        }
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        if (date == null || !date.isBefore(today)) {
            throw new InvalidInputException("Only a past day can be completed; use check-out for today.");
        }
        Instant at = date.atTime(time).atZone(calendar.zone()).toInstant();
        if (at.isAfter(now)) {
            throw new InvalidInputException("The check-out time cannot be in the future.");
        }

        lock(managerId, date);
        guard.requireOpen(managerId, YearMonth.from(date));
        ManagerDay row = days.findByManagerIdAndDayDate(managerId, date)
                .filter(ManagerDay::hasCheckIn)
                .orElseThrow(() -> new InvalidInputException("There is no check-in on " + date + " to complete."));
        if (row.getCheckOutAt() != null) {
            throw new ConflictException("This day already has a check-out.");
        }
        if (at.isBefore(row.getCheckInAt())) {
            throw new InvalidInputException("The check-out time must be after the check-in.");
        }
        row.checkOut(at, null);
        ManagerDay saved = days.saveAndFlush(row);
        history.save(new ManagerDayHistory(
                saved.getId(),
                actorUserId,
                now,
                Action.COMPLETE_CHECK_OUT,
                cleanReason,
                "{\"checkOutAt\":null}",
                "{\"checkOutAt\":\"" + at + "\"}"));
        audit.changed(
                actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "completeCheckOut", null, at + " (" + cleanReason + ")");
        UUID ownerUser = context.byManagerId(managerId).userId();
        if (ownerUser != null && !ownerUser.equals(actorUserId)) {
            events.publishEvent(new ManagerDayCorrected(managerId, date, actorUserId));
        }
        return ManagerDayView.of(saved, today);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The Field-day rule (also used by corrections): a trimmed purpose of at most 500 characters or at least one linked
     * activity. Returns the trimmed purpose, or null when there is none.
     */
    public static String requireFieldBasis(String purposeText, int linkCount) {
        String purpose = purposeText == null ? "" : purposeText.trim();
        if (purpose.length() > MAX_TEXT) {
            throw new InvalidInputException("The purpose can be at most " + MAX_TEXT + " characters.");
        }
        if (purpose.isEmpty() && linkCount == 0) {
            throw new InvalidInputException("A Field day needs a purpose or a linked planned activity.");
        }
        return purpose.isEmpty() ? null : purpose;
    }

    /** Every link must be one of the user's planned activities dated that day; anything else reads as not found. */
    void requireOwnActivities(UUID userId, LocalDate date, Set<ActivityLink> links) {
        if (links.isEmpty()) {
            return;
        }
        Set<ActivityLink> available = new java.util.HashSet<>();
        for (Planned p : planned.forDay(userId, date)) {
            available.add(new ActivityLink(p.kind(), p.id()));
        }
        for (ActivityLink link : links) {
            if (link.getKind() == null || link.getActivityId() == null || !available.contains(link)) {
                throw new NotFoundException("That planned activity was not found for you on this day.");
            }
        }
    }

    /** Serializes writes to one Manager-day so two simultaneous check-ins record exactly one. */
    private void lock(UUID managerId, LocalDate date) {
        lockDay(jdbc, managerId, date);
    }

    /** The same lock for every writer of a Manager-day (check-in, correction, forgotten check-in). */
    static void lockDay(JdbcTemplate jdbc, UUID managerId, LocalDate date) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "manager-day:" + managerId + ":" + date);
    }

    private ManagerAttendanceSettings settings() {
        return settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."));
    }

    private LocalTime localTime(Instant at) {
        return at.atZone(calendar.zone()).toLocalTime();
    }

    private static DayType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Choose Office or Field as the day type.");
        }
        try {
            return DayType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The day type must be OFFICE or FIELD.");
        }
    }

    private static Location location(LocationInput in, String action) {
        if (in == null || in.latitude() == null || in.longitude() == null) {
            throw new InvalidInputException("Your location is required to " + action + ".");
        }
        if (in.latitude().abs().compareTo(MAX_LAT) > 0) {
            throw new InvalidInputException("The latitude must be between -90 and 90.");
        }
        if (in.longitude().abs().compareTo(MAX_LNG) > 0) {
            throw new InvalidInputException("The longitude must be between -180 and 180.");
        }
        BigDecimal accuracy = in.accuracyMeters();
        if (accuracy != null && (accuracy.signum() < 0 || accuracy.compareTo(MAX_ACCURACY) > 0)) {
            throw new InvalidInputException("The location accuracy is not valid.");
        }
        return new Location(in.latitude(), in.longitude(), accuracy);
    }

    private static String checkInJson(ManagerDay d) {
        return "{\"dayType\":\"" + d.getDayType() + "\",\"checkInAt\":\"" + d.getCheckInAt() + "\",\"late\":" + d.isLate() + "}";
    }
}
