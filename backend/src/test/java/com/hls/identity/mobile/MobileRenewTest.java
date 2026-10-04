package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T024 (US1, FR-004, FR-005): renewal with the credential in the body, rotation, reuse
 * detection revoking the whole session, and the web cookie path left unchanged.
 */
class MobileRenewTest extends MobileTestBase {

    private record Signed(UUID userId, String credential) {}

    private Signed androidSignIn() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Renew Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        Raw login = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID);
        assertThat(login.status()).isEqualTo(200);
        return new Signed(id, login.string("renewalCredential"));
    }

    private Raw renew(String credential) {
        return postRaw("/api/v1/auth/renew", new AuthDtos.RenewRequest(credential), ANDROID);
    }

    @Test
    void renewalRotatesTheCredentialAndNeverSetsACookie() {
        Signed signed = androidSignIn();

        Raw renewed = renew(signed.credential());

        assertThat(renewed.status()).isEqualTo(200);
        assertThat(renewed.string("accessToken")).isNotBlank();
        assertThat(renewed.string("renewalCredential")).isNotBlank().isNotEqualTo(signed.credential());
        assertThat(renewed.setCookies()).isEmpty();
        assertThat(renewed.body()).contains("TEACHER");
    }

    @Test
    void theRotatedCredentialRenewsAgain() {
        Signed signed = androidSignIn();
        String second = renew(signed.credential()).string("renewalCredential");

        Raw third = renew(second);

        assertThat(third.status()).isEqualTo(200);
        assertThat(third.string("renewalCredential")).isNotBlank().isNotEqualTo(second);
    }

    @Test
    void replayingAnAlreadyUsedCredentialRevokesTheWholeSession() {
        Signed signed = androidSignIn();
        String rotated = renew(signed.credential()).string("renewalCredential");

        Raw replay = renew(signed.credential());

        assertThat(replay.status()).isEqualTo(401);
        assertThat(replay.setCookies()).isEmpty();
        assertThat(activeSessionCount(signed.userId())).isZero();
        // The credential that was legitimately issued next is dead too (FR-010).
        assertThat(renew(rotated).status()).isEqualTo(401);
        awaitTrue(
                "the reuse event in login history",
                () -> loginHistoryCount(signed.userId(), "SESSION_REVOKED_REUSE", "%reuse detected%") == 1);
    }

    @Test
    void unknownBlankAndMissingCredentialsAreRefused() {
        assertThat(renew("not-a-real-credential").status()).isEqualTo(401);
        assertThat(renew("").status()).isEqualTo(401);
        assertThat(postRaw("/api/v1/auth/renew", null, ANDROID).status()).isEqualTo(401);
    }

    @Test
    void anAndroidRenewalIgnoresACookieSentWithoutABody() {
        // The renewal credential travels only in the body for the app (research.md §3).
        Raw login = postRaw("/api/v1/auth/login", loginBody(), null);
        String cookie = login.setCookies().get(0).split(";")[0];

        Raw renewed = postRaw("/api/v1/auth/renew", null, ANDROID, Map.of("Cookie", cookie));

        assertThat(renewed.status()).isEqualTo(401);
    }

    private AuthDtos.LoginRequest loginBody() {
        String phone = nextPhone();
        userAdminService.createUser("Cookie Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD);
        return new AuthDtos.LoginRequest(phone, PASSWORD);
    }

    @Test
    void theWebCookiePathIsUnchanged() {
        Raw login = postRaw("/api/v1/auth/login", loginBody(), null);
        String cookie = login.setCookies().get(0).split(";")[0];

        Raw renewed = postRaw("/api/v1/auth/renew", null, null, Map.of("Cookie", cookie));

        assertThat(renewed.status()).isEqualTo(200);
        assertThat(renewed.hasRenewalCookie()).isTrue();
        assertThat(renewed.map()).doesNotContainKey("renewalCredential");
    }
}
