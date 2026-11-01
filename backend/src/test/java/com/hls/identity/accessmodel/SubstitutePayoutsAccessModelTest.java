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

/** Spec 017b T016: the Substitute Payouts menu item, actions, scope, seeded grants and eligibility per role. */
class SubstitutePayoutsAccessModelTest extends IntegrationTestBase {

    private static final PermissionModule MODULE = PermissionModule.SUBSTITUTE_PAYOUTS;

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

    private static AccessModelDtos.NavItemView item(AccessModelDtos.AccessModelResponse response, String label) {
        return response.navigation().stream()
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals(label))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminDirectorAndManagerSeeTheItemUnderPayrollAndFinance() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            var response = accessModelFor(role);
            var item = item(response, "Substitute Payouts");
            assertThat(item).as(role.name()).isNotNull();
            assertThat(item.route()).isEqualTo("/operations/substitute-payouts");
            assertThat(response.navigation().stream()
                            .filter(s -> s.section().equals("PAYROLL & FINANCE"))
                            .flatMap(s -> s.items().stream())
                            .map(AccessModelDtos.NavItemView::label))
                    .contains("Substitute Payouts");
        }
        assertThat(item(accessModelFor(Role.ADMIN), "Substitute Payouts").actions())
                .containsExactly("VIEW", "EDIT", "APPROVE", "EXPORT");
        assertThat(item(accessModelFor(Role.MANAGER), "Substitute Payouts").actions()).containsExactly("VIEW", "EDIT");
        assertThat(accessModelFor(Role.DIRECTOR).dataScope()).containsEntry("SUBSTITUTE_PAYOUTS", "ORG_WIDE");
        assertThat(accessModelFor(Role.MANAGER).dataScope()).containsEntry("SUBSTITUTE_PAYOUTS", "ASSIGNED");
    }

    @Test
    void teacherAndSystemHaveNoItemAndNoScopeAndTheTeacherLostMySubstitutions() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            var response = accessModelFor(role);
            assertThat(item(response, "Substitute Payouts")).as(role.name()).isNull();
            assertThat(response.dataScope()).doesNotContainKey("SUBSTITUTE_PAYOUTS");
        }
        assertThat(item(accessModelFor(Role.TEACHER), "My Substitutions")).isNull();
    }

    @Test
    void eligibilityAndSeededGrantsFollowTheRoleTable() {
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, MODULE)).containsExactlyInAnyOrderElementsOf(MODULE.actions());
        assertThat(PermissionEligibility.actionsFor(Role.DIRECTOR, MODULE)).containsExactlyInAnyOrderElementsOf(MODULE.actions());
        assertThat(PermissionEligibility.actionsFor(Role.MANAGER, MODULE))
                .containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.EDIT);
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, MODULE)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, MODULE)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.CONTENT_CONTRIBUTOR, MODULE)).isEmpty();
        for (PermissionAction action : MODULE.actions()) {
            assertThat(matrix.isGranted(Role.ADMIN, MODULE, action)).as("Admin " + action).isTrue();
            assertThat(matrix.isGranted(Role.DIRECTOR, MODULE, action)).as("Director " + action).isTrue();
        }
        assertThat(matrix.isGranted(Role.MANAGER, MODULE, PermissionAction.EDIT)).isTrue();
        assertThat(matrix.isGranted(Role.MANAGER, MODULE, PermissionAction.APPROVE)).isFalse();
        assertThat(matrix.isGranted(Role.MANAGER, MODULE, PermissionAction.EXPORT)).isFalse();
    }
}
