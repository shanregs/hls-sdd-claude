package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T017: each Manager task tile equals the count the Tasks list returns for the link's view. */
class TaskTilesTest extends TaskTestBase {

    private static final String AREA = "/api/v1/dashboard/areas/MANAGERS";

    private static final List<String> VIEWS = List.of("today", "upcoming", "overdue", "awaiting", "escalated");

    @SuppressWarnings("unchecked")
    private long figure(String token, String suffix, String query) {
        Resp tile = get(AREA + "/tiles/managers.tasks." + suffix + query, token);
        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        List<Map<String, Object>> values = (List<Map<String, Object>>) tile.map().get("values");
        return ((Number) values.get(0).get("value")).longValue();
    }

    private long listed(String token, String view) {
        Resp list = get(BASE + "?view=" + view + "&size=100", token);
        return content(list).size();
    }

    private void seed(String admin, UUID assignee, String managerToken) {
        newTask(admin, assignee, today());
        newTask(admin, assignee, today().plusDays(4));
        UUID overdue = newTask(admin, assignee, today().plusDays(1));
        moveDueDate(overdue, today().minusDays(2));
        UUID awaiting = newTask(admin, assignee, today().plusDays(2));
        startTask(managerToken, awaiting);
        completeTask(managerToken, awaiting);
    }

    @Test
    void everyTileEqualsTheTasksListForItsView() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        seed(admin, manager.signed().userId(), manager.token());

        for (String view : VIEWS) {
            assertThat(figure(manager.token(), view, "")).as("manager " + view).isEqualTo(listed(manager.token(), view));
        }
        assertThat(figure(manager.token(), "today", "")).isEqualTo(1);
        assertThat(figure(manager.token(), "overdue", "")).isEqualTo(1);
        assertThat(figure(manager.token(), "awaiting", "")).isEqualTo(1);
    }

    @Test
    void aManagerSeesOnlyOwnTasksAndTheZoneFilterNarrowsAdmin() {
        String admin = signInAs(Role.ADMIN).token();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx a = newManager(admin, zoneA);
        ManagerCtx b = newManager(admin, zoneB);
        newTask(admin, a.signed().userId(), today());
        newTask(admin, b.signed().userId(), today());
        newTask(admin, b.signed().userId(), today());

        assertThat(figure(a.token(), "today", "")).isEqualTo(1);
        assertThat(figure(b.token(), "today", "")).isEqualTo(2);
        assertThat(figure(admin, "today", "?zoneId=" + zoneA)).isEqualTo(1);
        assertThat(figure(admin, "today", "?zoneId=" + zoneB)).isEqualTo(2);
        assertThat(figure(admin, "today", "")).isGreaterThanOrEqualTo(3);
    }
}
