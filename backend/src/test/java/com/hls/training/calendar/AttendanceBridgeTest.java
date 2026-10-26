package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.TrainingAttendance;
import com.hls.school.api.ConflictException;
import com.hls.training.calendar.internal.AttendanceBridge;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T039: the only writer to Teacher attendance, against mocked ports. */
class AttendanceBridgeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);
    private final AttendanceReadApi read = mock(AttendanceReadApi.class);
    private final TrainingAttendance write = mock(TrainingAttendance.class);
    private final AttendanceBridge bridge = new AttendanceBridge(read, write);
    private final UUID actor = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();

    private static MarkView mark(String code, String name) {
        return new MarkView(DAY, code, name, "PRESENCE", BigDecimal.ONE, null, null, "SUPERVISOR", null, null, null, null, 0L, null, false, null);
    }

    @Test
    void presentWholeOrHalfWritesTheTrainingDay() {
        var whole = bridge.markPresent(actor, teacher, DAY, new BigDecimal("1.00"));
        var half = bridge.markPresent(actor, teacher, DAY, new BigDecimal("0.50"));
        assertThat(whole.saved()).isTrue();
        assertThat(half.saved()).isTrue();
        verify(write).markTrainingDay(actor, teacher, DAY, new BigDecimal("1.00"));
        verify(write).markTrainingDay(actor, teacher, DAY, new BigDecimal("0.50"));
    }

    @Test
    void aTrainingMarkAlreadyThereIsUpdated() {
        when(read.marksOf(teacher, YearMonth.of(2026, 10))).thenReturn(List.of(mark("T", "Training")));
        assertThat(bridge.markPresent(actor, teacher, DAY, new BigDecimal("0.50")).saved()).isTrue();
        verify(write).markTrainingDay(actor, teacher, DAY, new BigDecimal("0.50"));
    }

    @Test
    void aLockedMonthRefusesTheRowWithoutWriting() {
        when(read.isLocked(teacher, YearMonth.of(2026, 10))).thenReturn(true);
        var out = bridge.markPresent(actor, teacher, DAY, BigDecimal.ONE);
        assertThat(out.refused()).isTrue();
        assertThat(out.code()).isEqualTo("MONTH_LOCKED");
        assertThat(out.message()).contains("locked");
        verify(write, never()).markTrainingDay(any(), any(), any(), any());
    }

    @Test
    void aDifferentExistingMarkIsAClashAndNothingIsWritten() {
        when(read.marksOf(teacher, YearMonth.of(2026, 10))).thenReturn(List.of(mark("P", "Present at school")));
        var out = bridge.markPresent(actor, teacher, DAY, BigDecimal.ONE);
        assertThat(out.clash()).isTrue();
        assertThat(out.message()).contains("Present at school");
        verify(write, never()).markTrainingDay(any(), any(), any(), any());
    }

    @Test
    void aCorrectionAwayFromPresentClearsTheTrainingDay() {
        when(read.marksOf(teacher, YearMonth.of(2026, 10))).thenReturn(List.of(mark("T", "Training")));
        assertThat(bridge.clear(actor, teacher, DAY).saved()).isTrue();
        verify(write).clearTrainingDay(actor, teacher, DAY);
    }

    @Test
    void clearingNeverTouchesASchoolMarkOrAnEmptyDay() {
        when(read.marksOf(teacher, YearMonth.of(2026, 10))).thenReturn(List.of(mark("P", "Present at school")));
        assertThat(bridge.clear(actor, teacher, DAY).saved()).isTrue();
        UUID other = UUID.randomUUID();
        assertThat(bridge.clear(actor, other, DAY).saved()).isTrue();
        verify(write, never()).clearTrainingDay(any(), any(), any());
    }

    @Test
    void clearingInALockedMonthIsRefused() {
        when(read.marksOf(teacher, YearMonth.of(2026, 10))).thenReturn(List.of(mark("T", "Training")));
        when(read.isLocked(teacher, YearMonth.of(2026, 10))).thenReturn(true);
        assertThat(bridge.clear(actor, teacher, DAY).refused()).isTrue();
        verify(write, never()).clearTrainingDay(any(), any(), any());
    }

    @Test
    void aRefusalFromTheWriteIsReturnedNotThrown() {
        org.mockito.Mockito.doThrow(new ConflictException("You cannot mark a date in the future."))
                .when(write)
                .markTrainingDay(any(), eq(teacher), any(), any());
        var out = bridge.markPresent(actor, teacher, DAY, BigDecimal.ONE);
        assertThat(out.refused()).isTrue();
        assertThat(out.message()).contains("future");
    }
}
