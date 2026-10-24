package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T045 and T046: the filters combine, the summary follows them, and the filter choices endpoint. */
class CalendarFilterTest extends ActivityCalendarTestBase {

    private static LocalDate jun(int day) {
        return LocalDate.of(2020, 6, day);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp, String key) {
        return (List<Map<String, Object>>) resp.map().get(key);
    }

    @Test
    void filtersCombineAndTheSummaryFollowsThem() {
        String admin = admin();
        UUID zone1 = zone(admin);
        UUID zone2 = zone(admin);
        ManagerCtx a = newManager(admin, zone1);
        ManagerCtx c = newManager(admin, zone2);
        UUID prospect1 = prospect(admin, zone1);
        LocalDate f = today.plusDays(30);

        UUID missedTask = task(admin, a.signed().userId(), f);
        moveTaskTo(missedTask, jun(2));
        UUID otherTask = task(admin, c.signed().userId(), f);
        moveTaskTo(otherTask, jun(3));
        UUID visit1 = activity(a.token(), prospect1, f);
        moveVisitTo(visit1, jun(9));
        UUID visit2 = activity(a.token(), prospect1, f);
        moveVisitTo(visit2, jun(9));

        String base = CAL + "?month=2020-06";
        Resp all = get(base, directorToken());
        assertThat(itemsOf(all, visit1)).hasSize(1);
        assertThat(itemsOf(all, visit2)).as("two items of one Manager on one day").hasSize(1);

        Resp taskMissed = get(base + "&kind=TASK&status=MISSED", directorToken());
        assertThat(taskMissed.status()).isEqualTo(200);
        assertThat(items(taskMissed)).isNotEmpty().allSatisfy(i -> {
            assertThat(i).containsEntry("kind", "TASK").containsEntry("status", "MISSED");
        });
        assertThat(summary(taskMissed).get("planned")).isEqualTo(items(taskMissed).size());
        assertThat(summary(taskMissed).get("missed")).isEqualTo(items(taskMissed).size());

        Resp byManager = get(base + "&managerId=" + a.signed().userId(), directorToken());
        assertThat(itemsOf(byManager, missedTask)).hasSize(1);
        assertThat(itemsOf(byManager, otherTask)).isEmpty();

        Resp byZone = get(base + "&zoneId=" + zone2, directorToken());
        assertThat(itemsOf(byZone, otherTask)).hasSize(1);
        assertThat(itemsOf(byZone, missedTask)).isEmpty();

        Resp combined = get(base + "&managerId=" + a.signed().userId() + "&zoneId=" + zone2, directorToken());
        assertThat(items(combined)).isEmpty();
    }

    @Test
    void badFilterValuesAnswer400() {
        assertThat(get(CAL + "?month=2020-06&kind=PARTY", admin()).status()).isEqualTo(400);
        assertThat(get(CAL + "?month=2020-06&status=LOST", admin()).status()).isEqualTo(400);
        assertThat(get(CAL + "?month=2020-13", admin()).status()).isEqualTo(400);
        assertThat(get(CAL + "?month=2020-06&managerId=nope", admin()).status()).isEqualTo(400);
    }

    @Test
    void theFilterChoicesListManagersZonesAndKinds() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        ManagerCtx b = newManager(admin, zone);

        Resp director = get(CAL + "/filters", directorToken());
        assertThat(director.status()).as(director.body()).isEqualTo(200);
        List<String> managerIds = list(director, "managers").stream().map(m -> (String) m.get("id")).toList();
        assertThat(managerIds).contains(a.signed().userId().toString(), b.signed().userId().toString());
        assertThat(list(director, "zones").stream().map(z -> z.get("id"))).contains(zone.toString());
        assertThat(list(director, "kinds")).hasSize(6);
        assertThat(list(director, "kinds").stream().filter(k -> "TRAINING".equals(k.get("kind"))).findFirst().orElseThrow())
                .containsEntry("available", false);
        assertThat(list(director, "kinds").stream().filter(k -> "TASK".equals(k.get("kind"))).findFirst().orElseThrow())
                .containsEntry("available", true);

        Resp mine = get(CAL + "/filters", a.token());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(list(mine, "managers")).hasSize(1);
        assertThat(list(mine, "managers").get(0)).containsEntry("id", a.signed().userId().toString());
    }

    @Test
    void aTeacherIsRefusedTheFilterChoices() {
        assertThat(get(CAL + "/filters", signInAs(com.hls.identity.user.Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(CAL + "/needs-attention", signInAs(com.hls.identity.user.Role.SYSTEM).token()).status()).isEqualTo(403);
    }
}
