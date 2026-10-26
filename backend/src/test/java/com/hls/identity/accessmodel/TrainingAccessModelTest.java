package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionEligibility;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024: the training menu items, actions, scope, seeded grants and eligibility per role. */
class TrainingAccessModelTest extends IntegrationTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    private AccessModelDtos.AccessModelResponse accessModelFor(Role role) {
        return client.get()
                .uri("/api/v1/me/access-model")
                .header("Authorization", "Bearer " + signInAs(role).token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AccessModelDtos.AccessModelResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static AccessModelDtos.NavItemView item(AccessModelDtos.AccessModelResponse response, String section, String label) {
        return response.navigation().stream()
                .filter(s -> s.section().equals(section))
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals(label))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminAndDirectorSeeTheCalendarAndExceptionsInOperations() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            var response = accessModelFor(role);
            var calendar = item(response, "OPERATIONS", "Training Calendar");
            assertThat(calendar).as(role.name()).isNotNull();
            assertThat(calendar.route()).isEqualTo("/operations/training");
            assertThat(calendar.actions()).containsExactly("VIEW", "CREATE", "EDIT", "DELETE", "EXPORT");
            assertThat(item(response, "OPERATIONS", "Training Exceptions").route()).isEqualTo("/operations/training/exceptions");
            assertThat(item(response, "OPERATIONS", "My Training")).isNull();
            assertThat(response.dataScope())
                    .containsEntry("TRAINING", "ORG_WIDE")
                    .containsEntry("TRAINING_ATTENDANCE", "ORG_WIDE")
                    .doesNotContainKey("TRAINING_REGISTRATION");
        }
    }

    @Test
    void aManagerSeesMyTrainingWithViewOnlyAndAssignedScope() {
        var response = accessModelFor(Role.MANAGER);

        var mine = item(response, "OPERATIONS", "My Training");
        assertThat(mine).isNotNull();
        assertThat(mine.route()).isEqualTo("/operations/my-training");
        assertThat(mine.actions()).containsExactly("VIEW");
        assertThat(item(response, "OPERATIONS", "Training Calendar")).isNull();
        assertThat(response.dataScope()).containsEntry("TRAINING", "ASSIGNED").containsEntry("TRAINING_ATTENDANCE", "ASSIGNED");
    }

    @Test
    void aTeacherSeesUpcomingTrainingAndHistoryInMyAttendance() {
        var response = accessModelFor(Role.TEACHER);

        assertThat(item(response, "MY ATTENDANCE", "Upcoming Training").route()).isEqualTo("/my-attendance/training");
        assertThat(item(response, "MY ATTENDANCE", "My Training History").route()).isEqualTo("/my-attendance/training/history");
        assertThat(item(response, "MY ATTENDANCE", "Upcoming Training").actions()).containsExactly("VIEW", "CREATE", "EDIT");
        assertThat(item(response, "OPERATIONS", "Training Calendar")).isNull();
        assertThat(response.dataScope()).containsEntry("TRAINING_REGISTRATION", "OWN").doesNotContainKeys("TRAINING", "TRAINING_ATTENDANCE");
    }

    @Test
    void systemSeesNoTraining() {
        var response = accessModelFor(Role.SYSTEM);

        assertThat(response.navigation().stream().flatMap(s -> s.items().stream()).map(AccessModelDtos.NavItemView::label))
                .noneMatch(label -> label.contains("Training"));
        assertThat(response.dataScope()).doesNotContainKeys("TRAINING", "TRAINING_ATTENDANCE", "TRAINING_REGISTRATION");
    }

    @Test
    void seededGrantsFollowTheDefaults() {
        for (PermissionAction action : PermissionModule.TRAINING.actions()) {
            assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.TRAINING, action)).as("ADMIN " + action).isTrue();
            assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.TRAINING, action)).as("DIRECTOR " + action).isTrue();
            assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.TRAINING, action)).as("MANAGER " + action)
                    .isEqualTo(action == PermissionAction.VIEW);
            assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.TRAINING, action)).isFalse();
            assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.TRAINING, action)).isFalse();
        }
        for (PermissionAction action : PermissionModule.TRAINING_ATTENDANCE.actions()) {
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
                assertThat(matrix.isGranted(role, PermissionModule.TRAINING_ATTENDANCE, action)).as(role + " " + action).isTrue();
            }
            assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.TRAINING_ATTENDANCE, action)).isFalse();
        }
        for (PermissionAction action : PermissionModule.TRAINING_REGISTRATION.actions()) {
            assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.TRAINING_REGISTRATION, action)).as("TEACHER " + action).isTrue();
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.SYSTEM)) {
                assertThat(matrix.isGranted(role, PermissionModule.TRAINING_REGISTRATION, action)).as(role + " " + action).isFalse();
            }
        }
    }

    @Test
    void eligibilityKeepsTheModulesWithTheirRoles() {
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, PermissionModule.TRAINING)).isEqualTo(PermissionModule.TRAINING.actions());
        assertThat(PermissionEligibility.actionsFor(Role.DIRECTOR, PermissionModule.TRAINING)).isEqualTo(PermissionModule.TRAINING.actions());
        // a Manager can never be granted a write on the calendar
        assertThat(PermissionEligibility.actionsFor(Role.MANAGER, PermissionModule.TRAINING)).containsExactly(PermissionAction.VIEW);
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, PermissionModule.TRAINING)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, PermissionModule.TRAINING)).isEmpty();

        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.TRAINING_ATTENDANCE))
                    .as(role.name())
                    .isEqualTo(PermissionModule.TRAINING_ATTENDANCE.actions());
        }
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, PermissionModule.TRAINING_ATTENDANCE)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, PermissionModule.TRAINING_ATTENDANCE)).isEmpty();

        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, PermissionModule.TRAINING_REGISTRATION))
                .isEqualTo(PermissionModule.TRAINING_REGISTRATION.actions());
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.SYSTEM)) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.TRAINING_REGISTRATION)).as(role.name()).isEmpty();
        }
    }
}
