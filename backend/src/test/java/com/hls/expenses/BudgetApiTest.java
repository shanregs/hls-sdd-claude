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

/** Spec 015 US5 (FR-021, FR-022): setting and changing budgets, their history, and the breakdown behind actual. */
class BudgetApiTest extends ExpensesTestBase {

    private String admin;
    private Signed director;
    private UUID zone;
    private YearMonth month;

    @BeforeEach
    void setUp() {
        admin = admin().token();
        director = director();
        zone = zone(admin);
        month = YearMonth.from(today().minusDays(1));
    }

    private Map<String, Object> body(UUID area, String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("zoneId", area == null ? null : area.toString());
        body.put("month", month.toString());
        body.put("amount", amount);
        return body;
    }

    @Test
    void adminAndDirectorSetAndChangeABudgetAndTheHistoryKeepsEveryChange() {
        Resp first = put(REVIEW + "/budgets", director.token(), body(zone, "10000.00"));
        assertThat(first.status()).as(first.body()).isEqualTo(200);
        assertThat(first.map()).containsEntry("created", true);
        String budgetId = (String) first.map().get("budgetId");

        Resp second = put(REVIEW + "/budgets", admin, body(zone, "12500.50"));
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.map()).containsEntry("created", false).containsEntry("budgetId", budgetId);
        // the same amount again changes nothing and adds no history
        assertThat(put(REVIEW + "/budgets", admin, body(zone, "12500.50")).status()).isEqualTo(200);

        Resp history = get(REVIEW + "/budgets/" + budgetId + "/history", director.token());
        assertThat(history.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) (Object) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(history.body());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("oldAmount", "10000.00").containsEntry("newAmount", "12500.50").containsKey("at");
        assertThat(rows.get(0).get("actorName")).isNotNull();
        assertThat(rows.get(1).get("oldAmount")).isNull();
        assertThat(rows.get(1)).containsEntry("newAmount", "10000.00").containsEntry("actorId", director.userId().toString());
        assertChangeRecorded(admin, "EXPENSE_BUDGET", UUID.fromString(budgetId), "amount");
    }

    @Test
    void managerAndTeacherAreRefused() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER)) {
            String token = signInAs(role).token();
            assertThat(put(REVIEW + "/budgets", token, body(zone, "100.00")).status()).as(role.name()).isEqualTo(403);
            assertThat(get(REVIEW + "/budgets?month=" + month, token).status()).as(role.name()).isEqualTo(403);
        }
    }

    @Test
    void invalidBudgetsAreRefused() {
        assertThat(put(REVIEW + "/budgets", director.token(), body(zone, "-1.00")).status()).isEqualTo(400);
        assertThat(put(REVIEW + "/budgets", director.token(), body(zone, "10.005")).status()).isEqualTo(400);
        assertThat(put(REVIEW + "/budgets", director.token(), body(UUID.randomUUID(), "10.00")).status()).isEqualTo(400);
        Map<String, Object> badMonth = body(zone, "10.00");
        badMonth.put("month", "October");
        assertThat(put(REVIEW + "/budgets", director.token(), badMonth).status()).isEqualTo(400);
        assertThat(get(REVIEW + "/budgets?month=nope", director.token()).status()).isEqualTo(400);
        assertThat(get(REVIEW + "/budgets/" + UUID.randomUUID() + "/history", director.token()).status()).isEqualTo(404);
    }

    @Test
    void aStaleBudgetVersionIsRefused() {
        Resp created = put(REVIEW + "/budgets", director.token(), body(zone, "100.00"));
        assertThat(created.status()).isEqualTo(200);
        Map<String, Object> stale = body(zone, "200.00");
        stale.put("version", 7);
        assertThat(put(REVIEW + "/budgets", director.token(), stale).status()).isEqualTo(409);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDetailListsTheClaimsBehindActualByCategoryAndClaimant() {
        String one = signInAs(Role.MANAGER).token();
        String two = signInAs(Role.TEACHER).token();
        UUID travelOne = claim(one, "TRAVEL_FUEL", "1000.00");
        UUID suppliesOne = claim(one, "OFFICE_SUPPLIES", "250.00");
        UUID travelTwo = claim(two, "TRAVEL_FUEL", "500.00");
        claim(two, "OFFICE_SUPPLIES", "75.00"); // stays Submitted: not behind actual
        for (UUID id : List.of(travelOne, suppliesOne, travelTwo)) {
            assertThat(approve(admin, id).status()).isEqualTo(200);
        }

        Resp detail = get(REVIEW + "/budgets/detail?month=" + month + "&zoneId=" + zone, director.token());
        assertThat(detail.status()).as(detail.body()).isEqualTo(200);
        List<Map<String, Object>> byCategory = (List<Map<String, Object>>) detail.map().get("byCategory");
        assertThat(byCategory).extracting(c -> c.get("category")).containsExactly("OFFICE_SUPPLIES", "TRAVEL_FUEL");
        assertThat(byCategory.get(1)).containsEntry("total", "1500.00").containsEntry("count", 2);
        assertThat(byCategory.get(0)).containsEntry("total", "250.00").containsEntry("count", 1);
        List<Map<String, Object>> byClaimant = (List<Map<String, Object>>) detail.map().get("byClaimant");
        assertThat(byClaimant).hasSize(2);
        assertThat(byClaimant.get(0)).containsEntry("total", "1250.00");
        assertThat(byClaimant.get(1)).containsEntry("total", "500.00");
        assertThat((List<?>) detail.map().get("claims")).hasSize(3);
    }

    @Test
    void theReviewQueueShowsTheAreasRemainingBudgetForTheClaimsMonth() {
        String claimant = signInAs(Role.MANAGER).token();
        assertThat(put(REVIEW + "/budgets", director.token(), body(zone, "10000.00")).status()).isEqualTo(200);
        UUID approved = claim(claimant, "TRAVEL_FUEL", "3000.00");
        assertThat(approve(admin, approved).status()).isEqualTo(200);
        UUID waiting = claim(claimant, "TRAVEL_FUEL", "400.00");

        Resp queue = get(REVIEW + "?zone=" + zone, admin);
        Map<String, Object> row = content(queue).stream().filter(r -> waiting.toString().equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(row).containsEntry("remainingBudget", "7000.00");

        // an area with no budget shows none
        UUID otherZone = zone(admin);
        UUID unbudgeted = claimIn(claimant, otherZone, "TRAVEL_FUEL", "30.00");
        Map<String, Object> noBudget = content(get(REVIEW + "?zone=" + otherZone, admin)).stream()
                .filter(r -> unbudgeted.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
        assertThat(noBudget.get("remainingBudget")).isNull();
    }

    private UUID claim(String token, String category, String amount) {
        return claimIn(token, zone, category, amount);
    }

    private UUID claimIn(String token, UUID area, String category, String amount) {
        LocalDate date = today().minusDays(1);
        Map<String, Object> claim = claimBody(date, amount, category);
        claim.put("zoneId", area.toString());
        UUID id = newDraft(token, claim);
        uploadBill(token, id, "bill.jpg", JPG);
        assertThat(submit(token, id).status()).isEqualTo(200);
        return id;
    }
}
