package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerLeaveAttendance;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.manager.internal.ManagerDayHistory.Action;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.school.api.ConflictException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes approved Manager leave onto the Manager's days and removes it again (spec 032 US3). Both calls check every
 * day first and change nothing if any day is in a locked month or already holds leave from another request.
 */
@Service
public class ManagerLeaveAttendanceImpl implements ManagerLeaveAttendance {

    private final ManagerMonthGuard guard;
    private final ManagerDayRepository days;
    private final ManagerDayHistoryRepository history;
    private final ManagerQueries managers;
    private final WorkingCalendar workingCalendar;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public ManagerLeaveAttendanceImpl(
            ManagerMonthGuard guard,
            ManagerDayRepository days,
            ManagerDayHistoryRepository history,
            ManagerQueries managers,
            WorkingCalendar workingCalendar,
            Clock clock,
            JdbcTemplate jdbc) {
        this.guard = guard;
        this.days = days;
        this.history = history;
        this.managers = managers;
        this.workingCalendar = workingCalendar;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> workingDays(UUID managerId, LocalDate from, LocalDate to) {
        ManagerEmployment employment = managers.employment(List.of(managerId)).get(managerId);
        if (employment == null || employment.joiningDate() == null) {
            return List.of();
        }
        return workingCalendar.workingDays(null, from, to).stream()
                .filter(d -> !d.isBefore(employment.joiningDate()))
                .filter(d -> employment.exitDate() == null || !d.isAfter(employment.exitDate()))
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public void apply(UUID requestId, UUID actor, UUID managerId, List<LeaveDay> leaveDays) {
        List<LeaveDay> ordered = leaveDays.stream().sorted(Comparator.comparing(LeaveDay::date)).toList();
        for (LeaveDay day : ordered) {
            CheckInService.lockDay(jdbc, managerId, day.date());
            guard.requireOpen(managerId, YearMonth.from(day.date()));
            ManagerDay row = days.findByManagerIdAndDayDate(managerId, day.date()).orElse(null);
            if (row == null) {
                continue;
            }
            if (row.hasLeave() && !requestId.equals(row.getLeaveRequestId())) {
                throw new ConflictException("The Manager already has leave on " + day.date() + ".");
            }
            if (!day.half() && row.hasCheckIn()) {
                throw new ConflictException("The Manager checked in on " + day.date()
                        + ", so a full day of leave cannot be added. Correct that day first.");
            }
        }
        for (LeaveDay day : ordered) {
            ManagerDay row = days.findByManagerIdAndDayDate(managerId, day.date())
                    .orElseGet(() -> new ManagerDay(managerId, day.date()));
            String before = DayJson.snapshot(row);
            row.applyLeave(requestId, day.half() ? new BigDecimal("0.5") : BigDecimal.ONE, day.unpaid());
            ManagerDay saved = days.saveAndFlush(row);
            history.save(new ManagerDayHistory(
                    saved.getId(), actor, clock.instant(), Action.LEAVE_APPLIED, "Leave approved", before, DayJson.snapshot(saved)));
        }
    }

    @Override
    @Transactional
    public void remove(UUID requestId, UUID actor) {
        List<ManagerDay> rows = days.findByLeaveRequestId(requestId).stream()
                .sorted(Comparator.comparing(ManagerDay::getDayDate))
                .toList();
        for (ManagerDay row : rows) {
            CheckInService.lockDay(jdbc, row.getManagerId(), row.getDayDate());
            guard.requireOpen(row.getManagerId(), YearMonth.from(row.getDayDate()));
        }
        for (ManagerDay row : rows) {
            String before = DayJson.snapshot(row);
            row.clearLeave();
            ManagerDay saved = days.saveAndFlush(row);
            history.save(new ManagerDayHistory(
                    saved.getId(), actor, clock.instant(), Action.LEAVE_REMOVED, "Leave removed", before, DayJson.snapshot(saved)));
        }
    }
}
