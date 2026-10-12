package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US1: each check-in, check-out and completion is audited and kept in the history, with the actor. */
class CheckInAuditTest extends ManagerAttendanceTestBase {

    private UUID dayId(UUID managerId) {
        return jdbc.queryForObject("select id from manager_day where manager_id = ?", UUID.class, managerId);
    }

    @Test
    void checkInAndCheckOutWriteAnAuditEntryAndAHistoryRowWithTheActor() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);

        checkIn(manager.token(), "OFFICE");
        checkOut(manager.token());

        UUID day = dayId(manager.managerId());
        assertChangeRecorded(admin, "MANAGER_DAY", day, "checkIn");
        assertChangeRecorded(admin, "MANAGER_DAY", day, "checkOut");
        assertThat(historyRows(manager.managerId(), "CHECK_IN")).isEqualTo(1);
        assertThat(historyRows(manager.managerId(), "CHECK_OUT")).isEqualTo(1);
        Long byManager = jdbc.queryForObject(
                "select count(*) from manager_day_history where day_id = ? and changed_by = ?",
                Long.class,
                day,
                manager.signed().userId());
        assertThat(byManager).isEqualTo(2);
        String original = jdbc.queryForObject(
                "select after_json from manager_day_history where day_id = ? and action = 'CHECK_IN'", String.class, day);
        assertThat(original).contains("OFFICE");
    }

    @Test
    void aCompletionIsAuditedWithTheReasonAndTheAdminAsActor() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        LocalDate yesterday = today().minusDays(1);
        Instant in = yesterday.atTime(9, 0).atZone(businessCalendar.zone()).toInstant();
        UUID day = seedDay(manager.managerId(), yesterday, in, null);

        Resp resp = post(
                ATT + "/managers/" + manager.managerId() + "/days/" + yesterday + "/complete-check-out",
                admin,
                Map.of("time", "18:05", "reason", "Forgot to tap out"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertChangeRecorded(admin, "MANAGER_DAY", day, "completeCheckOut");
        Map<String, Object> row = jdbc.queryForMap(
                "select reason, changed_by from manager_day_history where day_id = ? and action = 'COMPLETE_CHECK_OUT'", day);
        assertThat(row.get("reason")).isEqualTo("Forgot to tap out");
    }

    @Test
    void aRefusedRequestWritesNeitherAHistoryRowNorAnAuditEntry() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = joinedManager(admin);
        long auditBefore = auditCount(admin);
        long historyBefore = jdbc.queryForObject("select count(*) from manager_day_history", Long.class);

        Resp noLocation = post(ME + "/check-in", manager.token(), Map.of("dayType", "OFFICE"));
        Resp checkOutFirst = checkOut(manager.token());
        lockMonth(manager.managerId(), YearMonth.from(today()));
        Resp locked = checkIn(manager.token(), "OFFICE");

        assertThat(noLocation.status()).isEqualTo(400);
        assertThat(checkOutFirst.status()).isEqualTo(400);
        assertThat(locked.status()).isEqualTo(409);
        assertThat(dayRows(manager.managerId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from manager_day_history", Long.class)).isEqualTo(historyBefore);
        Thread.sleep(800);
        assertThat(auditCount(admin)).isEqualTo(auditBefore);
    }

    private long auditCount(String adminToken) {
        Resp resp = get("/api/v1/audit/change-history?entityType=MANAGER_DAY&size=1", adminToken);
        assertThat(resp.status()).isEqualTo(200);
        return total(resp);
    }
}
