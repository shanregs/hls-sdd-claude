package com.hls.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Amendment A8 to spec 005: principal and accountant contacts of a School, per role and per scope. */
class SchoolContactApiTest extends MasterDataTestBase {

    private static Map<String, Object> contact(String name, String phone, String email) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("phone", phone);
        c.put("email", email);
        return c;
    }

    private static Map<String, Object> both(Map<String, Object> principal, Map<String, Object> accountant) {
        Map<String, Object> body = new HashMap<>();
        body.put("principal", principal);
        body.put("accountant", accountant);
        return body;
    }

    @Test
    void anAdminSavesReadsChangesAndClearsBothContactsAndEachChangeIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        String url = "/api/v1/schools/" + school + "/contacts";

        assertThat(get(url, admin).map().get("principal")).isNull();

        Resp saved = put(url, admin, both(contact("Mrs. Rao", "9111111111", "rao@school.test"), contact("Mr. Iyer", "9222222222", null)));
        assertThat(saved.status()).isEqualTo(200);
        assertThat(get(url, admin).body()).contains("Mrs. Rao", "rao@school.test", "Mr. Iyer");
        assertChangeRecorded(admin, "SCHOOL", school, "principal");
        assertChangeRecorded(admin, "SCHOOL", school, "accountant");

        Resp cleared = put(url, admin, both(contact("Mrs. Rao", "9111111111", "rao@school.test"), null));
        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.map().get("accountant")).isNull();
        assertThat(cleared.map().get("principal")).isNotNull();
    }

    @Test
    void aContactWithoutANameOrWithATooLongPhoneIsRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        String url = "/api/v1/schools/" + school + "/contacts";

        assertThat(put(url, admin, both(contact(null, "9111111111", null), null)).status()).isEqualTo(400);
        assertThat(put(url, admin, both(contact("X", "9".repeat(21), null), null)).status()).isEqualTo(400);
        assertThat(get(url, admin).map().get("principal")).isNull();
    }

    @Test
    void aManagerEditsContactsOnlyForASchoolInTheirZonesAndSystemAndTeacherAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        UUID[] theirs = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());

        String own = "/api/v1/schools/" + mine[2] + "/contacts";
        String other = "/api/v1/schools/" + theirs[2] + "/contacts";
        assertThat(put(own, manager.token(), both(contact("Mrs. Rao", null, null), null)).status()).isEqualTo(200);
        assertThat(get(own, manager.token()).status()).isEqualTo(200);
        assertThat(get(other, manager.token()).status()).isEqualTo(404);
        assertThat(put(other, manager.token(), both(contact("Mrs. Rao", null, null), null)).status()).isEqualTo(404);

        assertThat(get(own, signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(own, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
    }
}
