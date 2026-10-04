package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T065 (US4, FR-026, FR-028): per role, who may see the location on the audit screens, and
 * that nobody else ever receives it, on those screens or on any other endpoint.
 */
class AuditLocationVisibilityTest extends MobileTestBase {

    private UUID subjectId;
    private String subjectPhone;

    private static final String FROM = Instant.now().minus(Duration.ofDays(1)).toString();
    private static final String TO = Instant.now().plus(Duration.ofDays(1)).toString();

    @BeforeEach
    void androidSignInWithLocation() {
        subjectPhone = nextPhone();
        subjectId = userAdminService
                .createUser("Visible Teacher " + subjectPhone, subjectPhone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        Raw login = postRaw(
                "/api/v1/auth/login",
                new AuthDtos.LoginRequest(subjectPhone, PASSWORD),
                "android/1.0.0",
                withLocation(12.345678, 77.654321, 9.5));
        awaitRows("SELECT * FROM login_history_entry WHERE user_id = ? AND event_type = 'SIGN_IN_SUCCESS'", 1, subjectId);
        // A user-activity entry from the app too: a profile edit with the same location.
        callRaw(
                org.springframework.http.HttpMethod.PUT,
                "/api/v1/me/profile",
                login.string("accessToken"),
                new com.hls.identity.user.AccountController.UpdateProfileRequest("Visible Renamed", "vis." + subjectPhone, ""),
                "android/1.0.0",
                withLocation(12.345678, 77.654321, 9.5));
        awaitRows("SELECT * FROM user_activity_entry WHERE affected_user_id = ? AND action = 'PROFILE_UPDATED'", 1, subjectId);
    }

    private Raw audit(String token, String path) {
        return getRaw(path, token, null, Map.of());
    }

    @Test
    void adminAndSystemSeeTheLocationOnLoginHistoryUserActivityAndLogs() {
        for (Role role : List.of(Role.ADMIN, Role.SYSTEM)) {
            String token = signInAs(role).token();

            Raw login = audit(token, "/api/v1/audit/login-history?userId=" + subjectId);
            assertThat(login.status()).as(role + " login-history").isEqualTo(200);
            assertThat(login.body())
                    .contains("\"source\":\"ANDROID\"")
                    .contains("\"appVersion\":\"1.0.0\"")
                    .contains("\"status\":\"AVAILABLE\"")
                    .contains("12.345678")
                    .contains("77.654321")
                    .contains("\"deviceRooted\":false");

            Raw logs = audit(token, "/api/v1/audit/logs?type=LOGIN&userId=" + subjectId);
            assertThat(logs.status()).as(role + " logs").isEqualTo(200);
            assertThat(logs.body()).contains("12.345678").contains("\"source\":\"ANDROID\"");

            Raw activity = audit(token, "/api/v1/audit/user-activity?affectedUserId=" + subjectId);
            assertThat(activity.status()).as(role + " user-activity").isEqualTo(200);
            assertThat(activity.body()).contains("\"source\"").contains("\"location\"");
        }
    }

    @Test
    void directorManagerAndTeacherAreRefusedAndNeverReceiveLocation() {
        for (Role role : List.of(Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            String token = signInAs(role).token();
            for (String path : List.of(
                    "/api/v1/audit/login-history?userId=" + subjectId,
                    "/api/v1/audit/user-activity",
                    "/api/v1/audit/logs",
                    "/api/v1/audit/api-access",
                    "/api/v1/audit/login-history/export?from=" + FROM + "&to=" + TO,
                    "/api/v1/audit/api-access/export?from=" + FROM + "&to=" + TO)) {
                Raw response = audit(token, path);

                assertThat(response.status()).as(role + " " + path).isEqualTo(403);
                assertThat(response.body() == null ? "" : response.body())
                        .as(role + " " + path)
                        .doesNotContain("latitude")
                        .doesNotContain("12.345678");
            }
        }
    }

    @Test
    void noOtherEndpointReturnsLocationToATeacherManagerOrDirector() {
        for (Role role : List.of(Role.TEACHER, Role.MANAGER, Role.DIRECTOR)) {
            String token = signInAs(role).token();
            for (String path : List.of("/api/v1/me/profile", "/api/v1/me/sessions", "/api/v1/me/access-model")) {
                Raw response = getRaw(path, token, "android/1.0.0", withLocation(1, 2, 3));

                assertThat(response.status()).as(role + " " + path).isEqualTo(200);
                assertThat(response.body()).as(role + " " + path).doesNotContain("latitude").doesNotContain("longitude");
            }
        }
    }

    @Test
    void theSourceFilterNarrowsTheListAndAnInvalidSourceIsRejected() {
        // Give the subject a web sign-in too, so both sources exist for the user.
        postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(subjectPhone, PASSWORD), null);
        awaitRows("SELECT * FROM login_history_entry WHERE user_id = ? AND source = 'WEB'", 1, subjectId);
        String token = signInAs(Role.ADMIN).token();

        Raw android = audit(token, "/api/v1/audit/login-history?size=100&source=ANDROID&userId=" + subjectId);
        Raw web = audit(token, "/api/v1/audit/login-history?size=100&source=WEB&userId=" + subjectId);
        Raw lower = audit(token, "/api/v1/audit/login-history?size=100&source=android&userId=" + subjectId);
        Raw bogus = audit(token, "/api/v1/audit/login-history?source=BLACKBERRY");

        assertThat(android.body()).contains("\"source\":\"ANDROID\"").doesNotContain("\"source\":\"WEB\"");
        assertThat(web.body()).contains("\"source\":\"WEB\"").doesNotContain("\"source\":\"ANDROID\"");
        assertThat(lower.status()).isEqualTo(200);
        assertThat(bogus.status()).isEqualTo(400);
        assertThat(audit(token, "/api/v1/audit/user-activity?source=BLACKBERRY").status()).isEqualTo(400);
    }
}
