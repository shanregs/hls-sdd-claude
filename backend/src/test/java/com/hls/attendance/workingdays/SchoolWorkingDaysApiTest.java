package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 037 FR-018, FR-024 to FR-026, SC-007: every endpoint refuses what the caller's role may not do. */
class SchoolWorkingDaysApiTest extends WorkingDaysTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @Test
    void anAdminMayCreateEditAndDelete() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp created = createRow(admin, school, thisYear(), 3, 26);
        assertThat(created.status()).isEqualTo(201);
        assertThat(editRow(admin, created.id(), 25, versionOf(created)).status()).isEqualTo(200);
        Resp current = get(BASE + "?year=" + thisYear() + "&schoolId=" + school, admin);
        long version = ((Number) items(current).get(0).get("version")).longValue();
        assertThat(deleteRow(admin, created.id(), version).status()).isEqualTo(204);
    }

    @Test
    void aDirectorMayCreateAndEditButNotDeleteByDefault() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];

        Resp created = createRow(director, school, thisYear(), 3, 26);
        assertThat(created.status()).isEqualTo(201);
        Resp edited = editRow(director, created.id(), 25, versionOf(created));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(get(BASE + "?year=" + thisYear(), director).status()).isEqualTo(200);
        assertThat(deleteRow(director, created.id(), versionOf(edited)).status()).isEqualTo(403);
        assertThat(items(listRows(director, thisYear(), school))).hasSize(1);
    }

    @Test
    void managerTeacherAndSystemAreRefusedEveryEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp created = createRow(admin, school, thisYear(), 3, 26);

        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            String who = role.name();
            assertThat(get(BASE, token).status()).as(who + " list").isEqualTo(403);
            assertThat(get(BASE + "/schools", token).status()).as(who + " schools").isEqualTo(403);
            assertThat(createRow(token, school, thisYear(), 4, 20).status()).as(who + " create").isEqualTo(403);
            assertThat(editRow(token, created.id(), 20, versionOf(created)).status()).as(who + " edit").isEqualTo(403);
            assertThat(deleteRow(token, created.id(), versionOf(created)).status()).as(who + " delete").isEqualTo(403);
        }
        assertThat(items(listRows(admin, thisYear(), school))).hasSize(1);
    }

    @Test
    void withoutATokenEveryEndpointIsUnauthorized() {
        assertThat(get(BASE, null).status()).isEqualTo(401);
        assertThat(createRow(null, UUID.randomUUID(), thisYear(), 3, 20).status()).isEqualTo(401);
    }

    @Test
    void aManagerGrantedViewSeesOnlyTheirSchoolsAndStillCannotWrite() {
        World w = newWorld();
        UUID rowA = createRowOk(w.admin(), w.schoolA(), thisYear(), 3, 26);
        UUID rowB = createRowOk(w.admin(), w.schoolB(), thisYear(), 3, 24);
        UUID actor = UUID.randomUUID();
        assertThat(matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor)
                        .success())
                .isTrue();
        try {
            String manager = w.managerA().token();
            List<Map<String, Object>> rows = items(get(BASE + "?year=" + thisYear() + "&size=100", manager));
            assertThat(rows).extracting(r -> r.get("id")).contains(rowA.toString()).doesNotContain(rowB.toString());
            assertThat(get(BASE + "/schools", manager).body()).contains(w.schoolA().toString()).doesNotContain(w.schoolB().toString());
            assertThat(items(get(BASE + "?year=" + thisYear() + "&schoolId=" + w.schoolB(), manager))).isEmpty();

            assertThat(createRow(manager, w.schoolA(), thisYear(), 4, 20).status()).isEqualTo(403);
            assertThat(editRow(manager, rowA, 20, 0).status()).isEqualTo(403);
            assertThat(deleteRow(manager, rowA, 0).status()).isEqualTo(403);
            // a Manager can never be granted a write action
            assertThat(matrix.updateGrant(
                                    Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.CREATE, true, actor)
                            .success())
                    .isFalse();
        } finally {
            matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, false, actor);
        }
        assertThat(get(BASE, w.managerA().token()).status()).isEqualTo(403);
    }

    @Test
    void systemCannotBeGrantedTheModule() {
        UUID actor = UUID.randomUUID();
        assertThat(matrix.updateGrant(Role.SYSTEM, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor)
                        .success())
                .isFalse();
        assertThat(matrix.updateGrant(Role.TEACHER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor)
                        .success())
                .isFalse();
    }
}
