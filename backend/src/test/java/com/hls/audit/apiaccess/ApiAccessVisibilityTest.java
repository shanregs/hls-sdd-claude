package com.hls.audit.apiaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T091 (US4, FR-028, SC-009): the API Access screen's endpoints are for Admin and System
 * only, the filters work, bad input is a 400, and the list stays responsive with 10,000 rows.
 */
class ApiAccessVisibilityTest extends MobileTestBase {

    private static final String FROM = Instant.now().minus(Duration.ofDays(1)).toString();
    private static final String TO = Instant.now().plus(Duration.ofDays(1)).toString();

    private Raw call(String token, String path) {
        return getRaw(path, token, null, Map.of());
    }

    private void insertRow(UUID userId, String method, String route, int status, String locationStatus) {
        boolean available = "AVAILABLE".equals(locationStatus);
        jdbc.update(
                "INSERT INTO api_access_entry (id, occurred_at, source_event_id, user_id, http_method, route_template,"
                        + " status_code, source, app_version, location_status, latitude, longitude, accuracy_meters,"
                        + " location_captured_at) VALUES (?, now(), ?, ?, ?, ?, ?, 'ANDROID', '1.0.0', ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                method,
                route,
                status,
                locationStatus,
                available ? 12.5 : null,
                available ? 77.5 : null,
                available ? 5.0f : null,
                available ? Timestamp.from(Instant.now()) : null);
    }

    @Test
    void adminAndSystemCanListAndExportButOthersGet403() {
        for (Role role : List.of(Role.ADMIN, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(call(token, "/api/v1/audit/api-access").status()).as(role.name()).isEqualTo(200);
            Raw export = call(token, "/api/v1/audit/api-access/export?from=" + FROM + "&to=" + TO);
            assertThat(export.status()).as(role.name()).isEqualTo(200);
            assertThat(export.body()).startsWith("occurredAt,userId,sessionId,httpMethod,routeTemplate,statusCode,source");
        }
        for (Role role : List.of(Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            String token = signInAs(role).token();
            assertThat(call(token, "/api/v1/audit/api-access").status()).as(role.name()).isEqualTo(403);
            assertThat(call(token, "/api/v1/audit/api-access/export?from=" + FROM + "&to=" + TO).status())
                    .as(role.name())
                    .isEqualTo(403);
        }
        assertThat(call(null, "/api/v1/audit/api-access").status()).isEqualTo(401);
    }

    @Test
    void filtersByUserMethodAndLocationStatus() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        insertRow(userA, "GET", "/api/v1/me/profile", 200, "AVAILABLE");
        insertRow(userA, "PUT", "/api/v1/me/profile", 200, "NO_FIX");
        insertRow(userB, "GET", "/api/v1/me/sessions", 200, "AVAILABLE");
        String token = signInAs(Role.ADMIN).token();

        Raw forA = call(token, "/api/v1/audit/api-access?size=100&userId=" + userA);
        Raw puts = call(token, "/api/v1/audit/api-access?size=100&userId=" + userA + "&httpMethod=put");
        Raw noFix = call(token, "/api/v1/audit/api-access?size=100&userId=" + userA + "&locationStatus=NO_FIX");
        Raw available = call(token, "/api/v1/audit/api-access?size=100&userId=" + userB + "&locationStatus=AVAILABLE");

        assertThat(forA.map().get("totalElements")).isEqualTo(2);
        assertThat(puts.map().get("totalElements")).isEqualTo(1);
        assertThat(noFix.map().get("totalElements")).isEqualTo(1);
        assertThat(noFix.body()).contains("\"status\":\"NO_FIX\"").doesNotContain("\"latitude\":12.5");
        assertThat(available.map().get("totalElements")).isEqualTo(1);
        assertThat(available.body()).contains("\"routeTemplate\":\"/api/v1/me/sessions\"").contains("12.5");
    }

    @Test
    void dateRangeFilterAndBadInputIsA400() {
        String token = signInAs(Role.ADMIN).token();

        assertThat(call(token, "/api/v1/audit/api-access?from=" + TO + "&to=" + FROM).status()).isEqualTo(400);
        assertThat(call(token, "/api/v1/audit/api-access?locationStatus=ELSEWHERE").status()).isEqualTo(400);
        assertThat(call(token, "/api/v1/audit/api-access/export?from=" + TO + "&to=" + FROM).status()).isEqualTo(400);
        assertThat(call(token, "/api/v1/audit/api-access?from=" + Instant.now().plus(Duration.ofDays(3))).status())
                .isEqualTo(200);
    }

    @Test
    void theListStaysResponsiveWithTenThousandRows() {
        UUID user = UUID.randomUUID();
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            batch.add(new Object[] {
                UUID.randomUUID(), UUID.randomUUID(), user, "GET", "/api/v1/me/profile", 200, "NO_FIX"
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO api_access_entry (id, occurred_at, source_event_id, user_id, http_method, route_template,"
                        + " status_code, source, app_version, location_status) VALUES (?, now(), ?, ?, ?, ?, ?, 'ANDROID',"
                        + " '1.0.0', ?)",
                batch);
        String token = signInAs(Role.ADMIN).token();

        long start = System.nanoTime();
        Raw first = call(token, "/api/v1/audit/api-access?userId=" + user + "&page=0&size=25");
        Raw last = call(token, "/api/v1/audit/api-access?userId=" + user + "&page=399&size=25");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.map().get("totalElements")).isEqualTo(10_000);
        assertThat(last.status()).isEqualTo(200);
        assertThat(elapsedMs).as("two list requests over 10,000 rows").isLessThan(5_000);
    }
}
