package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 9 (FR-017..FR-019): append-only dated salary, "as of" lookups, strict access. */
class SalaryHistoryTest extends MasterDataTestBase {

    @Test
    void currentAndAsOfFollowTheDatedHistoryAndEveryRowIsKept() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        String path = "/api/v1/teachers/" + teacher + "/salary";
        assertThat(post(path, admin, Map.of("amount", 20000, "effectiveOn", "2026-04-01")).status())
                .isEqualTo(201);
        assertThat(post(path, admin, Map.of("amount", 22000.50, "effectiveOn", "2026-10-01")).status())
                .isEqualTo(201);

        Resp history = get(path, admin);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("20000.00", "22000.50");
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) history.map().get("current");
        assertThat(current).containsEntry("effectiveOn", "2026-10-01");

        assertThat(get(path + "?asOf=2026-06-15", admin).map()).containsEntry("amount", 20000.0);
        assertThat(get(path + "?asOf=2026-10-15", admin).map()).containsEntry("amount", 22000.5);
        assertThat(get(path + "?asOf=2026-10-01", admin).map()).containsEntry("amount", 22000.5);
        assertThat(get(path + "?asOf=2026-01-01", admin).map()).containsEntry("amount", null);
    }

    @Test
    void aCorrectionIsANewRowAndNothingCanBeEditedOrDeleted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        String path = "/api/v1/teachers/" + teacher + "/salary";
        post(path, admin, Map.of("amount", 18000, "effectiveOn", "2026-04-01"));
        post(path, admin, Map.of("amount", 19000, "effectiveOn", "2026-04-01")); // correction, same day

        Resp history = get(path, admin);
        assertThat(history.body()).contains("18000.00", "19000.00");
        assertThat(get(path + "?asOf=2026-04-01", admin).map()).containsEntry("amount", 19000.0);
        assertThat(put(path, admin, Map.of("amount", 1, "effectiveOn", "2026-04-01")).status()).isEqualTo(405);
        assertThat(delete(path, admin).status()).isEqualTo(405);
    }

    @Test
    void invalidAmountsAndDatesAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        String path = "/api/v1/teachers/" + teacher + "/salary";

        assertThat(post(path, admin, Map.of("amount", -1, "effectiveOn", "2026-04-01")).status())
                .isEqualTo(400);
        assertThat(post(path, admin, Map.of("amount", 1000)).status()).isEqualTo(400);
        assertThat(post("/api/v1/teachers/" + UUID.randomUUID() + "/salary", admin, Map.of("amount", 1, "effectiveOn", "2026-04-01"))
                        .status())
                .isEqualTo(404);
    }

    @Test
    void directorMayRecordAndViewButManagerTeacherAndSystemAreRefusedOnEverySalaryEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID teacher = teacher(admin);
        String path = "/api/v1/teachers/" + teacher + "/salary";
        assertThat(post(path, director, Map.of("amount", 15000, "effectiveOn", "2026-04-01")).status())
                .isEqualTo(201);
        assertThat(get(path, director).status()).isEqualTo(200);

        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER, Role.SYSTEM}) {
            String token = signInAs(role).token();
            assertThat(get(path, token).status()).as("GET as %s", role).isEqualTo(403);
            assertThat(get(path + "?asOf=2026-06-01", token).status()).isEqualTo(403);
            assertThat(post(path, token, Map.of("amount", 1, "effectiveOn", "2026-04-01")).status())
                    .isEqualTo(403);
        }
        assertThat(get(path, null).status()).isEqualTo(401);
    }

    @Test
    void noOtherTeacherOrSchoolResponseCarriesSalaryAndOnlyAdminSeesTheAuditEntry() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] school = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, school[0]);
        assignSchoolManager(admin, school[2], manager.managerId());
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, school[2]);
        post("/api/v1/teachers/" + teacher + "/salary", admin, Map.of("amount", 31000, "effectiveOn", "2026-04-01"));

        for (String body : new String[] {
            get("/api/v1/teachers/" + teacher, admin).body(),
            get("/api/v1/teachers?size=100", admin).body(),
            get("/api/v1/teachers/" + teacher, manager.token()).body(),
            get("/api/v1/teachers?size=100", manager.token()).body(),
            get("/api/v1/schools/" + school[2], manager.token()).body(),
            get("/api/v1/managers/" + manager.managerId(), admin).body()
        }) {
            assertThat(body).doesNotContainIgnoringCase("salary").doesNotContain("31000");
        }

        assertChangeRecorded(admin, "TEACHER_SALARY", teacher, "salary");
        String system = signInAs(Role.SYSTEM).token();
        assertThat(get("/api/v1/audit/change-history?entityType=TEACHER_SALARY&size=100", system).body())
                .doesNotContain(teacher.toString());
    }

    @Test
    void aManagerWhoWasGrantedSalaryViewStillOnlyReachesTeachersInScope() {
        // salary access is a runtime-editable grant; even then the Teacher scope applies (404 outside it)
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        ManagerCtx manager = newManager(admin, zone(admin));
        put("/api/v1/identity/permission-matrix/MANAGER/TEACHER_SALARY/VIEW", admin, Map.of("granted", true));
        try {
            assertThat(get("/api/v1/teachers/" + teacher + "/salary", manager.token()).status()).isEqualTo(404);
        } finally {
            put("/api/v1/identity/permission-matrix/MANAGER/TEACHER_SALARY/VIEW", admin, Map.of("granted", false));
        }
    }
}
