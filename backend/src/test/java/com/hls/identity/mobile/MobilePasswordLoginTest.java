package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T021 (US1): password sign-in for the Android client returns the renewal credential in the
 * body and sets no cookie; the web is unchanged; failure responses are unchanged.
 */
class MobilePasswordLoginTest extends MobileTestBase {

    private UUID createTeacher(String phone) {
        return userAdminService
                .createUser("Mobile Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
    }

    @Test
    void androidGetsTheRenewalCredentialInTheBodyAndNoCookie() {
        String phone = nextPhone();
        UUID userId = createTeacher(phone);

        Raw response = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.hasRenewalCookie()).isFalse();
        assertThat(response.setCookies()).isEmpty();
        assertThat(response.string("accessToken")).isNotBlank();
        assertThat(response.string("renewalCredential")).isNotBlank().hasSizeGreaterThan(20);
        assertThat(response.body()).contains("TEACHER");
        assertThat(activeSessionCount(userId)).isEqualTo(1);
    }

    @Test
    void theSessionRecordsThatItCameFromTheAndroidApp() {
        String phone = nextPhone();
        UUID userId = createTeacher(phone);

        postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), "android/1.4.2");

        var row = jdbc.queryForMap("SELECT client_type, app_version FROM session WHERE user_id = ?", userId);
        assertThat(row.get("client_type")).isEqualTo("ANDROID");
        assertThat(row.get("app_version")).isEqualTo("1.4.2");
    }

    @Test
    void webSignInIsUnchangedCookieAndNoCredentialInTheBody() {
        String phone = nextPhone();
        UUID userId = createTeacher(phone);

        Raw response = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.hasRenewalCookie()).isTrue();
        assertThat(response.map()).doesNotContainKey("renewalCredential");
        var row = jdbc.queryForMap("SELECT client_type, app_version FROM session WHERE user_id = ?", userId);
        assertThat(row.get("client_type")).isEqualTo("WEB");
        assertThat(row.get("app_version")).isNull();
    }

    @Test
    void aWrongPasswordIsTheSameGenericFailureForAndroid() {
        String phone = nextPhone();
        createTeacher(phone);

        Raw android = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, "wrong-password-1"), ANDROID);
        Raw web = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, "wrong-password-1"), null);
        Raw unknown = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest("9000000001", "wrong-password-1"), ANDROID);

        assertThat(android.status()).isEqualTo(401);
        assertThat(android.string("message")).isEqualTo(web.string("message")).isEqualTo(unknown.string("message"));
        assertThat(android.map()).doesNotContainKey("renewalCredential");
    }

    @Test
    void lockoutStillAppliesToAndroid() {
        String phone = nextPhone();
        createTeacher(phone);

        for (int i = 0; i < 5; i++) {
            postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, "wrong-password-1"), ANDROID);
        }
        Raw locked = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID);

        assertThat(locked.status()).isEqualTo(423);
        assertThat(locked.string("message")).startsWith("Account locked until");
        assertThat(locked.string("unlockAt")).isNotBlank();
    }
}
