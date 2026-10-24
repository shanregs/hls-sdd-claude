package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T039 (SC-004): a Manager never sees another Manager's items, whatever the filters. */
class CalendarScopeMatrixTest extends ActivityCalendarTestBase {

    private static LocalDate apr(int day) {
        return LocalDate.of(2020, 4, day);
    }

    @SuppressWarnings("unchecked")
    private static boolean involves(Map<String, Object> item, UUID user) {
        return ((List<Map<String, Object>>) item.get("people")).stream().anyMatch(p -> user.toString().equals(p.get("userId")));
    }

    @Test
    void aManagerSeesOnlyOwnItemsForEveryFilterCombination() {
        String admin = admin();
        UUID zone1 = zone(admin);
        UUID zone2 = zone(admin);
        ManagerCtx a = newManager(admin, zone1);
        ManagerCtx b = newManager(admin, zone1);
        ManagerCtx c = newManager(admin, zone2);
        UUID aId = a.signed().userId();
        UUID bId = b.signed().userId();
        UUID cId = c.signed().userId();
        UUID prospect1 = prospect(admin, zone1);
        LocalDate f = today.plusDays(30);

        UUID taskA = task(admin, aId, f);
        moveTaskTo(taskA, apr(3));
        UUID taskB = task(admin, bId, f);
        moveTaskTo(taskB, apr(4));
        UUID taskC = task(admin, cId, f);
        moveTaskTo(taskC, apr(5));
        UUID visitB = activity(b.token(), prospect1, f);
        moveVisitTo(visitB, apr(6));
        Map<String, Object> shared = activityBody(prospect1, "VISIT", f);
        shared.put("attendeeUserIds", List.of(aId, bId));
        Resp sharedResp = post(M + "/activities", b.token(), shared);
        assertThat(sharedResp.status()).as(sharedResp.body()).isEqualTo(201);
        UUID sharedVisit = sharedResp.id();
        moveVisitTo(sharedVisit, apr(7));

        // A sees the own task and the shared visit, never B's or C's
        Resp mine = calendar(a.token(), "2020-04");
        assertThat(mine.status()).isEqualTo(200);
        assertThat(itemsOf(mine, taskA)).hasSize(1);
        assertThat(itemsOf(mine, sharedVisit)).hasSize(1);
        assertThat(itemsOf(mine, taskB)).isEmpty();
        assertThat(itemsOf(mine, taskC)).isEmpty();
        assertThat(itemsOf(mine, visitB)).isEmpty();
        assertThat(summary(mine)).doesNotContainKey("byManager");

        // B sees the shared visit too, and the Director sees everything
        assertThat(itemsOf(calendar(b.token(), "2020-04"), sharedVisit)).hasSize(1);
        assertThat(itemsOf(calendar(directorToken(), "2020-04"), taskC)).hasSize(1);

        List<String> managers = Arrays.asList(null, aId.toString(), bId.toString(), cId.toString());
        List<String> zones = Arrays.asList(null, zone1.toString(), zone2.toString());
        List<String> kinds = Arrays.asList(null, "TASK", "SCHOOL_VISIT");
        List<String> statuses = Arrays.asList(null, "MISSED", "DONE");
        for (String m : managers) {
            for (String z : zones) {
                for (String k : kinds) {
                    for (String s : statuses) {
                        String url = CAL + "?month=2020-04" + param("managerId", m) + param("zoneId", z) + param("kind", k) + param("status", s);
                        Resp resp = get(url, a.token());
                        if (m != null && !m.equals(aId.toString())) {
                            assertThat(resp.status()).as(url).isEqualTo(404);
                            continue;
                        }
                        assertThat(resp.status()).as(url + resp.body()).isEqualTo(200);
                        for (Map<String, Object> item : items(resp)) {
                            assertThat(involves(item, aId)).as(url).isTrue();
                            assertThat(involves(item, cId)).as(url).isFalse();
                        }
                        if (zone2.toString().equals(z)) {
                            assertThat(items(resp)).as("a Zone filter never widens: " + url).isEmpty();
                        }
                    }
                }
            }
        }
    }

    private static String param(String name, String value) {
        return value == null ? "" : "&" + name + "=" + value;
    }
}
