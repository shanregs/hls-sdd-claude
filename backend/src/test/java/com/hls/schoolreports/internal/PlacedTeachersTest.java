package com.hls.schoolreports.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.reporting.api.MonthState;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.PlacementSpan;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A Zone Manager month state and default month ignore Teachers placed in other Zones. */
class PlacedTeachersTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final UUID MINE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID T_MINE = UUID.randomUUID();
    private static final UUID T_OTHER = UUID.randomUUID();

    private final TeacherDirectory teachers = mock(TeacherDirectory.class);
    private final AttendanceReadApi attendance = mock(AttendanceReadApi.class);

    private void placements() {
        when(teachers.teachersPlacedDuring(any(), any())).thenReturn(Set.of(T_MINE, T_OTHER));
        when(teachers.placementsOverlapping(any(), any(), any()))
                .thenReturn(List.of(
                        new PlacementSpan(T_MINE, MINE, LocalDate.of(2026, 1, 1), null),
                        new PlacementSpan(T_OTHER, OTHER, LocalDate.of(2026, 1, 1), null)));
    }

    @Test
    void anotherZonesOpenTeacherDoesNotKeepMyMonthOpen() {
        placements();
        when(attendance.lockedTeachers(any(), any())).thenAnswer(i -> {
            Collection<UUID> asked = i.getArgument(0);
            return asked.contains(T_OTHER) ? Set.<UUID>of() : Set.copyOf(asked);
        });

        assertThat(PlacedTeachers.stateOf(teachers, attendance, Set.of(MINE), MONTH)).isEqualTo(MonthState.LOCKED);
        assertThat(PlacedTeachers.stateOf(teachers, attendance, null, MONTH)).isEqualTo(MonthState.OPEN);
    }

    @Test
    void noTeacherAtMySchoolsGivesNone() {
        placements();

        assertThat(PlacedTeachers.stateOf(teachers, attendance, Set.of(UUID.randomUUID()), MONTH)).isEqualTo(MonthState.NONE);
    }

    @Test
    void lastLockedLooksOnlyAtMyTeachersAndReadsPlacementsOncePerCall() {
        placements();
        YearMonth august = MONTH.minusMonths(1);
        when(attendance.lockedTeachers(any(), any())).thenAnswer(i -> {
            Collection<UUID> asked = i.getArgument(0);
            YearMonth m = i.getArgument(1);
            return m.equals(august) && !asked.contains(T_OTHER) ? Set.copyOf(asked) : Set.<UUID>of();
        });

        assertThat(PlacedTeachers.lastLocked(teachers, attendance, Set.of(MINE), MONTH)).isEqualTo(Optional.of(august));
        assertThat(PlacedTeachers.lastLocked(teachers, attendance, null, MONTH)).isEmpty();
        verify(teachers, times(2)).placementsOverlapping(any(), any(), any());
    }
}
