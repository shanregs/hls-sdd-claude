package com.hls.organization.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-008: the designation on a date is the latest row on or before it; the row recorded last wins on a shared date. */
class ManagerDesignationOnDateTest {

    private static final UUID MANAGER = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static ManagerDesignation row(UUID designation, String effectiveOn, long seq) {
        ManagerDesignation row =
                new ManagerDesignation(MANAGER, designation, LocalDate.parse(effectiveOn), UUID.randomUUID(), Instant.EPOCH);
        try {
            Field f = ManagerDesignation.class.getDeclaredField("seq");
            f.setAccessible(true);
            f.set(row, seq);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return row;
    }

    // three rows: A from 1 Jan, B from 16 Mar, then C also from 16 Mar recorded later
    private static final List<ManagerDesignation> ROWS =
            List.of(row(A, "2026-01-01", 1), row(B, "2026-03-16", 2), row(C, "2026-03-16", 3));

    private static Optional<UUID> on(String date) {
        return ManagerEmploymentService.inEffectOn(ROWS, LocalDate.parse(date)).map(ManagerDesignation::getDesignationId);
    }

    @Test
    void nothingBeforeTheFirstRow() {
        assertThat(on("2025-12-31")).isEmpty();
    }

    @Test
    void theFirstRowAppliesFromItsOwnDate() {
        assertThat(on("2026-01-01")).contains(A);
        assertThat(on("2026-02-28")).contains(A);
    }

    @Test
    void aChangeInTheMiddleOfAMonthSplitsTheMonth() {
        assertThat(on("2026-03-15")).contains(A);
        assertThat(on("2026-03-16")).contains(C);
        assertThat(on("2026-03-31")).contains(C);
    }

    @Test
    void whenTwoRowsShareADateTheOneRecordedLastWins() {
        assertThat(on("2026-03-16")).contains(C);
        assertThat(ManagerEmploymentService.inEffectOn(
                        List.of(row(C, "2026-03-16", 3), row(B, "2026-03-16", 2)), LocalDate.parse("2026-03-16")))
                .map(ManagerDesignation::getDesignationId)
                .contains(C);
    }

    @Test
    void afterTheLastRowItKeepsApplying() {
        assertThat(on("2030-01-01")).contains(C);
    }

    @Test
    void aRowNotYetReadBackFromTheDatabaseCountsAsTheLatest() {
        ManagerDesignation fresh =
                new ManagerDesignation(MANAGER, B, LocalDate.parse("2026-03-16"), UUID.randomUUID(), Instant.EPOCH);
        assertThat(ManagerEmploymentService.inEffectOn(List.of(row(A, "2026-03-16", 9), fresh), LocalDate.parse("2026-04-01")))
                .map(ManagerDesignation::getDesignationId)
                .contains(B);
    }

    @Test
    void anEmptyHistoryHasNoDesignation() {
        assertThat(ManagerEmploymentService.inEffectOn(List.of(), LocalDate.parse("2026-04-01"))).isEmpty();
    }
}
