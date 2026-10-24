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
