package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 037 US4: every change is audited and recorded; a row's history and the Deleted rows view are readable. */
class WorkingDaysHistoryTest extends WorkingDaysTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) (List<?>) org.springframework.boot.json.JsonParserFactory.getJsonParser()
                .parseList(resp.body());
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    @Test
    void createEditAndDeleteLeaveThreeHistoryRowsAndThreeAuditEntriesWithTheRightValues() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear();
        Resp created = createRow(admin, school, year, 3, 26);
        Resp edited = editRow(admin, created.id(), 25.5, versionOf(created));
        assertThat(deleteRow(admin, created.id(), versionOf(edited)).status()).isEqualTo(204);

        List<Map<String, Object>> entries = list(get(BASE + "/history?schoolId=" + school + "&year=" + year + "&month=3", admin));
        assertThat(entries).extracting(e -> e.get("action")).containsExactly("DELETE", "EDIT", "CREATE");
        assertThat(entries.get(0).get("newValue")).isNull();
        assertThat(number(entries.get(0).get("oldValue"))).isEqualTo(25.5);
        assertThat(number(entries.get(1).get("oldValue"))).isEqualTo(26.0);
        assertThat(number(entries.get(1).get("newValue"))).isEqualTo(25.5);
        assertThat(entries.get(2).get("oldValue")).isNull();
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.get("actorRoles")).isEqualTo("ADMIN");
            assertThat(e.get("actorName")).isNotNull();
            assertThat(e.get("occurredAt")).isNotNull();
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(auditCount(created.id())).isEqualTo(3));
        Resp audit = get("/api/v1/audit/change-history?entityType=SCHOOL_WORKING_DAYS&entityId=" + created.id() + "&size=100", admin);
        assertThat(audit.body()).contains("\"field\":\"created\"").contains("\"field\":\"workingDays\"").contains("\"field\":\"deleted\"");
        assertThat(audit.body()).contains("25.5");
    }

    @Test
    void aRefusedChangeWritesNoHistoryAndNoAuditEntry() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp created = createRow(admin, school, thisYear(), 3, 26);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(auditCount(created.id())).isEqualTo(1));

        assertThat(editRow(admin, created.id(), 99, versionOf(created)).status()).isEqualTo(400);
        assertThat(editRow(admin, created.id(), 20, versionOf(created) + 5).status()).isEqualTo(409);
        assertThat(createRow(admin, school, thisYear(), 3, 20).status()).isEqualTo(409);
        assertThat(historyCount(school)).isEqualTo(1);
        assertThat(auditCount(created.id())).isEqualTo(1);
    }

    @Test
    void theRowHistoryIsNewestFirstAndAGoneRowIsReportedAsDeleted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp created = createRow(admin, school, thisYear(), 4, 20);
        Resp edited = editRow(admin, created.id(), 21, versionOf(created));

        List<Map<String, Object>> entries = list(get(BASE + "/" + created.id() + "/history", admin));
        assertThat(entries).extracting(e -> e.get("action")).containsExactly("EDIT", "CREATE");

        deleteRow(admin, created.id(), versionOf(edited));
        Resp gone = get(BASE + "/" + created.id() + "/history", admin);
        assertThat(gone.status()).isEqualTo(404);
        assertThat(gone.map()).containsEntry("code", "ROW_DELETED");
        assertThat(get(BASE + "/history?schoolId=" + school + "&year=" + thisYear() + "&month=4", admin).status())
                .isEqualTo(200);
    }

    @Test
    void theDeletedRowsViewListsEachDeletionOfTheSchoolAndYearOnly() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        UUID other = schoolInNewZone(admin)[2];
        int year = thisYear();
        Resp march = createRow(admin, school, year, 3, 26);
        Resp june = createRow(admin, school, year, 6, 22.5);
        Resp nextYear = createRow(admin, school, year + 1, 3, 20);
        Resp otherSchool = createRow(admin, other, year, 3, 10);
        createRowOk(admin, school, year, 9, 18);
        deleteRow(admin, march.id(), versionOf(march));
        deleteRow(admin, june.id(), versionOf(june));
        deleteRow(admin, nextYear.id(), versionOf(nextYear));
        deleteRow(admin, otherSchool.id(), versionOf(otherSchool));

        List<Map<String, Object>> deleted = list(get(BASE + "/deleted?schoolId=" + school + "&year=" + year, admin));
        assertThat(deleted).extracting(e -> e.get("month")).containsExactly(6, 3);
        assertThat(number(deleted.get(0).get("lastValue"))).isEqualTo(22.5);
        assertThat(deleted.get(0).get("deletedByName")).isNotNull();
        assertThat(deleted.get(0).get("deletedAt")).isNotNull();

        // a month deleted and then recreated is still listed as a past deletion
        createRowOk(admin, school, year, 3, 25);
        assertThat(list(get(BASE + "/deleted?schoolId=" + school + "&year=" + year, admin))).hasSize(2);
        assertThat(list(get(BASE + "/deleted?schoolId=" + school + "&year=" + (year + 2), admin))).isEmpty();
    }

    @Test
    void historyIsForUsersWithViewOnlyAndInsideTheirScope() {
        World w = newWorld();
        Resp rowA = createRow(w.admin(), w.schoolA(), thisYear(), 3, 26);
        createRowOk(w.admin(), w.schoolB(), thisYear(), 3, 24);

        String historyA = BASE + "/history?schoolId=" + w.schoolA() + "&year=" + thisYear() + "&month=3";
        String historyB = BASE + "/history?schoolId=" + w.schoolB() + "&year=" + thisYear() + "&month=3";
        assertThat(get(historyA, w.director()).status()).isEqualTo(200);
        assertThat(get(BASE + "/" + rowA.id() + "/history", w.director()).status()).isEqualTo(200);
        assertThat(get(BASE + "/deleted?schoolId=" + w.schoolA() + "&year=" + thisYear(), w.director()).status())
                .isEqualTo(200);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(historyA, token).status()).as(role.name()).isEqualTo(403);
            assertThat(get(BASE + "/deleted?schoolId=" + w.schoolA() + "&year=" + thisYear(), token).status())
                    .as(role.name())
                    .isEqualTo(403);
        }
        assertThat(get(historyA, w.managerA().token()).status()).isEqualTo(403);

        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor);
        try {
            assertThat(get(historyA, w.managerA().token()).status()).isEqualTo(200);
            assertThat(get(historyB, w.managerA().token()).status()).isEqualTo(404);
            assertThat(get(BASE + "/deleted?schoolId=" + w.schoolB() + "&year=" + thisYear(), w.managerA().token()).status())
                    .isEqualTo(404);
        } finally {
            matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, false, actor);
        }
        assertThat(get(BASE + "/history?schoolId=" + UUID.randomUUID() + "&year=" + thisYear() + "&month=3", w.admin()).status())
                .isEqualTo(404);
    }

    @Test
    void theAuditTrailStillRefusesToChangeAnEntry() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp created = createRow(admin, school, thisYear(), 5, 20);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(auditCount(created.id())).isEqualTo(1));
        // the audit API of spec 003 has no way to change or remove an entry
        assertThat(put("/api/v1/audit/change-history/" + created.id(), admin, Map.of("afterValue", "x")).status())
                .isIn(404, 405);
        assertThat(delete("/api/v1/audit/change-history/" + created.id(), admin).status()).isIn(404, 405);
    }
}
