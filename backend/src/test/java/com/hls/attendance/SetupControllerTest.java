package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/** Spec 008 US5: status codes and the non-working calendar, per-role authorization and audit. */
class SetupControllerTest extends AttendanceTestBase {

    private static final String CODES = "/api/v1/attendance/status-codes";
    private static final String CALENDAR = "/api/v1/attendance/calendar";

    private static String randomCode() {
        return "X" + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private Resp createCode(String token, String shortCode) {
        return post(
                CODES, token, Map.of("shortCode", shortCode, "name", "Sick", "category", "LEAVE", "weight", 0));
    }

    @Test
    void adminAndDirectorCanAddACustomCodeAndItAppearsInTheList() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            String token = signInAs(role).token();
            String code = randomCode();

            Resp created = createCode(token, code);

            assertThat(created.status()).as(created.body()).isEqualTo(201);
            assertThat(created.map()).containsEntry("shortCode", code).containsEntry("system", false);
            assertThat(get(CODES, token).body()).contains(code);
        }
    }

    @Test
    void theFourBuiltInCodesExistWithCategoriesAndWeights() {
        String token = signInAs(Role.ADMIN).token();

        String body = get(CODES, token).body();

        assertThat(body).contains("\"shortCode\":\"P\"", "\"shortCode\":\"L\"", "\"shortCode\":\"T\"", "\"shortCode\":\"N\"");
        assertThat(body).contains("\"category\":\"WORKED\"", "\"category\":\"LEAVE\"", "\"category\":\"TRAINING\"", "\"category\":\"NON_WORKING\"");
    }

    @Test
    void substitutionHolidayAndAbsentAreSeededAsOrdinaryCodes() {
        String token = signInAs(Role.ADMIN).token();

        assertThat(codeByShort(token, "S")).containsEntry("name", "Substitution").containsEntry("category", "WORKED").containsEntry("system", false);
        assertThat(codeByShort(token, "H")).containsEntry("name", "Holiday").containsEntry("category", "NON_WORKING").containsEntry("system", false);
        assertThat(codeByShort(token, "A")).containsEntry("name", "Absent").containsEntry("category", "LEAVE").containsEntry("system", false);
    }

    @Test
    void managerAndTeacherMayReadCodesButNotChangeThem() {
        for (Role role : new Role[] {Role.MANAGER, Role.TEACHER}) {
            String token = signInAs(role).token();

            assertThat(get(CODES, token).status()).as("%s list", role).isEqualTo(200);
            assertThat(createCode(token, randomCode()).status()).as("%s create", role).isEqualTo(403);
            assertThat(get(CALENDAR, token).status()).as("%s calendar", role).isEqualTo(200);
            assertThat(post(CALENDAR + "/non-working-dates", token, Map.of("onDate", "2030-01-01", "description", "x")).status())
                    .as("%s date", role)
                    .isEqualTo(403);
            assertThat(put(CALENDAR + "/default", token, Map.of("weeklyOff", List.of(), "version", 0)).status())
                    .as("%s default", role)
                    .isEqualTo(403);
        }
    }

    @Test
    void systemSeesNoAttendanceSetupAtAll() {
        String token = signInAs(Role.SYSTEM).token();

        assertThat(get(CODES, token).status()).isEqualTo(403);
        assertThat(createCode(token, randomCode()).status()).isEqualTo(403);
        // The holiday calendar is readable by everyone but only editable by Admin and Director.
        assertThat(get(CALENDAR, token).status()).isEqualTo(200);
        assertThat(post(CALENDAR + "/non-working-dates", token, Map.of("onDate", "2030-01-01", "description", "x")).status())
                .isEqualTo(403);
    }

    @Test
    void noSessionIsRefused() {
        assertThat(get(CODES, null).status()).isEqualTo(401);
        assertThat(get(CALENDAR, null).status()).isEqualTo(401);
    }

    @Test
    void aDuplicateShortCodeIgnoringCaseIsRefused() {
        String token = signInAs(Role.ADMIN).token();
        String code = randomCode();
        assertThat(createCode(token, code).status()).isEqualTo(201);

        Resp duplicate = createCode(token, code.toLowerCase());

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("already exists");
    }

    @Test
    void invalidCodeInputIsRefusedWithAReason() {
        String token = signInAs(Role.ADMIN).token();

        assertThat(post(CODES, token, Map.of("shortCode", "TOOLONGCODE", "name", "x", "category", "LEAVE", "weight", 0)).status()).isEqualTo(400);
        assertThat(post(CODES, token, Map.of("shortCode", randomCode(), "name", "x", "category", "BOGUS", "weight", 0)).status()).isEqualTo(400);
        assertThat(post(CODES, token, Map.of("shortCode", randomCode(), "name", "x", "category", "LEAVE", "weight", 2)).status()).isEqualTo(400);
        assertThat(post(CODES, token, Map.of("shortCode", randomCode(), "name", " ", "category", "LEAVE", "weight", 0)).status()).isEqualTo(400);
    }

    @Test
    void aBuiltInCodeCanBeRenamedButNotDeactivated() {
        String token = signInAs(Role.ADMIN).token();
        Map<String, Object> present = codeByShort(token, "P");
        long version = ((Number) present.get("version")).longValue();
        String id = (String) present.get("id");

        Resp deactivate = put(CODES + "/" + id, token, Map.of("name", "Present", "weight", 1, "active", false, "version", version));

        assertThat(deactivate.status()).isEqualTo(409);
        assertThat(deactivate.body()).contains("cannot be deactivated");
    }

    @Test
    void aCustomCodeCanBeDeactivatedAndLeavesTheActiveList() {
        String token = signInAs(Role.ADMIN).token();
        String code = randomCode();
        Resp created = createCode(token, code);
        UUID id = created.id();
        long version = ((Number) created.map().get("version")).longValue();

        Resp off = put(CODES + "/" + id, token, Map.of("name", "Sick", "weight", 0, "active", false, "version", version));

        assertThat(off.status()).as(off.body()).isEqualTo(200);
        assertThat(get(CODES, token).body()).doesNotContain(code);
        assertThat(get(CODES + "?activeOnly=false", token).body()).contains(code);
    }

    @Test
    void aStaleVersionIsRefused() {
        String token = signInAs(Role.ADMIN).token();
        Resp created = createCode(token, randomCode());

        Resp stale = put(CODES + "/" + created.id(), token, Map.of("name", "New", "weight", 0, "active", true, "version", 99));

        assertThat(stale.status()).isEqualTo(409);
    }

    @Test
    void nonWorkingDatesCanBeAddedOnceAndRemoved() {
        String token = signInAs(Role.ADMIN).token();
        String date = "2031-" + String.format("%02d", ThreadLocalRandom.current().nextInt(1, 13)) + "-"
                + String.format("%02d", ThreadLocalRandom.current().nextInt(1, 28));

        Resp added = post(CALENDAR + "/non-working-dates", token, Map.of("onDate", date, "description", "Declared holiday"));
        Resp duplicate = post(CALENDAR + "/non-working-dates", token, Map.of("onDate", date, "description", "Again"));
        Resp removed = delete(CALENDAR + "/non-working-dates/" + date, token);
        Resp missing = delete(CALENDAR + "/non-working-dates/" + date, token);

        assertThat(added.status()).as(added.body()).isEqualTo(201);
        assertThat(added.body()).contains(date);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.body()).doesNotContain(date);
        assertThat(missing.status()).isEqualTo(404);
    }

    @Test
    void aSchoolOverrideIsCreatedReplacedAndRemoved() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];

        Resp created = put(CALENDAR + "/schools/" + school, admin, Map.of("weeklyOff", List.of("SUN", "SAT")));
        Resp replaced = put(CALENDAR + "/schools/" + school, admin, Map.of("weeklyOff", List.of()));
        Resp unknownSchool = put(CALENDAR + "/schools/" + UUID.randomUUID(), admin, Map.of("weeklyOff", List.of("SUN")));
        Resp badDay = put(CALENDAR + "/schools/" + school, admin, Map.of("weeklyOff", List.of("FUNDAY")));
        Resp removed = delete(CALENDAR + "/schools/" + school, admin);
        Resp removedAgain = delete(CALENDAR + "/schools/" + school, admin);

        assertThat(created.status()).as(created.body()).isEqualTo(200);
        assertThat(created.body()).contains(school.toString()).contains("\"SAT\"");
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(replaced.body()).doesNotContain("\"SAT\"");
        assertThat(unknownSchool.status()).isEqualTo(404);
        assertThat(badDay.status()).isEqualTo(400);
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.body()).doesNotContain(school.toString());
        assertThat(removedAgain.status()).isEqualTo(404);
    }

    @Test
    void theDefaultWeeklyOffCanChangeButOnlyWithTheCurrentVersion() {
        String admin = signInAs(Role.ADMIN).token();
        Map<String, Object> before = get(CALENDAR, admin).map();
        long version = ((Number) before.get("defaultVersion")).longValue();
        try {
            Resp stale = put(CALENDAR + "/default", admin, Map.of("weeklyOff", List.of("SUN"), "version", version + 5));
            Resp ok = put(CALENDAR + "/default", admin, Map.of("weeklyOff", List.of("SUN", "SAT"), "version", version));

            assertThat(stale.status()).isEqualTo(409);
            assertThat(ok.status()).as(ok.body()).isEqualTo(200);
            assertThat(ok.body()).contains("\"defaultWeeklyOff\":[\"SAT\",\"SUN\"]");
        } finally {
            long current = ((Number) get(CALENDAR, admin).map().get("defaultVersion")).longValue();
            put(CALENDAR + "/default", admin, Map.of("weeklyOff", List.of("SUN"), "version", current));
        }
    }

    @Test
    void setupChangesAreRecordedInChangeHistoryAndHiddenFromSystem() {
        String admin = signInAs(Role.ADMIN).token();
        String system = signInAs(Role.SYSTEM).token();
        Resp created = createCode(admin, randomCode());

        assertChangeRecorded(admin, "ATTENDANCE_CODE", created.id(), "created");
        Resp asSystem = get("/api/v1/audit/change-history?size=100", system);
        assertThat(asSystem.status()).isEqualTo(200);
        assertThat(asSystem.body()).doesNotContain(created.id().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> codeByShort(String token, String shortCode) {
        List<Map<String, Object>> list = (List<Map<String, Object>>)
                org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(get(CODES, token).body()).stream()
                        .map(o -> (Map<String, Object>) o)
                        .filter(m -> shortCode.equals(m.get("shortCode")))
                        .toList();
        return list.get(0);
    }
}
