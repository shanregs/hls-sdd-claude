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
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "RECRUITMENT", "SYSTEM", "AUDIT", "ACCOUNT");
    }

    @Test
    void directorKeepsTheSameOrderWithoutTheAuditSection() {
        assertThat(sectionsFor(Role.DIRECTOR))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "RECRUITMENT", "SYSTEM", "ACCOUNT");
    }

    @Test
    void managerSeesDashboardMasterDataOperationsAccount() {
        assertThat(sectionsFor(Role.MANAGER))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "RECRUITMENT", "ACCOUNT");
    }

    @Test
    void teacherSeesMyAttendanceInTheOperationsSlotBeforeAccount() {
        assertThat(sectionsFor(Role.TEACHER))
                .containsExactly("Dashboard", "MASTER DATA", "MY ATTENDANCE", "LEAVE", "ACCOUNT");
    }

    @Test
    void systemSeesItsOwnDashboardThenMasterDataThenConfigurationAndAudit() {
        assertThat(sectionsFor(Role.SYSTEM))
                .containsExactly(
                        "SYSTEM DASHBOARD", "MASTER DATA", "SYSTEM CONFIGURATION", "AUDIT", "ACCOUNT");
    }

    @Test
    void systemHasAnAllSessionsItemInItsConfigurationSectionAndNobodyElseDoes() {
        for (Role role : Role.values()) {
            String token = signInAs(role).token();
            var navigation = client.get()
                    .uri("/api/v1/me/access-model")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectBody(AccessModelDtos.AccessModelResponse.class)
                    .returnResult()
                    .getResponseBody()
                    .navigation();
            boolean hasItem = navigation.stream()
                    .flatMap(s -> s.items().stream())
                    .anyMatch(i -> i.label().equals("All Sessions") && i.route().equals("/identity/sessions"));
            assertThat(hasItem).as(role.name()).isEqualTo(role == Role.SYSTEM);
        }
    }

    @Test
    void aUserWithSeveralRolesGetsOneCombinedOrder() {
        assertThat(sectionsFor(Role.MANAGER, Role.TEACHER))
                .containsExactly("Dashboard", "MASTER DATA", "OPERATIONS", "RECRUITMENT", "MY ATTENDANCE", "LEAVE", "ACCOUNT");
    }

    @Test
    void notificationsAreInTheAccountSectionForTheFourBusinessRolesAndNotForSystem() {
        for (Role role : Role.values()) {
            var navigation = client.get()
                    .uri("/api/v1/me/access-model")
                    .header("Authorization", "Bearer " + signInAs(role).token())
                    .exchange()
                    .expectBody(AccessModelDtos.AccessModelResponse.class)
                    .returnResult()
                    .getResponseBody()
                    .navigation();
            var account = navigation.stream().filter(s -> s.section().equals("ACCOUNT")).findFirst().orElseThrow();
            boolean has = account.items().stream()
                    .anyMatch(i -> i.label().equals("Notifications") && i.route().equals("/account/notifications"));
            assertThat(has).as(role.name()).isEqualTo(role != Role.SYSTEM);
        }
    }

    @Test
    void schoolContractsIsAnOperationsItemForAdminDirectorAndManagerOnly() {
        for (Role role : Role.values()) {
            var navigation = client.get()
                    .uri("/api/v1/me/access-model")
                    .header("Authorization", "Bearer " + signInAs(role).token())
                    .exchange()
                    .expectBody(AccessModelDtos.AccessModelResponse.class)
                    .returnResult()
                    .getResponseBody();
            boolean has = navigation.navigation().stream()
                    .filter(s -> s.section().equals("OPERATIONS"))
                    .flatMap(s -> s.items().stream())
                    .anyMatch(i -> i.label().equals("School Contracts")
                            && i.route().equals("/operations/school-contracts"));
            boolean expected = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.MANAGER;
            assertThat(has).as(role.name()).isEqualTo(expected);
            String scope = navigation.dataScope().get("SCHOOL_CONTRACTS");
            assertThat(scope)
                    .as(role.name() + " scope")
                    .isEqualTo(role == Role.MANAGER ? "ASSIGNED" : expected ? "ORG_WIDE" : null);
        }
    }

    @Test
    void leaveItemsAppearOnlyForTheRolesThatHoldThem() {
        record Expect(Role role, boolean applyLeave, boolean leaveManagement) {}
        for (Expect e : new Expect[] {
            new Expect(Role.TEACHER, true, false),
            new Expect(Role.MANAGER, false, true),
            new Expect(Role.ADMIN, false, true),
            new Expect(Role.DIRECTOR, false, true),
            new Expect(Role.SYSTEM, false, false)
        }) {
            var navigation = client.get()
                    .uri("/api/v1/me/access-model")
                    .header("Authorization", "Bearer " + signInAs(e.role()).token())
                    .exchange()
                    .expectBody(AccessModelDtos.AccessModelResponse.class)
                    .returnResult()
                    .getResponseBody()
                    .navigation();
            var routes = navigation.stream().flatMap(sec -> sec.items().stream()).map(i -> i.route()).toList();
            assertThat(routes.contains("/leave/apply")).as(e.role() + " apply").isEqualTo(e.applyLeave());
            assertThat(routes.contains("/leave/history")).as(e.role() + " history").isEqualTo(e.applyLeave());
            assertThat(routes.contains("/operations/leave")).as(e.role() + " management").isEqualTo(e.leaveManagement());
        }
    }
}
