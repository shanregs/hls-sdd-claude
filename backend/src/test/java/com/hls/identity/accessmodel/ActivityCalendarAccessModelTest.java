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

/** Spec 035 T024: the Activity Calendar permission module, its seeded View, its menu label per role and its scope. */
class ActivityCalendarAccessModelTest extends IntegrationTestBase {

    private static final String MATRIX = "/api/v1/identity/permission-matrix";

    @Autowired
    private PermissionMatrixService matrix;

    private String accessModel(Role role) {
        return get("/api/v1/me/access-model", signInAs(role).token()).body();
    }

    @Test
    void theModuleIsViewOnlyAndEligibleForAdminDirectorAndManagerOnly() {
        assertThat(PermissionModule.ACTIVITY_CALENDAR.actions()).containsExactly(PermissionAction.VIEW);
        for (Role role : Role.values()) {
            boolean eligible = role == Role.ADMIN || role == Role.DIRECTOR || role == Role.MANAGER;
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.ACTIVITY_CALENDAR))
                    .as("ACTIVITY_CALENDAR " + role)
                    .isEqualTo(eligible ? EnumSet.of(PermissionAction.VIEW) : EnumSet.noneOf(PermissionAction.class));
        }
    }

    @Test
    void viewIsSeededForAdminDirectorAndManagerAndNeverForTeacherOrSystem() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            assertThat(matrix.isGranted(role, PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW))
                    .as(role.name())
                    .isTrue();
        }
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(matrix.isGranted(role, PermissionModule.ACTIVITY_CALENDAR, PermissionAction.VIEW))
                    .as(role.name())
                    .isFalse();
        }
    }

    @Test
    void theMatrixRefusesToGrantTheCalendarToTeacherOrSystem() {
        String admin = signInAs(Role.ADMIN).token();
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            Resp refused = put(MATRIX + "/" + role + "/ACTIVITY_CALENDAR/VIEW", admin, Map.of("granted", true));
            assertThat(refused.status()).as(role.name()).isEqualTo(409);
            assertThat(refused.body()).contains("does not apply");
        }
    }

    @Test
    void adminAndDirectorSeeActivityCalendarAndAManagerSeesMyActivityCalendar() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String body = accessModel(role);
            assertThat(body).as(role.name()).contains("\"Activity Calendar\"", "\"/operations/activity-calendar\"");
            assertThat(body).doesNotContain("My Activity Calendar");
            assertThat(body).contains("\"ACTIVITY_CALENDAR\":\"ORG_WIDE\"");
        }
        String manager = accessModel(Role.MANAGER);
        assertThat(manager).contains("\"My Activity Calendar\"", "\"/operations/activity-calendar\"");
        assertThat(manager).contains("\"ACTIVITY_CALENDAR\":\"OWN\"");
    }

    @Test
    void aTeacherAndSystemSeeNoCalendarItem() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String body = accessModel(role);
            assertThat(body).as(role.name()).doesNotContain("/operations/activity-calendar");
            assertThat(body).doesNotContain("\"ACTIVITY_CALENDAR\"");
        }
    }
}
