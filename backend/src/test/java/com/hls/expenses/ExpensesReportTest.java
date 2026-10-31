package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 014c US2: the Expenses report equals the 015 screens (budget, pending, cost measures, approved spend), says "no Schools
 * won" instead of dividing by nothing, shows the Head office row, and is closed to a Zone Manager and a Teacher.
 */
class ExpensesReportTest extends ExpensesTestBase {

    private static final String REPORT = "/api/v1/reports/expenses";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("rows");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cell(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index);
    }

    private static Map<String, Object> rowById(Map<String, Object> section, String id) {
        return rows(section).stream().filter(r -> id.equals(r.get("id"))).findFirst().orElseThrow();
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString());
    }

    @Test
    void spendBudgetAndHeadOfficeEqualTheBudgetScreen() {
        String admin = admin().token();
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        UUID zone = zone(admin);
        LocalDate day = today().minusDays(1);
        YearMonth month = YearMonth.from(day);
        approvedClaim(claimant, director, zone, day, "OFFICE_SUPPLIES", "100.25", null);
        approvedClaim(claimant, director, zone, day, "TRAVEL_FUEL", "200.00", null);
        approvedClaim(claimant, director, null, day, "OFFICE_SUPPLIES", "40.00", null);
        Map<String, Object> body = new HashMap<>();
        body.put("zoneId", zone.toString());
        body.put("month", month.toString());
        body.put("amount", "250.00");
        assertThat(put(REVIEW + "/budgets", director, body).status()).isEqualTo(200);

        Resp resp = get(REPORT + "?month=" + month, director);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        // spend: both categories of the zone and the Head office row
        Map<String, Object> spend = section(resp, "spend");
        assertThat(rowById(spend, zone + "|OFFICE_SUPPLIES").get("label").toString()).contains("Office");
        assertThat(money(cell(rowById(spend, zone + "|OFFICE_SUPPLIES"), 3).get("value"))).isEqualByComparingTo("100.25");
        assertThat(money(cell(rowById(spend, zone + "|TRAVEL_FUEL"), 3).get("value"))).isEqualByComparingTo("200.00");
        assertThat(money(cell(rowById(spend, "head-office|OFFICE_SUPPLIES"), 3).get("value"))).isEqualByComparingTo("40.00");
        // budget: every row equals the 015 budget screen
        Resp screen = get(REVIEW + "/budgets?month=" + month, director);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> areas = (List<Map<String, Object>>) screen.map().get("areas");
        Map<String, Object> budget = section(resp, "budget");
        assertThat(rows(budget)).hasSize(areas.size());
        for (int i = 0; i < areas.size(); i++) {
            Map<String, Object> area = areas.get(i);
            Map<String, Object> ours = rows(budget).get(i);
            assertThat(ours.get("label")).isEqualTo(area.get("areaName"));
            assertThat(money(cell(ours, 1).get("value"))).isEqualByComparingTo(money(area.get("actual")));
            assertThat(money(cell(ours, 2).get("value"))).isEqualByComparingTo(money(area.get("pending")));
            if (area.get("budget") == null) {
                assertThat(cell(ours, 0).get("state")).isEqualTo("NO_DATA");
                assertThat(cell(ours, 3).get("state")).isEqualTo("NO_DATA");
            } else {
                assertThat(money(cell(ours, 0).get("value"))).isEqualByComparingTo(money(area.get("budget")));
                assertThat(money(cell(ours, 3).get("value"))).isEqualByComparingTo(money(area.get("remaining")));
            }
        }
        Map<String, Object> ours = rowById(budget, zone.toString());
        assertThat(cell(ours, 4).get("value")).isEqualTo("Over budget");
        assertThat(money(cell(ours, 3).get("value"))).isEqualByComparingTo("-50.25");
        // Head office has no budget: no data for budget, a real actual
        Map<String, Object> head = rowById(budget, "head-office");
        assertThat(cell(head, 0).get("state")).isEqualTo("NO_DATA");
        assertThat(money(cell(head, 1).get("value"))).isEqualByComparingTo("40.00");
        // the zone filter narrows the budget and spend sections to that zone
        Resp narrowed = get(REPORT + "?month=" + month + "&zoneId=" + zone, director);
        assertThat(rows(section(narrowed, "budget"))).hasSize(1);
        assertThat(rows(section(narrowed, "spend"))).hasSize(2);
    }

    @Test
    void pendingAndCostMeasuresEqualTheirScreensAndNoSchoolsWonIsExplained() {
        String admin = admin().token();
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        newSubmittedClaim(claimant, "12.00");
        UUID zone = zone(admin);
        UUID quietZone = zone(admin);
        LocalDate day = today().minusDays(1);
        YearMonth month = YearMonth.from(day);
        approvedClaim(claimant, director, zone, day, "MARKETING", "900.00", null);
        insertWonProspect(zone, noonOn(day));
        insertWonProspect(zone, noonOn(day));
        insertWonProspect(zone, noonOn(day));
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        Resp resp = get(REPORT + "?month=" + month + "&zoneId=" + zone, director);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Resp pendingScreen = get(REVIEW + "/pending", director);
        Map<String, Object> pending = section(resp, "pending");
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) pending.get("totals");
        assertThat(((Number) cell(totals, 0).get("value")).longValue()).isEqualTo(((Number) pendingScreen.map().get("count")).longValue());
        assertThat(money(cell(totals, 1).get("value"))).isEqualByComparingTo(money(pendingScreen.map().get("total")));
        assertThat(rows(pending)).hasSize(4);
        assertThat(pending.get("title").toString()).contains("as of today");

        Resp schoolScreen = get(REVIEW + "/costs/per-school?from=" + from + "&to=" + to + "&zoneId=" + zone, director);
        Map<String, Object> costs = section(resp, "costs");
        Map<String, Object> perSchool = rowById(costs, "perSchool");
        assertThat(money(cell(perSchool, 0).get("value"))).isEqualByComparingTo(money(schoolScreen.map().get("cost")));
        assertThat(money(cell(perSchool, 2).get("value"))).isEqualByComparingTo("300.00");
        assertThat(((Number) cell(perSchool, 1).get("value")).longValue()).isEqualTo(3);

        Resp quiet = get(REPORT + "?month=" + month + "&zoneId=" + quietZone, director);
        Map<String, Object> none = rowById(section(quiet, "costs"), "perSchool");
        assertThat(cell(none, 2).get("state")).isEqualTo("NO_DATA");
        assertThat(cell(none, 3).get("value")).isEqualTo("No Schools won in this period");
    }

    @Test
    void aZoneManagerAndATeacherAreRefusedAndSeeNoMenuItem() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(get(REPORT, token).status()).as(role.name()).isEqualTo(403);
            assertThat(get("/api/v1/reports", token).body()).as(role.name()).doesNotContain("\"key\":\"expenses\"");
        }
        assertThat(get("/api/v1/reports", director().token()).body()).contains("\"key\":\"expenses\"");
        assertThat(get("/api/v1/reports", admin().token()).body()).contains("\"key\":\"expenses\"");
    }
}
