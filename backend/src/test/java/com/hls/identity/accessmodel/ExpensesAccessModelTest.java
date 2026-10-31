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

/** Spec 015 T007: the Expenses and My Expenses menu items, scopes, seeded grants and eligibility per role. */
class ExpensesAccessModelTest extends IntegrationTestBase {

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
                .filter(s -> s.section().equals("PAYROLL & FINANCE"))
                .flatMap(s -> s.items().stream())
                .filter(i -> i.label().equals(label))
                .findFirst()
                .orElse(null);
    }

    @Test
    void adminAndDirectorSeeBothItemsManagerAndTeacherOnlyMyExpenses() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            var response = accessModelFor(role);
            assertThat(item(response, "Expenses")).as(role.name()).isNotNull();
            assertThat(item(response, "Expenses").route()).isEqualTo("/operations/expenses");
            assertThat(item(response, "My Expenses")).as(role.name()).isNotNull();
            assertThat(response.dataScope()).containsEntry("EXPENSES", "ORG_WIDE").containsEntry("MY_EXPENSES", "OWN");
        }
        assertThat(item(accessModelFor(Role.ADMIN), "Expenses").actions())
                .containsExactly("VIEW", "CREATE", "EDIT", "APPROVE", "PROCESS", "EXPORT");
        assertThat(item(accessModelFor(Role.DIRECTOR), "Expenses").actions())
                .containsExactly("VIEW", "CREATE", "EDIT", "APPROVE", "EXPORT");
        for (Role role : List.of(Role.MANAGER, Role.TEACHER)) {
            var response = accessModelFor(role);
            assertThat(item(response, "Expenses")).as(role.name()).isNull();
            assertThat(item(response, "My Expenses")).as(role.name()).isNotNull();
            assertThat(item(response, "My Expenses").route()).isEqualTo("/operations/my-expenses");
            assertThat(item(response, "My Expenses").actions()).containsExactly("VIEW", "CREATE", "EDIT", "DELETE");
            assertThat(response.dataScope()).doesNotContainKey("EXPENSES").containsEntry("MY_EXPENSES", "OWN");
        }
    }

    @Test
    void systemHasNeitherItemNorScope() {
        var response = accessModelFor(Role.SYSTEM);
        assertThat(item(response, "Expenses")).isNull();
        assertThat(item(response, "My Expenses")).isNull();
        assertThat(response.dataScope()).doesNotContainKeys("EXPENSES", "MY_EXPENSES");
    }

    @Test
    void seededGrantsFollowTheDefaults() {
        for (PermissionAction action : PermissionModule.EXPENSES.actions()) {
            assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.EXPENSES, action)).as("ADMIN " + action).isTrue();
            assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.EXPENSES, action))
                    .as("DIRECTOR " + action)
                    .isEqualTo(action != PermissionAction.PROCESS);
            for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
                assertThat(matrix.isGranted(role, PermissionModule.EXPENSES, action)).as(role + " " + action).isFalse();
            }
        }
        for (PermissionAction action : PermissionModule.MY_EXPENSES.actions()) {
            for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
                assertThat(matrix.isGranted(role, PermissionModule.MY_EXPENSES, action)).as(role + " " + action).isTrue();
            }
            assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.MY_EXPENSES, action)).isFalse();
        }
    }

    @Test
    void eligibilityRefusesManagerTeacherAndSystemOnExpensesAndSystemOnMyExpenses() {
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, PermissionModule.EXPENSES))
                .isEqualTo(PermissionModule.EXPENSES.actions());
        assertThat(PermissionEligibility.actionsFor(Role.DIRECTOR, PermissionModule.EXPENSES))
                .isEqualTo(PermissionModule.EXPENSES.actions());
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.EXPENSES)).as(role.name()).isEmpty();
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.MY_EXPENSES))
                    .as(role.name())
                    .isEqualTo(PermissionModule.MY_EXPENSES.actions());
        }
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, PermissionModule.MY_EXPENSES)).isEmpty();
    }
}
