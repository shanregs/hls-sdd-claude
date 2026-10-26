package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 035 T032 (SC-002, SC-007): the hand-counted month of quickstart section 1. */
class CalendarMonthIntegrationTest extends ActivityCalendarTestBase {

    private static LocalDate mar(int day) {
        return LocalDate.of(2020, 3, day);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> counts(Map<String, Object> summary, String kind) {
        return ((Map<String, Map<String, Object>>) summary.get("byKind")).get(kind);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> manager(Map<String, Object> summary, UUID userId) {
        return ((List<Map<String, Object>>) summary.get("byManager")).stream()
                .filter(m -> userId.toString().equals(m.get("userId")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theHandCountedMonthGivesTheExpectedStatusesAndTotals() {
        String admin = admin();
        UUID zone1 = zone(admin);
        UUID zone2 = zone(admin);
        ManagerCtx a = newManager(admin, zone1);
        ManagerCtx b = newManager(admin, zone1);
        ManagerCtx c = newManager(admin, zone2);
        UUID prospect1 = prospect(admin, zone1);
        LocalDate f = today.plusDays(30);

        // V1: a visit done on the 5th
        UUID v1 = activity(a.token(), prospect1, f);
        assertThat(complete(a.token(), v1, "Met the principal", null).status()).isEqualTo(200);
        moveVisitTo(v1, mar(5));
        jdbc.update("update marketing_activity set completed_on = ? where id = ?", mar(5), v1);

        // D1: a drive held on the 8th (scheduled by A)
        UUID college = college(admin);
        UUID d1 = drive(a.token(), college, List.of(a.signed().userId()), f);
        assertThat(post("/api/v1/recruitment/drives/" + d1 + "/status", a.token(), Map.of("status", "HELD")).status()).isEqualTo(200);
        jdbc.update("update campus_drive_date set drive_date = ? where drive_id = ?", mar(8), d1);
        jdbc.update("update campus_drive set held_on = ? where id = ?", mar(8), d1);

        // T1: a task due on the 10th left open
        UUID t1 = task(admin, a.signed().userId(), f);
        moveTaskTo(t1, mar(10));

        // V2: a visit moved from the 14th to the 20th
        UUID v2 = activity(a.token(), prospect1, f);
        assertThat(post(M + "/activities/" + v2 + "/reschedule", a.token(), Map.of("date", f.plusDays(3).toString())).status())
                .isEqualTo(200);
        moveVisitTo(v2, mar(20));
        jdbc.update("update activity_date_history set old_date = ?, new_date = ? where activity_id = ?", mar(14), mar(20), v2);

        // V3: a cancelled visit
        UUID v3 = activity(a.token(), prospect1, f);
        assertThat(post(M + "/activities/" + v3 + "/cancel", a.token(), Map.of("reason", "School closed")).status()).isEqualTo(200);
        moveVisitTo(v3, mar(22));

        // D2: a 3-date drive of B
        UUID d2 = drive(b.token(), college, List.of(), f, f.plusDays(1), f.plusDays(2));
        jdbc.update("update campus_drive_date set drive_date = ? where drive_id = ? and drive_date = ?", mar(25), d2, f);
        jdbc.update("update campus_drive_date set drive_date = ? where drive_id = ? and drive_date = ?", mar(26), d2, f.plusDays(1));
        jdbc.update("update campus_drive_date set drive_date = ? where drive_id = ? and drive_date = ?", mar(27), d2, f.plusDays(2));

        // V4: a visit with A and B as attendees, done on the 12th
        Map<String, Object> shared = activityBody(prospect1, "VISIT", f);
        shared.put("attendeeUserIds", List.of(a.signed().userId(), b.signed().userId()));
        Resp sharedResp = post(M + "/activities", a.token(), shared);
        assertThat(sharedResp.status()).as(sharedResp.body()).isEqualTo(201);
        UUID v4 = sharedResp.id();
        assertThat(complete(a.token(), v4, "Joint visit", null).status()).isEqualTo(200);
        moveVisitTo(v4, mar(12));
        jdbc.update("update marketing_activity set completed_on = ? where id = ?", mar(12), v4);

        // T3: a task of B completed late (due on the 15th, completed on the 29th)
        UUID t3 = task(admin, b.signed().userId(), f);
        assertThat(act(b.token(), t3, "acknowledge").status()).isEqualTo(200);
        assertThat(act(b.token(), t3, "start").status()).isEqualTo(200);
        assertThat(post("/api/v1/tasks/" + t3 + "/complete", b.token(), Map.of("note", "Visited and met the Principal")).status()).isEqualTo(200);
        moveTaskTo(t3, mar(15));
        taskCompletedAt(t3, Instant.parse("2020-03-29T06:00:00Z"));

        // T2: an open task of C with no place, due on the 18th
        UUID t2 = task(admin, c.signed().userId(), f);
        moveTaskTo(t2, mar(18));

        Resp resp = calendar(directorToken(), "2020-03");
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);

        assertThat(item(resp, v1)).containsEntry("status", "DONE").containsEntry("doneLate", false);
        assertThat(item(resp, d1)).containsEntry("status", "DONE");
        assertThat(item(resp, t1)).containsEntry("status", "MISSED");
        assertThat(item(resp, t1).get("daysLate")).isNotNull();
        assertThat(item(resp, v2)).containsEntry("status", "MISSED").containsEntry("rescheduled", true)
                .containsEntry("originalDate", "2020-03-14").containsEntry("date", "2020-03-20");
        assertThat(item(resp, v2).get("daysMoved")).isEqualTo(6);
        assertThat(item(resp, v3)).containsEntry("status", "CANCELLED");
        assertThat(itemsOf(resp, d2)).hasSize(3).allSatisfy(i -> assertThat(i).containsEntry("status", "MISSED"));
        assertThat(item(resp, v4)).containsEntry("status", "DONE");
        assertThat(item(resp, t3)).containsEntry("status", "DONE").containsEntry("doneLate", true)
                .containsEntry("completedOn", "2020-03-29");
        assertThat(item(resp, t2)).containsEntry("status", "MISSED");
        assertThat(item(resp, t2).get("place")).isNull();

        // zones: the prospect's Zone for a visit, the owner's Zone otherwise
        assertThat(item(resp, v1).get("zoneId")).isEqualTo(zone1.toString());
        assertThat(item(resp, t1).get("zoneId")).isEqualTo(zone1.toString());
        assertThat(item(resp, t2).get("zoneId")).isEqualTo(zone2.toString());

        // the owning-screen link is present for Director (holds the grants) and never carries text of the activity
        assertThat(item(resp, v1).get("link")).isEqualTo(Map.of("screen", "MARKETING", "path", "/marketing/calendar"));
        assertThat(resp.body()).doesNotContain("Met the principal", "Joint visit", "School closed");

        // the shared visit has both Managers as people
        assertThat((List<?>) item(resp, v4).get("people")).hasSize(2);

        // 11 items, 8 distinct activities that count as planned, no item counted twice
        assertThat(items(resp)).hasSize(11);
        Map<String, Object> s = summary(resp);
        assertThat(s).containsEntry("planned", 8).containsEntry("done", 4).containsEntry("doneLate", 1)
                .containsEntry("open", 0).containsEntry("missed", 4).containsEntry("cancelled", 1).containsEntry("rescheduled", 1);
        assertThat(counts(s, "SCHOOL_VISIT")).containsEntry("planned", 3).containsEntry("done", 2).containsEntry("missed", 1)
                .containsEntry("cancelled", 1).containsEntry("rescheduled", 1);
        assertThat(counts(s, "CAMPUS_DRIVE")).containsEntry("planned", 2).containsEntry("done", 1).containsEntry("missed", 1);
        assertThat(counts(s, "TASK")).containsEntry("planned", 3).containsEntry("done", 1).containsEntry("doneLate", 1)
                .containsEntry("missed", 2);

        // the shared visit is under both Managers, once in the totals
        assertThat(manager(s, a.signed().userId())).containsEntry("planned", 5).containsEntry("done", 3)
                .containsEntry("missed", 2).containsEntry("cancelled", 1).containsEntry("rescheduled", 1);
        assertThat(manager(s, b.signed().userId())).containsEntry("planned", 3).containsEntry("done", 2)
                .containsEntry("doneLate", 1).containsEntry("missed", 1);
        assertThat(manager(s, c.signed().userId())).containsEntry("planned", 1).containsEntry("missed", 1);

        // every source was read
        assertThat(resp.map().get("partial")).isEqualTo(false);
        assertThat(resp.body()).contains("\"group\":\"TRAINING\"").doesNotContain("not included yet");
    }

    @Test
    void aChangeInAnOwningScreenShowsOnTheNextLoadAndAFutureItemIsOpen() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        UUID prospect = prospect(admin, zone);
        LocalDate date = today.plusYears(3).withDayOfMonth(10);
        String month = java.time.YearMonth.from(date).toString();
        UUID visit = activity(a.token(), prospect, date);

        Resp before = calendar(admin, month);
        assertThat(item(before, visit)).containsEntry("status", "PLANNED");
        assertThat(summary(before)).containsEntry("open", 1).containsEntry("planned", 1);

        assertThat(post(M + "/activities/" + visit + "/reschedule", a.token(), Map.of("date", date.plusDays(2).toString())).status())
                .isEqualTo(200);
        Resp moved = calendar(admin, month);
        assertThat(item(moved, visit)).containsEntry("rescheduled", true).containsEntry("originalDate", date.toString());

        assertThat(complete(a.token(), visit, "Done", null).status()).isEqualTo(200);
        Resp done = calendar(admin, month);
        assertThat(item(done, visit)).containsEntry("status", "DONE");
        assertThat(summary(done)).containsEntry("done", 1).containsEntry("open", 0);
    }

    @Test
    void anEmptyMonthReturnsZeroCountsAndNoItems() {
        Resp resp = calendar(admin(), "2001-01");
        assertThat(resp.status()).isEqualTo(200);
        assertThat(items(resp)).isEmpty();
        assertThat(summary(resp)).containsEntry("planned", 0).containsEntry("done", 0).containsEntry("missed", 0)
                .containsEntry("cancelled", 0).containsEntry("rescheduled", 0);
    }

}
