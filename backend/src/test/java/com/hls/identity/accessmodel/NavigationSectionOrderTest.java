package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 002 FR-008a: the navigation sections always run Dashboard, Master Data, Operations, System,
 * Audit, Account (a section a role does not have is simply left out), and the items inside a
 * section keep their own order.
 */
class NavigationSectionOrderTest extends IntegrationTestBase {

    private List<String> sectionsFor(Role... roles) {
        String token = signInAs(roles).token();
        return client.get()
                .uri("/api/v1/me/access-model")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AccessModelDtos.AccessModelResponse.class)
                .returnResult()
                .getResponseBody()
                .navigation()
                .stream()
                .map(AccessModelDtos.NavSection::section)
                .toList();
    }

    @Test
    void adminSeesDashboardMasterDataOperationsSystemAuditAccount() {
        assertThat(sectionsFor(Role.ADMIN))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "SYSTEM", "AUDIT", "ACCOUNT");
    }

    @Test
    void directorKeepsTheSameOrderWithoutTheAuditSection() {
        assertThat(sectionsFor(Role.DIRECTOR))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "SYSTEM", "ACCOUNT");
    }

    @Test
    void managerSeesDashboardMasterDataOperationsAccount() {
        assertThat(sectionsFor(Role.MANAGER))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "ACCOUNT");
    }

    @Test
    void teacherSeesMyAttendanceInTheOperationsSlotBeforeAccount() {
        assertThat(sectionsFor(Role.TEACHER))
                .containsExactly("Dashboard", "MASTER DATA", "MY ATTENDANCE", "ACCOUNT");
    }

    @Test
    void systemSeesItsOwnDashboardThenMasterDataThenConfigurationAndAudit() {
        assertThat(sectionsFor(Role.SYSTEM))
                .containsExactly(
                        "SYSTEM DASHBOARD", "MASTER DATA", "SYSTEM CONFIGURATION", "AUDIT", "ACCOUNT");
    }

    @Test
    void aUserWithSeveralRolesGetsOneCombinedOrder() {
        assertThat(sectionsFor(Role.MANAGER, Role.TEACHER))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "MY ATTENDANCE", "ACCOUNT");
    }
}
