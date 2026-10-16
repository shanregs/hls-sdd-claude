package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionEligibility;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 013b T008: the Payroll permission module, its seeded defaults, its menu item and scope per role. */
class PayrollAccessModelTest extends IntegrationTestBase {

    private static final String MATRIX = "/api/v1/identity/permission-matrix";

    @Autowired
    private PermissionMatrixService matrix;

    private String accessModel(Role role) {
        return get("/api/v1/me/access-model", signInAs(role).token()).body();
    }

    @Test
    void thePayrollModuleHasTheFourActionsAndAppliesToAdminAndDirectorOnly() {
        assertThat(PermissionModule.PAYROLL.actions())
                .containsExactlyInAnyOrder(
                        PermissionAction.VIEW, PermissionAction.PROCESS, PermissionAction.APPROVE, PermissionAction.EXPORT);
        for (Role role : Role.values()) {
            boolean staff = role == Role.ADMIN || role == Role.DIRECTOR;
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.PAYROLL))
                    .as("PAYROLL " + role)
                    .isEqualTo(staff
                            ? EnumSet.of(
                                    PermissionAction.VIEW,
                                    PermissionAction.PROCESS,
                                    PermissionAction.APPROVE,
                                    PermissionAction.EXPORT)
                            : EnumSet.noneOf(PermissionAction.class));
        }
    }

    @Test
    void theSeededDefaultsGiveTheAdminProcessAndTheDirectorApprove() {
        assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.VIEW)).isTrue();
        assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.PROCESS)).isTrue();
        assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.EXPORT)).isTrue();
        assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.APPROVE)).isFalse();

        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.VIEW)).isTrue();
        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.APPROVE)).isTrue();
        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.EXPORT)).isTrue();
        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.PAYROLL, PermissionAction.PROCESS)).isFalse();

        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            for (PermissionAction action : PermissionModule.PAYROLL.actions()) {
                assertThat(matrix.isGranted(role, PermissionModule.PAYROLL, action))
                        .as(role + " " + action)
                        .isFalse();
            }
        }
    }

    @Test
    void theMatrixRefusesToGrantPayrollToManagerTeacherOrSystem() {
        String admin = signInAs(Role.ADMIN).token();
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            for (PermissionAction action : PermissionModule.PAYROLL.actions()) {
                Resp refused = put(MATRIX + "/" + role + "/PAYROLL/" + action, admin, Map.of("granted", true));
                assertThat(refused.status()).as(role + " " + action).isEqualTo(409);
                assertThat(refused.body()).contains("does not apply");
            }
        }
        // there is no Create, Edit or Delete on Payroll
        assertThat(put(MATRIX + "/ADMIN/PAYROLL/EDIT", admin, Map.of("granted", true)).status()).isEqualTo(409);
    }

    @Test
    void adminAndDirectorSeeThePayrollItemWithAnOrgWideScope() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String body = accessModel(role);
            assertThat(body).as(role.name()).contains("\"/operations/payroll\"");
            assertThat(body).contains("\"PAYROLL\":\"ORG_WIDE\"");
        }
    }

    @Test
    void aManagerATeacherAndSystemSeeNoPayrollItem() {
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String body = accessModel(role);
            assertThat(body).as(role.name()).doesNotContain("/operations/payroll");
            assertThat(body).doesNotContain("\"PAYROLL\"");
        }
    }
}
