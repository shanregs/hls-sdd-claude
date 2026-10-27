package com.hls.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.expenses.api.ExpenseQueries;
import com.hls.identity.user.Role;
import com.hls.support.ExpensesTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 015 T056: the read interface gives the same figures as the HTTP views, from one source. */
class ExpenseQueriesInterfaceTest extends ExpensesTestBase {

    @Autowired
    private ExpenseQueries queries;

    @Test
    void owedToListsApprovedPersonalUnpaidClaimsOnly() {
        Signed claimant = signInAs(Role.MANAGER);
        String director = director().token();
        UUID approved = newSubmittedClaim(claimant.token(), "700.00");
        newSubmittedClaim(claimant.token(), "10.00");
        approve(director, approved);
        var owed = queries.owedTo(claimant.userId());
        assertThat(owed).extracting(ExpenseQueries.OwedClaim::claimId).containsExactly(approved);
        assertThat(owed.get(0).amount()).isEqualByComparingTo("700.00");
        assertThat(get(MINE, claimant.token()).map()).containsEntry("owed", "700.00");
    }

    @Test
    void approvedSpendMatchesTheBudgetView() {
        String admin = admin().token();
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        UUID zone = zone(admin);
        LocalDate day = today().minusDays(1);
        approvedClaim(claimant, director, zone, day, "OFFICE_SUPPLIES", "100.25", null);
        approvedClaim(claimant, director, zone, day, "TRAVEL_FUEL", "200.00", null);
        var spend = queries.approvedSpend(YearMonth.from(day), YearMonth.from(day), zone, null);
        assertThat(spend).extracting(ExpenseQueries.SpendRow::category).containsExactlyInAnyOrder("OFFICE_SUPPLIES", "TRAVEL_FUEL");
        BigDecimal sum = spend.stream().map(ExpenseQueries.SpendRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("300.25");
        assertThat(queries.approvedSpend(YearMonth.from(day), YearMonth.from(day), zone, "TRAVEL_FUEL")).hasSize(1);
    }

    /** Spec 014c A9: totals per claimant equal My Expenses; claimants with no claims are absent. */
    @Test
    void claimedByGivesThePerClaimantTotalsMyExpensesShows() {
        String director = director().token();
        Signed a = signInAs(Role.MANAGER);
        Signed b = signInAs(Role.MANAGER);
        Signed none = signInAs(Role.MANAGER);
        UUID approved = newSubmittedClaim(a.token(), "100.50");
        newSubmittedClaim(a.token(), "20.00");
        newSubmittedClaim(b.token(), "7.00");
        newDraft(b.token(), travelBody("999.00"));
        approve(director, approved);
        LocalDate day = today().minusDays(1);

        var claimed = queries.claimedBy(day, day, java.util.Set.of(a.userId(), b.userId(), none.userId()));

        assertThat(claimed).containsOnlyKeys(a.userId(), b.userId());
        assertThat(claimed.get(a.userId()).claims()).isEqualTo(2);
        assertThat(claimed.get(a.userId()).submitted()).isEqualByComparingTo("120.50");
        assertThat(claimed.get(a.userId()).approved()).isEqualByComparingTo("100.50");
        assertThat(claimed.get(b.userId()).claims()).isEqualTo(1);
        assertThat(claimed.get(b.userId()).submitted()).isEqualByComparingTo("7.00");
        assertThat(claimed.get(b.userId()).approved()).isEqualByComparingTo("0");
        Resp mine = get(MINE + "?month=" + YearMonth.from(day) + "&size=100", a.token());
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows = (java.util.List<java.util.Map<String, Object>>) mine.map().get("content");
        BigDecimal shown = rows.stream().map(r -> new BigDecimal(r.get("amount").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shown).isEqualByComparingTo(claimed.get(a.userId()).submitted());
        assertThat(queries.claimedBy(day, day, java.util.Set.of())).isEmpty();
        assertThat(queries.claimedBy(day.minusDays(60), day.minusDays(40), java.util.Set.of(a.userId()))).isEmpty();
    }

    /** Spec 014c A10: rows equal the 015 budget screen; Head office has a null zone; no budget gives a null budget, real actual. */
    @Test
    void budgetVsActualEqualsTheBudgetScreen() {
        String admin = admin().token();
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        UUID zone = zone(admin);
        LocalDate day = today().minusDays(1);
        YearMonth month = YearMonth.from(day);
        approvedClaim(claimant, director, zone, day, "OFFICE_SUPPLIES", "300.00", null);
        Resp set = put(REVIEW + "/budgets", director, java.util.Map.of("zoneId", zone.toString(), "month", month.toString(), "amount", "1000.00"));
        assertThat(set.status()).as(set.body()).isEqualTo(200);

        var lines = queries.budgetVsActual(month);

        Resp screen = get(REVIEW + "/budgets?month=" + month, director);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> areas = (java.util.List<java.util.Map<String, Object>>) screen.map().get("areas");
        assertThat(lines).hasSize(areas.size());
        assertThat(lines.get(0).zoneId()).isNull();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var area = areas.get(i);
            assertThat(line.areaName()).isEqualTo(area.get("areaName"));
            assertThat(line.actual()).isEqualByComparingTo(new BigDecimal(area.get("actual").toString()));
            assertThat(line.over()).isEqualTo(area.get("over"));
            if (area.get("budget") == null) {
                assertThat(line.budget()).isNull();
                assertThat(line.remaining()).isNull();
            } else {
                assertThat(line.budget()).isEqualByComparingTo(new BigDecimal(area.get("budget").toString()));
            }
        }
        var ours = lines.stream().filter(l -> zone.equals(l.zoneId())).findFirst().orElseThrow();
        assertThat(ours.budget()).isEqualByComparingTo("1000.00");
        assertThat(ours.actual()).isEqualByComparingTo("300.00");
        assertThat(ours.remaining()).isEqualByComparingTo("700.00");
        assertThat(lines.stream().filter(l -> l.zoneId() == null).findFirst().orElseThrow().areaName()).isEqualTo("Head office");
    }

    @Test
    void pendingAndCostMeasuresAgreeWithTheHttpViews() {
        String director = director().token();
        String claimant = signInAs(Role.MANAGER).token();
        newSubmittedClaim(claimant, "12.00");
        var summary = queries.pending();
        Resp http = get(REVIEW + "/pending", director);
        assertThat(summary.count()).isEqualTo(((Number) http.map().get("count")).longValue());
        assertThat(summary.total()).isEqualByComparingTo(new BigDecimal((String) http.map().get("total")));
        assertThat(summary.buckets()).hasSize(4);

        UUID zone = zone(admin().token());
        LocalDate to = today();
        LocalDate from = to.minusDays(10);
        approvedClaim(claimant, director, zone, to.minusDays(1), "MARKETING", "900.00", null);
        insertWonProspect(zone, noonOn(to.minusDays(1)));
        insertWonProspect(zone, noonOn(to.minusDays(2)));
        insertWonProspect(zone, noonOn(to.minusDays(3)));
        var measure = queries.costPerSchool(from, to, zone);
        Resp view = get(REVIEW + "/costs/per-school?from=" + from + "&to=" + to + "&zoneId=" + zone, director);
        assertThat(measure.cost()).isEqualByComparingTo(new BigDecimal((String) view.map().get("cost")));
        assertThat(measure.perUnit()).isEqualByComparingTo("300.00");
        assertThat(measure.divisor()).isEqualTo(3);
        assertThat(measure.state()).isEqualTo("OK");
        assertThat(measure.claimIds()).hasSize(1);
    }
}
