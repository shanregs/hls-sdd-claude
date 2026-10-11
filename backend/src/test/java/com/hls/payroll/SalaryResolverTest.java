package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.internal.Dated;
import com.hls.payroll.internal.SalaryResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 013a SC-001: the salary in effect equals the hand-computed answer, over a table of rows (T012). */
class SalaryResolverTest {

    private record Row(String label, LocalDate effectiveOn, Instant recordedAt) implements Dated {}

    private static Row row(String label, String effectiveOn, int recordedAtSecond) {
        return new Row(label, LocalDate.parse(effectiveOn), Instant.parse("2026-10-01T00:00:00Z").plusSeconds(recordedAtSecond));
    }

    private static String labelOn(List<Row> rows, String date) {
        return SalaryResolver.inEffect(rows, LocalDate.parse(date)).map(Row::label).orElse("none");
    }

    @Test
    void noRowsGivesNothing() {
        assertThat(SalaryResolver.inEffect(List.<Row>of(), LocalDate.parse("2026-11-01"))).isEmpty();
    }

    @Test
    void aRowAfterTheDateIsNotYetInEffect() {
        assertThat(labelOn(List.of(row("A", "2026-11-01", 0)), "2026-10-31")).isEqualTo("none");
    }

    @Test
    void aRowOnTheDateIsInEffect() {
        assertThat(labelOn(List.of(row("A", "2026-11-01", 0)), "2026-11-01")).isEqualTo("A");
    }

    @Test
    void theLatestOfSeveralOnOrBeforeTheDateWins() {
        List<Row> rows = List.of(row("A", "2026-01-01", 0), row("B", "2026-06-01", 1), row("C", "2027-02-01", 2));
        assertThat(labelOn(rows, "2026-12-31")).isEqualTo("B");
        assertThat(labelOn(rows, "2027-02-01")).isEqualTo("C");
        assertThat(labelOn(rows, "2026-05-31")).isEqualTo("A");
    }

    @Test
    void twoRowsOnOneDateGoToTheOneRecordedLast() {
        List<Row> rows = List.of(row("first", "2026-11-01", 0), row("corrected", "2026-11-01", 60));
        assertThat(labelOn(rows, "2026-11-30")).isEqualTo("corrected");
        // the order they are listed in does not matter
        assertThat(labelOn(List.of(rows.get(1), rows.get(0)), "2026-11-30")).isEqualTo("corrected");
    }

    @Test
    void aChangeInTheMiddleOfAMonthAppliesFromItsDay() {
        List<Row> rows = List.of(row("old", "2026-01-01", 0), row("new", "2026-11-16", 1));
        assertThat(labelOn(rows, "2026-11-15")).isEqualTo("old");
        assertThat(labelOn(rows, "2026-11-16")).isEqualTo("new");
    }

    @Test
    void aRowFarInThePastStillApplies() {
        assertThat(labelOn(List.of(row("old", "2000-01-01", 0)), "2031-03-15")).isEqualTo("old");
    }

    @Test
    void rowsAddedOutOfDateOrderStillResolveByEffectiveDate() {
        // recorded last, but effective earlier than the row before it: it must not win on the later date
        List<Row> rows = List.of(row("later date", "2027-02-01", 0), row("earlier date, recorded after", "2026-11-01", 100));
        assertThat(labelOn(rows, "2027-02-15")).isEqualTo("later date");
        assertThat(labelOn(rows, "2026-12-01")).isEqualTo("earlier date, recorded after");
    }

    @Test
    void aRetiredDesignationsHistoryResolvesTheSame() {
        // retirement is not part of the rows: a retired designation keeps resolving its history
        List<Row> rows = List.of(row("A", "2025-04-01", 0), row("B", "2026-04-01", 1));
        assertThat(labelOn(rows, "2026-10-11")).isEqualTo("B");
    }

    @Test
    void theSameInputsAlwaysGiveTheSameAnswer() {
        List<Row> rows = List.of(row("A", "2026-01-01", 0), row("B", "2026-01-01", 5), row("C", "2026-03-01", 2));
        Optional<Row> first = SalaryResolver.inEffect(rows, LocalDate.parse("2026-02-01"));
        for (int i = 0; i < 5; i++) {
            assertThat(SalaryResolver.inEffect(rows, LocalDate.parse("2026-02-01"))).isEqualTo(first);
        }
        assertThat(first.orElseThrow().label()).isEqualTo("B");
    }

    @Test
    void theHistoryIsListedNewestFirst() {
        List<Row> rows = List.of(row("A", "2026-01-01", 0), row("C", "2026-03-01", 2), row("B", "2026-01-01", 5));
        assertThat(SalaryResolver.newestFirst(rows)).extracting(Row::label).containsExactly("C", "B", "A");
    }
}
