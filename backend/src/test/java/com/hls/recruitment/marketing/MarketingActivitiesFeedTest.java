package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.designation.api.BusinessDate;
import com.hls.recruitment.api.ActivityEntry;
import com.hls.recruitment.api.MarketingActivities;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 035 T008: the second feed method returns planned, completed and cancelled visits with the new facts. */
class MarketingActivitiesFeedTest extends MarketingTestBase {

    @Autowired
    MarketingActivities feed;

    @Autowired
    BusinessDate businessDate;

    private ActivityEntry entry(UUID id, UUID user) {
        return feed.activitiesBetween(today.minusDays(40), today.plusDays(40), user).stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Test
    void plannedCompletedAndCancelledVisitsAreReturnedWithCompletionAndZone() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        UUID planned = activity(admin, prospect, today.plusDays(2));
        UUID completed = activity(admin, prospect, today.plusDays(3));
        UUID cancelled = activity(admin, prospect, today.plusDays(4));
        assertThat(complete(admin, completed, "Met the principal", null).status()).isEqualTo(200);
        assertThat(post(M + "/activities/" + cancelled + "/cancel", admin, Map.of("reason", "School closed")).status())
                .isEqualTo(200);

        ActivityEntry p = entry(planned, null);
        ActivityEntry c = entry(completed, null);
        ActivityEntry x = entry(cancelled, null);
        assertThat(p.status()).isEqualTo("PLANNED");
        assertThat(p.completedOn()).isNull();
        assertThat(p.kind()).isEqualTo("SCHOOL_VISIT");
        assertThat(p.zoneId()).isEqualTo(zone);
        assertThat(p.place()).isNotBlank();
        assertThat(p.schoolId()).isNull();
        assertThat(c.status()).isEqualTo("COMPLETED");
        assertThat(c.completedOn()).isEqualTo(businessDate.today());
        assertThat(x.status()).isEqualTo("CANCELLED");
        assertThat(x.completedOn()).isNull();
    }

    @Test
    void aVisitCompletedBeforeTheAmendmentHasNoCompletionDate() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.plusDays(2));
        assertThat(complete(admin, visit, "Done", null).status()).isEqualTo(200);
        jdbc.update("update marketing_activity set completed_on = null where id = ?", visit);

        ActivityEntry e = entry(visit, null);
        assertThat(e.status()).isEqualTo("COMPLETED");
        assertThat(e.completedOn()).isNull();
    }

    @Test
    void originalDateIsTheFirstPlannedDateAfterOneAndTwoMoves() {
        String admin = admin();
        UUID prospect = prospect(admin, zone(admin));
        UUID visit = activity(admin, prospect, today.plusDays(2));
        assertThat(entry(visit, null).originalDate()).isNull();

        assertThat(post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(5).toString())).status())
                .isEqualTo(200);
        assertThat(entry(visit, null).originalDate()).isEqualTo(today.plusDays(2));
        assertThat(entry(visit, null).date()).isEqualTo(today.plusDays(5));

        assertThat(post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(9).toString())).status())
                .isEqualTo(200);
        assertThat(entry(visit, null).originalDate()).isEqualTo(today.plusDays(2));
    }

    @Test
    void attendeesAreListedAndAUserFilterMatchesCreatorOrAttendee() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx a = newManager(admin, zone);
        ManagerCtx b = newManager(admin, zone);
        UUID prospect = prospect(admin, zone);
        Map<String, Object> body = activityBody(prospect, "VISIT", today.plusDays(2));
        body.put("attendeeUserIds", List.of(a.signed().userId(), b.signed().userId()));
        Resp created = post(M + "/activities", a.token(), body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID visit = created.id();
        UUID others = activity(admin, prospect, today.plusDays(2));

        ActivityEntry e = entry(visit, null);
        assertThat(e.ownerUserId()).isEqualTo(a.signed().userId());
        assertThat(e.attendeeUserIds()).contains(a.signed().userId(), b.signed().userId());
        assertThat(entry(visit, a.signed().userId())).isNotNull();
        assertThat(entry(visit, b.signed().userId())).isNotNull();
        assertThat(entry(others, a.signed().userId())).isNull();
    }

    @Test
    void noNotesOutcomeOrAttachmentsLeaveTheModule() {
        assertThat(ActivityEntry.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("notes", "outcome", "attachments", "cancelReason");
    }
}
