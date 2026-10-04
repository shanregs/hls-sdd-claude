package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 008: attendance navigation, actions, scope and seeded grants per role; seeding is idempotent. */
class AttendanceAccessModelTest extends IntegrationTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    private AccessModelDtos.AccessModelResponse accessModelFor(Role... roles) {
        String token = signInAs(roles).token();
        return client.get()
                .uri("/api/v1/me/access-model")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AccessModelDtos.AccessModelResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static List<String> labels(AccessModelDtos.AccessModelResponse response, String section) {
        return response.navigation().stream()
                .filter(s -> s.section().equals(section))
                .flatMap(s -> s.items().stream())
                .map(AccessModelDtos.NavItemView::label)
                .toList();
    }

    private static List<String> actionsOf(AccessModelDtos.AccessModelResponse response, String label) {
        return response.navigation().stream()
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals(label))
                .findFirst()
                .orElseThrow()
                .actions();
    }

    @Test
    void adminAndDirectorSeeAttendanceAndSetupWithOrgWideScope() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            var response = accessModelFor(role);
            assertThat(labels(response, "OPERATIONS")).containsExactly("Attendance");
            assertThat(labels(response, "MASTER DATA")).contains("Attendance Setup");
            assertThat(response.dataScope())
                    .containsEntry("ATTENDANCE", "ORG_WIDE")
                    .containsEntry("ATTENDANCE_SETUP", "ORG_WIDE");
            assertThat(labels(response, "MY ATTENDANCE")).isEmpty();
        }
        assertThat(actionsOf(accessModelFor(Role.ADMIN), "Attendance"))
                .containsExactly("VIEW", "CREATE", "EDIT", "DELETE", "PROCESS", "EXPORT");
        assertThat(actionsOf(accessModelFor(Role.DIRECTOR), "Attendance"))
                .containsExactly("VIEW", "CREATE", "EDIT", "PROCESS", "EXPORT");
        assertThat(actionsOf(accessModelFor(Role.DIRECTOR), "Attendance Setup")).containsExactly("VIEW", "EDIT");
    }

    @Test
    void managerSeesTeacherAttendanceWithAssignedScopeOnly() {
        var response = accessModelFor(Role.MANAGER);

        assertThat(labels(response, "OPERATIONS")).containsExactly("Teacher Attendance");
        assertThat(actionsOf(response, "Teacher Attendance")).containsExactly("VIEW", "CREATE", "EDIT");
        assertThat(response.dataScope())
                .containsEntry("TEACHER_ATTENDANCE", "ASSIGNED")
                .doesNotContainKeys("ATTENDANCE", "ATTENDANCE_SETUP", "MY_ATTENDANCE");
    }

    @Test
    void teacherSeesOwnAttendanceOnly() {
        var response = accessModelFor(Role.TEACHER);

        assertThat(labels(response, "MY ATTENDANCE")).containsExactly("My Attendance", "Attendance History");
        assertThat(labels(response, "OPERATIONS")).isEmpty();
        assertThat(actionsOf(response, "My Attendance")).containsExactly("VIEW", "CREATE", "EDIT");
        assertThat(response.dataScope()).containsEntry("MY_ATTENDANCE", "OWN");
    }

    @Test
    void systemSeesNoAttendanceAtAll() {
        var response = accessModelFor(Role.SYSTEM);

        assertThat(labels(response, "OPERATIONS")).isEmpty();
        assertThat(labels(response, "MY ATTENDANCE")).isEmpty();
        assertThat(response.dataScope())
                .doesNotContainKeys("ATTENDANCE", "ATTENDANCE_SETUP", "TEACHER_ATTENDANCE", "MY_ATTENDANCE");
        for (PermissionModule module : List.of(
                PermissionModule.ATTENDANCE,
                PermissionModule.ATTENDANCE_SETUP,
                PermissionModule.TEACHER_ATTENDANCE,
                PermissionModule.MY_ATTENDANCE)) {
            for (PermissionAction action : PermissionAction.values()) {
                assertThat(matrix.isGranted(Role.SYSTEM, module, action))
                        .as("SYSTEM %s %s", module, action)
                        .isFalse();
            }
        }
    }

    @Test
    void seedingTwiceChangesNothingAndKeepsAdminEdits() {
        // Revoke a default grant, then re-run the seed: the edit must survive.
        matrix.updateGrant(Role.DIRECTOR, PermissionModule.ATTENDANCE, PermissionAction.EXPORT, false, UUID.randomUUID());
        long before = matrix.findAll().size();

        matrix.seedDefaults();
        matrix.seedDefaults();

        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.ATTENDANCE, PermissionAction.EXPORT)).isFalse();
        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.ATTENDANCE, PermissionAction.PROCESS)).isTrue();
        assertThat(matrix.findAll()).hasSize((int) before);

        // restore for other tests sharing the container
        matrix.updateGrant(Role.DIRECTOR, PermissionModule.ATTENDANCE, PermissionAction.EXPORT, true, UUID.randomUUID());
    }
}
