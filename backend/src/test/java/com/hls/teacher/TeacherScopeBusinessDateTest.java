package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.designation.api.BusinessDate;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import com.hls.teacher.internal.TeacherRepository;
import com.hls.teacher.internal.TeacherScopeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regression: between 00:00 and 05:30 IST the UTC date is still yesterday; a Manager must still see Teachers placed effective the IST date. */
class TeacherScopeBusinessDateTest {

    @Test
    void managerSeesTeacherPlacedEffectiveTheIstDateJustAfterIstMidnight() {
        // 2026-11-07T19:00Z is 2026-11-08 00:30 IST.
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T19:00:00Z"), ZoneOffset.UTC);
        UUID manager = UUID.randomUUID();
        UUID school = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();

        ScopeQueries scopeQueries = mock(ScopeQueries.class);
        ScopeView view = new ScopeView(false, Set.of(), Set.of(school));
        when(scopeQueries.scopeOf(eq(manager), any())).thenReturn(view);
        TeacherPlacementSource placements = mock(TeacherPlacementSource.class);
        // Placed effective 2026-11-08: visible only when asked for that date or later.
        when(placements.teacherIdsAtSchoolsOn(any(), any())).thenAnswer(call -> {
            LocalDate on = call.getArgument(1);
            return on.isBefore(LocalDate.parse("2026-11-08")) ? Set.<UUID>of() : Set.of(teacher);
        });

        TeacherScopeService service = new TeacherScopeService(
                scopeQueries, placements, mock(TeacherRepository.class), new BusinessDate(clock, "Asia/Kolkata"));

        TeacherScope scope = service.teacherScope(manager, Set.of(Role.MANAGER));

        assertThat(scope.teacherIds()).containsExactly(teacher);
    }
}
