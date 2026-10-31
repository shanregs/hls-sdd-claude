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

/** Spec 026: the Teacher Performance menu item, actions, scope, seeded grants and eligibility per role. */
class PerformanceAccessModelTest extends IntegrationTestBase {

    private static final PermissionModule MODULE = PermissionModule.TEACHER_PERFORMANCE;

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

    private static AccessModelDtos.NavItemView item(AccessModelDtos.AccessModelResponse response) {
        return response.navigation().stream()
                .filter(s -> s.section().equals("PLANNING & TRAINING"))
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals("Teacher Performance"))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminDirectorAndManagerSeeTheItemWithTheirScope() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            var response = accessModelFor(role);
            assertThat(item(response)).as(role.name()).isNotNull();
            assertThat(item(response).route()).isEqualTo("/operations/teacher-performance");
            assertThat(item(response).actions()).containsExactly("VIEW", "CREATE", "EDIT");
        }
        assertThat(accessModelFor(Role.ADMIN).dataScope()).containsEntry("TEACHER_PERFORMANCE", "ORG_WIDE");
        assertThat(accessModelFor(Role.DIRECTOR).dataScope()).containsEntry("TEACHER_PERFORMANCE", "ORG_WIDE");
        assertThat(accessModelFor(Role.MANAGER).dataScope()).containsEntry("TEACHER_PERFORMANCE", "ASSIGNED");
    }

    @Test
    void teacherAndSystemHaveNoItemAndNoScope() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            var response = accessModelFor(role);
            assertThat(item(response)).as(role.name()).isNull();
            assertThat(response.dataScope()).as(role.name()).doesNotContainKey("TEACHER_PERFORMANCE");
        }
    }

    @Test
    void seededGrantsFollowTheDefaults() {
        for (PermissionAction action : MODULE.actions()) {
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
                assertThat(matrix.isGranted(role, MODULE, action)).as(role + " " + action).isTrue();
            }
            for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
                assertThat(matrix.isGranted(role, MODULE, action)).as(role + " " + action).isFalse();
            }
        }
        assertThat(MODULE.actions()).containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
    }

    @Test
    void teacherAndSystemCanNeverBeGrantedAnything() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(PermissionEligibility.actionsFor(role, MODULE)).as(role.name()).isEqualTo(MODULE.actions());
        }
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, MODULE)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, MODULE)).isEmpty();
    }
}
