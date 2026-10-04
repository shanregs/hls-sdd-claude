package com.hls.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.support.IntegrationTestBase;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/** Self-service settings: every signed-in user edits their own profile and changes their own password. */
class AccountControllerTest extends IntegrationTestBase {

    private static final String PROFILE = "/api/v1/me/profile";
    private static final String PASSWORD_URL = "/api/v1/me/password";
    private static final String NEW_PASSWORD = "brand-new-pass-77";

    private static String unique(String prefix) {
        return prefix + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    @Test
    void everyRoleCanReadTheirOwnProfile() {
        for (Role role : Role.values()) {
            Signed me = signInAs(role);

            Resp resp = get(PROFILE, me.token());

            assertThat(resp.status()).as("%s", role).isEqualTo(200);
            assertThat(resp.map()).containsEntry("phone", me.phone());
            assertThat(resp.body()).contains(role.name());
        }
    }

    @Test
    void aUserEditsTheirNameUsernameAndEmailButNotTheirPhone() {
        Signed me = signInAs(Role.TEACHER);
        String username = unique("tara");
        String email = unique("tara") + "@example.org";

        Resp updated = put(
                PROFILE, me.token(), Map.of("displayName", "Tara Renamed", "username", username, "email", email));

        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(updated.map())
                .containsEntry("displayName", "Tara Renamed")
                .containsEntry("username", username)
                .containsEntry("email", email)
                .containsEntry("phone", me.phone());
        assertThat(get(PROFILE, me.token()).map()).containsEntry("displayName", "Tara Renamed");
    }

    @Test
    void clearingUsernameAndEmailIsAllowed() {
        Signed me = signInAs(Role.MANAGER);
        put(PROFILE, me.token(), Map.of("displayName", "M", "username", unique("m"), "email", unique("m") + "@x.org"));

        Resp cleared = put(PROFILE, me.token(), Map.of("displayName", "M", "username", "", "email", ""));

        assertThat(cleared.status()).as(cleared.body()).isEqualTo(200);
        assertThat(cleared.map().get("username")).isNull();
        assertThat(cleared.map().get("email")).isNull();
    }

    @Test
    void invalidProfileValuesAreRefusedWithAReason() {
        Signed me = signInAs(Role.TEACHER);

        assertThat(put(PROFILE, me.token(), Map.of("displayName", "  ")).status()).isEqualTo(400);
        assertThat(put(PROFILE, me.token(), Map.of("displayName", "A", "username", "x!")).status()).isEqualTo(400);
        assertThat(put(PROFILE, me.token(), Map.of("displayName", "A", "email", "not-an-email")).status())
                .isEqualTo(400);
    }

    @Test
    void aTakenUsernameOrEmailIsRefusedButYourOwnIsFine() {
        Signed first = signInAs(Role.TEACHER);
        Signed second = signInAs(Role.TEACHER);
        String username = unique("taken");
        String email = unique("taken") + "@example.org";
        put(PROFILE, first.token(), Map.of("displayName", "First", "username", username, "email", email));

        Resp sameUsername = put(PROFILE, second.token(), Map.of("displayName", "Second", "username", username.toUpperCase()));
        Resp sameEmail = put(PROFILE, second.token(), Map.of("displayName", "Second", "email", email));
        Resp ownAgain = put(PROFILE, first.token(), Map.of("displayName", "First", "username", username, "email", email));

        assertThat(sameUsername.status()).isEqualTo(409);
        assertThat(sameEmail.status()).isEqualTo(409);
        assertThat(ownAgain.status()).isEqualTo(200);
    }

    @Test
    void aWrongCurrentPasswordOrAWeakNewOneIsRefused() {
        Signed me = signInAs(Role.TEACHER);

        Resp wrong = post(PASSWORD_URL, me.token(), Map.of("currentPassword", "nope-nope-nope", "newPassword", NEW_PASSWORD));
        Resp weak = post(PASSWORD_URL, me.token(), Map.of("currentPassword", PASSWORD, "newPassword", "short"));
        Resp phone = post(PASSWORD_URL, me.token(), Map.of("currentPassword", PASSWORD, "newPassword", me.phone()));
        Resp same = post(PASSWORD_URL, me.token(), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD));

        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.body()).contains("current password is incorrect");
        assertThat(weak.status()).isEqualTo(400);
        assertThat(phone.status()).isEqualTo(400);
        assertThat(same.status()).isEqualTo(400);
        assertThat(loginStatus(me.phone(), PASSWORD)).isEqualTo(200);
    }

    @Test
    void changingThePasswordSwitchesTheLoginAndEndsOtherSessions() {
        Signed me = signInAs(Role.TEACHER);
        signIn(me.userId(), me.phone(), PASSWORD); // a second signed-in device

        Resp changed = post(PASSWORD_URL, me.token(), Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));

        assertThat(changed.status()).isEqualTo(204);
        Resp sessions = get("/api/v1/me/sessions", me.token());
        assertThat(org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(sessions.body()))
                .as("only the session that changed the password stays active")
                .hasSize(1);
        assertThat(sessions.body()).contains("\"current\":true");
        assertThat(loginStatus(me.phone(), PASSWORD)).isNotEqualTo(200);
        assertThat(loginStatus(me.phone(), NEW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void thePasswordChangeAndProfileEditAppearInUserActivity() {
        Signed me = signInAs(Role.TEACHER);
        String admin = signInAs(Role.ADMIN).token();
        put(PROFILE, me.token(), Map.of("displayName", "Activity Check"));
        post(PASSWORD_URL, me.token(), Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp activity = get("/api/v1/audit/user-activity?size=100&affectedUserId=" + me.userId(), admin);
            assertThat(activity.status()).isEqualTo(200);
            assertThat(activity.body()).contains("PASSWORD_CHANGED").contains("PROFILE_UPDATED");
            assertThat(activity.body()).doesNotContain(NEW_PASSWORD);
        });
    }

    @Test
    void anonymousCallersAreRefused() {
        assertThat(get(PROFILE, null).status()).isEqualTo(401);
        assertThat(put(PROFILE, null, Map.of("displayName", "x")).status()).isEqualTo(401);
        assertThat(post(PASSWORD_URL, null, Map.of("currentPassword", "a", "newPassword", "b")).status()).isEqualTo(401);
    }
}
