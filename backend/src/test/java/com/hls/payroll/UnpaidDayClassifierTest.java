package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.api.UnpaidDay;
import com.hls.payroll.internal.UnpaidDayClassifier;
import com.hls.payroll.internal.UnpaidDayClassifier.Mark;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a FR-012: which payable dates are unpaid; by status code category, never the letter (T030). */
class UnpaidDayClassifierTest {

    private static final BigDecimal HALF = new BigDecimal("0.500");
    private static final UUID LOP_REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID CASUAL_REQUEST = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    private static LocalDate nov(int day) {
        return LocalDate.of(2026, 11, day);
    }

    private static Mark mark(int day, String category, String dayValue, UUID request) {
        return new Mark(nov(day), category, new BigDecimal(dayValue), request);
    }

    private static List<UnpaidDay> classify(List<Mark> marks, Set<LocalDate> payable) {
        return UnpaidDayClassifier.classify(marks, payable, Set.of(LOP_REQUEST), HALF);
    }

    @Test
    void anAbsenceWithNoApprovedLeaveIsUnpaid() {
        assertThat(classify(List.of(mark(3, "LEAVE", "1.00", null)), Set.of(nov(3))))
                .containsExactly(new UnpaidDay(nov(3), BigDecimal.ONE));
    }

    @Test
    void aLossOfPayLeaveDayIsUnpaid() {
        assertThat(classify(List.of(mark(4, "LEAVE", "1.00", LOP_REQUEST)), Set.of(nov(4))))
                .containsExactly(new UnpaidDay(nov(4), BigDecimal.ONE));
    }

    @Test
    void aHalfDayCountsAsTheHalfDayFraction() {
        assertThat(classify(List.of(mark(5, "LEAVE", "0.50", null)), Set.of(nov(5))))
                .containsExactly(new UnpaidDay(nov(5), HALF));
        assertThat(UnpaidDayClassifier.classify(
                        List.of(mark(5, "LEAVE", "0.50", null)), Set.of(nov(5)), Set.of(), new BigDecimal("0.250")))
                .containsExactly(new UnpaidDay(nov(5), new BigDecimal("0.250")));
    }

    @Test
    void approvedCasualOrSickLeaveIsNotUnpaid() {
        assertThat(classify(List.of(mark(6, "LEAVE", "1.00", CASUAL_REQUEST)), Set.of(nov(6)))).isEmpty();
    }

    @Test
    void aHolidayOrWeeklyOffIsNeverUnpaid() {
        // such a date is not in the payable (working) dates, and a NON_WORKING mark is not an absence either
        assertThat(classify(List.of(mark(7, "NON_WORKING", "1.00", null)), Set.of(nov(7)))).isEmpty();
        assertThat(classify(List.of(mark(8, "LEAVE", "1.00", null)), Set.of())).isEmpty();
    }

    @Test
    void aNonPayableDateIsNeverUnpaid() {
        // an absence mark before joining or after exit lies outside the payable dates
        assertThat(classify(List.of(mark(2, "LEAVE", "1.00", null), mark(25, "LEAVE", "1.00", null)), Set.of(nov(10))))
                .isEmpty();
    }

    @Test
    void presentAndTrainingMarksAreNotUnpaid() {
        assertThat(classify(
                        List.of(mark(9, "WORKED", "1.00", null), mark(10, "TRAINING", "1.00", null)),
                        Set.of(nov(9), nov(10))))
                .isEmpty();
    }

    @Test
    void classificationUsesTheCategoryNotTheLetter() {
        // the Mark carries no letter at all: whatever code a School adds, only its category decides
        assertThat(classify(List.of(mark(11, "LEAVE", "1.00", null)), Set.of(nov(11)))).hasSize(1);
        assertThat(classify(List.of(mark(11, "WORKED", "1.00", null)), Set.of(nov(11)))).isEmpty();
    }

    @Test
    void anUnmarkedDateIsNotClassified() {
        assertThat(classify(List.of(), Set.of(nov(12), nov(13)))).isEmpty();
    }

    @Test
    void theUnpaidDaysComeOldestFirst() {
        assertThat(classify(
                        List.of(mark(20, "LEAVE", "1.00", null), mark(3, "LEAVE", "1.00", LOP_REQUEST)),
                        Set.of(nov(3), nov(20))))
                .extracting(UnpaidDay::date)
                .containsExactly(nov(3), nov(20));
    }
}
