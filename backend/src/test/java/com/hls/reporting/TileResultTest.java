package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.reporting.api.Link;
import com.hls.reporting.api.TileResult;
import com.hls.reporting.api.TileResult.State;
import com.hls.reporting.api.TileResult.Value;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 028 FR-012 and research.md section 3: a number exists only in an OK result. */
class TileResultTest {

    @Test
    void anOkResultCarriesItsValues() {
        TileResult result = TileResult.ok("k", Value.count("Overdue", 7, Link.to("/tasks")));
        assertThat(result.state()).isEqualTo(State.OK);
        assertThat(result.values()).hasSize(1);
        assertThat(result.values().get(0).value()).isEqualTo(7L);
    }

    @Test
    void noDataNotAvailableAndNotWorkingDayCarryNoValues() {
        for (TileResult result : List.of(
                TileResult.noData("k", "No data yet"),
                TileResult.notAvailable("k", "Not available yet"),
                TileResult.notWorkingDay("k", "Weekly off"))) {
            assertThat(result.values()).isEmpty();
            assertThat(result.table()).isNull();
            assertThat(result.note()).isNotBlank();
        }
        assertThat(TileResult.noData("k", "n").state()).isEqualTo(State.NO_DATA);
        assertThat(TileResult.notAvailable("k", "n").state()).isEqualTo(State.NOT_AVAILABLE);
        assertThat(TileResult.notWorkingDay("k", "n").state()).isEqualTo(State.NOT_WORKING_DAY);
    }

    @Test
    void valuesAreEmptyUnlessTheStateIsOk() {
        Value value = Value.count("x", 0, null);
        for (State state : State.values()) {
            if (state == State.OK) {
                continue;
            }
            assertThatThrownBy(() -> new TileResult("k", state, List.of(value), false, "n", null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(TileResult.error("k", "Could not be read").values()).isEmpty();
    }

    @Test
    void theStatesAreExactlyTheFiveInTheContract() {
        assertThat(Arrays.stream(State.values()).map(Enum::name))
                .containsExactly("OK", "NO_DATA", "NOT_AVAILABLE", "NOT_WORKING_DAY", "ERROR");
    }

    @Test
    void stampingKeepsTheFigureAndSetsTheTimeAndTheOrganizationWideFlag() {
        Instant now = Instant.parse("2026-10-21T06:30:00Z");
        TileResult stamped = TileResult.ok("k", Value.count("a", 3, null)).stamped(now, true);
        assertThat(stamped.takenAt()).isEqualTo(now);
        assertThat(stamped.organizationWide()).isTrue();
        assertThat(stamped.values()).hasSize(1);
    }
}
