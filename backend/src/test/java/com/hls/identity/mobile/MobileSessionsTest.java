package com.hls.identity.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/**
 * Spec 018 T038 (US2, FR-006/FR-007): the session list shows Android and web sessions together,
 * marks the current one, a user can end another session but never someone else's, and an Android
 * logout sets no cookie.
 */
class MobileSessionsTest extends MobileTestBase {

    private record Account(String phone, UUID id) {}

    private Account teacher() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Sessions Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        return new Account(phone, id);
    }

    private String signIn(Account account, String clientHeader) {
        Raw login = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(account.phone(), PASSWORD), clientHeader);
        assertThat(login.status()).isEqualTo(200);
        return login.string("accessToken");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sessionsOf(String token) {
        Resp response = get("/api/v1/me/sessions", token);
        assertThat(response.status()).isEqualTo(200);
        return (List<Map<String, Object>>) (List<?>) JsonParserFactory.getJsonParser().parseList(response.body());
    }

    @Test
    void theListShowsWebAndAndroidSessionsTogetherWithTheCurrentOneMarked() {
        Account account = teacher();
        String webToken = signIn(account, null);
        String androidToken = signIn(account, "android/1.3.0");

        List<Map<String, Object>> seenFromAndroid = sessionsOf(androidToken);

        assertThat(seenFromAndroid).hasSize(2);
        Map<String, Object> android = seenFromAndroid.stream()
                .filter(s -> "ANDROID".equals(s.get("clientType")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> web = seenFromAndroid.stream()
                .filter(s -> "WEB".equals(s.get("clientType")))
                .findFirst()
                .orElseThrow();
        assertThat(android.get("current")).isEqualTo(true);
        assertThat(android.get("appVersion")).isEqualTo("1.3.0");
        assertThat(web.get("current")).isEqualTo(false);
        assertThat(web.get("appVersion")).isNull();
        // From the web session the same two sessions are listed with the current marker the other way.
        assertThat(sessionsOf(webToken).stream().filter(s -> s.get("current").equals(true)).findFirst().orElseThrow())
                .containsEntry("clientType", "WEB");
    }

    @Test
    void aUserCanEndAnotherOfTheirSessionsAndTheEndedTokenStopsWorking() {
        Account account = teacher();
        String webToken = signIn(account, null);
        String androidToken = signIn(account, ANDROID);
        String webSessionId = sessionsOf(androidToken).stream()
                .filter(s -> "WEB".equals(s.get("clientType")))
                .map(s -> s.get("id").toString())
                .findFirst()
                .orElseThrow();

        Resp ended = send(org.springframework.http.HttpMethod.DELETE, "/api/v1/me/sessions/" + webSessionId, androidToken, null);

        assertThat(ended.status()).isIn(200, 204);
        assertThat(get("/api/v1/me/sessions", webToken).status()).isEqualTo(401);
        assertThat(sessionsOf(androidToken)).hasSize(1);
    }

    @Test
    void aUserCannotEndSomeoneElsesSession() {
        Account mine = teacher();
        Account theirs = teacher();
        String myToken = signIn(mine, ANDROID);
        String theirToken = signIn(theirs, ANDROID);
        String theirSessionId = sessionsOf(theirToken).get(0).get("id").toString();

        Resp attempt = send(org.springframework.http.HttpMethod.DELETE, "/api/v1/me/sessions/" + theirSessionId, myToken, null);

        assertThat(attempt.status()).isEqualTo(403);
        assertThat(sessionsOf(theirToken)).hasSize(1);
    }

    @Test
    void anAndroidLogoutReturns204WithNoCookieAndEndsTheSession() {
        Account account = teacher();
        String token = signIn(account, ANDROID);

        var result = client.post()
                .uri("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + token)
                .header("X-HLS-Client", ANDROID)
                .exchange()
                .returnResult(String.class);

        assertThat(result.getStatus().value()).isEqualTo(204);
        assertThat(result.getResponseHeaders().get("Set-Cookie")).isNull();
        assertThat(activeSessionCount(account.id())).isZero();
        assertThat(get("/api/v1/me/sessions", token).status()).isEqualTo(401);
    }

    @Test
    void aWebLogoutStillExpiresTheCookie() {
        Account account = teacher();
        String token = signIn(account, null);

        var result = client.post()
                .uri("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .returnResult(String.class);

        assertThat(result.getStatus().value()).isEqualTo(204);
        assertThat(result.getResponseHeaders().get("Set-Cookie")).isNotNull();
    }
}
