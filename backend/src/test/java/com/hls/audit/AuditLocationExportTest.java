package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.AccountController;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Spec 018 T066 (US4, FR-026, FR-028): the CSV exports carry the new source, app version and
 * location columns, with empty values when there is no location, under the same permissions.
 */
class AuditLocationExportTest extends MobileTestBase {

    private static final String NEW_COLUMNS = "source,appVersion,locationStatus,latitude,longitude,accuracyMeters";

    private String range() {
        return "from=" + Instant.now().minus(Duration.ofDays(1)) + "&to=" + Instant.now().plus(Duration.ofDays(1));
    }

    private Raw export(String token, String path) {
        Raw response = getRaw(path, token, null, Map.of());
        assertThat(response.status()).as(path).isEqualTo(200);
        return response;
    }

    private List<String> lines(Raw csv) {
        return Arrays.stream(csv.body().split("\r?\n")).filter(l -> !l.isBlank()).toList();
    }

    @Test
    void everyExportHasTheNewColumnsAndTheRightValues() {
        // One Android sign-in with a location and one Android profile edit with no fix.
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Export Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        Raw login = postRaw(
                "/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), "android/1.0.0", withLocation(12.5, 77.25, 8.0));
        callRaw(
                HttpMethod.PUT,
                "/api/v1/me/profile",
                login.string("accessToken"),
                new AccountController.UpdateProfileRequest("Export Renamed", "exp." + phone, ""),
                "android/1.0.0",
                Map.of("X-HLS-Location-Status", "NO_FIX"));
        awaitRows("SELECT * FROM login_history_entry WHERE user_id = ? AND event_type = 'SIGN_IN_SUCCESS'", 1, id);
        awaitRows("SELECT * FROM user_activity_entry WHERE affected_user_id = ? AND action = 'PROFILE_UPDATED'", 1, id);
        awaitRows("SELECT * FROM api_access_entry WHERE user_id = ?", 1, id);
        String token = signInAs(Role.ADMIN).token();

        Raw loginCsv = export(token, "/api/v1/audit/login-history/export?userId=" + id + "&" + range());
        Raw activityCsv = export(token, "/api/v1/audit/user-activity/export?affectedUserId=" + id + "&" + range());
        Raw apiCsv = export(token, "/api/v1/audit/api-access/export?userId=" + id + "&" + range());
        Raw logsCsv = export(token, "/api/v1/audit/logs/export?userId=" + id + "&" + range());

        assertThat(lines(loginCsv).get(0)).contains(NEW_COLUMNS).endsWith("deviceRooted");
        assertThat(lines(loginCsv).get(1)).contains("ANDROID,1.0.0,AVAILABLE,12.500000,77.250000,8.0");

        assertThat(lines(activityCsv).get(0)).endsWith(NEW_COLUMNS);
        // No fix: status carries the reason, and the coordinate columns are empty.
        assertThat(lines(activityCsv).get(1)).contains("ANDROID,1.0.0,NO_FIX,,,");

        assertThat(lines(apiCsv).get(0))
                .startsWith("occurredAt,userId,sessionId,httpMethod,routeTemplate,statusCode,source,appVersion,locationStatus")
                .endsWith("locationCapturedAt");
        assertThat(lines(apiCsv).stream().skip(1).anyMatch(l -> l.contains("/api/v1/me/profile") && l.contains("NO_FIX")))
                .isTrue();

        assertThat(lines(logsCsv).get(0)).endsWith(NEW_COLUMNS);
        assertThat(logsCsv.body()).contains("ANDROID,1.0.0,AVAILABLE");
    }

    @Test
    void aWebRowExportsAsWebWithEmptyLocationColumns() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Export Web " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), null);
        awaitRows("SELECT * FROM login_history_entry WHERE user_id = ?", 1, id);
        String token = signInAs(Role.SYSTEM).token();

        Raw csv = export(token, "/api/v1/audit/login-history/export?userId=" + id + "&" + range());

        assertThat(lines(csv).get(1)).contains("WEB,,NOT_APPLICABLE,,,").endsWith("false");
    }
}
