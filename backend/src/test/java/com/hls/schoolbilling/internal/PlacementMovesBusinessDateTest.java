package com.hls.schoolbilling.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.school.api.ConflictException;
import com.hls.school.api.SchoolDirectory;
import com.hls.schoolbilling.api.PlacementMoves.AppliedMove;
import com.hls.teacher.api.TeacherDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: a move that starts on the IST date has already started, so it cannot be undone at 00:30 IST. */
class PlacementMovesBusinessDateTest {

    @Test
    void moveStartingOnTheIstDateCannotBeUndone() {
        // 2026-11-07T19:00Z is 2026-11-08 00:30 IST.
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        UUID created = UUID.randomUUID();
        ContractAssignmentRepository assignments = mock(ContractAssignmentRepository.class);
        ContractAssignment row = mock(ContractAssignment.class);
        when(row.getStatus()).thenReturn(AssignmentStatus.ACTIVE);
        when(row.getStartsOn()).thenReturn(LocalDate.parse("2026-11-08"));
        when(assignments.findById(created)).thenReturn(Optional.of(row));

        PlacementMovesImpl moves = new PlacementMovesImpl(
                mock(AssignmentService.class), mock(TeacherDirectory.class), mock(SchoolDirectory.class),
                mock(ContractRepository.class), mock(ContractPositionRepository.class), assignments, clock,
                new BusinessDate(clock, "Asia/Kolkata"));

        assertThatThrownBy(() -> moves.reverse(UUID.randomUUID(), new AppliedMove(List.of(), List.of(created))))
                .isInstanceOf(ConflictException.class);
    }
}
