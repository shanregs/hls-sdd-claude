package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.training.calendar.internal.GroupResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 024 T032: the groups, who is refused and why, and that a group is a snapshot of the day it is resolved. */
class GroupResolverTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    private final TeacherPlacementSource placements = mock(TeacherPlacementSource.class);
    private final SchoolDirectory schools = mock(SchoolDirectory.class);
    private final ManagerQueries managers = mock(ManagerQueries.class);
    private final TeacherDirectory teachers = mock(TeacherDirectory.class);
    private GroupResolver resolver;

    private final UUID active = UUID.randomUUID();
    private final UUID onLeave = UUID.randomUUID();
    private final UUID inTraining = UUID.randomUUID();
    private final UUID exited = UUID.randomUUID();
    private final UUID managerOne = UUID.randomUUID();
    private final UUID managerTwo = UUID.randomUUID();
    private final UUID inactiveManager = UUID.randomUUID();
    private final UUID school = UUID.randomUUID();
    private final UUID zone = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new GroupResolver(placements, schools, managers, teachers);
        Map<UUID, TeacherInfo> info = Map.of(
                active, new TeacherInfo(active, "Asha", "ACTIVE", null),
                onLeave, new TeacherInfo(onLeave, "Beena", "ON_LEAVE", null),
                inTraining, new TeacherInfo(inTraining, "Chitra", "IN_TRAINING", null),
                exited, new TeacherInfo(exited, "Divya", "EXITED", null));
        when(teachers.teacherInfo(any())).thenAnswer(call -> {
            java.util.Collection<UUID> ids = call.getArgument(0);
            java.util.Map<UUID, TeacherInfo> out = new java.util.HashMap<>();
            ids.forEach(id -> {
                if (info.containsKey(id)) {
                    out.put(id, info.get(id));
                }
            });
            return out;
        });
        when(teachers.activeTeachers()).thenReturn(List.of(info.get(active)));
        when(managers.allManagers()).thenReturn(List.of(
                new ManagerRef(managerOne, UUID.randomUUID(), "Meera", true),
                new ManagerRef(managerTwo, UUID.randomUUID(), "Nikhil", true),
                new ManagerRef(inactiveManager, UUID.randomUUID(), "Old Manager", false)));
        when(placements.teacherIdsAtSchoolsOn(any(), any())).thenReturn(Set.of(active, onLeave, inTraining, exited));
        when(managers.currentSchoolIds(any())).thenReturn(Map.of(managerOne, Set.of(school)));
        when(schools.schoolsInZone(zone)).thenReturn(List.of(new SchoolInfo(school, "S", UUID.randomUUID(), zone, true)));
        when(managers.managersOfZone(zone)).thenReturn(List.of(new ManagerRef(managerTwo, UUID.randomUUID(), "Nikhil", true)));
    }

    @Test
    void aSchoolGroupHasOnlyActiveTeachers() {
        var resolved = resolver.group(GroupResolver.SCHOOL, school, DAY);
        assertThat(resolved.teacherIds()).containsExactly(active);
        assertThat(resolved.managerIds()).isEmpty();
        // IN_TRAINING and EXITED are refused with a reason; ON_LEAVE is simply not in a group
        assertThat(resolved.refused()).extracting(GroupResolver.Refused::id).containsExactlyInAnyOrder(inTraining, exited);
        assertThat(resolved.refused()).allSatisfy(r -> assertThat(r.reason()).isNotBlank());
    }

    @Test
    void aManagerGroupIsTheTeachersAtTheirSchools() {
        assertThat(resolver.group(GroupResolver.MANAGER, managerOne, DAY).teacherIds()).containsExactly(active);
        assertThat(resolver.group(GroupResolver.MANAGER, managerTwo, DAY).teacherIds()).isEmpty();
    }

    @Test
    void aZoneGroupHasItsTeachersAndItsManagers() {
        var resolved = resolver.group(GroupResolver.ZONE, zone, DAY);
        assertThat(resolved.teacherIds()).containsExactly(active);
        assertThat(resolved.managerIds()).containsExactly(managerTwo);
    }

    @Test
    void theOrganisationWideGroups() {
        assertThat(resolver.group(GroupResolver.ALL_TEACHERS, null, DAY).teacherIds()).containsExactly(active);
        var managersOnly = resolver.group(GroupResolver.ALL_MANAGERS, null, DAY);
        assertThat(managersOnly.managerIds()).containsExactlyInAnyOrder(managerOne, managerTwo);
        assertThat(managersOnly.refused()).extracting(GroupResolver.Refused::id).containsExactly(inactiveManager);
        var all = resolver.group(GroupResolver.ALL, null, DAY);
        assertThat(all.size()).isEqualTo(3);
    }

    @Test
    void individualPicksAllowOnLeaveButRefuseInductionAndExit() {
        var resolved = resolver.individuals("TEACHER", List.of(active, onLeave, inTraining, exited));
        assertThat(resolved.teacherIds()).containsExactlyInAnyOrder(active, onLeave);
        assertThat(resolved.refused()).extracting(GroupResolver.Refused::id).containsExactlyInAnyOrder(inTraining, exited);
    }

    @Test
    void anInactiveManagerPickedByHandIsRefused() {
        var resolved = resolver.individuals("MANAGER", List.of(managerOne, inactiveManager));
        assertThat(resolved.managerIds()).containsExactly(managerOne);
        assertThat(resolved.refused()).extracting(GroupResolver.Refused::id).containsExactly(inactiveManager);
    }

    @Test
    void aGroupIsASnapshotOfTheCallTime() {
        var first = resolver.group(GroupResolver.SCHOOL, school, DAY);
        when(placements.teacherIdsAtSchoolsOn(any(), any())).thenReturn(Set.of());
        var later = resolver.group(GroupResolver.SCHOOL, school, DAY);
        assertThat(first.teacherIds()).containsExactly(active); // the earlier result does not change
        assertThat(later.teacherIds()).isEmpty();
    }

    @Test
    void badInputIsRefused() {
        assertThatThrownBy(() -> resolver.group("NOPE", null, DAY)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> resolver.group(GroupResolver.SCHOOL, null, DAY)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> resolver.individuals("TEACHER", List.of())).isInstanceOf(InvalidInputException.class);
    }
}
