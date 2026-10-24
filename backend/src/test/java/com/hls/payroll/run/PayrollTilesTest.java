package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 028 T039: the payroll tiles follow the month's run, its owed tab and the month locks; amounts are rupees. */
class PayrollTilesTest extends PayrollRunTestBase {

    private String tileUrl(String key) {
        return "/api/v1/dashboard/areas/PAYROLL/tiles/" + key + "?period=" + month;
    }

    @BeforeEach
    void clean() {
        resetRuns();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> value(Resp tile, String label) {
        for (Map<String, Object> v : (List<Map<String, Object>>) tile.map().get("values")) {
            if (label.equals(v.get("label"))) {
                return v;
            }
        }
        throw new AssertionError("No value " + label + " in " + tile.body());
    }

    @Test
    void aMonthWithNoRunSaysSoAsTextNotAsAnAmount() {
        String admin = signInAs(Role.ADMIN).token();

        Resp tile = get(tileUrl("payroll.run"), admin);

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        assertThat(tile.map().get("state")).isEqualTo("OK");
        assertThat(value(tile, "State")).containsEntry("value", "No run yet").containsEntry("unit", "TEXT");
        assertThat(tile.body()).doesNotContain("RUPEES");
        assertThat(get(tileUrl("payroll.paidOwed"), admin).map().get("state")).isEqualTo("NO_DATA");
    }

    @Test
    void theRunTileFollowsDraftAwaitingApprovedAndShowsTheTotalInRupees() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        String net = (String) ((Map<?, ?>) runOf(admin, draft.run()).get("totals")).get("net");

        Resp tile = get(tileUrl("payroll.run"), admin);
        assertThat(value(tile, "State")).containsEntry("value", "DRAFT");
        assertThat(value(tile, "Total net")).containsEntry("unit", "RUPEES");
        assertThat(new BigDecimal(String.valueOf(value(tile, "Total net").get("value")))).isEqualByComparingTo(net);

        assertThat(submitRun(admin, draft.run()).status()).isEqualTo(200);
        assertThat(value(get(tileUrl("payroll.run"), admin), "State")).containsEntry("value", "AWAITING_APPROVAL");

        String director = signInAs(Role.DIRECTOR).token();
        assertThat(approveRun(director, draft.run()).status()).isEqualTo(200);
        assertThat(value(get(tileUrl("payroll.run"), admin), "State")).containsEntry("value", "APPROVED");

        Resp paidOwed = get(tileUrl("payroll.paidOwed"), director);
        assertThat(paidOwed.status()).as(paidOwed.body()).isEqualTo(200);
        assertThat(value(paidOwed, "Paid so far")).containsEntry("unit", "RUPEES");
        long owedPeople = ((Number) value(paidOwed, "People still owed").get("value")).longValue();
        assertThat(owedPeople).isEqualTo(get(RUNS + "/" + draft.run() + "/owed", admin).body().split("\"personId\"").length - 1);
    }

    @Test
    void aCancelledRunShowsAsCancelled() {
        String admin = signInAs(Role.ADMIN).token();
        UUID run = readyDraft(admin).run();
        assertThat(post(RUNS + "/" + run + "/cancel", admin, Map.of("reason", "Wrong month")).status()).isEqualTo(200);

        assertThat(value(get(tileUrl("payroll.run"), admin), "State")).containsEntry("value", "CANCELLED");
    }

    @Test
    void theLocksTileCountsTeachersAndManagersWithALockedMonth() {
        String admin = signInAs(Role.ADMIN).token();
        readyDraft(admin); // locks two Teachers' months

        Resp tile = get(tileUrl("payroll.locks"), admin);

        assertThat(tile.status()).as(tile.body()).isEqualTo(200);
        Matcher m = Pattern.compile("(\\d+) of (\\d+) locked").matcher((String) value(tile, "Teachers").get("value"));
        assertThat(m.matches()).isTrue();
        assertThat(Integer.parseInt(m.group(1))).isGreaterThanOrEqualTo(2);
        assertThat((String) value(tile, "Managers").get("value")).matches("\\d+ of \\d+ locked");
    }
}
