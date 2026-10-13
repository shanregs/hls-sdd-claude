package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 032 US5: a Manager asks to add a forgotten check-in; an Admin or Director approves or rejects it. */
class RegularizationApiTest extends ManagerAttendanceTestBase {

    private static final String REG = ME + "/regularizations";

    private static Map<String, Object> request(LocalDate date, String time, String type, String purpose, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", date.toString());
        body.put("proposedTime", time);
        body.put("dayType", type);
        body.put("purpose", purpose);
        body.put("reason", reason);
        return body;
    }

    private Resp ask(ManagerCtx manager, LocalDate date) {
        return post(REG, manager.token(), request(date, "09:15", "OFFICE", null, "Phone was off"));
    }

    @Test
    void aManagerAsksForAForgottenCheckInOnAPastDay() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);

        Resp resp = ask(manager, day);

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.map()).containsEntry("status", "PENDING").containsEntry("date", day.toString());
        List<Map<String, Object>> mine = listOf(get(REG, manager.token()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("reason", "Phone was off");
        assertChangeRecorded(admin, "MANAGER_REGULARIZATION", resp.id(), "created");
    }

    @Test
    void theRequestIsRefusedForEachInvalidCase() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        LocalDate hasCheckIn = today().minusDays(3);
        seedDay(manager.managerId(), hasCheckIn, instant(hasCheckIn, 9, 0), null);

        assertThat(post(REG, manager.token(), request(today().plusDays(1), "09:15", "OFFICE", null, "Future")).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(today(), "23:59", "OFFICE", null, "Later today")).status())
                .as("a time in the future")
                .isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day, "09:15", "OFFICE", null, " ")).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day, "09:15", "OFFICE", null, null)).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day, "09:15", "FIELD", null, "Field with no purpose")).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day, "09:15", "LEAVE", null, "Bad type")).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day, "nine", "OFFICE", null, "Bad time")).status()).isEqualTo(400);
        assertThat(post(REG, manager.token(), request(hasCheckIn, "09:15", "OFFICE", null, "Already checked in")).status())
                .isEqualTo(400);
        assertThat(post(REG, manager.token(), request(day.minusYears(2), "09:15", "OFFICE", null, "Before joining")).status())
                .isEqualTo(400);

        assertThat(ask(manager, day).status()).isEqualTo(201);
        assertThat(ask(manager, day).status()).as("a second pending request for the date").isEqualTo(400);
        assertThat(listOf(get(REG, manager.token()))).hasSize(1);
    }

    @Test
    void aLockedMonthRefusesTheRequestWith409() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        lockMonth(manager.managerId(), YearMonth.from(day));

        assertThat(ask(manager, day).status()).isEqualTo(409);
        assertThat(listOf(get(REG, manager.token()))).isEmpty();
    }

    @Test
    void anAdminApprovesAndTheDayBecomesWorkedAndAddedLater() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        UUID id = ask(manager, day).id();

        Resp resp = post(ATT + "/regularizations/" + id + "/approve", admin, Map.of());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("status", "APPROVED");
        Map<String, Object> row = jdbc.queryForMap(
                "select check_in_at, added_later, day_type from manager_day where manager_id = ? and day_date = ?",
                manager.managerId(),
                day);
        assertThat(row.get("check_in_at")).isNotNull();
        assertThat(row.get("added_later")).isEqualTo(true);
        assertThat(row.get("day_type")).isEqualTo("OFFICE");
        assertThat(historyRows(manager.managerId(), "ADDED_LATER")).isEqualTo(1);
        assertThat(notifications(manager.signed().userId(), "MANAGER_REGULARIZATION_DECIDED")).isEqualTo(1);
        assertThat(post(ATT + "/regularizations/" + id + "/approve", admin, Map.of()).status()).isEqualTo(409);
        assertChangeRecorded(admin, "MANAGER_REGULARIZATION", id, "status");
    }

    @Test
    void aDirectorRejectsWithAReasonWhichIsRequired() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        UUID id = ask(manager, day).id();

        assertThat(post(ATT + "/regularizations/" + id + "/reject", director, Map.of()).status()).isEqualTo(400);
        assertThat(post(ATT + "/regularizations/" + id + "/reject", director, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp resp = post(ATT + "/regularizations/" + id + "/reject", director, Map.of("reason", "No such visit"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("status", "REJECTED").containsEntry("decisionReason", "No such visit");
        assertThat(dayRows(manager.managerId())).isZero();
        assertThat(notifications(manager.signed().userId(), "MANAGER_REGULARIZATION_DECIDED")).isEqualTo(1);
        assertThat(ask(manager, day).status()).as("a new request after a rejection").isEqualTo(201);
    }

    @Test
    void aManagerCannotDecideAndACallerWhoIsTheRequesterCannotDecideTheirOwn() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        UUID id = ask(manager, today().minusDays(2)).id();

        assertThat(post(ATT + "/regularizations/" + id + "/approve", manager.token(), Map.of()).status()).isEqualTo(403);
        assertThat(post(ATT + "/regularizations/" + id + "/reject", manager.token(), Map.of("reason", "x")).status()).isEqualTo(403);
        assertThat(post(ATT + "/regularizations/" + id + "/approve", teacher().token(), Map.of()).status()).isEqualTo(403);
        assertThat(get(ATT + "/regularizations", manager.token()).status()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select status from manager_regularization where id = ?", String.class, id))
                .isEqualTo("PENDING");

    }

    @Test
    void aUserWhoIsBothTheManagerAndAnAdminCannotDecideTheirOwnRequest() {
        String admin = signInAs(Role.ADMIN).token();
        Signed both = signInAs(Role.MANAGER, Role.ADMIN);
        UUID managerId = post("/api/v1/managers", admin, Map.of("userId", both.userId())).id();
        setJoining(managerId, today().minusMonths(6), null);
        UUID id = post(REG, both.token(), request(today().minusDays(2), "09:15", "OFFICE", null, "Mine")).id();

        Resp self = post(ATT + "/regularizations/" + id + "/approve", both.token(), Map.of());

        assertThat(self.status()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select status from manager_regularization where id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(post(ATT + "/regularizations/" + id + "/approve", admin, Map.of()).status()).isEqualTo(200);
    }

    @Test
    void approvalIntoALockedMonthIs409AndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        LocalDate day = today().minusDays(2);
        UUID id = ask(manager, day).id();
        lockMonth(manager.managerId(), YearMonth.from(day));

        Resp resp = post(ATT + "/regularizations/" + id + "/approve", admin, Map.of());

        assertThat(resp.status()).isEqualTo(409);
        assertThat(dayRows(manager.managerId())).isZero();
        assertThat(jdbc.queryForObject("select status from manager_regularization where id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void aPendingRequestCanBeCancelledOnlyByItsManager() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        ManagerCtx other = longJoinedManager(admin);
        UUID id = ask(manager, today().minusDays(2)).id();

        assertThat(delete(REG + "/" + id, other.token()).status()).isEqualTo(404);
        assertThat(delete(REG + "/" + id, admin).status()).isEqualTo(403);
        assertThat(delete(REG + "/" + id, manager.token()).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select status from manager_regularization where id = ?", String.class, id))
                .isEqualTo("CANCELLED");
        assertThat(delete(REG + "/" + id, manager.token()).status()).isEqualTo(409);
        assertThat(post(ATT + "/regularizations/" + id + "/approve", admin, Map.of()).status()).isEqualTo(409);
        assertChangeRecorded(admin, "MANAGER_REGULARIZATION", id, "status");
    }

    @Test
    void theAdminListFiltersByStatusAndManager() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        ManagerCtx manager = longJoinedManager(admin);
        UUID id = ask(manager, today().minusDays(2)).id();

        List<Map<String, Object>> pending =
                listOf(get(ATT + "/regularizations?status=PENDING&managerId=" + manager.managerId(), director));
        List<Map<String, Object>> approved =
                listOf(get(ATT + "/regularizations?status=APPROVED&managerId=" + manager.managerId(), admin));

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0)).containsEntry("id", id.toString());
        assertThat(pending.get(0).get("managerName")).isNotNull();
        assertThat(approved).isEmpty();
    }

    @Test
    void theAdminsAreToldOfARequest() {
        Signed admin = signInAs(Role.ADMIN);
        ManagerCtx manager = longJoinedManager(admin.token());
        long before = notifications(admin.userId(), "MANAGER_REGULARIZATION_REQUESTED");

        ask(manager, today().minusDays(2));

        assertThat(notifications(admin.userId(), "MANAGER_REGULARIZATION_REQUESTED")).isEqualTo(before + 1);
    }
}
