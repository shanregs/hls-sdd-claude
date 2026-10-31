package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayrollFigures;
import com.hls.payroll.api.PayrollFigures.PersonFigures;
import com.hls.payroll.api.PayrollFigures.RunState;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014a T018a: PayrollFigures gives the run totals and lines for a draft and an approved run, and nothing for a month with no run. */
class PayrollFiguresTest extends PayrollRunTestBase {

    @Autowired
    private PayrollFigures figures;

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private static BigDecimal money(Object text) {
        return new BigDecimal(String.valueOf(text));
    }

    @SuppressWarnings("unchecked")
    private void assertEqualsRun(String admin, UUID run) {
        Map<String, Object> view = runOf(admin, run);
        Map<String, Object> totals = (Map<String, Object>) view.get("totals");
        PayrollFigures.RunSummary summary = figures.runOf(month).orElseThrow();
        assertThat(summary.gross()).isEqualByComparingTo(money(totals.get("earned")));
        assertThat(summary.lossOfPay()).isEqualByComparingTo(money(totals.get("deduction")));
        assertThat(summary.reimbursements()).isEqualByComparingTo(money(totals.get("adjustments")));
        assertThat(summary.net()).isEqualByComparingTo(money(totals.get("net")));

        List<PersonFigures> people = figures.peopleOf(month);
        List<Map<String, Object>> lines = allLines(admin, run);
        assertThat(people).hasSameSizeAs(lines);
        BigDecimal netOfPeople = people.stream()
                .map(PersonFigures::net)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netOfPeople).isEqualByComparingTo(summary.net());
        for (Map<String, Object> line : lines) {
            PersonFigures p = people.stream()
                    .filter(x -> x.personId().toString().equals(line.get("personId")))
                    .findFirst()
                    .orElseThrow();
            if (line.get("net") == null) {
                assertThat(p.payable()).isFalse();
                assertThat(p.net()).isNull();
                continue;
            }
            assertThat(p.net()).isEqualByComparingTo(money(line.get("net")));
            assertThat(p.gross()).isEqualByComparingTo(money(line.get("earned")));
            assertThat(p.lossOfPay()).isEqualByComparingTo(money(line.get("deduction")));
            if (p.kind() == PayRules.Kind.TEACHER) {
                assertThat(p.zoneId()).isNotNull();
                assertThat(p.schoolId()).isNotNull();
            }
        }
    }

    @Test
    void aDraftAndAnApprovedRunGiveTheSameTotalsAsTheRun() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);

        assertThat(figures.runOf(month).orElseThrow().state()).isEqualTo(RunState.DRAFT);
        assertEqualsRun(admin, draft.run());

        submitAndApprove(admin, draft.run());

        assertThat(figures.runOf(month).orElseThrow().state()).isEqualTo(RunState.APPROVED);
        assertEqualsRun(admin, draft.run());
        assertThat(figures.lastApprovedMonth()).contains(month);
        assertThat(figures.runsBetween(month.minusMonths(2), month))
                .extracting(PayrollFigures.RunSummary::month)
                .containsExactly(month);
        assertThat(figures.historyOf(PayRules.Kind.TEACHER, draft.teacher()))
                .extracting(PayrollFigures.OwnMonth::month)
                .containsExactly(month);
    }

    @Test
    void aMonthWithNoRunGivesNoFigures() {
        assertThat(figures.runOf(month.minusMonths(5))).isEmpty();
        assertThat(figures.peopleOf(month.minusMonths(5))).isEmpty();
        assertThat(figures.runsBetween(month.minusMonths(5), month.minusMonths(4))).isEmpty();
    }
}
