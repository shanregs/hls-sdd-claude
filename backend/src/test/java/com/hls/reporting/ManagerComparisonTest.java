package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T021: the Manager-by-Manager table merges the module columns, filters by Zone, sorts, and is org-wide only. */
class ManagerComparisonTest extends TaskTestBase {

    private static final String URL = "/api/v1/dashboard/managers";

    @SuppressWarnings("unchecked")
    private static List<List<Map<String, Object>>> rows(Resp resp) {
        Map<String, Object> table = (Map<String, Object>) resp.map().get("table");
        return (List<List<Map<String, Object>>>) table.get("rows");
    }

    private static long cell(List<Map<String, Object>> row, int index) {
        return ((Number) row.get(index).get("value")).longValue();
    }

    @Test
    void theTableHasOneRowPerManagerInTheZoneWithTaskColumnsAndLinks() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zone = zone(admin);
        ManagerCtx one = newManager(admin, zone);
        ManagerCtx two = newManager(admin, zone);
        UUID late = newTask(admin, one.signed().userId(), today().plusDays(1));
        moveDueDate(late, today().minusDays(1));
        UUID late2 = newTask(admin, one.signed().userId(), today().plusDays(1));
        moveDueDate(late2, today().minusDays(3));
        newTask(admin, two.signed().userId(), today());

        Resp resp = get(URL + "?zoneId=" + zone + "&sort=tasks.overdue&dir=desc", admin);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        var rows = rows(resp);
        assertThat(rows).hasSize(2);
        assertThat(cell(rows.get(0), 1)).isEqualTo(2);
        assertThat(cell(rows.get(1), 1)).isEqualTo(0);
        assertThat(resp.body()).contains("assigneeUserId").contains(one.signed().userId().toString());

        Resp asc = get(URL + "?zoneId=" + zone + "&sort=tasks.overdue&dir=asc", admin);
        assertThat(cell(rows(asc).get(0), 1)).isEqualTo(0);
        assertThat(get(URL + "?sort=nonsense", admin).status()).isEqualTo(400);
    }

    @Test
    void onlyOrgWideCallersMayReadIt() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        assertThat(get(URL, manager.token()).status()).isEqualTo(403);
        assertThat(get(URL, signInAs(Role.DIRECTOR).token()).status()).isEqualTo(200);
        assertThat(get(URL, signInAs(Role.TEACHER).token()).status()).isEqualTo(404);
        assertThat(get(URL, null).status()).isEqualTo(401);
    }
}
