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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 029 T009: the sixth role, Content Contributor, in the access model, the matrix and the endpoints. */
class ContentContributorAccessModelTest extends IntegrationTestBase {

    private static final String MATRIX = "/api/v1/identity/permission-matrix";
    private static final Set<PermissionModule> ALLOWED = EnumSet.of(
            PermissionModule.DASHBOARD,
            PermissionModule.ACCOUNT_PROFILE,
            PermissionModule.MY_SESSIONS,
            PermissionModule.NOTIFICATIONS,
            PermissionModule.CONTENT);

    @Autowired
    private PermissionMatrixService matrix;

    private String accessModel(Signed signed) {
        return get("/api/v1/me/access-model", signed.token()).body();
    }

    @Test
    void theRoleValueFitsTheTwentyCharacterColumns() {
        assertThat(Role.values()).hasSize(6);
        assertThat(Role.CONTENT_CONTRIBUTOR.name().length()).isLessThanOrEqualTo(20);
    }

    @Test
    void contributorIsEligibleOnlyForItsOwnModulesAndNeverForApprove() {
        for (PermissionModule module : PermissionModule.values()) {
            Set<PermissionAction> actions = PermissionEligibility.actionsFor(Role.CONTENT_CONTRIBUTOR, module);
            if (!ALLOWED.contains(module)) {
                assertThat(actions).as(module.name()).isEmpty();
            }
        }
        assertThat(PermissionEligibility.actionsFor(Role.CONTENT_CONTRIBUTOR, PermissionModule.CONTENT))
                .containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
    }

    @Test
    void approveIsEligibleForAdminAndDirectorOnlyAndSeededForDirectorOnly() {
        for (Role role : Role.values()) {
            boolean eligible = role == Role.ADMIN || role == Role.DIRECTOR;
            assertThat(PermissionEligibility.isEligible(role, PermissionModule.CONTENT, PermissionAction.APPROVE))
                    .as(role.name())
                    .isEqualTo(eligible);
            assertThat(matrix.isGranted(role, PermissionModule.CONTENT, PermissionAction.APPROVE))
                    .as("seeded " + role)
                    .isEqualTo(role == Role.DIRECTOR);
        }
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.CONTENT_CONTRIBUTOR)) {
            assertThat(matrix.isGranted(role, PermissionModule.CONTENT, PermissionAction.CREATE)).as(role.name()).isTrue();
        }
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(matrix.isGranted(role, PermissionModule.CONTENT, PermissionAction.VIEW)).as(role.name()).isFalse();
        }
    }

    @Test
    void nothingOutsideTheAllowedModulesIsSeededForTheContributor() {
        for (PermissionModule module : PermissionModule.values()) {
            if (ALLOWED.contains(module)) {
                continue;
            }
            for (PermissionAction action : module.actions()) {
                assertThat(matrix.isGranted(Role.CONTENT_CONTRIBUTOR, module, action))
                        .as(module + " " + action)
                        .isFalse();
            }
        }
    }

    @Test
    void contributorMenuHasOnlyItsItems() {
        String body = accessModel(signInAs(Role.CONTENT_CONTRIBUTOR));
        assertThat(body).contains("CONTENT_CONTRIBUTOR").contains("My Content").contains("/operations/content/mine");
        assertThat(body).contains("\"CONTENT\":\"OWN\"");
        assertThat(body)
                .doesNotContain("My Tasks")
                .doesNotContain("Role & Permissions")
                .doesNotContain("User Management")
                .doesNotContain("Manager Tasks");
    }

    @Test
    void adminAndDirectorSeeContentManagerAndContributorSeeMyContentOthersSeeNothing() {
        assertThat(accessModel(signInAs(Role.ADMIN))).contains("\"label\":\"Content\"").contains("\"CONTENT\":\"ORG_WIDE\"");
        assertThat(accessModel(signInAs(Role.DIRECTOR))).contains("\"label\":\"Content\"");
        assertThat(accessModel(signInAs(Role.MANAGER))).contains("My Content").contains("\"CONTENT\":\"OWN\"");
        assertThat(accessModel(signInAs(Role.TEACHER))).doesNotContain("Content\"").doesNotContain("/operations/content");
        assertThat(accessModel(signInAs(Role.SYSTEM))).doesNotContain("/operations/content");
    }

    @Test
    void roleUnionWithManagerKeepsBothMenus() {
        String body = accessModel(signInAs(Role.MANAGER, Role.CONTENT_CONTRIBUTOR));
        assertThat(body).contains("My Tasks").contains("My Content");
    }

    @Test
    void contributorIsRefusedOnOtherModulesByDirectCall() {
        String token = signInAs(Role.CONTENT_CONTRIBUTOR).token();
        for (String uri : List.of(
                "/api/v1/tasks",
                "/api/v1/zones",
                "/api/v1/schools",
                "/api/v1/teachers",
                "/api/v1/identity/users",
                MATRIX)) {
            assertThat(get(uri, token).status()).as(uri).isIn(403, 404);
        }
    }

    @Test
    void theMatrixRefusesToGrantTheContributorAnythingElse() {
        String admin = signInAs(Role.ADMIN).token();
        Resp refused = put(MATRIX + "/CONTENT_CONTRIBUTOR/TASKS/VIEW", admin, Map.of("granted", true));
        assertThat(refused.status()).isEqualTo(409);
        Resp approve = put(MATRIX + "/CONTENT_CONTRIBUTOR/CONTENT/APPROVE", admin, Map.of("granted", true));
        assertThat(approve.status()).isEqualTo(409);
        Resp managerApprove = put(MATRIX + "/MANAGER/CONTENT/APPROVE", admin, Map.of("granted", true));
        assertThat(managerApprove.status()).isEqualTo(409);
    }

    @Test
    void adminMayBeGrantedApproveAndItTakesEffect() {
        String admin = signInAs(Role.ADMIN).token();
        try {
            Resp ok = put(MATRIX + "/ADMIN/CONTENT/APPROVE", admin, Map.of("granted", true));
            assertThat(ok.status()).isEqualTo(200);
            assertThat(matrix.isGranted(Role.ADMIN, PermissionModule.CONTENT, PermissionAction.APPROVE)).isTrue();
        } finally {
            put(MATRIX + "/ADMIN/CONTENT/APPROVE", admin, Map.of("granted", false));
        }
    }

    @Test
    void theMatrixShowsAllSixRoles() {
        String body = get(MATRIX, signInAs(Role.ADMIN).token()).body();
        assertThat(body).contains("CONTENT_CONTRIBUTOR").contains("CONTENT");
    }

    @Test
    void aUserWithOnlyTheContributorRoleCanBeCreatedAndSignsIn() {
        Signed signed = signInAs(Role.CONTENT_CONTRIBUTOR);
        assertThat(roleAssignmentRepository.findByUserId(signed.userId())).hasSize(1);
        assertThat(get("/api/v1/me/access-model", signed.token()).status()).isEqualTo(200);
    }
}
