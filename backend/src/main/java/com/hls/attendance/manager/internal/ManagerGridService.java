package com.hls.attendance.manager.internal;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.internal.CalendarService;
import com.hls.attendance.manager.internal.ManagerContext.Acting;
import com.hls.attendance.manager.internal.ManagerDay.DayType;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.DayRecord;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.Figures;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.InvalidInputException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager's month and the grid of every Manager's month (spec 032 US5). The status of a day is worked out here for
 * display; the figures come from {@link ManagerMonthCalculator}. The grid reads the month's records in one query, the
 * Managers and their employment in one each, so the number of queries does not grow with the number of Managers.
 */
@Service
public class ManagerGridService {

    public static final String UNAVAILABLE_NO_JOINING =
            "The joining date has not been recorded, so the working days and the unpaid days cannot be worked out.";

    /** A device location, shown only on a day's detail. */
    public record Place(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters) {}

    public record HistoryEntry(
            UUID id,
            String action,
            UUID changedBy,
            String changedByName,
            Instant changedAt,
            String reason,
            String beforeJson,
            String afterJson) {}

    public record DayEntry(
            LocalDate date,
            String status,
            UUID id,
            String dayType,
            String purpose,
            Instant checkInAt,
            Instant checkOutAt,
            boolean late,
            boolean hasPhoto,
            ManagerDayView.Leave leave,
            List<String> flags,
            List<ManagerDayView.ActivityRef> activities,
            Place checkInPlace,
            Place checkOutPlace,
            List<HistoryEntry> history) {}

    public record MonthFigures(
            BigDecimal workingDays,
            BigDecimal daysWorked,
            BigDecimal leavePaid,
            BigDecimal leaveUnpaid,
            BigDecimal unpaid,
            BigDecimal fieldDays,
            List<LocalDate> pending) {}

    public record MonthView(
            String month,
            UUID managerId,
            String managerName,
            boolean locked,
            MonthFigures figures,
            String figuresUnavailable,
            List<DayEntry> days) {}

    public record GridRow(
            UUID managerId,
            String name,
            String employeeId,
            boolean locked,
            MonthFigures figures,
            String figuresUnavailable,
            List<String> statuses) {}

    public record Grid(String month, LocalDate firstDate, int daysInMonth, List<GridRow> managers) {}

    /** What the calendar and the clock say about the month. */
    private record Facts(
            YearMonth month, LocalDate today, LocalTime now, LocalTime cutoff, Set<LocalDate> working, Set<LocalDate> holidays) {}

    private final ManagerContext context;
    private final ManagerQueries managers;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final ManagerMonthStateRepository monthStates;
    private final ManagerAttendanceSettingsRepository settings;
    private final WorkingCalendar workingCalendar;
    private final CalendarService calendar;
    private final BusinessCalendar business;
    private final AppUserRepository users;
    private final Clock clock;
    private final com.hls.attendance.api.ManagerTrainingDays trainingDays;

    public ManagerGridService(
            ManagerContext context,
            ManagerQueries managers,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            ManagerMonthStateRepository monthStates,
            ManagerAttendanceSettingsRepository settings,
            WorkingCalendar workingCalendar,
            CalendarService calendar,
            BusinessCalendar business,
            AppUserRepository users,
            Clock clock,
            com.hls.attendance.api.ManagerTrainingDays trainingDays) {
        this.context = context;
        this.managers = managers;
        this.days = days;
        this.history = history;
        this.monthStates = monthStates;
        this.settings = settings;
        this.workingCalendar = workingCalendar;
        this.calendar = calendar;
        this.business = business;
        this.users = users;
        this.clock = clock;
        this.trainingDays = trainingDays;
    }

    /** {@code yyyy-MM}, or a 400 that says what a month looks like. */
    public static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("The month is required, for example 2026-11.");
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("The month must look like 2026-11.");
        }
    }

    /** The signed-in Manager's own month, with their locations and without the history. */
    @Transactional(readOnly = true)
    public MonthView myMonth(UUID userId, YearMonth month) {
        Acting manager = context.resolve(userId);
        return build(manager, month, false);
    }

    /** One Manager's month in full for an Admin or Director: locations and each day's history. */
    @Transactional(readOnly = true)
    public MonthView managerMonth(UUID managerId, YearMonth month) {
        return build(context.byManagerId(managerId), month, true);
    }

    @Transactional(readOnly = true)
    public Grid grid(YearMonth month, UUID zoneId, String query) {
        Facts facts = facts(month);
        List<ManagerRef> all = managers.allManagers();
        Set<UUID> inZone = zoneId == null
                ? null
                : managers.managersOfZone(zoneId).stream().map(ManagerRef::id).collect(Collectors.toSet());
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<UUID> ids = all.stream().map(ManagerRef::id).toList();
        Map<UUID, ManagerEmployment> employment = managers.employment(ids);
        Map<UUID, List<ManagerDay>> rows = new HashMap<>();
        for (ManagerDay d : days.findByManagerIdInAndDayDateBetween(ids, month.atDay(1), month.atEndOfMonth())) {
            rows.computeIfAbsent(d.getManagerId(), k -> new ArrayList<>()).add(d);
        }
        Set<UUID> locked = monthStates.findForMonth(ids, month.toString()).stream()
                .filter(ManagerMonthState::isLocked)
                .map(s -> s.getId().getManagerId())
                .collect(Collectors.toSet());

        Map<UUID, Map<LocalDate, java.math.BigDecimal>> training =
                trainingDays.attendedDaysOf(ids, month.atDay(1), month.atEndOfMonth());

        List<GridRow> out = new ArrayList<>();
        for (ManagerRef ref : all) {
            if (inZone != null && !inZone.contains(ref.id())) {
                continue;
            }
            ManagerEmployment emp = employment.get(ref.id());
            String employeeId = emp == null ? null : emp.employeeId();
            if (!needle.isEmpty()
                    && !ref.displayName().toLowerCase(Locale.ROOT).contains(needle)
                    && (employeeId == null || !employeeId.toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            List<ManagerDay> mine = rows.getOrDefault(ref.id(), List.of());
            if (!ref.active() && mine.isEmpty()) {
                continue;
            }
            Map<LocalDate, ManagerDay> byDate = byDate(mine);
            List<String> statuses = new ArrayList<>();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                statuses.add(status(byDate.get(date), date, emp, facts));
            }
            FiguresResult figures = figures(emp, byDate, facts, training.getOrDefault(ref.id(), Map.of()));
            out.add(new GridRow(
                    ref.id(),
                    ref.displayName(),
                    employeeId,
                    locked.contains(ref.id()),
                    figures.figures(),
                    figures.unavailable(),
                    statuses));
        }
        return new Grid(month.toString(), month.atDay(1), month.lengthOfMonth(), out);
    }

    // ------------------------------------------------------------------ one Manager

    private MonthView build(Acting manager, YearMonth month, boolean detail) {
        Facts facts = facts(month);
        ManagerEmployment emp = new ManagerEmployment(
                manager.managerId(), null, manager.joiningDate(), manager.exitDate(), true);
        List<ManagerDay> rows = days.findByManagerIdAndDayDateBetweenOrderByDayDate(
                manager.managerId(), month.atDay(1), month.atEndOfMonth());
        Map<LocalDate, ManagerDay> byDate = byDate(rows);
        boolean locked = monthStates.findForMonth(List.of(manager.managerId()), month.toString()).stream()
                .anyMatch(ManagerMonthState::isLocked);

        Map<UUID, List<HistoryEntry>> histories = detail ? histories(rows) : Map.of();
        List<DayEntry> entries = new ArrayList<>();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            ManagerDay row = byDate.get(date);
            String status = status(row, date, emp, facts);
            if (row == null) {
                entries.add(new DayEntry(date, status, null, null, null, null, null, false, false, null, List.of(), List.of(), null, null, detail ? List.of() : null));
                continue;
            }
            ManagerDayView view = ManagerDayView.of(row, facts.today());
            entries.add(new DayEntry(
                    date,
                    status,
                    row.getId(),
                    view.dayType(),
                    view.purpose(),
                    view.checkInAt(),
                    view.checkOutAt(),
                    view.late(),
                    view.hasPhoto(),
                    view.leave(),
                    view.flags(),
                    view.activities(),
                    place(row.getCheckInLat(), row.getCheckInLng(), row.getCheckInAccuracyM()),
                    place(row.getCheckOutLat(), row.getCheckOutLng(), row.getCheckOutAccuracyM()),
                    detail ? histories.getOrDefault(row.getId(), List.of()) : null));
        }
        FiguresResult figures = figures(
                emp, byDate, facts, trainingDays.attendedDays(manager.managerId(), month.atDay(1), month.atEndOfMonth()));
        return new MonthView(
                month.toString(), manager.managerId(), manager.name(), locked, figures.figures(), figures.unavailable(), entries);
    }

    private Map<UUID, List<HistoryEntry>> histories(List<ManagerDay> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<ManagerDayHistory> all = history.findByDayIdInOrderByChangedAtAsc(
                rows.stream().map(ManagerDay::getId).toList());
        Set<UUID> actors = all.stream().map(ManagerDayHistory::getChangedBy).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!actors.isEmpty()) {
            for (AppUser u : users.findAllById(actors)) {
                names.put(u.getId(), u.getDisplayName());
            }
        }
        Map<UUID, List<HistoryEntry>> out = new HashMap<>();
        for (ManagerDayHistory h : all) {
            out.computeIfAbsent(h.getDayId(), k -> new ArrayList<>())
                    .add(new HistoryEntry(
                            h.getId(),
                            h.getAction().name(),
                            h.getChangedBy(),
                            names.getOrDefault(h.getChangedBy(), "Unknown user"),
                            h.getChangedAt(),
                            h.getReason(),
                            h.getBeforeJson(),
                            h.getAfterJson()));
        }
        return out;
    }

    // ------------------------------------------------------------------ rules

    private record FiguresResult(MonthFigures figures, String unavailable) {}

    private FiguresResult figures(
            ManagerEmployment emp, Map<LocalDate, ManagerDay> byDate, Facts facts, Map<LocalDate, java.math.BigDecimal> training) {
        if (emp == null || emp.joiningDate() == null) {
            return new FiguresResult(null, UNAVAILABLE_NO_JOINING);
        }
        List<LocalDate> workingDates = facts.working().stream()
                .filter(d -> employed(emp, d))
                .sorted()
                .toList();
        Map<LocalDate, DayRecord> records = new HashMap<>();
        for (Map.Entry<LocalDate, ManagerDay> e : byDate.entrySet()) {
            ManagerDay d = e.getValue();
            records.put(
                    e.getKey(),
                    new DayRecord(d.hasCheckIn(), d.getDayType() == DayType.FIELD, d.getLeaveFraction(), d.isLeaveUnpaid()));
        }
        for (Map.Entry<LocalDate, java.math.BigDecimal> t : training.entrySet()) {
            DayRecord base = records.get(t.getKey());
            records.put(
                    t.getKey(),
                    base == null
                            ? new DayRecord(false, false, null, false, t.getValue())
                            : new DayRecord(base.checkedIn(), base.field(), base.leaveFraction(), base.leaveUnpaid(), t.getValue()));
        }
        Figures f = ManagerMonthCalculator.compute(workingDates, records, facts.today());
        return new FiguresResult(
                new MonthFigures(
                        f.workingDays(), f.daysWorked(), f.leavePaid(), f.leaveUnpaid(), f.unpaid(), f.fieldDays(), f.pending()),
                null);
    }

    /**
     * The status shown for a date: WORKED, FIELD, NO_CHECK_OUT, LEAVE, HOLIDAY, WEEKLY_OFF, NOT_EMPLOYED, PENDING,
     * MISSED or ABSENT.
     */
    private static String status(ManagerDay row, LocalDate date, ManagerEmployment emp, Facts facts) {
        if (row != null && row.hasCheckIn()) {
            if (row.getCheckOutAt() == null && date.isBefore(facts.today())) {
                return "NO_CHECK_OUT";
            }
            return row.getDayType() == DayType.FIELD ? "FIELD" : "WORKED";
        }
        if (row != null && row.hasLeave()) {
            return "LEAVE";
        }
        if (!facts.working().contains(date)) {
            return facts.holidays().contains(date) ? "HOLIDAY" : "WEEKLY_OFF";
        }
        if (!employed(emp, date)) {
            return "NOT_EMPLOYED";
        }
        if (date.isAfter(facts.today())) {
            return "PENDING";
        }
        if (date.equals(facts.today())) {
            return facts.now().isAfter(facts.cutoff()) ? "MISSED" : "PENDING";
        }
        return "ABSENT";
    }

    private static boolean employed(ManagerEmployment emp, LocalDate date) {
        return emp != null
                && emp.joiningDate() != null
                && !date.isBefore(emp.joiningDate())
                && (emp.exitDate() == null || !date.isAfter(emp.exitDate()));
    }

    private Facts facts(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Set<LocalDate> working = new HashSet<>(workingCalendar.workingDays(null, from, to));
        Set<LocalDate> holidays = calendar.nonWorkingDates(from, to);
        ManagerAttendanceSettings s = settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."));
        return new Facts(
                month,
                business.today(),
                LocalTime.ofInstant(clock.instant(), business.zone()),
                s.getMissedCutoff(),
                working,
                holidays);
    }

    private static Map<LocalDate, ManagerDay> byDate(Collection<ManagerDay> rows) {
        Map<LocalDate, ManagerDay> map = new HashMap<>();
        rows.forEach(r -> map.put(r.getDayDate(), r));
        return map;
    }

    private static Place place(BigDecimal lat, BigDecimal lng, BigDecimal accuracy) {
        return lat == null || lng == null ? null : new Place(lat, lng, accuracy);
    }
}
