package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.api.ManagerAttendanceReadApi;
import com.hls.attendance.api.ManagerLeaveAttendance;
import com.hls.attendance.api.ManagerLeaveAttendance.LeaveDay;
import com.hls.attendance.api.ManagerMonth.Found;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 032 US6: locking a Manager's month for payroll, what it freezes and what it refuses. */
class ManagerMonthLockTest extends ManagerAttendanceTestBase {

    @Autowired
    private ManagerAttendanceReadApi readApi;

    @Autowired
    private ManagerLeaveAttendance leaveAttendance;

    private List<LocalDate> w() {
        return workingDates(lastMonth());
    }

    private Resp lock(String token, YearMonth month, UUID managerId) {
        Map<String, Object> body = new HashMap<>();
        if (managerId != null) {
            body.put("managerId", managerId.toString());
        }
        return post(ATT + "/months/" + month + "/lock", token, body);
    }

    private Resp reopen(String token, YearMonth month, UUID managerId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("managerId", managerId.toString());
        body.put("reason", reason);
        return post(ATT + "/months/" + month + "/reopen", token, body);
    }

    /** Employed on the first five working days of last month: three worked, two absent. */
    private ManagerCtx fiveDayManager(String admin) {
        ManagerCtx manager = newManager(admin);
        List<LocalDate> w = w();
        setJoining(manager.managerId(), w.get(0), w.get(4));
        for (int i = 0; i < 3; i++) {
            seedDay(manager.managerId(), w.get(i), instant(w.get(i), 9, 0), i == 2 ? null : instant(w.get(i), 18, 0));
        }
        return manager;
    }

    private Map<String, Object> monthRow(UUID managerId) {
        return jdbc.queryForMap(
                "select state, frozen_working_days, frozen_days_worked, frozen_unpaid, frozen_unpaid_dates, locked_by"
                        + " from manager_month where manager_id = ? and year_month = ?",
                managerId,
                lastMonth().toString());
    }

    private long events(UUID managerId, String event) {
        return jdbc.queryForObject(
                "select count(*) from manager_month_event where manager_id = ? and event = ?", Long.class, managerId, event);
    }

    private UUID casual() {
        return jdbc.queryForObject("select id from leave_type where code = 'CASUAL'", UUID.class);
    }

    private Resp askLeave(String token, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("leaveTypeId", casual().toString());
        body.put("firstDate", date.toString());
        body.put("lastDate", date.toString());
        body.put("firstHalf", false);
        body.put("lastHalf", false);
        body.put("reason", "Family function");
        return post(ME + "/leave", token, body);
    }

    private Resp askCheckIn(String token, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date.toString());
        body.put("proposedTime", "09:15");
        body.put("dayType", "OFFICE");
        body.put("reason", "Phone was off");
        return post(ME + "/regularizations", token, body);
    }

    @Test
    void lockingOneManagerFreezesTheFiguresAndWritesTheEventAndTheAudit() {
        Signed adminUser = signInAs(Role.ADMIN);
        String admin = adminUser.token();
        ManagerCtx manager = fiveDayManager(admin);
        List<LocalDate> w = w();

        Resp resp = lock(admin, lastMonth(), manager.managerId());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(listOf(resp, "locked")).hasSize(1);
        Map<String, Object> row = monthRow(manager.managerId());
        assertThat(row.get("state")).isEqualTo("LOCKED");
        assertThat((BigDecimal) row.get("frozen_working_days")).isEqualByComparingTo("5");
        assertThat((BigDecimal) row.get("frozen_days_worked")).isEqualByComparingTo("3");
        assertThat((BigDecimal) row.get("frozen_unpaid")).isEqualByComparingTo("2");
        assertThat(row.get("frozen_unpaid_dates")).isEqualTo(w.get(3) + ":1.0," + w.get(4) + ":1.0");
        assertThat(row.get("locked_by")).isEqualTo(adminUser.userId());
        assertThat(events(manager.managerId(), "LOCKED")).isEqualTo(1);
        assertChangeRecorded(admin, "MANAGER_MONTH", manager.managerId(), "state");
        assertThat(auditBody(admin, "MANAGER_MONTH", manager.managerId())).contains("OPEN").contains("LOCKED " + lastMonth());
    }

    @Test
    void theReadApiReturnsTheFrozenFiguresUntilTheMonthIsReopened() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);
        List<LocalDate> w = w();
        lock(admin, lastMonth(), manager.managerId());

        // a record changes after the lock; the frozen figures do not
        jdbc.update("delete from manager_day where manager_id = ? and day_date = ?", manager.managerId(), w.get(0));
        Found frozen = (Found) readApi.month(manager.managerId(), lastMonth());
        assertThat(frozen.locked()).isTrue();
        assertThat(frozen.daysWorked()).isEqualByComparingTo("3");
        assertThat(frozen.unpaid()).isEqualByComparingTo("2");
        assertThat(frozen.unpaidDays()).extracting(d -> d.date()).containsExactly(w.get(3), w.get(4));

        assertThat(reopen(admin, lastMonth(), manager.managerId(), "Check the first day").status()).isEqualTo(200);
        Found live = (Found) readApi.month(manager.managerId(), lastMonth());
        assertThat(live.locked()).isFalse();
        assertThat(live.daysWorked()).isEqualByComparingTo("2");
        assertThat(live.unpaid()).isEqualByComparingTo("3");
    }

    @Test
    void aMonthThatHasNotEndedIs409() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);

        Resp current = lock(admin, YearMonth.from(today()), manager.managerId());
        Resp future = lock(admin, YearMonth.from(today()).plusMonths(1), manager.managerId());
        Resp all = lock(admin, YearMonth.from(today()), null);

        assertThat(current.status()).as(current.body()).isEqualTo(409);
        assertThat(current.body()).contains("not ended");
        assertThat(future.status()).isEqualTo(409);
        assertThat(all.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from manager_month where manager_id = ?", Long.class, manager.managerId()))
                .isZero();
    }

    @Test
    void aPendingLeaveRequestRefusesTheLockAndIsListedUntilItIsDecided() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);
        List<LocalDate> w = w();
        UUID request = askLeave(manager.token(), w.get(3)).id();

        Resp refused = lock(admin, lastMonth(), manager.managerId());

        assertThat(refused.status()).as(refused.body()).isEqualTo(409);
        List<Map<String, Object>> pending = listOf(refused, "pending");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0)).containsEntry("kind", "LEAVE").containsEntry("requestId", request.toString());
        assertThat(pending.get(0)).containsKey("managerName");
        assertThat(jdbc.queryForObject("select count(*) from manager_month where manager_id = ?", Long.class, manager.managerId()))
                .isZero();

        post(ATT + "/leave/" + request + "/reject", admin, Map.of("reason", "Month end"));
        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(200);
    }

    @Test
    void aPendingForgottenCheckInRefusesTheLockAndIsListedUntilItIsDecided() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);
        UUID request = askCheckIn(manager.token(), w().get(3)).id();

        Resp refused = lock(admin, lastMonth(), manager.managerId());

        assertThat(refused.status()).as(refused.body()).isEqualTo(409);
        List<Map<String, Object>> pending = listOf(refused, "pending");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0)).containsEntry("kind", "FORGOTTEN_CHECK_IN").containsEntry("requestId", request.toString());

        post(ATT + "/regularizations/" + request + "/reject", admin, Map.of("reason", "No such visit"));
        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(200);
    }

    @Test
    void aSecondLockOfTheSameManagerIs409() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);

        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(200);
        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(409);
        assertThat(events(manager.managerId(), "LOCKED")).isEqualTo(1);
    }

    @Test
    void lockingAllLocksEachLockableManagerAndReturnsTheRefusedWithTheirReason() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx fine = fiveDayManager(admin);
        ManagerCtx waiting = fiveDayManager(admin);
        askLeave(waiting.token(), w().get(3));
        ManagerCtx noJoining = newManager(admin);
        ManagerCtx notYetEmployed = newManager(admin);
        setJoining(notYetEmployed.managerId(), today(), null);

        Resp resp = lock(admin, lastMonth(), null);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(listOf(resp, "locked")).extracting(m -> m.get("managerId")).contains(fine.managerId().toString());
        List<Map<String, Object>> refused = listOf(resp, "refused");
        Map<String, Object> pendingRefusal = refused.stream()
                .filter(r -> waiting.managerId().toString().equals(r.get("managerId")))
                .findFirst()
                .orElseThrow();
        assertThat((String) pendingRefusal.get("reason")).contains("waiting for a decision");
        assertThat(listOf(pendingRefusal, "pending")).hasSize(1);
        Map<String, Object> noJoiningRefusal = refused.stream()
                .filter(r -> noJoining.managerId().toString().equals(r.get("managerId")))
                .findFirst()
                .orElseThrow();
        assertThat((String) noJoiningRefusal.get("reason")).contains("joining date");
        assertThat(resp.body()).doesNotContain(notYetEmployed.managerId().toString());
        assertThat(monthRow(fine.managerId()).get("state")).isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject("select count(*) from manager_month where manager_id = ?", Long.class, waiting.managerId()))
                .isZero();

        Resp again = lock(admin, lastMonth(), null);
        assertThat(((Number) again.map().get("alreadyLocked")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(listOf(again, "locked")).extracting(m -> m.get("managerId")).doesNotContain(fine.managerId().toString());
    }

    @Test
    void everyWriterIsRefusedWith409InALockedMonthAndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);
        List<LocalDate> w = w();
        lock(admin, lastMonth(), manager.managerId());
        long rows = dayRows(manager.managerId());

        Resp correct = put(
                ATT + "/managers/" + manager.managerId() + "/days/" + w.get(1),
                admin,
                Map.of("reason", "Late fix", "checkOutTime", "17:00"));
        Resp complete = post(
                ATT + "/managers/" + manager.managerId() + "/days/" + w.get(2) + "/complete-check-out",
                admin,
                Map.of("time", "18:00", "reason", "Left a note"));
        Resp ownComplete = post(ME + "/days/" + w.get(2) + "/complete-check-out", manager.token(), Map.of("time", "18:00", "reason", "x"));
        Resp request = askCheckIn(manager.token(), w.get(3));

        assertThat(correct.status()).as(correct.body()).isEqualTo(409);
        assertThat(complete.status()).as(complete.body()).isEqualTo(409);
        assertThat(ownComplete.status()).isEqualTo(409);
        assertThat(request.status()).as(request.body()).isEqualTo(409);
        assertThatThrownBy(() -> leaveAttendance.apply(
                        UUID.randomUUID(), UUID.randomUUID(), manager.managerId(), List.of(new LeaveDay(w.get(3), false, false))))
                .isInstanceOf(ConflictException.class);
        assertThat(dayRows(manager.managerId())).isEqualTo(rows);
        assertThat(historyRows(manager.managerId(), "CORRECT")).isZero();
    }

    @Test
    void reopeningNeedsAReasonClearsTheFrozenFiguresAndAllowsChangesAgain() {
        Signed adminUser = signInAs(Role.ADMIN);
        String admin = adminUser.token();
        ManagerCtx manager = fiveDayManager(admin);
        List<LocalDate> w = w();
        lock(admin, lastMonth(), manager.managerId());

        assertThat(reopen(admin, lastMonth(), manager.managerId(), null).status()).as("no reason").isEqualTo(400);
        assertThat(reopen(admin, lastMonth(), manager.managerId(), "  ").status()).as("blank reason").isEqualTo(400);
        assertThat(reopen(admin, lastMonth(), manager.managerId(), "x".repeat(501)).status()).as("long reason").isEqualTo(400);
        assertThat(monthRow(manager.managerId()).get("state")).isEqualTo("LOCKED");

        Resp ok = reopen(admin, lastMonth(), manager.managerId(), "A visit was missed");

        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        Map<String, Object> row = monthRow(manager.managerId());
        assertThat(row.get("state")).isEqualTo("OPEN");
        assertThat(row.get("frozen_working_days")).isNull();
        assertThat(row.get("frozen_unpaid_dates")).isNull();
        assertThat(events(manager.managerId(), "REOPENED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select reason from manager_month_event where manager_id = ? and event = 'REOPENED'",
                        String.class,
                        manager.managerId()))
                .isEqualTo("A visit was missed");
        assertChangeRecorded(admin, "MANAGER_MONTH", manager.managerId(), "reopenReason");
        assertThat(auditBody(admin, "MANAGER_MONTH", manager.managerId())).contains("A visit was missed");

        Map<String, Object> fix = new HashMap<>();
        fix.put("reason", "Visit on the fourth day");
        fix.put("dayType", "OFFICE");
        fix.put("checkInTime", "09:10");
        assertThat(put(ATT + "/managers/" + manager.managerId() + "/days/" + w.get(3), admin, fix).status()).isEqualTo(200);

        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(200);
        Map<String, Object> relocked = monthRow(manager.managerId());
        assertThat((BigDecimal) relocked.get("frozen_days_worked")).isEqualByComparingTo("4");
        assertThat((BigDecimal) relocked.get("frozen_unpaid")).isEqualByComparingTo("1");
        assertThat(events(manager.managerId(), "LOCKED")).isEqualTo(2);
    }

    @Test
    void reopeningAMonthThatIsNotLockedOrAnUnknownManagerIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);

        assertThat(reopen(admin, lastMonth(), manager.managerId(), "Reason").status()).isEqualTo(409);
        assertThat(reopen(admin, lastMonth(), UUID.randomUUID(), "Reason").status()).isEqualTo(404);
        assertThat(lock(admin, lastMonth(), UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(post(ATT + "/months/not-a-month/lock", admin, Map.of()).status()).isEqualTo(400);
    }

    @Test
    void onlyAnAdminWithProcessMayLockOrReopen() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = fiveDayManager(admin);
        Map<String, String> others = Map.of(
                "director", signInAs(Role.DIRECTOR).token(),
                "manager", manager.token(),
                "teacher", teacher().token(),
                "system", signInAs(Role.SYSTEM).token());

        others.forEach((who, token) -> {
            assertThat(lock(token, lastMonth(), manager.managerId()).status()).as(who + " lock").isEqualTo(403);
            assertThat(reopen(token, lastMonth(), manager.managerId(), "Reason").status()).as(who + " reopen").isEqualTo(403);
        });
        assertThat(jdbc.queryForObject("select count(*) from manager_month where manager_id = ?", Long.class, manager.managerId()))
                .isZero();
        assertThat(lock(admin, lastMonth(), manager.managerId()).status()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Resp resp, String field) {
        return listOf(resp.map(), field);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> map, String field) {
        return (List<Map<String, Object>>) map.get(field);
    }
}
