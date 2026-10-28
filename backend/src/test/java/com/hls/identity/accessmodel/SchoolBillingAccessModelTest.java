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

/** Spec 022: the School Billing menu item, actions, scope, seeded grants and eligibility per role. */
class SchoolBillingAccessModelTest extends IntegrationTestBase {

    private static final PermissionModule MODULE = PermissionModule.SCHOOL_BILLING;

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
                .filter(s -> s.section().equals("PAYROLL & FINANCE"))
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals("School Billing"))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminDirectorAndManagerSeeTheItemWithTheirActionsAndScope() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(item(accessModelFor(role))).as(role.name()).isNotNull();
            assertThat(item(accessModelFor(role)).route()).isEqualTo("/operations/school-billing");
        }
        assertThat(item(accessModelFor(Role.ADMIN)).actions()).containsExactly("VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT");
        assertThat(item(accessModelFor(Role.MANAGER)).actions()).containsExactly("VIEW", "CREATE", "EXPORT");
        assertThat(accessModelFor(Role.DIRECTOR).dataScope()).containsEntry("SCHOOL_BILLING", "ORG_WIDE");
        assertThat(accessModelFor(Role.MANAGER).dataScope()).containsEntry("SCHOOL_BILLING", "ASSIGNED");
    }

    @Test
    void teacherAndSystemHaveNoItemAndNoScope() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            var response = accessModelFor(role);
            assertThat(item(response)).as(role.name()).isNull();
            assertThat(response.dataScope()).doesNotContainKey("SCHOOL_BILLING");
        }
    }

    @Test
    void eligibilityAndSeededGrantsFollowTheRoleTable() {
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, MODULE)).containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.CREATE,
                PermissionAction.EDIT, PermissionAction.APPROVE, PermissionAction.EXPORT);
        assertThat(PermissionEligibility.actionsFor(Role.MANAGER, MODULE))
                .containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EXPORT);
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, MODULE)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, MODULE)).isEmpty();
        assertThat(matrix.isGranted(Role.MANAGER, MODULE, PermissionAction.CREATE)).isTrue();
        assertThat(matrix.isGranted(Role.MANAGER, MODULE, PermissionAction.EDIT)).isFalse();
        assertThat(matrix.isGranted(Role.DIRECTOR, MODULE, PermissionAction.APPROVE)).isTrue();
    }
}
