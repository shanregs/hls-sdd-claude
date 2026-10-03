package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 005 (Constitution Principle IX): MASTER DATA navigation, actions, scope and the seed per role. */
class MasterDataAccessModelTest extends IntegrationTestBase {

    @Autowired
    private PermissionMatrixService permissionMatrixService;

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

    private static List<String> masterDataLabels(AccessModelDtos.AccessModelResponse response) {
        return response.navigation().stream()
                .filter(s -> s.section().equals("MASTER DATA"))
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
    void adminAndDirectorSeeAllFourItemsWithOrgWideScope() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR}) {
            var response = accessModelFor(role);
            assertThat(masterDataLabels(response)).containsExactly("Zones", "Schools", "Managers", "Teachers");
            assertThat(response.dataScope())
                    .containsEntry("ZONES", "ORG_WIDE")
                    .containsEntry("SCHOOLS", "ORG_WIDE")
                    .containsEntry("MANAGERS", "ORG_WIDE")
                    .containsEntry("TEACHERS", "ORG_WIDE")
                    .containsEntry("TEACHER_SALARY", "ORG_WIDE");
        }
        assertThat(actionsOf(accessModelFor(Role.ADMIN), "Zones")).contains("VIEW", "CREATE", "EDIT", "DELETE");
        assertThat(actionsOf(accessModelFor(Role.DIRECTOR), "Zones"))
                .contains("VIEW", "CREATE", "EDIT")
                .doesNotContain("DELETE");
    }

    @Test
    void managerSeesSchoolsAndTeachersOnlyWithAssignedScope() {
        var response = accessModelFor(Role.MANAGER);

        assertThat(masterDataLabels(response)).containsExactly("Schools", "Teachers");
        assertThat(actionsOf(response, "Schools")).containsExactly("VIEW", "EDIT");
        assertThat(actionsOf(response, "Teachers")).containsExactly("VIEW", "EDIT");
        assertThat(response.dataScope())
                .containsEntry("SCHOOLS", "ASSIGNED")
                .containsEntry("TEACHERS", "ASSIGNED")
                .doesNotContainKeys("ZONES", "MANAGERS", "TEACHER_SALARY");
    }

    @Test
    void teacherAndSystemSeeNoMasterDataAndNoScope() {
        for (Role role : new Role[] {Role.TEACHER, Role.SYSTEM}) {
            var response = accessModelFor(role);
            assertThat(masterDataLabels(response)).isEmpty();
            assertThat(response.dataScope()).doesNotContainKeys("ZONES", "SCHOOLS", "MANAGERS", "TEACHERS", "TEACHER_SALARY");
        }
    }

    @Test
    void aManagerWhoIsAlsoDirectorGetsTheUnion() {
        var response = accessModelFor(Role.MANAGER, Role.DIRECTOR);

        assertThat(masterDataLabels(response)).containsExactly("Zones", "Schools", "Managers", "Teachers");
        assertThat(response.dataScope()).containsEntry("SCHOOLS", "ORG_WIDE");
    }

    @Test
    void salaryIsGrantedToAdminAndDirectorOnly() {
        for (Role role : Role.values()) {
            boolean expected = role == Role.ADMIN || role == Role.DIRECTOR;
            assertThat(permissionMatrixService.isGranted(role, PermissionModule.TEACHER_SALARY, PermissionAction.VIEW))
                    .as("%s VIEW", role)
                    .isEqualTo(expected);
            assertThat(permissionMatrixService.isGranted(
                            role, PermissionModule.TEACHER_SALARY, PermissionAction.CREATE))
                    .as("%s CREATE", role)
                    .isEqualTo(expected);
        }
    }
}
