package com.hls.identity.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 002 FR-005/FR-006: which grants can exist for a role on a module, as the Role &amp; Permissions
 * screen shows them (icon, greyed icon, or not applicable) and as the server enforces them.
 */
class PermissionEligibilityTest extends IntegrationTestBase {

    private static final String MATRIX = "/api/v1/identity/permission-matrix";

    @Autowired
    private PermissionMatrixService matrix;

    @Test
    void everyModuleHasAtLeastOneAction() {
        for (PermissionModule module : PermissionModule.values()) {
            assertThat(module.actions()).as(module.name()).isNotEmpty();
        }
    }

    @Test
    void everySeededGrantIsEligible() {
        assertThat(matrix.findAll())
                .allSatisfy(entry -> assertThat(PermissionEligibility.isEligible(
                                entry.getRole(), entry.getModule(), entry.getAction()))
                        .as("%s %s %s", entry.getRole(), entry.getModule(), entry.getAction())
                        .isTrue());
    }

    @Test
    void onlyMatrixManagersCanHoldRoleAndPermissions() {
        for (Role role : Role.values()) {
            boolean manager = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.SYSTEM;
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.IDENTITY_PERMISSIONS).isEmpty())
                    .as(role.name())
                    .isEqualTo(!manager);
        }
    }

    @Test
    void sessionManagementIsSystemsByDefaultAndOnlyMatrixManagersMayHoldIt() {
        assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.VIEW)).isTrue();
        assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.SESSION_MANAGEMENT, PermissionAction.DELETE)).isTrue();
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)) {
            assertThat(matrix.isGranted(role, PermissionModule.SESSION_MANAGEMENT, PermissionAction.VIEW))
                    .as("%s granted by default", role)
                    .isFalse();
        }
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, PermissionModule.SESSION_MANAGEMENT))
                .containsExactly(PermissionAction.VIEW, PermissionAction.DELETE);
        assertThat(PermissionEligibility.actionsFor(Role.MANAGER, PermissionModule.SESSION_MANAGEMENT)).isEmpty();
        assertThat(PermissionEligibility.actionsFor(Role.TEACHER, PermissionModule.SESSION_MANAGEMENT)).isEmpty();
    }

    @Test
    void notificationsApplyToTheFourBusinessRolesAndNeverToSystem() {
        for (Role role : Role.values()) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.NOTIFICATIONS))
                    .as("NOTIFICATIONS " + role)
                    .isEqualTo(role == Role.SYSTEM
                            ? java.util.EnumSet.noneOf(PermissionAction.class)
                            : java.util.EnumSet.of(PermissionAction.VIEW, PermissionAction.DELETE));
        }
    }

    @Test
    void leaveModulesApplyOnlyToTheRolesThatUseThem() {
        for (Role role : Role.values()) {
            boolean supervisor = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.MANAGER;
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.LEAVE_MANAGEMENT))
                    .as("LEAVE_MANAGEMENT " + role)
                    .isEqualTo(supervisor
                            ? java.util.EnumSet.of(PermissionAction.VIEW, PermissionAction.APPROVE)
                            : java.util.EnumSet.noneOf(PermissionAction.class));
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.MY_LEAVE))
                    .as("MY_LEAVE " + role)
                    .isEqualTo(role == Role.TEACHER
                            ? java.util.EnumSet.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.DELETE)
                            : java.util.EnumSet.noneOf(PermissionAction.class));
        }
    }

    @Test
    void schoolContractsApplyToStaffRolesOnlyWithTheSeededDefaults() {
        for (Role role : Role.values()) {
            boolean staff = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.MANAGER;
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.SCHOOL_CONTRACTS))
                    .as("SCHOOL_CONTRACTS " + role)
                    .isEqualTo(staff
                            ? java.util.EnumSet.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)
                            : java.util.EnumSet.noneOf(PermissionAction.class));
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction action : List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                assertThat(matrix.isGranted(role, PermissionModule.SCHOOL_CONTRACTS, action))
                        .as(role + " " + action)
                        .isTrue();
            }
        }
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.VIEW)).isTrue();
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.CREATE)).isFalse();
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.EDIT)).isFalse();
    }

    @Test
    void systemHasNoBusinessDataModulesButKeepsTheHolidayCalendarAndAudit() {
        for (PermissionModule business : List.of(
                PermissionModule.ZONES,
                PermissionModule.SCHOOLS,
                PermissionModule.MANAGERS,
                PermissionModule.TEACHERS,
                PermissionModule.TEACHER_SALARY,
                PermissionModule.ATTENDANCE,
                PermissionModule.TEACHER_ATTENDANCE,
                PermissionModule.MY_ATTENDANCE,
                PermissionModule.ATTENDANCE_SETUP,
                PermissionModule.LEAVE_MANAGEMENT,
                PermissionModule.MY_LEAVE,
                PermissionModule.NOTIFICATIONS,
                PermissionModule.SCHOOL_CONTRACTS)) {
            assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, business)).as(business.name()).isEmpty();
        }
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, PermissionModule.HOLIDAY_CALENDAR))
                .contains(PermissionAction.VIEW);
        assertThat(PermissionEligibility.actionsFor(Role.SYSTEM, PermissionModule.AUDIT_LOGS))
                .contains(PermissionAction.VIEW, PermissionAction.EXPORT);
    }

    @Test
    void theMatrixResponseListsEveryModuleWithItsEligibleActionsPerRole() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = get(MATRIX, admin);

        assertThat(resp.status()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) resp.map().get("modules");
        assertThat(modules).hasSize(PermissionModule.values().length);
        Map<String, Object> users = modules.stream()
                .filter(m -> "USER_MANAGEMENT".equals(m.get("module")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, List<String>> eligible = (Map<String, List<String>>) users.get("eligible");
        assertThat(eligible.get("ADMIN")).containsExactly("VIEW", "CREATE", "EDIT");
        assertThat(eligible).containsKeys("ADMIN", "DIRECTOR", "MANAGER", "TEACHER", "SYSTEM");
        Map<String, Object> permissions = modules.stream()
                .filter(m -> "IDENTITY_PERMISSIONS".equals(m.get("module")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, List<String>> permissionsEligible = (Map<String, List<String>>) permissions.get("eligible");
        assertThat(permissionsEligible.get("TEACHER")).isEmpty();
        assertThat(permissionsEligible.get("ADMIN")).containsExactly("VIEW", "EDIT");
    }

    @Test
    void aGrantThatDoesNotApplyIsRefusedAndChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();

        Resp wrongAction = put(MATRIX + "/MANAGER/DASHBOARD/DELETE", admin, Map.of("granted", true));
        Resp wrongRole = put(MATRIX + "/TEACHER/IDENTITY_PERMISSIONS/VIEW", admin, Map.of("granted", true));
        Resp systemBusiness = put(MATRIX + "/SYSTEM/ZONES/VIEW", admin, Map.of("granted", true));

        for (Resp refused : List.of(wrongAction, wrongRole, systemBusiness)) {
            assertThat(refused.status()).isEqualTo(409);
            assertThat(refused.body()).contains("does not apply");
        }
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.DASHBOARD, PermissionAction.DELETE)).isFalse();
        assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.IDENTITY_PERMISSIONS, PermissionAction.VIEW))
                .isFalse();
        assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.ZONES, PermissionAction.VIEW)).isFalse();
    }
}
