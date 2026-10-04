package com.hls.identity.clientcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T097 (US4, FR-021, FR-025): location never decides the outcome of a request. A request
 * with a valid location, no location, only a reason, or an invalid location gets the same response.
 */
class LocationNeverGatesRequestTest extends MobileTestBase {

    private static final List<Map<String, String>> VARIANTS = List.of(
            withLocation(12.5, 77.5, 10.0),
            Map.of(),
            Map.of("X-HLS-Location-Status", "PERMISSION_DENIED"),
            Map.of("X-HLS-Location-Status", "NO_FIX"),
            Map.of("X-HLS-Location", "lat=999;lng=999;acc=-5;ts=1"),
            Map.of("X-HLS-Location", "garbage"));

    private String phone;
    private String token;

    private void signedInTeacher() {
        phone = nextPhone();
        userAdminService.createUser("Gate Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD);
        token = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID)
                .string("accessToken");
    }

    @Test
    void readsReturnTheSameStatusWhateverTheLocationHeaders() {
        signedInTeacher();
        for (String path : List.of("/api/v1/me/profile", "/api/v1/me/sessions", "/api/v1/me/access-model")) {
            for (Map<String, String> headers : VARIANTS) {
                assertThat(getRaw(path, token, ANDROID, headers).status()).as(path + " " + headers).isEqualTo(200);
            }
        }
    }

    @Test
    void signInOutcomesAreTheSameWhateverTheLocationHeaders() {
        signedInTeacher();
        for (Map<String, String> headers : VARIANTS) {
            Raw good = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID, headers);
            Raw bad = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, "wrong-password-1"), ANDROID, headers);

            assertThat(good.status()).as(headers.toString()).isEqualTo(200);
            assertThat(bad.status()).as(headers.toString()).isEqualTo(401);
            assertThat(bad.string("message")).isEqualTo("Your phone number/username or password is incorrect.");
        }
    }

    @Test
    void renewalAndLogoutAreUnaffected() {
        signedInTeacher();
        String credential = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID, Map.of())
                .string("renewalCredential");
        for (Map<String, String> headers : VARIANTS.subList(0, 3)) {
            Raw renewed = postRaw("/api/v1/auth/renew", new AuthDtos.RenewRequest(credential), ANDROID, headers);
            assertThat(renewed.status()).as(headers.toString()).isEqualTo(200);
            credential = renewed.string("renewalCredential");
        }
        Raw logout = callRaw(org.springframework.http.HttpMethod.POST, "/api/v1/auth/logout", token, null, ANDROID,
                Map.of("X-HLS-Location", "garbage"));
        assertThat(logout.status()).isEqualTo(204);
    }
}
