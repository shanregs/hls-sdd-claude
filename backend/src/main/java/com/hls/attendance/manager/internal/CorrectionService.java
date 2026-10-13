package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerDayCorrected;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerContext.Acting;
import com.hls.attendance.manager.internal.ManagerDay.DayType;
import com.hls.attendance.manager.internal.ManagerDay.Location;
import com.hls.attendance.manager.internal.ManagerDayHistory.Action;
import com.hls.school.api.InvalidInputException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An Admin or Director corrects a Manager's day with a reason (spec 032 US5, research.md section 9). The original and
 * every change stay in the append-only history with who and when, the day is marked corrected, the Manager is told
 * and each changed field is audited with its prior and new value. A day is never deleted: clearing sets it back to
 * "no check-in". Nothing changes in a locked month.
 */
@Service
public class CorrectionService {

    /** What to change. A null field is left alone; {@code clear} removes the check-in and check-out. */
    public record Correction(
            String dayType,
            String purpose,
            List<ActivityLink> activities,
            String checkInTime,
            String checkOutTime,
            Boolean clearCheckOut,
            Boolean clear,
            String reason) {

        boolean clearsCheckOut() {
            return Boolean.TRUE.equals(clearCheckOut);
        }

        boolean clears() {
            return Boolean.TRUE.equals(clear);
        }
    }

    private final ManagerContext context;
    private final ManagerMonthGuard guard;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final ManagerAttendanceSettingsRepository settings;
    private final CheckInService checkIns;
    private final BusinessCalendar calendar;
    private final ManagerAttendanceAudit audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public CorrectionService(
            ManagerContext context,
            ManagerMonthGuard guard,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            ManagerAttendanceSettingsRepository settings,
            CheckInService checkIns,
            BusinessCalendar calendar,
            ManagerAttendanceAudit audit,
            ApplicationEventPublisher events,
            Clock clock,
            JdbcTemplate jdbc) {
        this.context = context;
        this.guard = guard;
        this.days = days;
        this.history = history;
        this.settings = settings;
        this.checkIns = checkIns;
        this.calendar = calendar;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Transactional
    public ManagerDayView correct(UUID actorUserId, UUID managerId, LocalDate date, Correction change) {
        String reason = change.reason() == null ? "" : change.reason().trim();
        if (reason.isEmpty()) {
            throw new InvalidInputException("A reason is required to correct a day.");
        }
        if (reason.length() > CheckInService.MAX_TEXT) {
            throw new InvalidInputException("The reason can be at most " + CheckInService.MAX_TEXT + " characters.");
        }
        Instant now = clock.instant();
        LocalDate today = calendar.today();
        if (date == null || date.isAfter(today)) {
            throw new InvalidInputException("Only today or a past day can be corrected.");
        }
        Acting manager = context.byManagerId(managerId);

        CheckInService.lockDay(jdbc, managerId, date);
        guard.requireOpen(managerId, YearMonth.from(date));
        ManagerDay row = days.findByManagerIdAndDayDate(managerId, date).orElseGet(() -> new ManagerDay(managerId, date));
        String before = DayJson.snapshot(row);
        Snapshot was = Snapshot.of(row);

        if (change.clears()) {
            clearDay(row);
        } else {
            apply(row, manager, date, now, change);
        }
        row.markCorrected();
        ManagerDay saved = days.saveAndFlush(row);
        history.save(new ManagerDayHistory(
                saved.getId(), actorUserId, now, Action.CORRECT, reason, before, DayJson.snapshot(saved)));

        Snapshot is = Snapshot.of(saved);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "dayType", was.dayType, is.dayType);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "purpose", was.purpose, is.purpose);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "checkInAt", was.checkInAt, is.checkInAt);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "checkOutAt", was.checkOutAt, is.checkOutAt);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "activities", was.activities, is.activities);
        audit.changed(actorUserId, ManagerAttendanceAudit.DAY, saved.getId(), "correctionReason", null, reason);
        events.publishEvent(new ManagerDayCorrected(managerId, date, actorUserId));
        return ManagerDayView.of(saved, today);
    }

    private void apply(ManagerDay row, Acting manager, LocalDate date, Instant now, Correction change) {
        boolean hadCheckIn = row.hasCheckIn();
        boolean touched = change.dayType() != null
                || change.purpose() != null
                || change.activities() != null
                || change.checkInTime() != null
                || change.checkOutTime() != null
                || change.clearsCheckOut();
        if (!touched) {
            throw new InvalidInputException("Say what to change: the day type, the purpose, a time, or clear the day.");
        }
        if (change.checkOutTime() != null && change.clearsCheckOut()) {
            throw new InvalidInputException("Give a check-out time or clear it, not both.");
        }
        if (!hadCheckIn) {
            if (change.dayType() == null || change.checkInTime() == null) {
                throw new InvalidInputException("To add a check-in, give the day type and the check-in time.");
            }
            if (row.hasFullLeave()) {
                throw new InvalidInputException("The Manager is on approved leave for the whole of this day.");
            }
            requireEmployed(manager, date);
        }

        DayType type = change.dayType() == null ? row.getDayType() : parseType(change.dayType());
        Set<ActivityLink> links = new LinkedHashSet<>(change.activities() == null ? row.getActivities() : change.activities());
        String purposeText = change.purpose() == null ? row.getPurpose() : change.purpose();
        String purpose = null;
        boolean unlinked = false;
        if (type == DayType.FIELD) {
            purpose = CheckInService.requireFieldBasis(purposeText, links.size());
            if (change.activities() != null) {
                checkIns.requireOwnActivities(manager.userId(), date, links);
            }
            unlinked = purpose != null && links.isEmpty();
        } else {
            links.clear();
        }

        Instant checkIn = row.getCheckInAt();
        boolean late = row.isLate();
        if (change.checkInTime() != null) {
            checkIn = at(date, change.checkInTime(), now, "check-in");
            late = LocalTime.ofInstant(checkIn, calendar.zone()).isAfter(lateAfter());
        }
        Instant checkOut = row.getCheckOutAt();
        if (change.clearsCheckOut()) {
            checkOut = null;
        } else if (change.checkOutTime() != null) {
            if (checkIn == null) {
                throw new InvalidInputException("There is no check-in to check out of.");
            }
            checkOut = at(date, change.checkOutTime(), now, "check-out");
        }
        if (checkOut != null && checkIn != null && checkOut.isBefore(checkIn)) {
            throw new InvalidInputException("The check-out time must be after the check-in time.");
        }

        row.setDayType(type);
        row.setPurpose(purpose, unlinked);
        row.replaceActivities(links);
        row.setCheckInAt(checkIn, late);
        row.checkOut(checkOut, checkOut == null ? null : locationOf(row));
    }

    private static Location locationOf(ManagerDay row) {
        return row.getCheckOutLat() == null ? null : new Location(row.getCheckOutLat(), row.getCheckOutLng(), row.getCheckOutAccuracyM());
    }

    private static void clearDay(ManagerDay row) {
        if (!row.hasCheckIn()) {
            throw new InvalidInputException("There is no check-in on this day to clear.");
        }
        row.clearAttendance();
    }

    private void requireEmployed(Acting manager, LocalDate date) {
        if (manager.joiningDate() == null) {
            throw new InvalidInputException("The Manager's joining date has not been recorded yet.");
        }
        if (date.isBefore(manager.joiningDate())) {
            throw new InvalidInputException("This date is before the Manager's joining date.");
        }
        if (manager.exitDate() != null && date.isAfter(manager.exitDate())) {
            throw new InvalidInputException("This date is after the Manager's exit date.");
        }
    }

    private Instant at(LocalDate date, String time, Instant now, String what) {
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(time.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The " + what + " time must look like 18:05.");
        }
        Instant at = date.atTime(parsed).atZone(calendar.zone()).toInstant();
        if (at.isAfter(now)) {
            throw new InvalidInputException("The " + what + " time cannot be in the future.");
        }
        return at;
    }

    private LocalTime lateAfter() {
        return settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."))
                .getLateAfter();
    }

    private static DayType parseType(String value) {
        try {
            return DayType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The day type must be OFFICE or FIELD.");
        }
    }

    /** The audited fields of a day as text. */
    private record Snapshot(
            String dayType, String purpose, String checkInAt, String checkOutAt, String activities) {

        static Snapshot of(ManagerDay d) {
            String links = d.getActivities().stream()
                    .map(a -> a.getKind() + ":" + a.getActivityId())
                    .sorted()
                    .reduce((x, y) -> x + "," + y)
                    .orElse(null);
            return new Snapshot(
                    d.getDayType() == null ? null : d.getDayType().name(),
                    d.getPurpose(),
                    Objects.toString(d.getCheckInAt(), null),
                    Objects.toString(d.getCheckOutAt(), null),
                    links);
        }
    }
}
