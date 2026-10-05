package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The marketing calendar also shows the person's recruitment drives (spec 016) and never blocks a clash. */
class CalendarDrivesTest extends MarketingTestBase {

    private UUID driveFor(String admin, UUID interviewerUserId, String date) {
        Resp college = post("/api/v1/recruitment/colleges", admin, Map.of("name", uniqueName("Calendar College"), "city", "Chennai"));
        assertThat(college.status()).as(college.body()).isEqualTo(201);
        Resp drive = post(
                "/api/v1/recruitment/drives",
                admin,
                Map.of("collegeId", college.id(), "dates", List.of(date), "interviewerUserIds", List.of(interviewerUserId)));
        assertThat(drive.status()).as(drive.body()).isEqualTo(201);
        return drive.id();
    }

    @Test
    void theCalendarListsTheCallersDrivesNextToTheirVisitsAndAClashIsNotBlocked() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID drive = driveFor(admin, manager.signed().userId(), today.plusDays(3).toString());
        UUID otherDrive = driveFor(admin, signInAs(com.hls.identity.user.Role.MANAGER).userId(), today.plusDays(3).toString());
        UUID prospect = prospect(admin, zone);

        // a visit on the same day as the drive is accepted: the calendar shows both
        UUID visit = activity(manager.token(), prospect, today.plusDays(3));
        String range = "from=" + today + "&to=" + today.plusDays(30);

        String mine = get(M + "/activities?mine=true&" + range, manager.token()).body();
        assertThat(mine).contains(visit.toString(), drive.toString()).doesNotContain(otherDrive.toString());
        assertThat(mine).contains("CAMPUS_DRIVE");
    }

    @Test
    void theMarketingActivitiesInterfaceReturnsPlannedVisitsOnly() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        UUID planned = activity(admin, prospect, today.plusDays(2));
        UUID cancelled = activity(admin, prospect, today.plusDays(2));
        post(M + "/activities/" + cancelled + "/cancel", admin, Map.of("reason", "School closed"));

        String all = get(M + "/activities?from=" + today + "&to=" + today.plusDays(10), admin).body();

        assertThat(all).contains(planned.toString(), cancelled.toString());
        assertThat(plannedIds()).contains(planned).doesNotContain(cancelled);
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.hls.recruitment.api.MarketingActivities marketing;

    private List<UUID> plannedIds() {
        return marketing.plannedBetween(today, today.plusDays(10), null).stream().map(com.hls.recruitment.api.PlannedActivity::id).toList();
    }
}
