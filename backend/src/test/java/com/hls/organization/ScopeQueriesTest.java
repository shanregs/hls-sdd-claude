package com.hls.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.support.MasterDataTestBase;
import com.hls.teacher.api.TeacherScopeQueries;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The shared Java scope API later modules use (spec 005 FR-021). */
class ScopeQueriesTest extends MasterDataTestBase {

    @Autowired
    private ScopeQueries scopeQueries;

    @Autowired
    private TeacherScopeQueries teacherScopeQueries;

    @Test
    void adminAndDirectorAreOrgWideAndUnassignedUsersHaveNothing() {
        UUID anyone = UUID.randomUUID();

        assertThat(scopeQueries.scopeOf(anyone, Set.of(Role.ADMIN)).orgWide()).isTrue();
        assertThat(scopeQueries.scopeOf(anyone, Set.of(Role.DIRECTOR)).orgWide()).isTrue();
        assertThat(scopeQueries.scopeOf(anyone, Set.of(Role.TEACHER))).isEqualTo(ScopeView.none());
        assertThat(scopeQueries.scopeOf(anyone, Set.of(Role.SYSTEM))).isEqualTo(ScopeView.none());
        assertThat(scopeQueries.scopeOf(anyone, Set.of(Role.MANAGER))).isEqualTo(ScopeView.none());
    }

    @Test
    void aManagersScopeIsTheirCurrentZonesAndSchoolsAndEndedRowsDropOut() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());

        ScopeView scope = scopeQueries.scopeOf(manager.signed().userId(), Set.of(Role.MANAGER));
        assertThat(scope.orgWide()).isFalse();
        assertThat(scope.zoneIds()).containsExactly(ids[0]);
        assertThat(scope.schoolIds()).containsExactly(ids[2]);
        assertThat(scope.allowsSchool(ids[2])).isTrue();

        assignSchoolManagerRaw(admin, ids[2], null);
        assertThat(scopeQueries.scopeOf(manager.signed().userId(), Set.of(Role.MANAGER)).schoolIds())
                .isEmpty();
    }

    @Test
    void aMultiRoleUserGetsTheUnionAndOrgWideWins() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);

        ScopeView union = scopeQueries.scopeOf(manager.signed().userId(), Set.of(Role.MANAGER, Role.DIRECTOR));

        assertThat(union.orgWide()).isTrue();
    }

    @Test
    void teacherScopeCoversPlacedTeachersForManagersAllForAdminAndOwnRecordForTeachers() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        UUID placed = teacher(admin);
        UUID other = teacher(admin);
        placeTeacher(admin, placed, ids[2]);
        Signed teacherUser = signInAs(Role.TEACHER);
        put("/api/v1/teachers/" + other + "/user", admin, java.util.Map.of("userId", teacherUser.userId()));

        var managerScope = teacherScopeQueries.teacherScope(manager.signed().userId(), Set.of(Role.MANAGER));
        assertThat(managerScope.orgWide()).isFalse();
        assertThat(managerScope.teacherIds()).containsExactly(placed);
        assertThat(teacherScopeQueries.isTeacherInScope(manager.signed().userId(), Set.of(Role.MANAGER), other))
                .isFalse();

        assertThat(teacherScopeQueries.teacherScope(UUID.randomUUID(), Set.of(Role.ADMIN)).orgWide()).isTrue();
        var own = teacherScopeQueries.teacherScope(teacherUser.userId(), Set.of(Role.TEACHER));
        assertThat(own.teacherIds()).containsExactly(other);

        // a future-dated placement does not put the Teacher in the Manager's scope yet
        UUID future = teacher(admin);
        placeTeacherRaw(admin, future, ids[2], LocalDate.now().plusDays(3));
        assertThat(teacherScopeQueries
                        .teacherScope(manager.signed().userId(), Set.of(Role.MANAGER))
                        .teacherIds())
                .doesNotContain(future);
    }
}
