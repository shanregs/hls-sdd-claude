package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsApiTest extends MarketingTestBase {

    private Object version(String token) {
        return get(M + "/settings", token).map().get("version");
    }

    @Test
    void adminAndDirectorReadAndChangeTheLimitWithinOneToNinetyAndEachChangeIsAudited() {
        String admin = admin();
        String director = directorToken();

        Resp read = get(M + "/settings", director);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.map()).containsKey("mouOverdueDays");

        assertThat(put(M + "/settings", admin, Map.of("mouOverdueDays", 0, "version", version(admin))).status()).isEqualTo(400);
        assertThat(put(M + "/settings", admin, Map.of("mouOverdueDays", 91, "version", version(admin))).status()).isEqualTo(400);
        Resp ok = put(M + "/settings", admin, Map.of("mouOverdueDays", 21, "version", version(admin)));
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.map()).containsEntry("mouOverdueDays", 21);
        assertThat(put(M + "/settings", director, Map.of("mouOverdueDays", 14, "version", 0)).status()).isEqualTo(409);
        assertThat(put(M + "/settings", director, Map.of("mouOverdueDays", 14, "version", version(director))).status()).isEqualTo(200);
        assertChangeRecorded(admin, "MARKETING_SETTING", "mou_overdue_days", "value");
    }

    @Test
    void zoneManagerTeacherAndSystemAreRefusedAndNoTokenIs401() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(M + "/settings", token).status()).as(role + " read").isEqualTo(403);
            assertThat(put(M + "/settings", token, Map.of("mouOverdueDays", 10, "version", 0)).status()).as(role + " write").isEqualTo(403);
        }
        assertThat(get(M + "/settings", null).status()).isEqualTo(401);
    }
}
