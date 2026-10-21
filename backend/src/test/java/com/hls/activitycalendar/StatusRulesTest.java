package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.activitycalendar.internal.StatusRules;
import com.hls.activitycalendar.internal.StatusRules.Result;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Spec 035 T027: the state rules of data-model.md. */
class StatusRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static Result derive(String nativeStatus, LocalDate date, LocalDate completedOn) {
        return StatusRules.derive(nativeStatus, date, date, completedOn, TODAY);
    }

    @Test
    void cancelledIsCancelledWhateverTheDate() {
        assertThat(derive("CANCELLED", TODAY.minusDays(3), null).status()).isEqualTo("CANCELLED");
        assertThat(derive("CANCELLED", TODAY.plusDays(3), null).status()).isEqualTo("CANCELLED");
    }

    @Test
    void completedHeldAndVerifiedAreDone() {
        for (String done : new String[] {"COMPLETED", "HELD", "VERIFIED"}) {
            Result r = derive(done, TODAY.minusDays(2), TODAY.minusDays(2));
            assertThat(r.status()).as(done).isEqualTo("DONE");
            assertThat(r.doneLate()).as(done).isFalse();
        }
    }

    @Test
    void doneAfterTheDateIsDoneLateAndOnOrBeforeIsNot() {
        assertThat(derive("COMPLETED", TODAY.minusDays(5), TODAY).doneLate()).isTrue();
        assertThat(derive("COMPLETED", TODAY.minusDays(5), TODAY.minusDays(5)).doneLate()).isFalse();
        assertThat(derive("COMPLETED", TODAY.minusDays(5), TODAY.minusDays(7)).doneLate()).isFalse();
        // a completion date that is not known (before the amendment) is Done without the late mark
        Result unknown = derive("HELD", TODAY.minusDays(5), null);
        assertThat(unknown.status()).isEqualTo("DONE");
        assertThat(unknown.doneLate()).isFalse();
    }

    @Test
    void openAndBeforeTodayIsMissedWithDaysLate() {
        for (String open : new String[] {"PLANNED", "ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS"}) {
            Result r = derive(open, TODAY.minusDays(4), null);
            assertThat(r.status()).as(open).isEqualTo("MISSED");
            assertThat(r.daysLate()).as(open).isEqualTo(4);
        }
    }

    @Test
    void anOpenItemDatedTodayOrLaterStaysPlanned() {
        assertThat(derive("PLANNED", TODAY, null).status()).isEqualTo("PLANNED");
        assertThat(derive("ASSIGNED", TODAY.plusDays(1), null).status()).isEqualTo("PLANNED");
        assertThat(derive("PLANNED", TODAY, null).daysLate()).isNull();
    }

    @Test
    void aMultiDateDriveIsJudgedOnItsLastDate() {
        LocalDate first = TODAY.minusDays(2);
        LocalDate last = TODAY.plusDays(1);
        assertThat(StatusRules.derive("PLANNED", first, last, null, TODAY).status()).isEqualTo("PLANNED");
        assertThat(StatusRules.derive("PLANNED", first, TODAY.minusDays(1), null, TODAY).status()).isEqualTo("MISSED");
    }

    @Test
    void movedItemsReportDaysMovedAndNeverMovedOnesDoNot() {
        LocalDate original = TODAY.minusDays(6);
        assertThat(StatusRules.rescheduled(original, TODAY)).isTrue();
        assertThat(StatusRules.daysMoved(original, TODAY)).isEqualTo(6);
        assertThat(StatusRules.rescheduled(null, TODAY)).isFalse();
        assertThat(StatusRules.daysMoved(null, TODAY)).isNull();
        assertThat(StatusRules.rescheduled(TODAY, TODAY)).isFalse();
    }
}
