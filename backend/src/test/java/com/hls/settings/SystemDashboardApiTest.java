package com.hls.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 011 T017: the System dashboard per role, its counts, and what the audit feed may show. */
class SystemDashboardApiTest extends IntegrationTestBase {

    private static final String DASHBOARD = "/api/v1/system/dashboard";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String name) {
        return (Map<String, Object>) resp.map().get(name);
    }

    @Test
    void onlySystemReadsItByDefault() {
        assertThat(get(DASHBOARD, signInAs(Role.SYSTEM).token()).status()).isEqualTo(200);
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            assertThat(get(DASHBOARD, signInAs(role).token()).status()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(DASHBOARD, null).status()).isEqualTo(401);
    }

    @Test
    void healthShowsTheDatabaseAndTheLatestMigration() {
        Resp dashboard = get(DASHBOARD, signInAs(Role.SYSTEM).token());
        Map<String, Object> health = section(dashboard, "health");
        assertThat(health).containsEntry("database", "UP");
        assertThat(Integer.parseInt((String) health.get("migrationVersion"))).isGreaterThanOrEqualTo(40);
        assertThat(health.get("startedAt")).isNotNull();
        assertThat(((Number) health.get("uptimeSeconds")).longValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void userCountsFollowTheAccounts() {
        String token = signInAs(Role.SYSTEM).token();
        Map<String, Object> before = section(get(DASHBOARD, token), "users");
        Signed teacher = signInAs(Role.TEACHER);
        Signed inactive = signInAs(Role.TEACHER);
        userAdminService.deactivateUser(teacher.userId(), inactive.userId());
        for (int i = 0; i < 5; i++) {
            loginStatus(teacher.phone(), "wrong-" + i);
        }

        Map<String, Object> after = section(get(DASHBOARD, token), "users");
        assertThat(num(after, "total") - num(before, "total")).isEqualTo(2);
        assertThat(num(after, "inactive") - num(before, "inactive")).isEqualTo(1);
        assertThat(num(after, "lockedNow") - num(before, "lockedNow")).isEqualTo(1);
        assertThat(num(after, "active") + num(after, "inactive")).isEqualTo(num(after, "total"));
        Map<String, Object> byRole = (Map<String, Object>) after.get("byRole");
        assertThat(byRole).containsKeys("ADMIN", "DIRECTOR", "MANAGER", "TEACHER", "SYSTEM");
        assertThat(((Number) byRole.get("TEACHER")).longValue())
                .isGreaterThanOrEqualTo(((Number) ((Map<String, Object>) before.get("byRole")).get("TEACHER")).longValue() + 2);
    }

    private static long num(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void recentAuditShowsNewestFirstWithAtMostTenAndCountsFailedSignIns() {
        String token = signInAs(Role.SYSTEM).token();
        long failedBefore = ((Number) get(DASHBOARD, token).map().get("failedSignInsLast24h")).longValue();
        Signed user = signInAs(Role.TEACHER);
        loginStatus(user.phone(), "wrong-once");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp dashboard = get(DASHBOARD, token);
            assertThat(((Number) dashboard.map().get("failedSignInsLast24h")).longValue()).isGreaterThan(failedBefore);
            Map<String, Object> audit = section(dashboard, "recentAudit");
            assertThat(audit).containsEntry("status", "OK");
            List<Map<String, Object>> events = (List<Map<String, Object>>) audit.get("events");
            assertThat(events).isNotEmpty().hasSizeLessThanOrEqualTo(10);
            List<String> times = events.stream().map(e -> (String) e.get("at")).toList();
            assertThat(times).isSortedAccordingTo(java.util.Comparator.reverseOrder());
            assertThat(events).anyMatch(e -> "LOGIN".equals(e.get("kind")));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void businessChangesAreNotShownToSystem() {
        String admin = signInAs(Role.ADMIN).token();
        String system = signInAs(Role.SYSTEM).token();
        String zoneName = "Hidden-zone-" + System.nanoTime();
        assertThat(post("/api/v1/zones", admin, Map.of("name", zoneName)).status()).isEqualTo(201);
        // a setting change, which System may see, written after the zone
        assertThat(put("/api/v1/settings/system", system, Map.of("values", Map.of("mobile.location-reuse-seconds", "11")))
                        .status())
                .isEqualTo(200);
        try {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Resp dashboard = get(DASHBOARD, system);
                List<Map<String, Object>> events =
                        (List<Map<String, Object>>) section(dashboard, "recentAudit").get("events");
                assertThat(events).anyMatch(e -> ((String) e.get("summary")).contains("SYSTEM_SETTING"));
                assertThat(events).noneMatch(e -> ((String) e.get("summary")).startsWith("ZONE "));
            });
        } finally {
            put("/api/v1/settings/system", system, Map.of("values", Map.of("mobile.location-reuse-seconds", "10")));
        }
    }
}
