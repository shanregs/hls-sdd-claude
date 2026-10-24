package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 028 T028 and the visit tile of T018: each marketing tile equals the Marketing dashboard; missing figures are never 0. */
class MarketingTilesTest extends WonProspectTestBase {

    private static final String D = "/api/v1/dashboard/areas/";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static long value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return ((Number) v.get("value")).longValue();
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    private Resp tile(String token, String area, String key, String query) {
        Resp resp = get(D + area + "/tiles/" + key + query, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp;
    }

    @Test
    void visitsStagesAndWinLossEqualTheMarketingDashboardWithinAZoneManagersScope() {
        String admin = admin();
        UUID zone = zone(admin);
        ManagerCtx manager = newManager(admin, zone);
        UUID p1 = prospect(admin, zone);
        UUID p2 = prospect(admin, zone);
        UUID p3 = prospect(admin, zone);
        prospect(admin, zone(admin)); // elsewhere: not in the Manager's scope
        post(M + "/prospects/" + p2 + "/stage", admin, Map.of("stage", "VISIT"));
        post(M + "/prospects/" + p3 + "/stage", admin, Map.of("stage", "LOST", "reason", "Not interested"));
        complete(admin, activity(admin, p1, today), "Done", null);
        activity(admin, p2, today);

        Map<String, Object> dash = get(M + "/dashboard?period=" + YearMonth.from(today), manager.token()).map();
        Map<String, Object> visits = map(dash.get("visits"));
        String token = manager.token();

        Resp schoolVisits = tile(token, "SCHOOLS", "schools.visits", "");
        assertThat(value(schoolVisits, "Completed")).isEqualTo(((Number) visits.get("completed")).longValue());
        assertThat(value(schoolVisits, "Planned")).isEqualTo(((Number) visits.get("planned")).longValue());
        assertThat(value(schoolVisits, "Missed")).isEqualTo(((Number) visits.get("missed")).longValue());
        Resp managerVisits = tile(token, "MANAGERS", "managers.visits", "");
        assertThat(value(managerVisits, "Completed")).isEqualTo(value(schoolVisits, "Completed"));

        Map<String, Object> byStage = map(dash.get("prospectsByStage"));
        Resp stages = tile(token, "SCHOOLS", "schools.stages", "");
        for (Map.Entry<String, Object> e : byStage.entrySet()) {
            assertThat(value(stages, e.getKey())).as(e.getKey()).isEqualTo(((Number) e.getValue()).longValue());
        }
        Resp winLoss = tile(token, "SCHOOLS", "schools.winLoss", "");
        assertThat(value(winLoss, "Lost")).isEqualTo(1);
        assertThat(value(winLoss, "Won")).isEqualTo(0);
        assertThat(value(tile(token, "SCHOOLS", "schools.finalStage", ""), "Awaiting review"))
                .isEqualTo(((Number) byStage.get("FINAL_STAGE")).longValue());
    }

    @Test
    void theZoneFilterNarrowsAdminAndTheWinRateHasNoDataOnAZeroDenominator() {
        String admin = admin();
        UUID busy = zone(admin);
        UUID empty = zone(admin);
        UUID p = prospect(admin, busy);
        activity(admin, p, today);

        Resp inBusy = tile(admin, "SCHOOLS", "schools.stages", "?zoneId=" + busy);
        assertThat(value(inBusy, "PROSPECT")).isEqualTo(1);
        Resp inEmpty = tile(admin, "SCHOOLS", "schools.stages", "?zoneId=" + empty);
        assertThat(value(inEmpty, "PROSPECT")).isEqualTo(0);

        Resp winLoss = tile(admin, "SCHOOLS", "schools.winLoss", "?zoneId=" + empty);
        assertThat(winLoss.map().get("state")).isEqualTo("NO_DATA");
        assertThat((List<?>) winLoss.map().get("values")).isEmpty();
    }

    @Test
    void demandSupplyIsNotAvailableWhenSupplyIsMissingAndIsOrganizationWideUnderAZone() {
        String admin = admin();
        UUID zone = zone(admin);
        Resp tile = tile(admin, "SCHOOLS", "schools.demandSupply", "?zoneId=" + zone);
        Map<String, Object> dash = get(M + "/dashboard", admin).map();
        if (dash.get("supply") == null) {
            assertThat(tile.map().get("state")).isEqualTo("NOT_AVAILABLE");
            assertThat((List<?>) tile.map().get("values")).isEmpty();
        } else {
            assertThat(tile.map().get("state")).isEqualTo("OK");
        }
        assertThat(tile.map().get("organizationWide")).isEqualTo(true);
    }
}
