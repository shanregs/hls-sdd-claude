package com.hls.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 039 user theme API: self-service for every role, last change wins, unreadable accents refused. */
class UserThemeControllerTest extends IntegrationTestBase {

    private static final String THEME = "/api/v1/me/theme";

    private static Map<String, Object> body(String preset, String mode, String accent, String changedAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("preset", preset);
        m.put("mode", mode);
        m.put("customAccent", accent);
        m.put("sidebarDark", false);
        m.put("changedAt", changedAt);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> preference(Resp resp) {
        return (Map<String, Object>) resp.map().get("preference");
    }

    @Test
    void everyRoleStartsOnTheDefaultAndSeesTheCatalogue() {
        for (Role role : Role.values()) {
            Resp resp = get(THEME, signInAs(role).token());

            assertThat(resp.status()).as("%s", role).isEqualTo(200);
            assertThat(preference(resp)).containsEntry("preset", "classic").containsEntry("mode", "device");
            assertThat(resp.map()).containsEntry("isDefault", true);
            assertThat(resp.body()).contains("indigo-amber", "teal-coral", "deep-blue-green", "statusChips");
        }
    }

    @Test
    void unauthenticatedCallsAreRefused() {
        assertThat(get(THEME, null).status()).isEqualTo(401);
    }

    @Test
    void aChoiceIsSavedPerUserAndNotSharedWithOthers() {
        Signed me = signInAs(Role.TEACHER);
        Signed other = signInAs(Role.MANAGER);

        Resp saved = put(THEME, me.token(), body("teal-coral", "dark", "#7E57C2", Instant.now().toString()));

        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        assertThat(preference(get(THEME, me.token())))
                .containsEntry("preset", "teal-coral").containsEntry("mode", "dark").containsEntry("customAccent", "#7E57C2");
        assertThat(preference(get(THEME, other.token()))).containsEntry("preset", "classic");
    }

    @Test
    void theLaterChangeWins() {
        Signed me = signInAs(Role.ADMIN);
        Instant now = Instant.now();
        put(THEME, me.token(), body("indigo-amber", "light", null, now.toString()));

        Resp older = put(THEME, me.token(), body("deep-blue-green", "dark", null, now.minusSeconds(60).toString()));

        assertThat(older.status()).isEqualTo(200);
        assertThat(older.map()).containsEntry("applied", false);
        assertThat(preference(older)).containsEntry("preset", "indigo-amber");
        Resp newer = put(THEME, me.token(), body("deep-blue-green", "dark", null, now.plusSeconds(60).toString()));
        assertThat(newer.map()).containsEntry("applied", true);
        assertThat(preference(newer)).containsEntry("preset", "deep-blue-green");
    }

    @Test
    void unreadableAccentIsRefusedWithASuggestion() {
        Signed me = signInAs(Role.DIRECTOR);

        Resp resp = put(THEME, me.token(), body("classic", "light", "#FFEB3B", Instant.now().toString()));

        assertThat(resp.status()).isEqualTo(422);
        assertThat(resp.map()).containsEntry("code", "ACCENT_UNREADABLE").containsEntry("suggested", "#786C00");
        assertThat(get(THEME, me.token()).map()).containsEntry("isDefault", true);
    }

    @Test
    void invalidValuesAreRefused() {
        Signed me = signInAs(Role.TEACHER);
        String now = Instant.now().toString();

        assertThat(put(THEME, me.token(), body("nope", "light", null, now)).map()).containsEntry("code", "UNKNOWN_PRESET");
        assertThat(put(THEME, me.token(), body("classic", "sepia", null, now)).map()).containsEntry("code", "INVALID_MODE");
        assertThat(put(THEME, me.token(), body("classic", "light", "red", now)).map()).containsEntry("code", "INVALID_COLOUR");
    }

    @Test
    void resetReturnsToTheDefault() {
        Signed me = signInAs(Role.MANAGER);
        put(THEME, me.token(), body("teal-coral", "dark", null, Instant.now().toString()));

        assertThat(delete(THEME, me.token()).status()).isEqualTo(204);

        assertThat(get(THEME, me.token()).map()).containsEntry("isDefault", true);
    }

    @Test
    void contrastCheckReportsBothVariants() {
        Signed me = signInAs(Role.TEACHER);

        Resp resp = post(THEME + "/contrast-check", me.token(), Map.of("accent", "#FFEB3B"));

        assertThat(resp.status()).isEqualTo(200);
        assertThat(resp.body()).contains("\"light\"", "\"dark\"", "#786C00");
    }
}
