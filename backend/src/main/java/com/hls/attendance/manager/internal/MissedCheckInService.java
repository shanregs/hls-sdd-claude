package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerCheckInMissed;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The missed check-in alert and the lists behind it (spec 032 US4). A Manager has missed the day when it is a working
 * day inside their employment, the cut-off has passed (always, for an earlier day), and the day holds neither a
 * check-in nor approved leave. The lists are computed from the records, so a later check-in removes the day; the alert
 * is raised once per Manager and date through the unique key of {@code manager_missed_alert}.
 */
@Service
public class MissedCheckInService {

    public record Missed(UUID managerId, String name, String employeeId) {}

    public record OnLeave(UUID managerId, String name, BigDecimal fraction, boolean unpaid) {}

    private final ManagerQueries managers;
    private final ManagerDayRepository days;
    private final MissedAlertRepository alerts;
    private final ManagerAttendanceSettingsRepository settings;
    private final WorkingCalendar workingCalendar;
    private final BusinessCalendar business;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public MissedCheckInService(
            ManagerQueries managers,
            ManagerDayRepository days,
            MissedAlertRepository alerts,
            ManagerAttendanceSettingsRepository settings,
            WorkingCalendar workingCalendar,
            BusinessCalendar business,
            ApplicationEventPublisher events,
            Clock clock,
            JdbcTemplate jdbc) {
        this.managers = managers;
        this.days = days;
        this.alerts = alerts;
        this.settings = settings;
        this.workingCalendar = workingCalendar;
        this.business = business;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public LocalDate today() {
        return business.today();
    }

    public LocalTime now() {
        return LocalTime.ofInstant(clock.instant(), business.zone());
    }

    /** The Managers who have missed the date as of {@code now} (the time matters only for today). */
    @Transactional(readOnly = true)
    public List<Missed> missedOn(LocalDate date, LocalTime now) {
        return qualifying(date, now).stream()
                .map(q -> new Missed(q.ref().id(), q.ref().displayName(), q.employeeId()))
                .toList();
    }

    /** The Managers on approved leave on the date, with how much of the day and whether it is unpaid. */
    @Transactional(readOnly = true)
    public List<OnLeave> onLeaveOn(LocalDate date) {
        List<ManagerDay> rows = days.findByDayDate(date).stream().filter(ManagerDay::hasLeave).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, ManagerRef> refs = managers.allManagers().stream()
                .collect(Collectors.toMap(ManagerRef::id, Function.identity(), (a, b) -> a));
        return rows.stream()
                .filter(r -> refs.containsKey(r.getManagerId()))
                .map(r -> new OnLeave(
                        r.getManagerId(),
                        refs.get(r.getManagerId()).displayName(),
                        r.getLeaveFraction(),
                        r.isLeaveUnpaid()))
                .sorted(Comparator.comparing(o -> o.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Raises the alert for every Manager who has missed the date and has not been alerted yet; returns how many were
     * raised. Safe to run twice or on two instances: the insert does nothing when the row exists, and only the run
     * that inserted it publishes the event.
     */
    @Transactional
    public int alertDue(LocalDate date, LocalTime now) {
        List<Qualifying> due = qualifying(date, now);
        if (due.isEmpty()) {
            return 0;
        }
        Set<UUID> alerted = new HashSet<>(alerts.alertedOn(date, due.stream().map(q -> q.ref().id()).toList()));
        int raised = 0;
        for (Qualifying q : due) {
            if (alerted.contains(q.ref().id())) {
                continue;
            }
            int inserted = jdbc.update(
                    "insert into manager_missed_alert (manager_id, alert_date, created_at) values (?, ?, ?)"
                            + " on conflict do nothing",
                    q.ref().id(),
                    date,
                    java.sql.Timestamp.from(clock.instant()));
            if (inserted > 0) {
                events.publishEvent(new ManagerCheckInMissed(q.ref().id(), date));
                raised++;
            }
        }
        return raised;
    }

    /**
     * Working days of the month on which each given Manager missed the check-in, counting only days already past (today
     * once the cut-off has passed). One query for the records and one for the working days, however many Managers.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> missedDaysInMonth(YearMonth month, Collection<UUID> managerIds) {
        LocalDate today = business.today();
        LocalDate last = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        if (managerIds.isEmpty() || last.isBefore(month.atDay(1))) {
            return Map.of();
        }
        boolean todayCounts = last.equals(today) && now().isAfter(cutoff());
        LocalDate through = last.equals(today) && !todayCounts ? today.minusDays(1) : last;
        List<LocalDate> working = workingCalendar.workingDays(null, month.atDay(1), through);
        if (working.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ManagerEmployment> employment = managers.employment(managerIds);
        Map<String, ManagerDay> rows = new HashMap<>();
        for (ManagerDay d : days.findByManagerIdInAndDayDateBetween(managerIds, month.atDay(1), through)) {
            rows.put(d.getManagerId() + "|" + d.getDayDate(), d);
        }
        Map<UUID, Long> result = new HashMap<>();
        for (UUID id : managerIds) {
            long missed = 0;
            for (LocalDate date : working) {
                if (!employedOn(employment.get(id), date)) {
                    continue;
                }
                ManagerDay row = rows.get(id + "|" + date);
                if (row == null || (!row.hasCheckIn() && !row.hasLeave())) {
                    missed++;
                }
            }
            result.put(id, missed);
        }
        return result;
    }

    /** True when the date is not a working day (weekly off or holiday). */
    @Transactional(readOnly = true)
    public boolean isNonWorkingDay(LocalDate date) {
        return workingCalendar.workingDays(null, date, date).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private record Qualifying(ManagerRef ref, String employeeId) {}

    private List<Qualifying> qualifying(LocalDate date, LocalTime now) {
        LocalDate today = business.today();
        if (date.isAfter(today)) {
            return List.of();
        }
        if (date.equals(today) && !now.isAfter(cutoff())) {
            return List.of();
        }
        if (workingCalendar.workingDays(null, date, date).isEmpty()) {
            return List.of();
        }
        List<ManagerRef> active = managers.allManagers().stream().filter(ManagerRef::active).toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<UUID, ManagerEmployment> employment =
                managers.employment(active.stream().map(ManagerRef::id).toList());
        Map<UUID, ManagerDay> rows = days.findByDayDate(date).stream()
                .collect(Collectors.toMap(ManagerDay::getManagerId, Function.identity(), (a, b) -> a));
        return active.stream()
                .filter(ref -> employedOn(employment.get(ref.id()), date))
                .filter(ref -> {
                    ManagerDay row = rows.get(ref.id());
                    return row == null || (!row.hasCheckIn() && !row.hasLeave());
                })
                .sorted(Comparator.comparing(ref -> ref.displayName().toLowerCase(Locale.ROOT)))
                .map(ref -> new Qualifying(ref, employment.get(ref.id()).employeeId()))
                .toList();
    }

    private static boolean employedOn(ManagerEmployment emp, LocalDate date) {
        return emp != null
                && emp.joiningDate() != null
                && !emp.joiningDate().isAfter(date)
                && (emp.exitDate() == null || !emp.exitDate().isBefore(date));
    }

    private LocalTime cutoff() {
        return settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."))
                .getMissedCutoff();
    }
}
