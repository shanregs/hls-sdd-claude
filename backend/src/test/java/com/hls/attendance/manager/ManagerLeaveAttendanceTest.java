package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.api.ManagerLeaveAttendance;
import com.hls.attendance.api.ManagerLeaveAttendance.LeaveDay;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 032 US3: the interface the leave module uses to write approved leave onto a Manager's days. */
class ManagerLeaveAttendanceTest extends ManagerAttendanceTestBase {

    @Autowired
    private ManagerLeaveAttendance leaveAttendance;

    private Map<String, Object> row(UUID managerId, LocalDate date) {
        return jdbc.queryForMap(
                "select leave_request_id, leave_fraction, leave_unpaid from manager_day where manager_id = ? and day_date = ?",
                managerId,
                date);
    }

    private UUID actor() {
        return signInAs(Role.ADMIN).userId();
    }

    @Test
    void applyWritesTheLeaveOnEachDayCreatingTheRowAndAHistoryEntry() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        List<LocalDate> working = workingDates(lastMonth());
        LocalDate first = working.get(0);
        LocalDate second = working.get(1);
        UUID request = UUID.randomUUID();
        seedDay(manager.managerId(), second, instant(second, 9, 0), instant(second, 13, 0));

        leaveAttendance.apply(
                request, actor(), manager.managerId(), List.of(new LeaveDay(first, false, false), new LeaveDay(second, true, true)));

        assertThat(row(manager.managerId(), first).get("leave_request_id")).isEqualTo(request);
        assertThat((BigDecimal) row(manager.managerId(), first).get("leave_fraction")).isEqualByComparingTo("1.0");
        assertThat(row(manager.managerId(), first).get("leave_unpaid")).isEqualTo(false);
        assertThat((BigDecimal) row(manager.managerId(), second).get("leave_fraction")).isEqualByComparingTo("0.5");
        assertThat(row(manager.managerId(), second).get("leave_unpaid")).isEqualTo(true);
        assertThat(historyRows(manager.managerId(), "LEAVE_APPLIED")).isEqualTo(2);
        assertThat(dayRows(manager.managerId())).isEqualTo(2);
    }

    @Test
    void removeClearsTheLeaveAndKeepsTheCheckIn() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = workingDates(lastMonth()).get(0);
        UUID request = UUID.randomUUID();
        seedDay(manager.managerId(), day, instant(day, 9, 0), instant(day, 13, 0));
        leaveAttendance.apply(request, actor(), manager.managerId(), List.of(new LeaveDay(day, true, false)));

        leaveAttendance.remove(request, actor());

        assertThat(row(manager.managerId(), day).get("leave_request_id")).isNull();
        assertThat(row(manager.managerId(), day).get("leave_fraction")).isNull();
        assertThat(row(manager.managerId(), day).get("leave_unpaid")).isEqualTo(false);
        assertThat(jdbc.queryForObject(
                        "select check_in_at is not null from manager_day where manager_id = ? and day_date = ?",
                        Boolean.class,
                        manager.managerId(),
                        day))
                .isTrue();
        assertThat(historyRows(manager.managerId(), "LEAVE_REMOVED")).isEqualTo(1);
    }

    @Test
    void aFullDayOfLeaveRefusesACheckInAndAHalfDayAcceptsOne() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx full = joinedManager(admin);
        ManagerCtx half = joinedManager(admin);
        LocalDate today = today();
        leaveAttendance.apply(UUID.randomUUID(), actor(), full.managerId(), List.of(new LeaveDay(today, false, false)));
        leaveAttendance.apply(UUID.randomUUID(), actor(), half.managerId(), List.of(new LeaveDay(today, true, false)));

        assertThat(checkIn(full.token(), "OFFICE").status()).isEqualTo(400);
        Resp accepted = checkIn(half.token(), "OFFICE");

        assertThat(accepted.status()).as(accepted.body()).isEqualTo(201);
    }

    @Test
    void bothFailWithAConflictInALockedMonthAndChangeNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = workingDates(lastMonth()).get(0);
        UUID request = UUID.randomUUID();
        lockMonth(manager.managerId(), lastMonth());

        assertThatThrownBy(() -> leaveAttendance.apply(request, actor(), manager.managerId(), List.of(new LeaveDay(day, false, false))))
                .isInstanceOf(ConflictException.class);
        assertThat(dayRows(manager.managerId())).isZero();

        jdbc.update("delete from manager_month where manager_id = ?", manager.managerId());
        leaveAttendance.apply(request, actor(), manager.managerId(), List.of(new LeaveDay(day, false, false)));
        lockMonth(manager.managerId(), lastMonth());

        assertThatThrownBy(() -> leaveAttendance.remove(request, actor())).isInstanceOf(ConflictException.class);
        assertThat(row(manager.managerId(), day).get("leave_request_id")).isEqualTo(request);
    }

    @Test
    void aFullDayOverAnExistingFullDayOfLeaveOrOverACheckInIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        List<LocalDate> working = workingDates(lastMonth());
        LocalDate onLeave = working.get(0);
        LocalDate worked = working.get(1);
        leaveAttendance.apply(UUID.randomUUID(), actor(), manager.managerId(), List.of(new LeaveDay(onLeave, false, false)));
        seedDay(manager.managerId(), worked, instant(worked, 9, 0), instant(worked, 17, 0));

        assertThatThrownBy(() -> leaveAttendance.apply(
                        UUID.randomUUID(), actor(), manager.managerId(), List.of(new LeaveDay(onLeave, false, false))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> leaveAttendance.apply(
                        UUID.randomUUID(), actor(), manager.managerId(), List.of(new LeaveDay(worked, false, false))))
                .isInstanceOf(ConflictException.class);
        assertThat(historyRows(manager.managerId(), "LEAVE_APPLIED")).isEqualTo(1);
    }

    @Test
    void workingDaysAreTheDefaultCalendarDaysInsideTheEmployment() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        List<LocalDate> working = workingDates(lastMonth());
        LocalDate joined = working.get(3);
        LocalDate exit = working.get(working.size() - 2);
        setJoining(manager.managerId(), joined, exit);

        List<LocalDate> days = leaveAttendance.workingDays(manager.managerId(), lastMonth().atDay(1), lastMonth().atEndOfMonth());

        assertThat(days).isEqualTo(working.stream().filter(d -> !d.isBefore(joined) && !d.isAfter(exit)).toList());
        ManagerCtx none = newManager(admin);
        assertThat(leaveAttendance.workingDays(none.managerId(), lastMonth().atDay(1), lastMonth().atEndOfMonth())).isEmpty();
    }
}
