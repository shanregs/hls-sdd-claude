package com.hls.leave.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US3: the Admin and Director decide a Manager's leave, and what the decisions do to the days. */
class ManagerLeaveDecisionTest extends ManagerLeaveTestBase {

    private Map<String, Object> leaveDay(UUID managerId, LocalDate date) {
        return jdbc.queryForMap(
                "select leave_request_id, leave_fraction, leave_unpaid, check_in_at from manager_day"
                        + " where manager_id = ? and day_date = ?",
                managerId,
                date);
    }

    @Test
    void approvalWritesPaidLeaveOntoTheWorkingDaysOnlyAndTellsTheManager() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] across = acrossAnOffDay();
        UUID id = ask(manager.token(), across[0], across[1]).id();
        List<LocalDate> working = workingDates(lastMonth()).stream()
                .filter(d -> !d.isBefore(across[0]) && !d.isAfter(across[1]))
                .toList();

        Resp resp = post(LEAVE + "/" + id + "/approve", admin, Map.of());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("status", "APPROVED");
        assertThat(leaveRows(id)).isEqualTo(working.size());
        for (LocalDate d : working) {
            Map<String, Object> row = leaveDay(manager.managerId(), d);
            assertThat((BigDecimal) row.get("leave_fraction")).isEqualByComparingTo("1.0");
            assertThat(row.get("leave_unpaid")).isEqualTo(false);
        }
        assertThat(historyRows(manager.managerId(), "LEAVE_APPLIED")).isEqualTo(working.size());
        assertThat(notifications(manager.signed().userId(), "MANAGER_LEAVE_DECIDED")).isEqualTo(1);
    }

    @Test
    void aLossOfPayRequestMakesUnpaidLeaveDays() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = post(MY_LEAVE, manager.token(), body(type("LOP"), days[0], days[1], false, false, "No balance")).id();

        assertThat(post(LEAVE + "/" + id + "/approve", admin, Map.of()).status()).isEqualTo(200);

        assertThat(leaveDay(manager.managerId(), days[0]).get("leave_unpaid")).isEqualTo(true);
        assertThat(leaveDay(manager.managerId(), days[1]).get("leave_unpaid")).isEqualTo(true);
    }

    @Test
    void halfDaysAtEitherEndAreWrittenAsHalfLeave() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = post(MY_LEAVE, manager.token(), body(type("CASUAL"), days[0], days[1], true, false, "Appointment")).id();

        post(LEAVE + "/" + id + "/approve", admin, Map.of());

        assertThat((BigDecimal) leaveDay(manager.managerId(), days[0]).get("leave_fraction")).isEqualByComparingTo("0.5");
        assertThat((BigDecimal) leaveDay(manager.managerId(), days[1]).get("leave_fraction")).isEqualByComparingTo("1.0");
    }

    @Test
    void aHalfDayOverACheckInIsKeptButAFullDayOverACheckInIs409AndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        seedDay(manager.managerId(), days[0], instant(days[0], 9, 0), instant(days[0], 13, 0));
        seedDay(manager.managerId(), days[1], instant(days[1], 9, 0), instant(days[1], 17, 0));
        UUID half = post(MY_LEAVE, manager.token(), body(type("CASUAL"), days[0], days[0], true, false, "Half")).id();
        UUID full = ask(manager.token(), days[1], days[1]).id();

        assertThat(post(LEAVE + "/" + half + "/approve", admin, Map.of()).status()).isEqualTo(200);
        assertThat(leaveDay(manager.managerId(), days[0]).get("check_in_at")).isNotNull();

        assertThat(post(LEAVE + "/" + full + "/approve", admin, Map.of()).status()).isEqualTo(409);
        assertThat(leaveStatus(full)).isEqualTo("PENDING");
        assertThat(leaveRows(full)).isZero();
    }

    @Test
    void rejectingNeedsAReasonAndWritesNoDays() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();

        assertThat(post(LEAVE + "/" + id + "/reject", director, Map.of()).status()).isEqualTo(400);
        assertThat(post(LEAVE + "/" + id + "/reject", director, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp resp = post(LEAVE + "/" + id + "/reject", director, Map.of("reason", "Month end"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("status", "REJECTED").containsEntry("decisionReason", "Month end");
        assertThat(leaveRows(id)).isZero();
        assertThat(notifications(manager.signed().userId(), "MANAGER_LEAVE_DECIDED")).isEqualTo(1);
        assertThat(post(LEAVE + "/" + id + "/approve", admin, Map.of()).status()).as("already decided").isEqualTo(409);
    }

    @Test
    void revokingNeedsAReasonAndRemovesTheDays() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();

        assertThat(post(LEAVE + "/" + id + "/revoke", admin, Map.of("reason", "Early")).status())
                .as("a pending request cannot be revoked")
                .isEqualTo(409);
        post(LEAVE + "/" + id + "/approve", admin, Map.of());
        assertThat(post(LEAVE + "/" + id + "/revoke", admin, Map.of()).status()).isEqualTo(400);
        assertThat(leaveRows(id)).isEqualTo(2);

        Resp resp = post(LEAVE + "/" + id + "/revoke", admin, Map.of("reason", "Needed at work"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("status", "REVOKED");
        assertThat(leaveRows(id)).isZero();
        assertThat(historyRows(manager.managerId(), "LEAVE_REMOVED")).isEqualTo(2);
        assertThat(notifications(manager.signed().userId(), "MANAGER_LEAVE_DECIDED")).isEqualTo(2);
    }

    @Test
    void aManagerNeverDecidesAndNobodyDecidesTheirOwnRequest() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();

        assertThat(post(LEAVE + "/" + id + "/approve", manager.token(), Map.of()).status()).isEqualTo(403);
        assertThat(post(LEAVE + "/" + id + "/reject", manager.token(), Map.of("reason", "x")).status()).isEqualTo(403);

        Signed both = signInAs(Role.MANAGER, Role.ADMIN);
        UUID managerId = post("/api/v1/managers", admin, Map.of("userId", both.userId())).id();
        setJoining(managerId, today().minusMonths(6), null);
        UUID mine = ask(both.token(), days[0], days[1]).id();

        assertThat(post(LEAVE + "/" + mine + "/approve", both.token(), Map.of()).status()).isEqualTo(403);
        assertThat(post(LEAVE + "/" + mine + "/reject", both.token(), Map.of("reason", "x")).status()).isEqualTo(403);
        assertThat(leaveStatus(mine)).isEqualTo("PENDING");
        assertThat(post(LEAVE + "/" + mine + "/approve", admin, Map.of()).status()).isEqualTo(200);
        assertThat(post(LEAVE + "/" + mine + "/revoke", both.token(), Map.of("reason", "x")).status()).isEqualTo(403);
    }

    @Test
    void approvalIntoALockedMonthIs409AndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();
        lockMonth(manager.managerId(), lastMonth());

        Resp resp = post(LEAVE + "/" + id + "/approve", admin, Map.of());

        assertThat(resp.status()).as(resp.body()).isEqualTo(409);
        assertThat(leaveStatus(id)).isEqualTo("PENDING");
        assertThat(leaveRows(id)).isZero();
        assertThat(dayRows(manager.managerId())).isZero();
    }

    @Test
    void revokingIntoALockedMonthIs409AndKeepsTheDays() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();
        post(LEAVE + "/" + id + "/approve", admin, Map.of());
        lockMonth(manager.managerId(), lastMonth());

        Resp resp = post(LEAVE + "/" + id + "/revoke", admin, Map.of("reason", "Late"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(409);
        assertThat(leaveStatus(id)).isEqualTo("APPROVED");
        assertThat(leaveRows(id)).isEqualTo(2);
    }

    @Test
    void theAdminsAreToldOfARequestAndOfItsCancellationButNotTheRequester() {
        String admin = signInAs(Role.ADMIN).token();
        Signed adminUser = signInAs(Role.ADMIN);
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        long requested = notifications(adminUser.userId(), "MANAGER_LEAVE_REQUESTED");
        long cancelled = notifications(adminUser.userId(), "MANAGER_LEAVE_CANCELLED");

        UUID id = ask(manager.token(), days[0], days[1]).id();
        delete(MY_LEAVE + "/" + id, manager.token());

        assertThat(notifications(adminUser.userId(), "MANAGER_LEAVE_REQUESTED")).isEqualTo(requested + 1);
        assertThat(notifications(adminUser.userId(), "MANAGER_LEAVE_CANCELLED")).isEqualTo(cancelled + 1);
        assertThat(notifications(manager.signed().userId(), "MANAGER_LEAVE_REQUESTED")).isZero();
    }

    @Test
    void everyStepWritesAManagerLeaveAuditEntryWithThePriorAndNewStatus() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID id = ask(manager.token(), days[0], days[1]).id();
        post(LEAVE + "/" + id + "/approve", admin, Map.of());
        post(LEAVE + "/" + id + "/revoke", admin, Map.of("reason", "Needed at work"));

        assertChangeRecorded(admin, "MANAGER_LEAVE", id, "created");
        assertChangeRecorded(admin, "MANAGER_LEAVE", id, "status");
        assertChangeRecorded(admin, "MANAGER_LEAVE", id, "decisionReason");
        String body = auditBody(admin, "MANAGER_LEAVE", id);
        assertThat(body).contains("PENDING").contains("APPROVED").contains("REVOKED").contains("Needed at work");
    }

    @Test
    void theDecisionListFiltersByStatusAndManager() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        ManagerCtx other = longJoinedManager(admin);
        LocalDate[] days = twoWorkingDays();
        UUID a = ask(manager.token(), days[0], days[0]).id();
        ask(other.token(), days[0], days[0]);
        post(LEAVE + "/" + a + "/approve", admin, Map.of());

        List<Map<String, Object>> approved = listOf(get(LEAVE + "?status=APPROVED&managerId=" + manager.managerId(), admin));
        List<Map<String, Object>> pendingOfOther = listOf(get(LEAVE + "?status=PENDING&managerId=" + other.managerId(), admin));

        assertThat(approved).hasSize(1);
        assertThat(approved.get(0)).containsEntry("id", a.toString()).containsKey("managerName");
        assertThat(pendingOfOther).hasSize(1);
        assertThat(get(LEAVE + "?status=NONSENSE", admin).status()).isEqualTo(400);
    }
}
