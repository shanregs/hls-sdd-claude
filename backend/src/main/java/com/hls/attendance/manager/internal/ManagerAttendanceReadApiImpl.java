package com.hls.attendance.manager.internal;

import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerMonth;
import com.hls.attendance.api.WorkingCalendar;
import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.DayRecord;
import com.hls.attendance.manager.internal.ManagerMonthCalculator.Figures;
import com.hls.attendance.manager.internal.ManagerMonthState.Frozen;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What payroll and reports read of a Manager's month (spec 032 US7). The figures come from {@link
 * ManagerMonthCalculator} over the Manager's records and the default calendar, except that a locked month returns the
 * figures frozen at the lock. The bulk form reads the employment, the records and the month states once each, however
 * many Managers are asked, and the single form is the bulk form for one Manager, so the two cannot disagree. Nothing
 * returned carries a location or a photo.
 */
@Service
@Transactional(readOnly = true)
public class ManagerAttendanceReadApiImpl implements ManagerAttendanceReadApi {

    private final ManagerQueries managers;
    private final ManagerDayRepository days;
    private final ManagerMonthStateRepository monthStates;
    private final WorkingCalendar workingCalendar;
    private final BusinessCalendar business;

    public ManagerAttendanceReadApiImpl(
            ManagerQueries managers,
            ManagerDayRepository days,
            ManagerMonthStateRepository monthStates,
            WorkingCalendar workingCalendar,
            BusinessCalendar business) {
        this.managers = managers;
        this.days = days;
        this.monthStates = monthStates;
        this.workingCalendar = workingCalendar;
        this.business = business;
    }

    @Override
    public ManagerMonth month(UUID managerId, YearMonth month) {
        return months(List.of(managerId), month).get(managerId);
    }

    @Override
    public Set<UUID> managersOnLeaveOn(Collection<UUID> managerIds, LocalDate date) {
        if (managerIds.isEmpty() || date == null) {
            return Set.of();
        }
        return days.findByManagerIdInAndDayDateBetween(new LinkedHashSet<>(managerIds), date, date).stream()
                .filter(ManagerDay::hasFullLeave)
                .map(ManagerDay::getManagerId)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<UUID, ManagerMonth> months(Collection<UUID> managerIds, YearMonth month) {
        Set<UUID> ids = new LinkedHashSet<>(managerIds);
        Map<UUID, ManagerMonth> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<UUID, ManagerEmployment> employment = managers.employment(ids);
        List<LocalDate> working = workingCalendar.workingDays(null, from, to);
        Map<UUID, List<ManagerDay>> records = new HashMap<>();
        for (ManagerDay d : days.findByManagerIdInAndDayDateBetween(ids, from, to)) {
            records.computeIfAbsent(d.getManagerId(), k -> new ArrayList<>()).add(d);
        }
        Map<UUID, ManagerMonthState> states = monthStates.findForMonth(ids, month.toString()).stream()
                .collect(Collectors.toMap(s -> s.getId().getManagerId(), s -> s, (a, b) -> a));
        LocalDate today = business.today();

        for (UUID id : ids) {
            ManagerEmployment emp = employment.get(id);
            if (emp == null) {
                result.put(id, new ManagerMonth.Unknown());
            } else if (emp.joiningDate() == null) {
                result.put(id, new ManagerMonth.NoJoiningDate());
            } else {
                result.put(id, found(month, emp, working, records.getOrDefault(id, List.of()), states.get(id), today));
            }
        }
        return result;
    }

    private static ManagerMonth found(
            YearMonth month,
            ManagerEmployment emp,
            List<LocalDate> working,
            List<ManagerDay> rows,
            ManagerMonthState state,
            LocalDate today) {
        boolean locked = state != null && state.isLocked();
        if (locked && state.frozen().workingDays() != null) {
            Frozen f = state.frozen();
            // a locked month is over, so nothing in it is still pending
            return new ManagerMonth.Found(
                    month,
                    f.workingDays(),
                    f.daysWorked(),
                    f.leavePaid(),
                    f.leaveUnpaid(),
                    f.unpaid(),
                    f.fieldDays(),
                    FrozenUnpaidDates.decode(f.unpaidDates()),
                    List.of(),
                    true);
        }
        List<LocalDate> employed = working.stream()
                .filter(d -> !d.isBefore(emp.joiningDate()))
                .filter(d -> emp.exitDate() == null || !d.isAfter(emp.exitDate()))
                .toList();
        Map<LocalDate, DayRecord> records = new HashMap<>();
        for (ManagerDay d : rows) {
            records.put(
                    d.getDayDate(),
                    new DayRecord(d.hasCheckIn(), d.getDayType() == ManagerDay.DayType.FIELD, d.getLeaveFraction(), d.isLeaveUnpaid()));
        }
        Figures f = ManagerMonthCalculator.compute(employed, records, today);
        return new ManagerMonth.Found(
                month,
                f.workingDays(),
                f.daysWorked(),
                f.leavePaid(),
                f.leaveUnpaid(),
                f.unpaid(),
                f.fieldDays(),
                f.unpaidDays(),
                f.pending(),
                locked);
    }
}
