package com.hls.identity.accessmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 032 T007: the Manager attendance menu items and data scopes per role. */
class ManagerAttendanceAccessModelTest extends IntegrationTestBase {

    private static final List<String> MANAGER_ROUTES =
            List.of("/manager-attendance/me", "/manager-attendance/me/month", "/manager-attendance/me/leave");
    private static final List<String> STAFF_ROUTES =
            List.of("/operations/manager-attendance", "/operations/manager-leave");

    private String accessModel(Role role) {
        return get("/api/v1/me/access-model", signInAs(role).token()).body();
    }

    @Test
    void aManagerSeesTheirOwnThreeItemsAndNoneOfTheStaffOnes() {
        String body = accessModel(Role.MANAGER);
        MANAGER_ROUTES.forEach(route -> assertThat(body).as(route).contains("\"" + route + "\""));
        STAFF_ROUTES.forEach(route -> assertThat(body).as(route).doesNotContain(route));
        assertThat(body).contains("\"MY_MANAGER_ATTENDANCE\":\"OWN\"");
    }

    @Test
    void adminAndDirectorSeeTheTwoStaffItemsAndNotTheManagersOwn() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String body = accessModel(role);
            STAFF_ROUTES.forEach(route -> assertThat(body).as(role + " " + route).contains("\"" + route + "\""));
            MANAGER_ROUTES.forEach(route -> assertThat(body).as(role + " " + route).doesNotContain("\"" + route + "\""));
            assertThat(body).contains("\"MANAGER_ATTENDANCE\":\"ORG_WIDE\"");
        }
    }

    @Test
    void aTeacherAndSystemSeeNoneOfThem() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String body = accessModel(role);
            MANAGER_ROUTES.forEach(route -> assertThat(body).as(role + " " + route).doesNotContain("\"" + route + "\""));
            STAFF_ROUTES.forEach(route -> assertThat(body).as(role + " " + route).doesNotContain(route));
        }
    }
}
