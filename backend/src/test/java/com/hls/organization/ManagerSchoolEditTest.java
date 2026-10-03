package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User Story 3 / FR-006: a Manager sees and edits only assigned Schools, and only contact fields. */
class ManagerSchoolEditTest extends MasterDataTestBase {

    private Map<String, Object> body(Map<String, Object> current, String key, String value) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", current.get("name"));
        body.put("address", current.get("address"));
        body.put("contactPerson", current.get("contactPerson"));
        body.put("contactPhone", current.get("contactPhone"));
        body.put("billingContact", current.get("billingContact"));
        body.put("version", current.get("version"));
        body.put(key, value);
        return body;
    }

    @Test
    void anAssignedManagerListsAndOpensOnlyTheirSchoolsAndEditsContactFields() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        UUID[] theirs = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());

        Resp list = get("/api/v1/schools", manager.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(total(list)).isEqualTo(1);
        assertThat(list.body()).contains(mine[2].toString()).doesNotContain(theirs[2].toString());
        assertThat(get("/api/v1/schools/" + theirs[2], manager.token()).status()).isEqualTo(404);

        Map<String, Object> current = get("/api/v1/schools/" + mine[2], manager.token()).map();
        Resp edit = put("/api/v1/schools/" + mine[2], manager.token(), body(current, "contactPhone", "9222222222"));
        assertThat(edit.status()).isEqualTo(200);
        assertThat(edit.map()).containsEntry("contactPhone", "9222222222");
        Map<String, Object> next = edit.map();
        assertThat(put("/api/v1/schools/" + mine[2], manager.token(), body(next, "address", "New Address 9"))
                        .status())
                .isEqualTo(200);
    }

    @Test
    void aManagerChangingNameOrBillingContactIsForbiddenAndNothingChanges() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());
        Map<String, Object> current = get("/api/v1/schools/" + mine[2], manager.token()).map();

        Resp rename = put("/api/v1/schools/" + mine[2], manager.token(), body(current, "name", "Hijacked"));
        Resp billing = put("/api/v1/schools/" + mine[2], manager.token(), body(current, "billingContact", "x@evil"));

        assertThat(rename.status()).isEqualTo(403);
        assertThat(rename.body()).contains("contact person, phone, and address");
        assertThat(billing.status()).isEqualTo(403);
        Map<String, Object> after = get("/api/v1/schools/" + mine[2], admin).map();
        assertThat(after).containsEntry("name", current.get("name")).containsEntry("billingContact", current.get("billingContact"));
        // a Manager cannot move, deactivate or create either
        assertThat(put("/api/v1/schools/" + mine[2] + "/place", manager.token(), Map.of("placeId", mine[1], "version", 0))
                        .status())
                .isEqualTo(403);
        assertThat(post("/api/v1/schools/" + mine[2] + "/deactivate", manager.token(), null).status())
                .isEqualTo(403);
        assertThat(post("/api/v1/schools", manager.token(), Map.of("name", "X", "placeId", mine[1], "address", "y"))
                        .status())
                .isEqualTo(403);
    }

    @Test
    void aManagerCannotEditAnotherManagersSchool() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] mine = schoolInNewZone(admin);
        UUID[] theirs = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, mine[0]);
        assignSchoolManager(admin, mine[2], manager.managerId());
        Map<String, Object> foreign = get("/api/v1/schools/" + theirs[2], admin).map();

        Resp edit = put("/api/v1/schools/" + theirs[2], manager.token(), body(foreign, "contactPhone", "9333333333"));

        assertThat(edit.status()).isEqualTo(404);
    }
}
