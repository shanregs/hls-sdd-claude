package com.hls.audit.loginhistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T062 (US4, FR-023, FR-024, FR-028a): a sign-in from the Android app is audited with its
 * source, app version and device location (or the reason there is none), and the rooted flag.
 */
class LoginHistoryLocationTest extends MobileTestBase {

    private record Account(String phone, UUID id) {}

    private Account teacher() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Location Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        return new Account(phone, id);
    }

    private Raw login(Account account, String password, String clientHeader, Map<String, String> headers) {
        return postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), password), clientHeader, headers);
    }

    private Map<String, Object> successRow(Account account) {
        List<Map<String, Object>> rows = awaitRows(
                "SELECT * FROM login_history_entry WHERE user_id = ? AND event_type = 'SIGN_IN_SUCCESS'",
                1,
                account.id());
        return rows.get(0);
    }

    @Test
    void aValidLocationIsStoredWithTheSignIn() {
        Account account = teacher();

        login(account, PASSWORD, "android/1.2.3", withLocation(12.971599, 77.594566, 18.5));

        Map<String, Object> row = successRow(account);
        assertThat(row.get("source")).isEqualTo("ANDROID");
        assertThat(row.get("app_version")).isEqualTo("1.2.3");
        assertThat(row.get("location_status")).isEqualTo("AVAILABLE");
        assertThat((BigDecimal) row.get("latitude")).isEqualByComparingTo("12.971599");
        assertThat((BigDecimal) row.get("longitude")).isEqualByComparingTo("77.594566");
        assertThat(((Number) row.get("accuracy_meters")).doubleValue()).isEqualTo(18.5);
        assertThat(row.get("location_captured_at")).isNotNull();
        assertThat(row.get("device_rooted")).isEqualTo(false);
    }

    @Test
    void eachReasonForNoLocationIsStoredWithNoCoordinates() {
        for (String reason : List.of("PERMISSION_DENIED", "SERVICES_OFF", "NO_FIX", "OTHER")) {
            Account account = teacher();

            login(account, PASSWORD, ANDROID, Map.of("X-HLS-Location-Status", reason));

            Map<String, Object> row = successRow(account);
            assertThat(row.get("source")).as(reason).isEqualTo("ANDROID");
            assertThat(row.get("location_status")).as(reason).isEqualTo(reason);
            assertThat(row.get("latitude")).as(reason).isNull();
            assertThat(row.get("longitude")).as(reason).isNull();
            assertThat(row.get("accuracy_meters")).as(reason).isNull();
            assertThat(row.get("location_captured_at")).as(reason).isNull();
        }
    }

    @Test
    void anAndroidSignInWithNoLocationHeadersIsOther() {
        Account account = teacher();

        login(account, PASSWORD, ANDROID, Map.of());

        assertThat(successRow(account).get("location_status")).isEqualTo("OTHER");
    }

    @Test
    void anInvalidLocationIsStoredAsInvalidAndTheValuesAreDiscardedButSignInStillSucceeds() {
        Account account = teacher();

        Raw response = login(account, PASSWORD, ANDROID, Map.of("X-HLS-Location", "lat=999;lng=77;acc=10;ts=1"));

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> row = successRow(account);
        assertThat(row.get("location_status")).isEqualTo("INVALID");
        assertThat(row.get("latitude")).isNull();
        assertThat(row.get("longitude")).isNull();
    }

    @Test
    void aWebSignInIsStoredAsWebWithNoLocationApplicable() {
        Account account = teacher();

        login(account, PASSWORD, null, Map.of("X-HLS-Location", locationHeader(1, 2, 3, java.time.Instant.now())));

        Map<String, Object> row = successRow(account);
        assertThat(row.get("source")).isEqualTo("WEB");
        assertThat(row.get("app_version")).isNull();
        assertThat(row.get("location_status")).isEqualTo("NOT_APPLICABLE");
        assertThat(row.get("latitude")).isNull();
    }

    @Test
    void theRootedFlagIsStoredOnTheLoginHistoryEntryAndNeverBlocksSignIn() {
        Account account = teacher();

        Raw response = login(account, PASSWORD, ANDROID, Map.of("X-HLS-Device-Integrity", "ROOTED_SUSPECTED"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(successRow(account).get("device_rooted")).isEqualTo(true);
    }

    @Test
    void failedSignInsAndLockoutCarryTheContextToo() {
        Account account = teacher();
        Map<String, String> headers = withLocation(12.5, 77.5, 9.0);

        for (int i = 0; i < 5; i++) {
            login(account, "wrong-password-1", ANDROID, headers);
        }

        List<Map<String, Object>> failures = awaitRows(
                "SELECT * FROM login_history_entry WHERE user_id = ? AND event_type IN ('SIGN_IN_FAILURE', 'LOCKOUT')",
                5,
                account.id());
        assertThat(failures).allSatisfy(row -> {
            assertThat(row.get("source")).isEqualTo("ANDROID");
            assertThat(row.get("location_status")).isEqualTo("AVAILABLE");
        });
        assertThat(failures.stream().map(r -> r.get("event_type")).toList()).contains("SIGN_IN_FAILURE", "LOCKOUT");
    }

    @Test
    void anUnknownAccountsFailedAttemptStillRecordsWhereItCameFrom() {
        postRaw(
                "/api/v1/auth/login",
                new AuthDtos.LoginRequest("9000000002", "wrong-password-1"),
                ANDROID,
                Map.of("X-HLS-Location-Status", "SERVICES_OFF"));

        List<Map<String, Object>> rows = awaitRows(
                "SELECT * FROM login_history_entry WHERE user_id IS NULL AND source = 'ANDROID'"
                        + " AND location_status = 'SERVICES_OFF' AND event_type = 'SIGN_IN_FAILURE'",
                1);
        assertThat(rows).isNotEmpty();
    }
}
