package com.hls.substitution.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.substitution.payout.DayStatusDeriver.CorrectionState;
import com.hls.substitution.payout.DayStatusDeriver.Facts;
import com.hls.substitution.payout.DayStatusDeriver.RunHolding;
import org.junit.jupiter.api.Test;

/** Spec 017b data-model.md "Status derivation": every row of the table, with no database. */
class DayStatusDeriverTest {

    private static Facts facts(
            boolean live, DayAttendance a, boolean locked, boolean priceable, RunHolding run, boolean monthApproved, boolean paid) {
        return new Facts(live, a, locked, priceable, run, monthApproved, paid, CorrectionState.NONE);
    }

    private static DayStatus of(boolean live, DayAttendance a, boolean locked, boolean priceable, RunHolding run) {
        return DayStatusDeriver.derive(facts(live, a, locked, priceable, run, false, false));
    }

    @Test
    void aCancelledAllocationFollowsWhatTheRunHolds() {
        assertThat(of(false, DayAttendance.NOT_MARKED, false, true, RunHolding.NONE)).isEqualTo(DayStatus.CANCELLED);
        assertThat(of(false, DayAttendance.PRESENT, true, true, RunHolding.DRAFT)).isEqualTo(DayStatus.CANCELLED_BEFORE_PAYMENT);
        assertThat(of(false, DayAttendance.PRESENT, true, true, RunHolding.AWAITING_APPROVAL))
                .isEqualTo(DayStatus.CHANGED_RUN_AWAITING);
        assertThat(of(false, DayAttendance.PRESENT, true, true, RunHolding.APPROVED)).isEqualTo(DayStatus.CORRECTION_NEEDED);
    }

    @Test
    void anUnmarkedDayIsAllocatedAndAbsentIsDidNotAttend() {
        assertThat(of(true, DayAttendance.NOT_MARKED, false, true, RunHolding.NONE)).isEqualTo(DayStatus.ALLOCATED);
        assertThat(of(true, DayAttendance.ABSENT, false, true, RunHolding.NONE)).isEqualTo(DayStatus.DID_NOT_ATTEND);
        assertThat(of(true, DayAttendance.ABSENT, true, true, RunHolding.DRAFT)).isEqualTo(DayStatus.DID_NOT_ATTEND);
    }

    @Test
    void presentButNotLockedIsAttended() {
        assertThat(of(true, DayAttendance.PRESENT, false, true, RunHolding.NONE)).isEqualTo(DayStatus.ATTENDED);
    }

    @Test
    void lockedAndPresentWithNoRateCannotBePaid() {
        assertThat(of(true, DayAttendance.PRESENT, true, false, RunHolding.NONE)).isEqualTo(DayStatus.CANNOT_PAY);
    }

    @Test
    void lockedAndPricedWaitsForTheRunOrForASupplementaryRun() {
        assertThat(DayStatusDeriver.derive(facts(true, DayAttendance.PRESENT, true, true, RunHolding.NONE, false, false)))
                .isEqualTo(DayStatus.LOCKED_WAITING_RUN);
        assertThat(DayStatusDeriver.derive(facts(true, DayAttendance.PRESENT, true, true, RunHolding.NONE, true, false)))
                .isEqualTo(DayStatus.BACKPAY_WAITING);
    }

    @Test
    void aDayAddedAfterTheMonthWasPaidNeedsACorrectionBecauseAPersonIsPaidOncePerMonth() {
        assertThat(DayStatusDeriver.derive(facts(true, DayAttendance.PRESENT, true, true, RunHolding.NONE, true, true)))
                .isEqualTo(DayStatus.CORRECTION_NEEDED);
    }

    @Test
    void theRunHoldingDecidesTheStatusOfAPricedLockedDay() {
        assertThat(of(true, DayAttendance.PRESENT, true, true, RunHolding.DRAFT)).isEqualTo(DayStatus.IN_DRAFT_RUN);
        assertThat(of(true, DayAttendance.PRESENT, true, true, RunHolding.AWAITING_APPROVAL))
                .isEqualTo(DayStatus.IN_RUN_AWAITING_APPROVAL);
        assertThat(of(true, DayAttendance.PRESENT, true, true, RunHolding.APPROVED)).isEqualTo(DayStatus.PAID);
    }

    @Test
    void aPaidDayStaysPaidWhenTheMonthIsReopened() {
        assertThat(of(true, DayAttendance.PRESENT, false, true, RunHolding.APPROVED)).isEqualTo(DayStatus.PAID);
    }

    @Test
    void changingADayAfterPaymentNeedsACorrectionAndWhileAwaitingApprovalItWaits() {
        assertThat(of(true, DayAttendance.ABSENT, true, true, RunHolding.APPROVED)).isEqualTo(DayStatus.CORRECTION_NEEDED);
        assertThat(of(true, DayAttendance.NOT_MARKED, false, true, RunHolding.APPROVED)).isEqualTo(DayStatus.CORRECTION_NEEDED);
        assertThat(of(true, DayAttendance.ABSENT, true, true, RunHolding.AWAITING_APPROVAL))
                .isEqualTo(DayStatus.CHANGED_RUN_AWAITING);
    }

    @Test
    void aRaisedCorrectionThenAnAppliedOneOverridesTheRest() {
        assertThat(DayStatusDeriver.derive(new Facts(
                        true, DayAttendance.ABSENT, true, true, RunHolding.APPROVED, true, true, CorrectionState.RAISED)))
                .isEqualTo(DayStatus.CORRECTION_RAISED);
        assertThat(DayStatusDeriver.derive(new Facts(
                        true, DayAttendance.ABSENT, true, true, RunHolding.APPROVED, true, true, CorrectionState.APPLIED)))
                .isEqualTo(DayStatus.CORRECTED);
    }
}
