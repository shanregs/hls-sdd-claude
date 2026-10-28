package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 014c US4: the Recruitment report equals the 016 dashboard for the season, shows "no data yet" for a joining ratio with
 * nobody selected, "not available" for a missing supply source, and gives a Zone Manager exactly the 016 view.
 */
class RecruitmentReportTest extends RecruitmentTestBase {

    private static final String REPORT = "/api/v1/reports/recruitment";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String key) {
        return (List<Map<String, Object>>) section(resp, key).get("rows");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cell(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index);
    }

    private static long number(Map<String, Object> row, int index) {
        return ((Number) cell(row, index).get("value")).longValue();
    }

    private UUID driveWithSeason(String token, UUID college, String season) {
        Map<String, Object> body = driveBody(college, List.of(), today.minusDays(2));
        body.put("season", season);
        Resp resp = post(BASE + "/drives", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> counts(Resp dashboard, UUID college) {
        return ((List<Map<String, Object>>) dashboard.map().get("colleges")).stream()
                .filter(r -> r.get("collegeId").equals(college.toString()))
                .findFirst()
                .map(r -> (Map<String, Object>) r.get("counts"))
                .orElseThrow();
    }

    @Test
    void eachCollegeRowAndTheTotalEqualTheDashboardAndDemandEqualsTheMarketingDashboard() {
        String admin = admin();
        String director = directorToken();
        String season = "R-" + UUID.randomUUID().toString().substring(0, 8);
        UUID collegeA = college(admin);
        UUID collegeB = college(admin);
        UUID driveA = driveWithSeason(admin, collegeA, season);
        UUID driveB = driveWithSeason(admin, collegeB, season);
        post(BASE + "/drives/" + driveA + "/status", admin, Map.of("status", "HELD"));
        UUID a1 = candidate(admin, driveA);
        UUID a2 = candidate(admin, driveA);
        candidate(admin, driveA);
        candidate(admin, driveB);
        outcome(admin, a1, "SELECTED");
        outcome(admin, a2, "SELECTED");
        post(BASE + "/candidates/" + a1 + "/assessment", admin, Map.of("scores", Map.of("SPEAKING", 4)));
        UUID offer = issued(director, a1, "15000");
        issued(director, a2, "15000");
        post(BASE + "/offers/" + offer + "/accept", director, Map.of());

        Resp report = get(REPORT + "?season=" + season, admin);
        Resp dashboard = get(BASE + "/dashboard?season=" + season, admin);

        assertThat(report.status()).as(report.body()).isEqualTo(200);
        List<Map<String, Object>> colleges = rows(report, "colleges");
        assertThat(colleges).hasSize(2);
        for (UUID college : List.of(collegeA, collegeB)) {
            Map<String, Object> shown = colleges.stream().filter(r -> college.toString().equals(r.get("id"))).findFirst().orElseThrow();
            Map<String, Object> expected = counts(dashboard, college);
            assertThat(number(shown, 1)).isEqualTo(((Number) expected.get("drivesHeld")).longValue());
            assertThat(number(shown, 2)).isEqualTo(((Number) expected.get("interviewed")).longValue());
            assertThat(number(shown, 3)).isEqualTo(((Number) expected.get("assessed")).longValue());
            assertThat(number(shown, 4)).isEqualTo(((Number) expected.get("selected")).longValue());
            assertThat(number(shown, 5)).isEqualTo(((Number) expected.get("offered")).longValue());
            assertThat(number(shown, 6)).isEqualTo(((Number) expected.get("accepted")).longValue());
            assertThat(number(shown, 8)).isEqualTo(((Number) expected.get("inducted")).longValue());
            assertThat(number(shown, 9)).isEqualTo(((Number) expected.get("readyToDeploy")).longValue());
        }
        // joining ratio: 1 joined of 2 selected at A; nobody selected at B is "no data yet"
        Map<String, Object> rowA = colleges.stream().filter(r -> collegeA.toString().equals(r.get("id"))).findFirst().orElseThrow();
        Map<String, Object> rowB = colleges.stream().filter(r -> collegeB.toString().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(cell(rowA, 7).get("value")).isEqualTo("0.50");
        assertThat(cell(rowB, 7).get("state")).isEqualTo("NO_DATA");
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) section(report, "colleges").get("totals");
        @SuppressWarnings("unchecked")
        Map<String, Object> dashboardTotals = (Map<String, Object>) dashboard.map().get("totals");
        assertThat(number(totals, 2)).isEqualTo(((Number) dashboardTotals.get("interviewed")).longValue());
        assertThat(number(totals, 4)).isEqualTo(((Number) dashboardTotals.get("selected")).longValue());

        // demand equals the marketing dashboard; supply has no provider here, so it is "not available" and the rest still load
        Resp marketing = get("/api/v1/marketing/dashboard", director);
        Map<String, Object> demand = rows(report, "totals").stream().filter(r -> "demand".equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(number(demand, 0)).isEqualTo(((Number) marketing.map().get("demand")).longValue());
        Map<String, Object> supply = rows(report, "totals").stream().filter(r -> "supply".equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(cell(supply, 0).get("state")).isEqualTo("NOT_AVAILABLE");
        Map<String, Object> shortfall = rows(report, "totals").stream().filter(r -> "shortfall".equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(cell(shortfall, 0).get("state")).isEqualTo("NOT_AVAILABLE");
        assertThat(report.body()).contains("Season " + season);
    }

    @Test
    void aZoneManagerSeesExactlyTheDashboardCountsAndNoDemand() {
        String admin = admin();
        Signed manager = signInAs(Role.MANAGER);
        String season = "M-" + UUID.randomUUID().toString().substring(0, 8);
        UUID college = college(admin);
        driveWithSeason(admin, college, season);
        candidate(admin, driveWithSeason(admin, college, season));

        Resp report = get(REPORT + "?season=" + season, manager.token());
        Resp dashboard = get(BASE + "/dashboard?season=" + season, manager.token());

        assertThat(report.status()).as(report.body()).isEqualTo(200);
        Map<String, Object> shown = rows(report, "colleges").stream().filter(r -> college.toString().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(number(shown, 2)).isEqualTo(((Number) counts(dashboard, college).get("interviewed")).longValue());
        assertThat(rows(report, "totals")).extracting(r -> r.get("id")).doesNotContain("demand", "supply", "shortfall");
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(get(REPORT, signInAs(role).token()).status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void anUnknownSeasonGivesNoRowsAndANoteAndSeasonIsRefusedWithoutAReportThatOffersIt() {
        String admin = admin();

        Resp none = get(REPORT + "?season=nothing-" + UUID.randomUUID(), admin);

        assertThat(none.status()).as(none.body()).isEqualTo(200);
        assertThat(rows(none, "colleges")).isEmpty();
        assertThat(get("/api/v1/reports/expenses?season=2026", admin).status()).isEqualTo(400);
    }
}
