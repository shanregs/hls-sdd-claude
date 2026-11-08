package com.hls.teacher.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.school.api.ChangeRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: activating a Teacher records the IST date, not the JVM-zone or UTC date. */
class TeacherRegistryBusinessDateTest {

    @Test
    void activateRecordsTheIstDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        UUID teacherId = UUID.randomUUID();
        TeacherService teacherService = mock(TeacherService.class);
        TeacherRepository teachers = mock(TeacherRepository.class);
        Teacher teacher = mock(Teacher.class);
        when(teacher.getStatus()).thenReturn(TeacherStatus.IN_TRAINING);
        when(teachers.findById(teacherId)).thenReturn(Optional.of(teacher));

        TeacherRegistryImpl registry = new TeacherRegistryImpl(
                teacherService, mock(SalaryService.class), teachers, mock(SalaryHistoryRepository.class),
                mock(ChangeRecorder.class), new BusinessDate(clock, "Asia/Kolkata"));

        registry.activate(UUID.randomUUID(), teacherId);

        verify(teacherService).changeStatus(any(), eq(teacherId), eq(TeacherStatus.ACTIVE), eq(LocalDate.parse("2026-11-08")));
    }
}
