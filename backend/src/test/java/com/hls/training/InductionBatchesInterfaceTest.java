package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.ExpensesTestBase;
import com.hls.training.api.InductionBatchView;
import com.hls.training.api.InductionBatches;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 015 T051: the additive reads of induction batches used by Training Stay links and the cost per Teacher. */
class InductionBatchesInterfaceTest extends ExpensesTestBase {

    @Autowired
    private InductionBatches batches;

    @Test
    void completedTeachersCountsOnlySignedOffCompleters() {
        LocalDate start = LocalDate.of(2023, 1, 2);
        UUID batch = insertBatch(uniqueName("B"), start, start.plusDays(20));
        insertCompleter(batch, start, start.plusDays(20));
        insertCompleter(batch, start, start.plusDays(20));
        jdbc.update(
                "insert into induction_enrolment (id, batch_id, teacher_id, starts_on, ends_on, enrolled_by, enrolled_at)"
                        + " values (?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), batch, UUID.randomUUID(), start, start.plusDays(20), UUID.randomUUID());
        assertThat(batches.completedTeachers(batch)).isEqualTo(2);
        assertThat(batches.completedTeachers(UUID.randomUUID())).isZero();
    }

    @Test
    void endingBetweenAndOpenFromIgnoreCancelledBatches() {
        LocalDate end = LocalDate.of(2023, 8, 15);
        UUID kept = insertBatch(uniqueName("Kept"), end.minusDays(10), end);
        UUID cancelled = insertBatch(uniqueName("Cancelled"), end.minusDays(10), end);
        jdbc.update("update induction_batch set status = 'CANCELLED' where id = ?", cancelled);

        assertThat(batches.endingBetween(end, end)).extracting(InductionBatchView::id).containsExactly(kept);
        assertThat(batches.endingBetween(end.plusDays(1), end.plusDays(5))).isEmpty();
        assertThat(batches.openFrom(end).stream().map(InductionBatchView::id)).contains(kept).doesNotContain(cancelled);
        assertThat(batches.openFrom(end.plusDays(1)).stream().map(InductionBatchView::id)).doesNotContain(kept);
    }
}
