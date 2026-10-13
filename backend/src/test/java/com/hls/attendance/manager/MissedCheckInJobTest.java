package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.attendance.manager.internal.MissedCheckInService;
import com.hls.identity.user.Role;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

/** Spec 032 US4: the missed check-in alert, raised once, to the right people, for the right Managers. */
class MissedCheckInJobTest extends ManagerAttendanceTestBase {

    private static final LocalTime EVENING = LocalTime.of(23, 0);

    @Autowired
    private MissedCheckInService service;

    /** A working day well in the past, so the clock does not matter. */
    private LocalDate pastWorkingDay() {
        return workingDates(lastMonth()).get(5);
    }

    private long alertRows(UUID managerId, LocalDate date) {
        return jdbc.queryForObject(
                "select count(*) from manager_missed_alert where manager_id = ? and alert_date = ?",
                Long.class,
                managerId,
                date);
    }

    private long missed(UUID userId) {
        return notifications(userId, "MANAGER_CHECK_IN_MISSED");
    }

    private boolean listed(LocalDate date, UUID managerId) {
        return service.missedOn(date, EVENING).stream().anyMatch(m -> m.managerId().equals(managerId));
    }

    @Test
    void aMissedDayNotifiesTheManagerAndEveryAdminAndDirectorOnceEvenIfTheJobRunsTwice() {
        Signed adminUser = signInAs(Role.ADMIN);
        Signed directorUser = signInAs(Role.DIRECTOR);
        ManagerCtx manager = longJoinedManager(adminUser.token());
        LocalDate date = pastWorkingDay();
        long adminBefore = missed(adminUser.userId());
        long directorBefore = missed(directorUser.userId());

        int first = service.alertDue(date, EVENING);

        assertThat(first).isGreaterThanOrEqualTo(1);
        assertThat(alertRows(manager.managerId(), date)).isEqualTo(1);
        assertThat(missed(manager.signed().userId())).isEqualTo(1);
        assertThat(missed(adminUser.userId())).isEqualTo(adminBefore + first);
        assertThat(missed(directorUser.userId())).isEqualTo(directorBefore + first);

        int second = service.alertDue(date, EVENING);

        assertThat(second).isZero();
        assertThat(missed(manager.signed().userId())).isEqualTo(1);
        assertThat(missed(adminUser.userId())).isEqualTo(adminBefore + first);
    }

    @Test
    void anAlertAlreadyRecordedIsNotRaisedAgain() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate date = pastWorkingDay();
        jdbc.update(
                "insert into manager_missed_alert (manager_id, alert_date, created_at) values (?, ?, now())",
                manager.managerId(),
                date);

        service.alertDue(date, EVENING);

        assertThat(missed(manager.signed().userId())).isZero();
        assertThat(alertRows(manager.managerId(), date)).isEqualTo(1);
    }

    @Test
    void noOneIsAlertedWhoHasLeaveACheckInNoJoiningDateOrNoEmploymentThatDay() {
        String admin = signInAs(Role.ADMIN).token();
        LocalDate date = pastWorkingDay();
        ManagerCtx due = longJoinedManager(admin);
        ManagerCtx onLeave = longJoinedManager(admin);
        seedLeave(onLeave.managerId(), date, 1.0);
        ManagerCtx halfLeave = longJoinedManager(admin);
        seedLeave(halfLeave.managerId(), date, 0.5);
        ManagerCtx checkedIn = longJoinedManager(admin);
        seedDay(checkedIn.managerId(), date, instant(date, 9, 0), instant(date, 18, 0));
        ManagerCtx notYetJoined = newManager(admin);
        setJoining(notYetJoined.managerId(), date.plusDays(1), null);
        ManagerCtx gone = newManager(admin);
        setJoining(gone.managerId(), date.minusMonths(6), date.minusDays(1));
        ManagerCtx noJoining = newManager(admin);

        service.alertDue(date, EVENING);

        assertThat(alertRows(due.managerId(), date)).isEqualTo(1);
        for (ManagerCtx m : List.of(onLeave, halfLeave, checkedIn, notYetJoined, gone, noJoining)) {
            assertThat(alertRows(m.managerId(), date)).as(m.managerId().toString()).isZero();
            assertThat(missed(m.signed().userId())).isZero();
        }
    }

    @Test
    void aWeeklyOffAndAHolidayAreNotAlerted() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        List<LocalDate> working = workingDates(lastMonth());
        LocalDate off = lastMonth().atDay(1);
        while (working.contains(off)) {
            off = off.plusDays(1);
        }
        LocalDate holiday = working.get(7);
        addHoliday(admin, holiday);
        try {
            service.alertDue(off, EVENING);
            service.alertDue(holiday, EVENING);
        } finally {
            removeHoliday(admin, holiday);
        }

        assertThat(alertRows(manager.managerId(), off)).isZero();
        assertThat(alertRows(manager.managerId(), holiday)).isZero();
        assertThat(missed(manager.signed().userId())).isZero();
    }

    @Test
    void aFutureDayIsNeverMissed() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);

        assertThat(service.alertDue(today().plusDays(1), EVENING)).isZero();
        assertThat(listed(today().plusDays(1), manager.managerId())).isFalse();
    }

    @Test
    void aCheckInAfterTheAlertRemovesTheDayFromTheMissedList() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate date = pastWorkingDay();
        service.alertDue(date, EVENING);
        assertThat(listed(date, manager.managerId())).isTrue();

        seedDay(manager.managerId(), date, instant(date, 11, 0), null);

        assertThat(listed(date, manager.managerId())).isFalse();
        assertThat(alertRows(manager.managerId(), date)).isEqualTo(1);
    }

    @Test
    void todayIsMissedOnlyAfterTheCutOffAndACheckInThenMarksItLate() {
        assumeTrue(!workingCalendarEmpty(today()), "today is not a working day");
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59', late_after = time '00:00' where id = 1");

        assertThat(service.missedOn(today(), LocalTime.of(12, 0))).extracting(m -> m.managerId()).doesNotContain(manager.managerId());

        jdbc.update("update manager_attendance_setting set missed_cutoff = time '00:01' where id = 1");
        assertThat(service.missedOn(today(), LocalTime.of(12, 0))).extracting(m -> m.managerId()).contains(manager.managerId());
        assertThat(service.alertDue(today(), LocalTime.of(12, 0))).isGreaterThanOrEqualTo(1);
        assertThat(missed(manager.signed().userId())).isEqualTo(1);

        Resp resp = checkIn(manager.token(), "OFFICE");

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("late", true);
        assertThat(service.missedOn(today(), LocalTime.of(12, 0))).extracting(m -> m.managerId()).doesNotContain(manager.managerId());
    }

    @Test
    void changingTheCutOffAppliesFromTheNextRun() {
        assumeTrue(!workingCalendarEmpty(today()), "today is not a working day");
        Signed adminUser = signInAs(Role.ADMIN);
        ManagerCtx manager = longJoinedManager(adminUser.token());
        jdbc.update("update manager_attendance_setting set missed_cutoff = time '23:59', late_after = time '00:00' where id = 1");
        assertThat(service.alertDue(today(), LocalTime.of(12, 0))).isZero();
        long version = ((Number) get(ATT + "/settings", adminUser.token()).map().get("version")).longValue();

        Resp changed = put(
                ATT + "/settings",
                adminUser.token(),
                Map.of("missedCutoff", "10:30", "lateAfter", "09:30", "version", version));
        assertThat(changed.status()).as(changed.body()).isEqualTo(200);

        assertThat(service.alertDue(today(), LocalTime.of(9, 0))).as("before the new cut-off").isZero();
        assertThat(service.alertDue(today(), LocalTime.of(10, 31))).as("after the new cut-off").isGreaterThanOrEqualTo(1);
        assertThat(missed(manager.signed().userId())).isEqualTo(1);
    }

    @Test
    void theJobRunsAtLeastEveryTwoMinutes() throws Exception {
        Class<?> job = Class.forName("com.hls.attendance.manager.internal.MissedCheckInJob");
        Method run = job.getDeclaredMethod("run");

        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRate()).isBetween(1L, 120_000L);
    }

    private boolean workingCalendarEmpty(LocalDate date) {
        return workingCalendar.workingDays(null, date, date).isEmpty();
    }
}
