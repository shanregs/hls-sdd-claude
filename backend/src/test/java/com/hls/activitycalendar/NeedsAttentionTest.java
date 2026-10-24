package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T042: the Needs attention list (FR-009, FR-010). */
class NeedsAttentionTest extends ActivityCalendarTestBase {

    private static LocalDate may(int day) {
        return LocalDate.of(2020, 5, day);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(Resp resp) {
        return (List<Map<String, Object>>) resp.map().get("groups");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("entries");
    }

    private static Map<String, Object> groupOf(Resp resp, UUID user) {
        return groups(resp).stream().filter(g -> user.toString().equals(g.get("userId"))).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static UUID activityOf(Map<String, Object> entry) {
        return UUID.fromString((String) ((Map<String, Object>) entry.get("item")).get("activityId"));
    }

    @Test
    void missedAndRescheduledItemsAreGroupedByManagerOldestFirst() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        ManagerCtx b = newManager(admin, zone);
        ManagerCtx quiet = newManager(admin, zone);
        UUID aId = a.signed().userId();
        UUID bId = b.signed().userId();
        UUID prospect = prospect(admin, zone);
        LocalDate f = today.plusDays(30);

        UUID late = task(admin, aId, f);
        moveTaskTo(late, may(10));
        UUID older = task(admin, aId, f);
        moveTaskTo(older, may(4));
        UUID done = task(admin, aId, f);
        UUID movedTwice = activity(b.token(), prospect, f);
        assertThat(post(M + "/activities/" + movedTwice + "/reschedule", b.token(), Map.of("date", f.plusDays(3).toString())).status()).isEqualTo(200);
        assertThat(post(M + "/activities/" + movedTwice + "/reschedule", b.token(), Map.of("date", f.plusDays(6).toString())).status()).isEqualTo(200);
        moveVisitTo(movedTwice, may(20));
        UUID cancelled = activity(b.token(), prospect, f);
        assertThat(post(M + "/activities/" + cancelled + "/cancel", b.token(), Map.of("reason", "Closed")).status()).isEqualTo(200);
        moveVisitTo(cancelled, may(12));
        // a missed task later completed leaves the list (FR-010)
        assertThat(act(a.token(), done, "acknowledge").status()).isEqualTo(200);
        assertThat(act(a.token(), done, "start").status()).isEqualTo(200);
        assertThat(post("/api/v1/tasks/" + done + "/complete", a.token(), Map.of("note", "Visited and met the Principal")).status()).isEqualTo(200);
        moveTaskTo(done, may(6));
        taskCompletedAt(done, Instant.parse("2020-05-20T06:00:00Z"));

        Resp resp = get(CAL + "/needs-attention?from=2020-05-01&to=2020-05-31", directorToken());
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> ga = groupOf(resp, aId);
        assertThat(ga).isNotNull();
        assertThat(entries(ga).stream().map(NeedsAttentionTest::activityOf).toList()).containsExactly(older, late);
        assertThat(entries(ga).get(0).get("reasons")).isEqualTo(List.of("MISSED"));
        assertThat(entries(ga).get(0).get("daysLate")).isNotNull();
        assertThat(ga.get("missed")).isEqualTo(2);
        assertThat(groupOf(resp, quiet.signed().userId())).as("only Managers with an entry").isNull();
        Map<String, Object> gb = groupOf(resp, bId);
        assertThat(gb).isNotNull();
        List<UUID> bActivities = entries(gb).stream().map(NeedsAttentionTest::activityOf).toList();
        assertThat(bActivities).doesNotContain(cancelled);
        assertThat(bActivities.stream().filter(movedTwice::equals)).hasSize(1);
        List<String> names = groups(resp).stream().map(g -> (String) g.get("name")).toList();
        assertThat(names).isSorted();

        // a Manager sees only their own group
        Resp mine = get(CAL + "/needs-attention?from=2020-05-01&to=2020-05-31", a.token());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(groups(mine)).hasSize(1);
        assertThat(groups(mine).get(0).get("userId")).isEqualTo(aId.toString());
    }

    @Test
    void anEmptyPeriodIsReportedAndALongRangeIsRefused() {
        Resp empty = get(CAL + "/needs-attention?from=2019-01-01&to=2019-01-31", admin());
        assertThat(empty.status()).isEqualTo(200);
        assertThat(groups(empty)).isEmpty();
        assertThat(get(CAL + "/needs-attention?from=2019-01-01&to=2019-06-30", admin()).status()).isEqualTo(400);
    }
}
