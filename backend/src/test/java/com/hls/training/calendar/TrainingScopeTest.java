package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.identity.user.Role;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import com.hls.training.calendar.internal.Caller;
import com.hls.training.calendar.internal.TrainingScope;
import com.hls.training.calendar.internal.TrainingScope.Scope;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T013 (SC-006): two Managers and two Teachers, and what each may reach. */
class TrainingScopeTest {

    private final UUID managerAUser = UUID.randomUUID();
    private final UUID managerBUser = UUID.randomUUID();
    private final UUID managerA = UUID.randomUUID();
    private final UUID managerB = UUID.randomUUID();
    private final UUID teacher1 = UUID.randomUUID();
    private final UUID teacher2 = UUID.randomUUID();
    private final UUID teacher3 = UUID.randomUUID();
    private final UUID teacher1User = UUID.randomUUID();
    private final UUID teacher2User = UUID.randomUUID();

    private TrainingScope scope() {
        TeacherScopeQueries scopes = mock(TeacherScopeQueries.class);
        when(scopes.teacherScope(any(), any())).thenAnswer(call -> {
            UUID user = call.getArgument(0);
            return user.equals(managerAUser)
                    ? new TeacherScope(false, Set.of(teacher1, teacher2))
                    : new TeacherScope(false, Set.of(teacher3));
        });
        TeacherDirectory teachers = mock(TeacherDirectory.class);
        when(teachers.teacherOfUser(teacher1User)).thenReturn(Optional.of(new TeacherInfo(teacher1, "T1", "ACTIVE", teacher1User)));
        when(teachers.teacherOfUser(teacher2User)).thenReturn(Optional.of(new TeacherInfo(teacher2, "T2", "ACTIVE", teacher2User)));
        ManagerQueries managers = mock(ManagerQueries.class);
        when(managers.managerByUserId(managerAUser)).thenReturn(Optional.of(new ManagerRef(managerA, managerAUser, "A", true)));
        when(managers.managerByUserId(managerBUser)).thenReturn(Optional.of(new ManagerRef(managerB, managerBUser, "B", true)));
        return new TrainingScope(scopes, teachers, managers);
    }

    @Test
    void adminAndDirectorReachEveryone() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            Scope s = scope().of(new Caller(UUID.randomUUID(), Set.of(role)));
            assertThat(s.orgWide()).isTrue();
            assertThat(s.allows("TEACHER", teacher3)).isTrue();
            assertThat(s.allows("MANAGER", managerB)).isTrue();
        }
    }

    @Test
    void aManagerReachesOwnTeachersAndThemselvesOnly() {
        Scope a = scope().of(new Caller(managerAUser, Set.of(Role.MANAGER)));
        assertThat(a.orgWide()).isFalse();
        assertThat(a.allowsTeacher(teacher1)).isTrue();
        assertThat(a.allowsTeacher(teacher2)).isTrue();
        assertThat(a.allowsTeacher(teacher3)).isFalse();
        assertThat(a.allowsManager(managerA)).isTrue();
        assertThat(a.allowsManager(managerB)).isFalse();

        Scope b = scope().of(new Caller(managerBUser, Set.of(Role.MANAGER)));
        assertThat(b.allowsTeacher(teacher3)).isTrue();
        assertThat(b.allowsTeacher(teacher1)).isFalse();
        assertThat(b.allowsManager(managerA)).isFalse();
    }

    @Test
    void aTeacherReachesOnlyThemselvesAndNoManager() {
        Scope t1 = scope().of(new Caller(teacher1User, Set.of(Role.TEACHER)));
        assertThat(t1.allowsTeacher(teacher1)).isTrue();
        assertThat(t1.allowsTeacher(teacher2)).isFalse();
        assertThat(t1.allowsManager(managerA)).isFalse();

        Scope t2 = scope().of(new Caller(teacher2User, Set.of(Role.TEACHER)));
        assertThat(t2.allowsTeacher(teacher2)).isTrue();
        assertThat(t2.allowsTeacher(teacher1)).isFalse();
    }

    @Test
    void outOfScopeAnswersNotFound() {
        Scope a = scope().of(new Caller(managerAUser, Set.of(Role.MANAGER)));
        a.require("TEACHER", teacher1);
        assertThatThrownBy(() -> a.require("TEACHER", teacher3)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> a.require("MANAGER", managerB)).isInstanceOf(NotFoundException.class);
    }
}
