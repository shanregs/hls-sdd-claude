package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 032: the cut-off and late time, how they are read and changed. */
class ManagerAttendanceSettingsTest extends ManagerAttendanceTestBase {

    private static final String SETTINGS = ATT + "/settings";

    private static Map<String, Object> body(String cutoff, String late, Object version) {
        Map<String, Object> body = new HashMap<>();
        body.put("missedCutoff", cutoff);
        body.put("lateAfter", late);
        body.put("version", version);
        return body;
    }

    private long version(String token) {
        return ((Number) get(SETTINGS, token).map().get("version")).longValue();
    }

    @Test
    void theDefaultsAreReadAsHoursAndMinutes() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = get(SETTINGS, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map()).containsEntry("missedCutoff", "10:30").containsEntry("lateAfter", "09:30");
        assertThat(resp.map()).containsKey("version");
    }

    @Test
    void anAdminAndADirectorChangeThemAndEachChangeIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();

        Resp byAdmin = put(SETTINGS, admin, body("11:00", "09:45", version(admin)));
        Resp byDirector = put(SETTINGS, director, body("10:30", "09:30", version(director)));

        assertThat(byAdmin.status()).as(byAdmin.body()).isEqualTo(200);
        assertThat(byAdmin.map()).containsEntry("missedCutoff", "11:00").containsEntry("lateAfter", "09:45");
        assertThat(byDirector.status()).as(byDirector.body()).isEqualTo(200);
        assertThat(get(SETTINGS, admin).map()).containsEntry("missedCutoff", "10:30");
        assertChangeRecorded(admin, "MANAGER_ATTENDANCE_SETTING", 1, "missedCutoff");
        String audit = auditBody(admin, "MANAGER_ATTENDANCE_SETTING", 1);
        assertThat(audit).contains("11:00").contains("10:30").contains("09:45");
    }

    @Test
    void theLateTimeMustBeBeforeTheCutOffAndTheTimesMustBeValid() {
        String admin = signInAs(Role.ADMIN).token();
        long version = version(admin);

        assertThat(put(SETTINGS, admin, body("09:30", "09:30", version)).status()).as("equal").isEqualTo(400);
        assertThat(put(SETTINGS, admin, body("09:00", "09:30", version)).status()).as("reversed").isEqualTo(400);
        assertThat(put(SETTINGS, admin, body("late", "09:30", version)).status()).as("bad cut-off").isEqualTo(400);
        assertThat(put(SETTINGS, admin, body("10:30", null, version)).status()).as("no late time").isEqualTo(400);
        assertThat(put(SETTINGS, admin, body("10:30", "09:30", null)).status()).as("no version").isEqualTo(400);
        assertThat(get(SETTINGS, admin).map()).containsEntry("missedCutoff", "10:30").containsEntry("lateAfter", "09:30");
    }

    @Test
    void aStaleVersionIs409AndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        long version = version(admin);
        assertThat(put(SETTINGS, admin, body("11:00", "09:30", version)).status()).isEqualTo(200);

        Resp stale = put(SETTINGS, admin, body("12:00", "09:30", version));

        assertThat(stale.status()).as(stale.body()).isEqualTo(409);
        assertThat(get(SETTINGS, admin).map()).containsEntry("missedCutoff", "11:00");
    }

    @Test
    void aManagerATeacherAndASystemUserGet403() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        long version = version(admin);
        Map<String, String> tokens = Map.of(
                "manager", manager.token(),
                "teacher", teacher().token(),
                "system", signInAs(Role.SYSTEM).token());

        tokens.forEach((who, token) -> {
            assertThat(get(SETTINGS, token).status()).as(who + " read").isEqualTo(403);
            assertThat(put(SETTINGS, token, body("11:00", "09:30", version)).status()).as(who + " change").isEqualTo(403);
        });
        assertThat(get(SETTINGS, admin).map()).containsEntry("missedCutoff", "10:30");
    }
}
