package com.hls.audit.apiaccess;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.auth.AuthDtos;
import com.hls.identity.user.Role;
import com.hls.support.MobileTestBase;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 018 T098: every Android request publishes an event, so completed publications must not pile up
 * in the Spring Modulith event registry (about 7 million requests a year at 100 users).
 */
class EventRegistryCleanupTest extends MobileTestBase {

    @Test
    void completedPublicationsAreDeletedNotKept() {
        String phone = nextPhone();
        UUID id = userAdminService
                .createUser("Registry Teacher " + phone, phone, Set.of(Role.TEACHER), null, PASSWORD)
                .getId();
        String token = postRaw("/api/v1/auth/login", new AuthDtos.LoginRequest(phone, PASSWORD), ANDROID)
                .string("accessToken");

        for (int i = 0; i < 30; i++) {
            getRaw("/api/v1/me/profile", token, ANDROID, Map.of());
        }

        awaitRows("SELECT * FROM api_access_entry WHERE user_id = ?", 30, id);
        awaitTrue("completed publications to be removed", () -> count("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL") == 0);
        assertThat(count("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM event_publication_archive")).isZero();
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
