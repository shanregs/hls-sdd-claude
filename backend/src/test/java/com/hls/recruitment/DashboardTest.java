package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardTest extends RecruitmentTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> row(Resp resp, UUID college) {
        return ((List<Map<String, Object>>) resp.map().get("colleges")).stream()
                .filter(r -> r.get("collegeId").equals(college.toString()))
                .findFirst()
                .map(r -> (Map<String, Object>) r.get("counts"))
                .orElseThrow();
    }

    private UUID driveWithSeason(String token, UUID college, String season) {
        Map<String, Object> body = driveBody(college, List.of(), today.minusDays(2));
        body.put("season", season);
        Resp resp = post(BASE + "/drives", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    @Test
    void countsPerCollegeMatchTheUnderlyingListsAndTheJoiningRatioIsJoinedOverSelected() {
        String admin = admin();
        String director = directorToken();
        String season = "S-" + UUID.randomUUID().toString().substring(0, 8);
        UUID collegeA = college(admin);
        UUID collegeB = college(admin);
        UUID driveA = driveWithSeason(admin, collegeA, season);
        UUID driveB = driveWithSeason(admin, collegeB, season);
        post(BASE + "/drives/" + driveA + "/status", admin, Map.of("status", "HELD"));

        UUID a1 = candidate(admin, driveA);
        UUID a2 = candidate(admin, driveA);
        UUID a3 = candidate(admin, driveA);
        UUID b1 = candidate(admin, driveB);
        outcome(admin, a1, "SELECTED");
        outcome(admin, a2, "SELECTED");
        outcome(admin, a3, "REJECTED");
        post(BASE + "/candidates/" + a1 + "/assessment", admin, Map.of("scores", Map.of("SPEAKING", 4)));
        UUID offer = issued(director, a1, "15000");
        issued(director, a2, "15000");
        post(BASE + "/offers/" + offer + "/accept", director, Map.of());
        assertThat(b1).isNotNull();

        Resp resp = get(BASE + "/dashboard?season=" + season, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> a = row(resp, collegeA);
        assertThat(a).containsEntry("drivesScheduled", 1).containsEntry("drivesHeld", 1).containsEntry("interviewed", 3)
                .containsEntry("assessed", 1).containsEntry("selected", 2).containsEntry("offered", 2).containsEntry("accepted", 1)
                .containsEntry("inducted", 0).containsEntry("joiningRatio", "0.50");
        Map<String, Object> b = row(resp, collegeB);
        assertThat(b).containsEntry("interviewed", 1).containsEntry("selected", 0).containsEntry("joiningRatio", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) resp.map().get("totals");
        assertThat(totals).containsEntry("interviewed", 4).containsEntry("drivesScheduled", 2);
        assertThat(get(BASE + "/candidates?drive=" + driveA, admin).body().split("\"id\"").length - 1).isEqualTo(3);
    }

    @Test
    void aSeasonLabelOrADateRangeLimitsWhichDrivesCount() {
        String admin = admin();
        String season = "Only-" + UUID.randomUUID().toString().substring(0, 8);
        UUID college = college(admin);
        driveWithSeason(admin, college, season);
        drive(admin, college, List.of(), today.plusDays(200));

        Resp bySeason = get(BASE + "/dashboard?season=" + season, admin);
        assertThat(row(bySeason, college)).containsEntry("drivesScheduled", 1);
        Resp byRange = get(BASE + "/dashboard?from=" + today.plusDays(190) + "&to=" + today.plusDays(210), admin);
        assertThat(row(byRange, college)).containsEntry("drivesScheduled", 1);
        Resp none = get(BASE + "/dashboard?season=" + season + "-nothing", admin);
        assertThat(none.status()).isEqualTo(200);
        assertThat(none.body()).contains("\"colleges\":[]");
    }

    @Test
    void aZoneManagerSeesAllCountsWithAMineFilterAndTeacherSystemAreRefused() {
        String admin = admin();
        Signed manager = signInAs(Role.MANAGER);
        String season = "M-" + UUID.randomUUID().toString().substring(0, 8);
        UUID college = college(admin);
        driveWithSeason(admin, college, season);
        Map<String, Object> mine = driveBody(college, List.of(), today.minusDays(1));
        mine.put("season", season);
        assertThat(post(BASE + "/drives", manager.token(), mine).status()).isEqualTo(201);

        assertThat(row(get(BASE + "/dashboard?season=" + season, manager.token()), college)).containsEntry("drivesScheduled", 2);
        assertThat(row(get(BASE + "/dashboard?season=" + season + "&mine=true", manager.token()), college)).containsEntry("drivesScheduled", 1);
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(BASE + "/dashboard", signInAs(role).token()).status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void anEmptyDashboardReturnsZeros() {
        Resp resp = get(BASE + "/dashboard?season=" + "none-" + UUID.randomUUID(), admin());

        assertThat(resp.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) resp.map().get("totals");
        assertThat(totals).containsEntry("interviewed", 0).containsEntry("accepted", 0).containsEntry("joiningRatio", null);
    }
}
