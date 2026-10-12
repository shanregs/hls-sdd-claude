package com.hls.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec 011 T010, T014, T019: the settings endpoints per role, validation, the effect of an edit on the next decision,
 * and the audit trail. Settings are global, so each test puts back what it changed.
 */
class SettingsApiTest extends IntegrationTestBase {

    private static final String SECURITY = "/api/v1/settings/security";
    private static final String SYSTEM = "/api/v1/settings/system";

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restoreDefaults() {
        jdbc.update("delete from system_setting where updated_by is not null");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> setting(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("settings"))
                .stream().filter(s -> key.equals(s.get("key"))).findFirst().orElseThrow();
    }

    private Resp save(String token, String group, Map<String, String> values) {
        return put("/api/v1/settings/" + group, token, Map.of("values", values));
    }

    // ------------------------------------------------------------------ per role

    @Test
    void systemAndAdminReadAndEditBothGroups() {
        for (Role role : List.of(Role.SYSTEM, Role.ADMIN)) {
            String token = signInAs(role).token();
            assertThat(get(SECURITY, token).status()).as(role + " security").isEqualTo(200);
            assertThat(get(SYSTEM, token).status()).as(role + " system").isEqualTo(200);
            assertThat(save(token, "security", Map.of("password.min-length", "10")).status()).isEqualTo(200);
        }
    }

    @Test
    void teacherManagerAndDirectorAreRefusedByDefault() {
        for (Role role : List.of(Role.TEACHER, Role.MANAGER, Role.DIRECTOR)) {
            String token = signInAs(role).token();
            assertThat(get(SECURITY, token).status()).as(role + " get security").isEqualTo(403);
            assertThat(get(SYSTEM, token).status()).as(role + " get system").isEqualTo(403);
            assertThat(save(token, "security", Map.of("password.min-length", "12")).status())
                    .as(role + " put security")
                    .isEqualTo(403);
            assertThat(save(token, "system", Map.of("mobile.location-wait-seconds", "5")).status())
                    .as(role + " put system")
                    .isEqualTo(403);
        }
    }

    @Test
    void noTokenIs401AndAnUnknownGroupIs404() {
        assertThat(get(SECURITY, null).status()).isEqualTo(401);
        assertThat(get("/api/v1/settings/business", signInAs(Role.SYSTEM).token()).status()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ reading

    @Test
    void theScreenListsEverySettingWithItsRangeAndDefault() {
        String token = signInAs(Role.SYSTEM).token();
        Resp security = get(SECURITY, token);
        assertThat((List<?>) security.map().get("settings")).hasSize(8);
        Map<String, Object> lockout = setting(security, "lockout.max-failed-attempts");
        assertThat(lockout).containsEntry("default", "5").containsEntry("min", 3).containsEntry("max", 20);
        assertThat((List<?>) get(SYSTEM, token).map().get("settings")).hasSize(3);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void anyBadValueStoresNothingAndNamesEachBadField() {
        String token = signInAs(Role.SYSTEM).token();
        Resp bad = save(token, "security", Map.of("password.min-length", "12", "lockout.max-failed-attempts", "1"));
        assertThat(bad.status()).isEqualTo(422);
        assertThat(bad.body()).contains("lockout.max-failed-attempts");
        assertThat(setting(get(SECURITY, token), "password.min-length")).containsEntry("value", "10");

        assertThat(save(token, "security", Map.of("password.min-length", "abc")).status()).isEqualTo(422);
        assertThat(save(token, "security", Map.of("password.min-length", "7")).status()).isEqualTo(422);
        assertThat(save(token, "security", Map.of("password.min-length", "65")).status()).isEqualTo(422);
        assertThat(save(token, "security", Map.of("mobile.location-wait-seconds", "5")).status())
                .as("a system key sent to the security group")
                .isEqualTo(422);
        assertThat(save(token, "system", Map.of("mobile.min-app-version", "2")).status()).isEqualTo(422);
        assertThat(put(SECURITY, token, Map.of("values", Map.of())).status()).isEqualTo(422);
    }

    // ------------------------------------------------------------------ effect of an edit

    @Test
    void theLockoutThresholdFollowsAnEdit() {
        String admin = signInAs(Role.SYSTEM).token();
        assertThat(save(admin, "security", Map.of("lockout.max-failed-attempts", "3")).status()).isEqualTo(200);
        Signed user = signInAs(Role.TEACHER);
        assertThat(loginStatus(user.phone(), "wrong-1")).isEqualTo(401);
        assertThat(loginStatus(user.phone(), "wrong-2")).isEqualTo(401);
        assertThat(loginStatus(user.phone(), "wrong-3")).isEqualTo(423);
        assertThat(loginStatus(user.phone(), PASSWORD)).as("still locked").isEqualTo(423);
    }

    @Test
    void thePasswordMinimumFollowsAnEdit() {
        String admin = signInAs(Role.ADMIN).token();
        Signed target = signInAs(Role.TEACHER);
        String path = "/api/v1/identity/users/" + target.userId() + "/reset-password";

        assertThat(save(admin, "security", Map.of("password.min-length", "14")).status()).isEqualTo(200);
        Resp refused = post(path, admin, Map.of("newPassword", "twelve-chars!"));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("at least 14 characters");
        assertThat(post(path, admin, Map.of("newPassword", "fourteen-chars!")).status()).isEqualTo(204);
    }

    @Test
    void theMobileSettingsFeedThePublicAppConfigAndTheVersionGate() {
        String token = signInAs(Role.SYSTEM).token();
        assertThat(save(token, "system", Map.of(
                        "mobile.min-app-version", "9.0.0",
                        "mobile.location-wait-seconds", "7",
                        "mobile.location-reuse-seconds", "20"))
                .status()).isEqualTo(200);

        var config = client.get().uri("/api/v1/mobile/app-config").exchange().returnResult(String.class);
        assertThat(config.getResponseBody())
                .contains("\"minimumVersion\":\"9.0.0\"")
                .contains("\"locationWaitSeconds\":7")
                .contains("\"locationReuseSeconds\":20");
        assertThat(client.get().uri("/api/v1/me/access-model").header("X-HLS-Client", "android/1.0.0")
                        .exchange().returnResult(String.class).getStatus().value())
                .isEqualTo(426);

        assertThat(save(token, "system", Map.of("mobile.min-app-version", "0.0.0")).status()).isEqualTo(200);
        assertThat(client.get().uri("/api/v1/me/access-model").header("X-HLS-Client", "android/1.0.0")
                        .exchange().returnResult(String.class).getStatus().value())
                .isEqualTo(401);
    }

    @Test
    void anUneditedValueFallsBackAndIsMarkedAsTheDefault() {
        String token = signInAs(Role.SYSTEM).token();
        assertThat(setting(get(SYSTEM, token), "mobile.location-wait-seconds"))
                .containsEntry("value", "4")
                .containsEntry("isDefault", true);
        save(token, "system", Map.of("mobile.location-wait-seconds", "9"));
        Map<String, Object> edited = setting(get(SYSTEM, token), "mobile.location-wait-seconds");
        assertThat(edited).containsEntry("value", "9").containsEntry("isDefault", false);
        assertThat(edited.get("updatedBy")).isNotNull();
    }

    @Test
    void theOtpValuesOfTheOldTableWereCarriedOver() {
        String token = signInAs(Role.SYSTEM).token();
        Resp security = get(SECURITY, token);
        assertThat(setting(security, "otp.resend-cooldown-seconds")).containsEntry("value", "30");
        assertThat(setting(security, "otp.max-consecutive-requests")).containsEntry("value", "5");
        assertThat(setting(security, "otp.lockout-hours")).containsEntry("value", "4");
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_name = 'otp_policy_settings'",
                        Integer.class))
                .isZero();
    }

    // ------------------------------------------------------------------ audit

    @Test
    void eachChangedValueIsAuditedAndAnUnchangedOneIsNot() {
        Signed system = signInAs(Role.SYSTEM);
        Resp saved = save(system.token(), "security", Map.of(
                "lockout.duration-minutes", "45",
                "otp.lockout-hours", "6",
                "password.min-length", "10"));
        assertThat(saved.status()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp history = get("/api/v1/audit/change-history?entityType=SECURITY_SETTING&size=100", system.token());
            assertThat(history.status()).isEqualTo(200);
            assertThat(history.body())
                    .contains("lockout.duration-minutes")
                    .contains("\"afterValue\":\"45\"")
                    .contains("\"beforeValue\":\"30\"")
                    .contains("otp.lockout-hours")
                    .doesNotContain("password.min-length");
        });
    }

    @Test
    void aRoleWithoutViewDoesNotSeeSettingChangesInChangeHistory() {
        Signed system = signInAs(Role.SYSTEM);
        save(system.token(), "system", Map.of("mobile.location-reuse-seconds", "33"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                        get("/api/v1/audit/change-history?entityType=SYSTEM_SETTING&size=100", system.token()).body())
                .contains("mobile.location-reuse-seconds"));

        // Director holds Change History View but no System Settings View, so the entries are hidden from them.
        String director = signInAs(Role.DIRECTOR).token();
        Resp directorView = get("/api/v1/audit/change-history?size=100", director);
        if (directorView.status() == 200) {
            assertThat(directorView.body()).doesNotContain("mobile.location-reuse-seconds");
        } else {
            assertThat(directorView.status()).isEqualTo(403);
        }
    }
}
