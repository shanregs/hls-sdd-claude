package com.hls.training.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.TrainingAttendance;
import com.hls.designation.api.BusinessDate;
import com.hls.school.api.ChangeRecorder;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: "today" in induction is the IST date; 00:30 IST on the 8th must already count as the 8th. */
class TrainingBusinessDateTest {

    private static final LocalDate TODAY_IST = LocalDate.parse("2026-11-08");
    // 2026-11-07T19:00Z is 2026-11-08 00:30 IST.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
    private final BusinessDate businessDate = new BusinessDate(clock, "Asia/Kolkata");

    @Test
    void attendanceCanBeRecordedForTheIstDate() {
        UUID enrolmentId = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        InductionEnrolmentRepository enrolments = mock(InductionEnrolmentRepository.class);
        InductionEnrolment enrolment = mock(InductionEnrolment.class);
        when(enrolment.isSignedOff()).thenReturn(false);
        when(enrolment.getStartsOn()).thenReturn(LocalDate.parse("2026-11-01"));
        when(enrolment.getEndsOn()).thenReturn(LocalDate.parse("2026-11-30"));
        when(enrolment.getTeacherId()).thenReturn(teacher);
        when(enrolments.findById(enrolmentId)).thenReturn(Optional.of(enrolment));
        TrainingAttendance attendance = mock(TrainingAttendance.class);

        InductionAttendanceService service = new InductionAttendanceService(
                enrolments, mock(InductionAbsenceRepository.class), mock(BatchService.class), attendance,
                mock(AttendanceReadApi.class), mock(TeacherDirectory.class), mock(ChangeRecorder.class), clock, businessDate);

        assertThatCode(() -> service.record(
                        UUID.randomUUID(), enrolmentId, new InductionAttendanceService.AttendanceRequest(TODAY_IST, "PRESENT", null)))
                .doesNotThrowAnyException();
        verify(attendance).markTrainingDay(any(), eq(teacher), eq(TODAY_IST), eq(new BigDecimal("1.00")));
    }

    @Test
    void signOffIsAllowedOnTheIstStartDateOfTheBatch() {
        UUID enrolmentId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        InductionEnrolmentRepository enrolments = mock(InductionEnrolmentRepository.class);
        InductionEnrolment enrolment = mock(InductionEnrolment.class);
        when(enrolment.getBatchId()).thenReturn(batchId);
        when(enrolment.isSignedOff()).thenReturn(false);
        when(enrolments.findById(enrolmentId)).thenReturn(Optional.of(enrolment));
        BatchService batchService = mock(BatchService.class);
        InductionBatch batch = mock(InductionBatch.class);
        when(batch.getStartsOn()).thenReturn(TODAY_IST);
        when(batchService.find(batchId)).thenReturn(batch);

        SignOffService service = new SignOffService(
                enrolments, mock(InductionBatchRepository.class), batchService, mock(TeacherRegistry.class),
                mock(ChangeRecorder.class), clock, businessDate);

        assertThatCode(() -> service.signOff(
                        UUID.randomUUID(), enrolmentId, new SignOffService.SignOffRequest("NOT_COMPLETED", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void releaseExitsTheTeacherOnTheIstDate() {
        UUID enrolmentId = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        InductionEnrolmentRepository enrolments = mock(InductionEnrolmentRepository.class);
        InductionEnrolment enrolment = mock(InductionEnrolment.class);
        when(enrolment.getResult()).thenReturn("NOT_COMPLETED");
        when(enrolment.getTeacherId()).thenReturn(teacher);
        when(enrolments.findById(enrolmentId)).thenReturn(Optional.of(enrolment));
        TeacherRegistry registry = mock(TeacherRegistry.class);

        SignOffService service = new SignOffService(
                enrolments, mock(InductionBatchRepository.class), mock(BatchService.class), registry,
                mock(ChangeRecorder.class), clock, businessDate);

        service.followUp(UUID.randomUUID(), enrolmentId, new SignOffService.FollowUpRequest("RELEASE", "left"));

        verify(registry).exit(any(), eq(teacher), eq(TODAY_IST), eq("left"));
    }

    @Test
    void nextBatchSearchStartsFromTheIstDate() {
        InductionBatchRepository batches = mock(InductionBatchRepository.class);
        when(batches.openFrom(any())).thenReturn(List.of());
        BatchService service = new BatchService(
                batches, mock(InductionEnrolmentRepository.class), mock(TeacherDirectory.class), mock(TeacherRegistry.class),
                mock(ChangeRecorder.class), clock, businessDate);

        service.nextWithRoom(null);

        verify(batches).openFrom(TODAY_IST);
    }
}
