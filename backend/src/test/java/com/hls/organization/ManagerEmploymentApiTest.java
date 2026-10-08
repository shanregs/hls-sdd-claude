package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.DesignationTestBase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 005a US2 (T016 to T019): a Manager's designation history, employee id, joining and exit dates. */
class ManagerEmploymentApiTest extends DesignationTestBase {

    private final LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));

    @Test
    void adminSetsEmployeeIdJoiningDateAndDesignationAndEachFieldIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx manager = newManager(admin);

        Resp before = get("/api/v1/managers/" + manager.managerId(), admin);
        assertThat(before.body()).contains("DESIGNATION").contains("JOINING_DATE");

        String employeeId = uniqueEmployeeId();
        Resp saved = employment(admin, manager.managerId(), "  " + employeeId + " ", today.minusDays(30), null);
        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(saved.body()).contains("\"employeeId\":\"" + employeeId + "\"").contains(today.minusDays(30).toString());

        Resp designated = designate(admin, manager.managerId(), designation, today.minusDays(30));
        assertThat(designated.status()).as(designated.body()).isEqualTo(200);
        assertThat(designated.body()).contains(designation.toString()).doesNotContain("\"DESIGNATION\"");

        assertChangeRecorded(admin, "MANAGER", manager.managerId(), "employeeId");
        assertChangeRecorded(admin, "MANAGER", manager.managerId(), "joiningDate");
        assertChangeRecorded(admin, "MANAGER", manager.managerId(), "designation");
        // clearing the id releases it for someone else
        assertThat(employment(admin, manager.managerId(), null, today.minusDays(30), null).status()).isEqualTo(200);
        ManagerCtx other = newManager(admin);
        assertThat(employment(admin, other.managerId(), employeeId, null, null).status()).isEqualTo(200);
    }

    @Test
    void anEmployeeIdUsedByAnyoneElseIsRefusedNamingThemIgnoringCapitalsAndEndSpaces() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx first = newManager(admin);
        ManagerCtx second = newManager(admin);
        UUID teacher = teacher(admin);
        String employeeId = uniqueEmployeeId();
        assertThat(employment(admin, first.managerId(), employeeId, null, null).status()).isEqualTo(200);

        Resp duplicate = employment(admin, second.managerId(), " " + employeeId.toLowerCase() + " ", null, null);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("already used by").contains("Tester");
        assertThat(teacherEmployment(admin, teacher, null, employeeId.toLowerCase()).status()).isEqualTo(409);

        assertThat(employment(admin, second.managerId(), "has space", null, null).status()).isEqualTo(400);
        assertThat(employment(admin, second.managerId(), "x".repeat(21), null, null).status()).isEqualTo(400);
        // saving the same id again for the same person is fine
        assertThat(employment(admin, first.managerId(), employeeId, null, null).status()).isEqualTo(200);
    }

    @Test
    void joiningAndExitDatesFollowTheRules() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);

        // a joining date in the future is accepted (a planned joiner)
        assertThat(employment(admin, manager.managerId(), null, today.plusDays(10), null).status()).isEqualTo(200);
        // an exit date needs an inactive Manager
        assertThat(employment(admin, manager.managerId(), null, today.plusDays(10), today.plusDays(20)).status())
                .isEqualTo(400);

        assertThat(employment(admin, manager.managerId(), null, today.minusDays(2), null).status()).isEqualTo(200);
        post("/api/v1/identity/users/" + manager.signed().userId() + "/deactivate", admin, null);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp view = get("/api/v1/managers/" + manager.managerId(), admin);
            assertThat(view.body()).contains("\"active\":false").contains("\"exitDate\":\"" + today + "\"");
        });
        assertChangeRecorded(admin, "MANAGER", manager.managerId(), "exitDate");

        assertThat(employment(admin, manager.managerId(), null, null, today.plusDays(90)).status()).isEqualTo(200);
        assertThat(employment(admin, manager.managerId(), null, null, today.plusDays(91)).status()).isEqualTo(400);
        assertThat(employment(admin, manager.managerId(), null, today, today.minusDays(1)).status()).isEqualTo(400);
        // the joining date cannot be after the exit date
        assertThat(employment(admin, manager.managerId(), null, today.plusDays(5), today.plusDays(3)).status())
                .isEqualTo(400);

        post("/api/v1/identity/users/" + manager.signed().userId() + "/reactivate", admin, null);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp view = get("/api/v1/managers/" + manager.managerId(), admin);
            assertThat(view.body()).contains("\"active\":true").contains("\"exitDate\":null");
        });
    }

    @Test
    void aManagerInactiveBeforeThisSpecIsFlaggedAndNothingBlocksTheRecord() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        jdbc.update("update manager set active = false, exit_date = null where id = ?", manager.managerId());

        Resp view = get("/api/v1/managers/" + manager.managerId(), admin);
        assertThat(view.status()).isEqualTo(200);
        assertThat(view.body()).contains("EXIT_DATE");
        // an Admin enters the date afterwards
        assertThat(employment(admin, manager.managerId(), null, null, today).status()).isEqualTo(200);
        assertThat(get("/api/v1/managers/" + manager.managerId(), admin).body()).doesNotContain("EXIT_DATE");
    }

    @Test
    void designationDatesFollowTheRulesAndHistoryIsKept() {
        String admin = signInAs(Role.ADMIN).token();
        UUID first = designation(admin, "MANAGER");
        UUID second = designation(admin, "MANAGER");
        UUID retired = designation(admin, "MANAGER");
        UUID teacherKind = designation(admin, "TEACHER");
        ManagerCtx manager = newManager(admin);

        // no joining date: the first designation may start on any date up to today, not after
        assertThat(designate(admin, manager.managerId(), first, today.plusDays(1)).status()).isEqualTo(400);
        LocalDate longAgo = today.minusMonths(8);
        assertThat(designate(admin, manager.managerId(), first, longAgo).status()).isEqualTo(200);

        // later changes may not start before the first day of this month
        assertThat(designate(admin, manager.managerId(), second, today.withDayOfMonth(1).minusDays(1)).status())
                .isEqualTo(400);
        // wrong kind, retired, unknown, same designation
        assertThat(designate(admin, manager.managerId(), teacherKind, today).status()).isEqualTo(400);
        assertThat(designate(admin, manager.managerId(), first, today).status()).isEqualTo(400);
        assertThat(designate(admin, manager.managerId(), UUID.randomUUID(), today).status()).isEqualTo(400);
        long v = rows(get("/api/v1/designations", admin)).stream()
                .filter(r -> retired.toString().equals(r.get("id")))
                .map(r -> ((Number) r.get("version")).longValue())
                .findFirst()
                .orElseThrow();
        put("/api/v1/designations/" + retired, admin, Map.of("retired", true, "version", v));
        assertThat(designate(admin, manager.managerId(), retired, today).status()).isEqualTo(400);
        // it cannot be cleared
        assertThat(post("/api/v1/managers/" + manager.managerId() + "/designation", admin, Map.of("effectiveOn", today.toString()))
                        .status())
                .isEqualTo(400);

        assertThat(designate(admin, manager.managerId(), second, today.plusDays(10)).status()).isEqualTo(200);
        Resp view = get("/api/v1/managers/" + manager.managerId(), admin);
        assertThat(view.body()).contains(first.toString()).contains(second.toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
                (List<Map<String, Object>>) ((Map<String, Object>) view.map().get("employment")).get("history");
        assertThat(history).hasSize(2);
        // rows can never be changed or deleted, even directly in the database
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from manager_designation where manager_id = ?", manager.managerId()))
                .hasMessageContaining("never changed or deleted");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update manager_designation set effective_on = effective_on where manager_id = ?", manager.managerId()))
                .hasMessageContaining("never changed or deleted");
    }

    @Test
    void theFirstDesignationMayStartOnTheJoiningDateEvenIfEarlierThanThisMonth() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx manager = newManager(admin);
        LocalDate joining = today.minusMonths(3);
        assertThat(employment(admin, manager.managerId(), null, joining, null).status()).isEqualTo(200);

        assertThat(designate(admin, manager.managerId(), designation, joining.minusDays(1)).status()).isEqualTo(400);
        assertThat(designate(admin, manager.managerId(), designation, joining).status()).isEqualTo(200);
    }

    @Test
    void onlyAdminAndDirectorMayChangeEvenWhenManagerEditIsGrantedAndAZoneManagerStillReads() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx target = newManager(admin);
        ManagerCtx zoneManager = newManager(admin);
        assertThat(designate(director, target.managerId(), designation, today).status()).isEqualTo(200);
        assertThat(employment(director, target.managerId(), uniqueEmployeeId(), null, null).status()).isEqualTo(200);

        long version = managerVersion(admin, target.managerId());
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("employeeId", "ZZ-1");
        body.put("version", version);
        for (String token : List.of(zoneManager.token(), signInAs(Role.TEACHER).token(), signInAs(Role.SYSTEM).token())) {
            assertThat(put("/api/v1/managers/" + target.managerId() + "/employment", token, body).status()).isEqualTo(403);
            assertThat(designate(token, target.managerId(), designation, today).status()).isEqualTo(403);
        }
        assertThat(get("/api/v1/managers/" + target.managerId(), zoneManager.token()).status()).isIn(200, 403);
        assertThat(get("/api/v1/managers/" + target.managerId(), admin).body()).contains("employment");
    }

    @Test
    void aStaleVersionIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("employeeId", uniqueEmployeeId());
        body.put("version", managerVersion(admin, manager.managerId()) + 5);
        assertThat(put("/api/v1/managers/" + manager.managerId() + "/employment", admin, body).status()).isEqualTo(409);
    }
}
