package com.hls.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.support.AuditVisibility;
import com.hls.identity.accessmodel.AccessModelDtos;
import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionEligibility;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 031 T006: the Approve action of School Contracts, its seeds, its menu item and the audit visibility of MoU entries. */
class SchoolContractsApproveGrantTest extends IntegrationTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @Autowired
    private AuditVisibility visibility;

    @Test
    void approveIsEligibleForDirectorAndManagerOnly() {
        for (Role role : Role.values()) {
            boolean eligible = role == Role.DIRECTOR || role == Role.MANAGER;
            assertThat(PermissionEligibility.isEligible(role, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE))
                    .as(role.name())
                    .isEqualTo(eligible);
        }
    }

    @Test
    void theSeedGivesDirectorAndManagerApproveAndLeavesTheOtherSeedsAlone() {
        assertThat(matrix.isGranted(Role.DIRECTOR, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE)).isTrue();
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE)).isTrue();
        assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE)).isFalse();
        assertThat(matrix.isGranted(Role.TEACHER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE)).isFalse();
        assertThat(matrix.isGranted(Role.SYSTEM, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.APPROVE)).isFalse();
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            for (PermissionAction a : List.of(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT)) {
                assertThat(matrix.isGranted(role, PermissionModule.SCHOOL_CONTRACTS, a)).as(role + " " + a).isTrue();
            }
        }
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.VIEW)).isTrue();
        assertThat(matrix.isGranted(Role.MANAGER, PermissionModule.SCHOOL_CONTRACTS, PermissionAction.EDIT)).isFalse();
    }

    @Test
    void theApprovalsMenuItemIsOfferedOnlyWithApprove() {
        for (Role role : Role.values()) {
            var navigation = client.get()
                    .uri("/api/v1/me/access-model")
                    .header("Authorization", "Bearer " + signInAs(role).token())
                    .exchange()
                    .expectBody(AccessModelDtos.AccessModelResponse.class)
                    .returnResult()
                    .getResponseBody();
            boolean has = navigation.navigation().stream()
                    .flatMap(s -> s.items().stream())
                    .anyMatch(i -> i.label().equals("MoU Approvals")
                            && i.route().equals("/operations/school-contracts/approvals"));
            assertThat(has).as(role.name()).isEqualTo(role == Role.DIRECTOR || role == Role.MANAGER);
        }
    }

    @Test
    void moUAuditEntriesAreHiddenFromARoleWithoutViewOnSchoolContracts() {
        List<String> types = List.of("CONTRACT", "MOU_DOCUMENT", "MOU_APPROVAL");
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role))).as(role.name()).containsAll(types);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(visibility.hiddenEntityTypes(Set.of(role))).as(role.name()).doesNotContainAnyElementsOf(types);
        }
    }
}
