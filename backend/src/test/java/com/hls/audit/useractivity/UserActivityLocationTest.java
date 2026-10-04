package com.hls.audit.useractivity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.activity.SessionEnded;
import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.AccountController;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 018 T063 (US4, FR-023): account actions made from the Android app are audited with the same
 * client context as sign-ins; events that happen outside any request carry none.
 */
class UserActivityLocationTest extends MobileTestBase {

    private record Account(String phone, UUID id, String token) {}

    private Account signedInTeacher() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Activity Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        Raw login = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), null);
        return new Account(phone, id, login.string("accessToken"));
    }

    private Map<String, Object> activity(UUID userId, String action) {
        return awaitRows(
                        "SELECT * FROM user_activity_entry WHERE affected_user_id = ? AND action = ?"
                                + " ORDER BY occurred_at DESC",
                        1,
                        userId,
                        action)
                .get(0);
    }

    @Test
    void aProfileUpdateFromTheAppCarriesTheLocation() {
        Account account = signedInTeacher();

        Raw response = callRaw(
                HttpMethod.PUT,
                "/api/v1/me/profile",
                account.token(),
                new AccountController.UpdateProfileRequest("Renamed Teacher", "renamed." + account.phone(), ""),
                "android/1.1.0",
                withLocation(12.34, 56.78, 7.5));

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> row = activity(account.id(), "PROFILE_UPDATED");
        assertThat(row.get("source")).isEqualTo("ANDROID");
        assertThat(row.get("app_version")).isEqualTo("1.1.0");
        assertThat(row.get("location_status")).isEqualTo("AVAILABLE");
        assertThat((BigDecimal) row.get("latitude")).isEqualByComparingTo("12.34");
        assertThat((BigDecimal) row.get("longitude")).isEqualByComparingTo("56.78");
    }

    @Test
    void endingOneOfYourSessionsFromTheAppCarriesTheContext() {
        Account account = signedInTeacher();
        Raw second = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), PASSWORD), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) (List<?>) org.springframework.boot.json
                .JsonParserFactory.getJsonParser()
                .parseList(get("/api/v1/me/sessions", second.string("accessToken")).body());
        String otherSessionId = sessions.stream()
                .filter(s -> Boolean.FALSE.equals(s.get("current")))
                .map(s -> s.get("id").toString())
                .findFirst()
                .orElseThrow();

        callRaw(
                HttpMethod.DELETE,
                "/api/v1/me/sessions/" + otherSessionId,
                second.string("accessToken"),
                null,
                ANDROID,
                Map.of("X-HLS-Location-Status", "PERMISSION_DENIED"));

        Map<String, Object> row = activity(account.id(), "SESSION_ENDED");
        assertThat(row.get("source")).isEqualTo("ANDROID");
        assertThat(row.get("location_status")).isEqualTo("PERMISSION_DENIED");
        assertThat(row.get("latitude")).isNull();
    }

    @Test
    void aPasswordChangeFromTheAppCarriesTheContext() {
        Account account = signedInTeacher();

        Raw response = callRaw(
                HttpMethod.POST,
                "/api/v1/me/password",
                account.token(),
                new AccountController.ChangePasswordRequest(PASSWORD, "another-strong-pass-1"),
                ANDROID,
                withLocation(1.5, 2.5, 3.0));

        assertThat(response.status()).isEqualTo(204);
        assertThat(activity(account.id(), "PASSWORD_CHANGED").get("source")).isEqualTo("ANDROID");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void anEventThatHappensOutsideAnyRequestIsWebWithNoLocation() {
        UUID userId = UUID.randomUUID();
        // Published from the test thread, with no HTTP request in scope: nothing client-specific exists.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(
                new SessionEnded(UUID.randomUUID(), Instant.now(), null, userId, UUID.randomUUID())));

        Map<String, Object> row = activity(userId, "SESSION_ENDED");

        assertThat(row.get("source")).isEqualTo("WEB");
        assertThat(row.get("app_version")).isNull();
        assertThat(row.get("location_status")).isEqualTo("NOT_APPLICABLE");
        assertThat(row.get("latitude")).isNull();
    }

    @Test
    void aWebProfileUpdateIsStoredAsWeb() {
        Account account = signedInTeacher();

        callRaw(
                HttpMethod.PUT,
                "/api/v1/me/profile",
                account.token(),
                new AccountController.UpdateProfileRequest("Web Rename", "web." + account.phone(), ""),
                null,
                Map.of());

        Map<String, Object> row = activity(account.id(), "PROFILE_UPDATED");
        assertThat(row.get("source")).isEqualTo("WEB");
        assertThat(row.get("location_status")).isEqualTo("NOT_APPLICABLE");
    }
}
