package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.hls.leave.internal.LeaveRequestRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Two simultaneous overlapping submissions can make Postgres report a deadlock (40P01) while it checks the
 * overlap exclusion constraint. The loser must be refused as an overlap (409), not fail with a 500. The
 * race itself is timing dependent, so this test makes the save report the deadlock directly.
 */
class LeaveSubmitLockFailureTest extends LeaveTestBase {

    @MockitoSpyBean
    private LeaveRequestRepository requests;

    @AfterEach
    void restore() {
        reset(requests);
    }

    @Test
    void aDeadlockOnTheOverlapConstraintIsRefusedAsAnOverlap() {
        World w = newWorld();
        LocalDate mon = monday();
        doThrow(new CannotAcquireLockException("deadlock detected")).when(requests).saveAndFlush(any());

        Resp resp = post(ME, w.teacherA().token(), draft(mon, mon.plusDays(1)));

        assertThat(resp.status()).as(resp.body()).isEqualTo(409);
        assertThat(resp.body()).contains("overlap");
    }
}
