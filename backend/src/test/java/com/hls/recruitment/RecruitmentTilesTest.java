package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T029: the Recruitment tiles equal the Recruitment dashboard for the same month; no data is not 0. */
class RecruitmentTilesTest extends RecruitmentTestBase {

    private static final String D = "/api/v1/dashboard/areas/RECRUITMENT/tiles/";

    @SuppressWarnings("unchecked")
    private static long value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return ((Number) v.get("value")).longValue();
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    @SuppressWarnings("unchecked")
    @Test
    void theFunnelAndReadyToDeployEqualTheRecruitmentDashboard() {
        String admin = admin();
        String director = directorToken();
        UUID college = college(admin);
        Map<String, Object> body = driveBody(college, List.of(), today);
        UUID drive = post(BASE + "/drives", admin, body).id();
        post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD"));
        UUID c1 = candidate(admin, drive);
        UUID c2 = candidate(admin, drive);
        outcome(admin, c1, "SELECTED");
        outcome(admin, c2, "SELECTED");
        UUID offer = issued(director, c1, "15000");
        issued(director, c2, "15000");
        post(BASE + "/offers/" + offer + "/accept", director, Map.of());

        YearMonth month = YearMonth.from(today);
        Map<String, Object> dash = get(BASE + "/dashboard?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth(), admin).map();
        Map<String, Object> totals = (Map<String, Object>) dash.get("totals");

        Resp funnel = get(D + "recruitment.funnel", admin);
        assertThat(funnel.status()).as(funnel.body()).isEqualTo(200);
        assertThat(value(funnel, "Drives held")).isEqualTo(((Number) totals.get("drivesHeld")).longValue());
        assertThat(value(funnel, "Interviewed")).isEqualTo(((Number) totals.get("interviewed")).longValue());
        assertThat(value(funnel, "Offers issued")).isEqualTo(((Number) totals.get("offered")).longValue());
        assertThat(value(funnel, "Joined")).isEqualTo(((Number) totals.get("accepted")).longValue());

        Resp ratio = get(D + "recruitment.joiningRatio", admin);
        assertThat(ratio.map().get("state")).isEqualTo("OK");
        assertThat(value(ratio, "Joining ratio")).isEqualTo(Math.round(Double.parseDouble((String) totals.get("joiningRatio")) * 100));

        Resp ready = get(D + "recruitment.readyToDeploy", admin);
        assertThat(value(ready, "Ready to deploy")).isEqualTo(((Number) dash.get("readyToDeployTotal")).longValue());
    }

    @Test
    void joiningRatioHasNoDataWhenNobodyWasSelectedInTheMonth() {
        String admin = admin();
        LocalDate farMonth = today.plusMonths(14);
        Resp ratio = get(D + "recruitment.joiningRatio?period=" + YearMonth.from(farMonth), admin);
        assertThat(ratio.status()).as(ratio.body()).isEqualTo(200);
        assertThat(ratio.map().get("state")).isEqualTo("NO_DATA");
        assertThat((List<?>) ratio.map().get("values")).isEmpty();
    }
}
