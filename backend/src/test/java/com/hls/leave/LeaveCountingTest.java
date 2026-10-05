package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.LeaveAttendance.LeaveDay;
import com.hls.leave.internal.LeaveCounter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Spec 009 research.md section 4: whole days, and half days on the first and last working day. */
class LeaveCountingTest {

    private static final UUID SCHOOL = UUID.randomUUID();
    private static final LocalDate MON = LocalDate.of(2026, 1, 12);

    private static List<LeaveDay> days(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new LeaveDay(MON.plusDays(i), SCHOOL, new BigDecimal("1.00")))
                .toList();
    }

    @Test
    void everyWorkingDayCountsOne() {
        var counted = LeaveCounter.count(days(4), false, false);

        assertThat(counted.ok()).isTrue();
        assertThat(counted.total()).isEqualByComparingTo("4");
        assertThat(counted.days()).allSatisfy(d -> assertThat(d.value()).isEqualByComparingTo("1"));
    }

    @Test
    void aHalfDayStartMakesTheFirstWorkingDayHalf() {
        var counted = LeaveCounter.count(days(3), true, false);

        assertThat(counted.total()).isEqualByComparingTo("2.5");
        assertThat(counted.days().get(0).value()).isEqualByComparingTo("0.5");
        assertThat(counted.days().get(2).value()).isEqualByComparingTo("1");
    }

    @Test
    void aHalfDayEndMakesTheLastWorkingDayHalf() {
        var counted = LeaveCounter.count(days(3), false, true);

        assertThat(counted.total()).isEqualByComparingTo("2.5");
        assertThat(counted.days().get(2).value()).isEqualByComparingTo("0.5");
    }

    @Test
    void bothHalvesOnALongerRangeCountTwoHalves() {
        var counted = LeaveCounter.count(days(4), true, true);

        assertThat(counted.total()).isEqualByComparingTo("3");
    }

    @Test
    void aSingleWorkingDayAcceptsOneHalfFlagButNotBoth() {
        assertThat(LeaveCounter.count(days(1), true, false).total()).isEqualByComparingTo("0.5");
        assertThat(LeaveCounter.count(days(1), false, true).total()).isEqualByComparingTo("0.5");

        var both = LeaveCounter.count(days(1), true, true);
        assertThat(both.ok()).isFalse();
        assertThat(both.problem()).contains("not both");
    }

    @Test
    void noWorkingDayIsRefused() {
        var counted = LeaveCounter.count(List.of(), false, false);

        assertThat(counted.ok()).isFalse();
        assertThat(counted.problem()).contains("working day");
    }

    @Test
    void daysAreCountedInDateOrderWhateverOrderTheyArriveIn() {
        List<LeaveDay> reversed = days(3).reversed();

        var counted = LeaveCounter.count(reversed, true, false);

        assertThat(counted.days()).extracting(LeaveDay::date).isSorted();
        assertThat(counted.days().get(0).value()).isEqualByComparingTo("0.5");
    }
}
