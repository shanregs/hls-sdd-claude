package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.recruitment.api.FollowUpScheduled;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** Visits: planned, completed with an outcome, rescheduled, cancelled; missed and follow-up overdue are derived. */
@Import(ActivityApiTest.Recorder.class)
class ActivityApiTest extends MarketingTestBase {

    /** The request runs on the server's thread, so events are recorded by a listener bean. */
    @TestConfiguration
    static class Recorder {
        final List<FollowUpScheduled> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(FollowUpScheduled event) {
            seen.add(event);
        }
    }

    @Autowired
    Recorder events;

    private Map<String, Object> view(String token, UUID activity) {
        return get(M + "/activities/" + activity, token).map();
    }

    @Test
    void completingNeedsAnOutcomeAndAFollowUpDatePublishesTheEvent() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.plusDays(1));

        assertThat(complete(admin, visit, " ", null).status()).isEqualTo(400);
        assertThat(complete(admin, visit, "Met the principal; wants a proposal", today.minusDays(5)).status()).isEqualTo(400);
        Resp done = complete(admin, visit, "Met the principal; wants a proposal", today.plusDays(10));

        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(done.map()).containsEntry("status", "COMPLETED");
        assertThat(events.seen).anyMatch(e -> e.activityId().equals(visit) && e.dueOn().equals(today.plusDays(10)) && e.title().startsWith("Follow up with"));
        assertThat(complete(admin, visit, "again", null).status()).isEqualTo(409);
    }

    @Test
    void aPlannedVisitPastItsDateIsMissedUntilRescheduledAndTheEarlierDateStays() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.minusDays(3));

        assertThat(view(admin, visit)).containsEntry("effectiveStatus", "MISSED").containsEntry("rescheduled", false);

        Resp moved = post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(2).toString()));
        assertThat(moved.status()).as(moved.body()).isEqualTo(200);
        assertThat(moved.map()).containsEntry("effectiveStatus", "PLANNED").containsEntry("rescheduled", true);
        assertThat(moved.body()).contains(today.minusDays(3).toString());
        assertThat(post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(2).toString())).status()).isEqualTo(409);
    }

    @Test
    void cancellingNeedsAReasonAndAClosedVisitCannotChange() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.plusDays(4));

        assertThat(post(M + "/activities/" + visit + "/cancel", admin, Map.of("reason", " ")).status()).isEqualTo(400);
        Resp cancelled = post(M + "/activities/" + visit + "/cancel", admin, Map.of("reason", "School closed"));
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.map()).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "School closed");
        assertThat(complete(admin, visit, "x", null).status()).isEqualTo(409);
        assertThat(post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(9).toString())).status()).isEqualTo(409);
    }

    @Test
    void followUpOverdueIsDerivedAndClearsWithALaterActivity() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.minusDays(10));
        complete(admin, visit, "Interested", today.minusDays(2));

        assertThat(view(admin, visit)).containsEntry("followUpOverdue", true);
        assertThat(get(M + "/prospects/" + prospect, admin).body()).contains("\"followUpOverdue\":true");

        activity(admin, prospect, today.plusDays(3));

        assertThat(view(admin, visit)).containsEntry("followUpOverdue", false);
        assertThat(get(M + "/prospects/" + prospect, admin).body()).contains("\"followUpOverdue\":false");
    }

    @Test
    void aVisitIsForAProspectOrASchoolNotBothAndAnAccountVisitNeedsNoProposal() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        UUID[] school = schoolInNewZone(admin);

        Map<String, Object> both = activityBody(prospect, "VISIT", today);
        both.put("schoolId", school[2]);
        assertThat(post(M + "/activities", admin, both).status()).isEqualTo(400);
        assertThat(post(M + "/activities", admin, Map.of("type", "VISIT", "date", today.toString())).status()).isEqualTo(400);
        assertThat(post(M + "/activities", admin, Map.of("prospectId", prospect, "type", "LUNCH", "date", today.toString())).status()).isEqualTo(400);
        Resp account = post(M + "/activities", admin, Map.of("schoolId", school[2], "type", "VISIT", "date", today.plusDays(1).toString()));
        assertThat(account.status()).as(account.body()).isEqualTo(201);
        assertThat(account.body()).contains(school[2].toString());
    }

    @Test
    void aZoneManagerReachesOnlyTheVisitsOfTheirZonesAndTeacherSystemAreRefused() {
        String admin = admin();
        UUID zoneA = zone(admin);
        UUID zoneB = zone(admin);
        ManagerCtx managerA = newManager(admin, zoneA);
        UUID visitA = activity(admin, prospect(admin, zoneA), today.plusDays(1));
        UUID visitB = activity(admin, prospect(admin, zoneB), today.plusDays(1));

        Resp list = get(M + "/activities", managerA.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains(visitA.toString()).doesNotContain(visitB.toString());
        assertThat(get(M + "/activities/" + visitB, managerA.token()).status()).isEqualTo(404);
        assertThat(complete(managerA.token(), visitB, "x", null).status()).isEqualTo(404);
        assertThat(post(M + "/activities/" + visitB + "/cancel", managerA.token(), Map.of("reason", "x")).status()).isEqualTo(404);
        assertThat(complete(managerA.token(), visitA, "Met them", null).status()).isEqualTo(200);
        UUID prospectB = prospect(admin, zoneB);
        assertThat(post(M + "/activities", managerA.token(), activityBody(prospectB, "CALL", today)).status()).isEqualTo(404);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(post(M + "/activities/" + visitA + "/cancel", signInAs(role).token(), Map.of("reason", "x")).status()).isEqualTo(403);
        }
        assertThat(get(M + "/activities", null).status()).isEqualTo(401);
    }

    @Test
    void theMineFilterAndTheDateRangeNarrowTheList() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID prospect = prospect(admin, zone);
        UUID mine = activity(manager.token(), prospect, today.plusDays(1));
        UUID others = activity(admin, prospect, today.plusDays(2));

        String body = get(M + "/activities?mine=true&from=" + today + "&to=" + today.plusDays(30), manager.token()).body();
        assertThat(body).contains(mine.toString()).doesNotContain(others.toString());
        LocalDate far = today.plusDays(300);
        assertThat(get(M + "/activities?from=" + far + "&to=" + far.plusDays(5), admin).body()).doesNotContain(mine.toString());
    }
}
