package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.api.RecruitmentFigures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014c A1: the public figures equal the Recruitment dashboard of spec 016 for the same season. */
class RecruitmentFiguresTest extends RecruitmentTestBase {

    @Autowired
    private RecruitmentFigures figures;

    @Test
    @SuppressWarnings("unchecked")
    void theFiguresEqualTheDashboardForASeededSeason() {
        String admin = admin();
        String director = directorToken();
        String season = "F-" + UUID.randomUUID().toString().substring(0, 8);
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

        Resp http = get(BASE + "/dashboard?season=" + season, admin);
        RecruitmentFigures.Result result = figures.bySeason(season);

        assertThat(http.status()).as(http.body()).isEqualTo(200);
        List<Map<String, Object>> colleges = (List<Map<String, Object>>) http.map().get("colleges");
        assertThat(result.colleges()).hasSize(colleges.size());
        for (Map<String, Object> row : colleges) {
            RecruitmentFigures.CollegeRow ours = result.colleges().stream()
                    .filter(c -> c.collegeId().toString().equals(row.get("collegeId")))
                    .findFirst()
                    .orElseThrow();
            same(ours.counts(), (Map<String, Object>) row.get("counts"));
        }
        same(result.totals(), (Map<String, Object>) http.map().get("totals"));
        assertThat(result.readyToDeployTotal()).isEqualTo(((Number) http.map().get("readyToDeployTotal")).longValue());
        assertThat(result.totals().interviewed()).isEqualTo(4);
        assertThat(result.totals().selected()).isEqualTo(2);
        assertThat(result.totals().joiningRatio()).isEqualTo("0.50");
        assertThat(figures.bySeason(season + "-none").colleges()).isEmpty();
        assertThat(figures.bySeason(season.toLowerCase()).totals().interviewed()).isEqualTo(4);
    }

    private UUID driveWithSeason(String token, UUID college, String season) {
        Map<String, Object> body = driveBody(college, List.of(), today.minusDays(2));
        body.put("season", season);
        Resp resp = post(BASE + "/drives", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    private static void same(RecruitmentFigures.Counts c, Map<String, Object> m) {
        assertThat(c.drivesScheduled()).isEqualTo(((Number) m.get("drivesScheduled")).longValue());
        assertThat(c.drivesHeld()).isEqualTo(((Number) m.get("drivesHeld")).longValue());
        assertThat(c.interviewed()).isEqualTo(((Number) m.get("interviewed")).longValue());
        assertThat(c.assessed()).isEqualTo(((Number) m.get("assessed")).longValue());
        assertThat(c.selected()).isEqualTo(((Number) m.get("selected")).longValue());
        assertThat(c.offered()).isEqualTo(((Number) m.get("offered")).longValue());
        assertThat(c.accepted()).isEqualTo(((Number) m.get("accepted")).longValue());
        assertThat(c.inducted()).isEqualTo(((Number) m.get("inducted")).longValue());
        assertThat(c.readyToDeploy()).isEqualTo(((Number) m.get("readyToDeploy")).longValue());
        assertThat(c.placed()).isEqualTo(((Number) m.get("placed")).longValue());
        assertThat(c.active()).isEqualTo(((Number) m.get("active")).longValue());
        assertThat(c.joiningRatio()).isEqualTo(m.get("joiningRatio"));
    }
}
