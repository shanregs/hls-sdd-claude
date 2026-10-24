package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 015 US5 (SC-006), written before the code against hand-computed tables: actual counts Approved, Paid and Paid by
 * company claims by expense date in the month, pending counts Submitted, nothing else counts anywhere.
 */
class BudgetVersusActualTest extends ExpensesTestBase {

    private String claimant;
    private String admin;
    private String director;
    private UUID zone;
    private YearMonth month;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        claimant = signInAs(Role.MANAGER).token();
        admin = admin().token();
        director = director().token();
        zone = zone(admin);
        day = today().minusDays(1);
        month = YearMonth.from(day);
    }

    /** A claim in the zone (null = Head office) on a date, with a bill, submitted. */
    private UUID submitted(UUID area, LocalDate date, String amount, String category, String paidBy) {
        Map<String, Object> body = claimBody(date, amount, category);
        body.put("zoneId", area == null ? null : area.toString());
        body.put("paidBy", paidBy);
        if ("OTHER".equals(category)) {
            body.put("reason", "One-off");
        }
        UUID id = newDraft(claimant, body);
        uploadBill(claimant, id, "bill.jpg", JPG);
        Resp resp = submit(claimant, id);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return id;
    }

    private UUID submitted(UUID area, String amount) {
        return submitted(area, day, amount, "TRAVEL_FUEL", "CLAIMANT");
    }

    private void setBudget(UUID area, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("zoneId", area == null ? null : area.toString());
        body.put("month", month.toString());
        body.put("amount", amount);
        Resp resp = put(REVIEW + "/budgets", director, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> area(UUID area) {
        Resp resp = get(REVIEW + "/budgets?month=" + month, director);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> areas = (List<Map<String, Object>>) resp.map().get("areas");
        return areas.stream()
                .filter(a -> area == null ? a.get("zoneId") == null : area.toString().equals(a.get("zoneId")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theWorkedExampleOfTheSpec() {
        setBudget(zone, "20000.00");
        UUID approved = submitted(zone, "12000.00");
        submitted(zone, "5000.00");
        assertThat(approve(admin, approved).status()).isEqualTo(200);

        Map<String, Object> row = area(zone);
        assertThat(row).containsEntry("budget", "20000.00").containsEntry("actual", "12000.00").containsEntry("pending", "5000.00")
                .containsEntry("remaining", "8000.00").containsEntry("over", false);
        assertThat(((Number) row.get("percentUsed")).doubleValue()).isEqualTo(60.0);

        // approving 10,000.00 more exceeds the budget; the approval still succeeds
        UUID more = submitted(zone, "10000.00");
        assertThat(approve(admin, more).status()).isEqualTo(200);
        Map<String, Object> over = area(zone);
        assertThat(over).containsEntry("actual", "22000.00").containsEntry("remaining", "-2000.00").containsEntry("over", true);
        assertThat(((Number) over.get("percentUsed")).doubleValue()).isEqualTo(110.0);
    }

    @Test
    void onlyApprovedPaidAndPaidByCompanyCountAsActualAndOnlySubmittedAsPending() {
        setBudget(zone, "20000.00");
        UUID approved = submitted(zone, "12000.00");
        submitted(zone, "5000.00"); // stays Submitted
        UUID rejected = submitted(zone, "900.00");
        UUID company = submitted(zone, day, "1000.50", "TRAVEL_FUEL", "COMPANY");
        UUID paidOutside = submitted(zone, "250.25");
        Map<String, Object> draft = claimBody(day, "700.00", "TRAVEL_FUEL");
        draft.put("zoneId", zone.toString());
        newDraft(claimant, draft); // a Draft counts nowhere
        UUID otherMonth = submitted(zone, day.minusMonths(1), "3000.00", "TRAVEL_FUEL", "CLAIMANT");

        assertThat(approve(admin, approved).status()).isEqualTo(200);
        assertThat(reject(admin, rejected, "No").status()).isEqualTo(200);
        assertThat(approve(admin, company).status()).isEqualTo(200);
        assertThat(approve(admin, paidOutside).status()).isEqualTo(200);
        assertThat(recordPaid(admin, paidOutside, today(), "CASH", null).status()).isEqualTo(200);
        assertThat(approve(admin, otherMonth).status()).isEqualTo(200);

        Map<String, Object> row = area(zone);
        assertThat(row).containsEntry("actual", "13250.75").containsEntry("pending", "5000.00").containsEntry("remaining", "6749.25");
        assertThat(((Number) row.get("percentUsed")).doubleValue()).isEqualTo(66.3);

        // the month before has its own figures
        Resp previous = get(REVIEW + "/budgets?month=" + month.minusMonths(1), director);
        @SuppressWarnings("unchecked")
        Map<String, Object> previousRow = ((List<Map<String, Object>>) previous.map().get("areas")).stream()
                .filter(a -> zone.toString().equals(a.get("zoneId")))
                .findFirst()
                .orElseThrow();
        assertThat(previousRow).containsEntry("actual", "3000.00").containsEntry("pending", "0.00").containsEntry("budget", null);
    }

    @Test
    void anAreaWithNoBudgetShowsNoBudgetNeverZero() {
        UUID claim = submitted(zone, "400.00");
        assertThat(approve(admin, claim).status()).isEqualTo(200);
        Map<String, Object> row = area(zone);
        assertThat(row.get("budget")).isNull();
        assertThat(row.get("remaining")).isNull();
        assertThat(row.get("percentUsed")).isNull();
        assertThat(row).containsEntry("actual", "400.00").containsEntry("over", false);
    }

    @Test
    void aZeroBudgetHasNoPercentageAndIsOverAsSoonAsAnythingIsSpent() {
        setBudget(zone, "0.00");
        Map<String, Object> empty = area(zone);
        assertThat(empty).containsEntry("budget", "0.00").containsEntry("over", false);
        assertThat(empty.get("percentUsed")).isNull();

        assertThat(approve(admin, submitted(zone, "10.00")).status()).isEqualTo(200);
        Map<String, Object> spent = area(zone);
        assertThat(spent).containsEntry("over", true).containsEntry("remaining", "-10.00");
        assertThat(spent.get("percentUsed")).isNull();
    }

    @Test
    void headOfficeIsItsOwnAreaAndZonesDoNotLeakIntoIt() {
        UUID headOffice = submitted(null, "123.45");
        UUID inZone = submitted(zone, "67.00");
        assertThat(approve(admin, headOffice).status()).isEqualTo(200);
        assertThat(approve(admin, inZone).status()).isEqualTo(200);

        Map<String, Object> head = area(null);
        assertThat(head).containsEntry("areaName", "Head office");
        assertThat(new java.math.BigDecimal((String) head.get("actual"))).isGreaterThanOrEqualTo(new java.math.BigDecimal("123.45"));
        assertThat(area(zone)).containsEntry("actual", "67.00");
        setBudget(null, "1000.00");
        assertThat(area(null)).containsEntry("budget", "1000.00");
        assertThat(area(zone).get("budget")).isNull();
    }

    @Test
    void aRejectedThenApprovedClaimCountsOnce() {
        UUID claim = submitted(zone, "500.00");
        assertThat(reject(admin, claim, "Wrong bill").status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "0.00").containsEntry("pending", "0.00");
        assertThat(submit(claimant, claim).status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "0.00").containsEntry("pending", "500.00");
        assertThat(approve(admin, claim).status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "500.00").containsEntry("pending", "0.00");
    }

    @Test
    void aReversedApprovalMovesFromActualToPending() {
        UUID claim = submitted(zone, "640.00");
        assertThat(approve(admin, claim).status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "640.00").containsEntry("pending", "0.00");
        assertThat(reverse(admin, claim, "Re-check").status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "0.00").containsEntry("pending", "640.00");
    }

    @Test
    void amountsKeepPaise() {
        UUID a = submitted(zone, "0.01");
        UUID b = submitted(zone, "1234.56");
        assertThat(approve(admin, a).status()).isEqualTo(200);
        assertThat(approve(admin, b).status()).isEqualTo(200);
        assertThat(area(zone)).containsEntry("actual", "1234.57");
    }
}
