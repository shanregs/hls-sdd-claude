package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollTestBase;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Spec 013a SC-004 (T014, T023): Admin and Director use every endpoint; the other roles get 403 on each. */
class SalaryStructuresAuthorizationTest extends PayrollTestBase {

    private static final String ITEM = "/operations/salary-structures";

    private record Endpoint(String name, BiFunction<SalaryStructuresAuthorizationTest, String, Resp> call) {}

    private List<Endpoint> endpoints(UUID designation) {
        return List.of(
                new Endpoint("list", (t, token) -> t.get(BASE + "/designations", token)),
                new Endpoint("detail", (t, token) -> t.get(BASE + "/designations/" + designation, token)),
                new Endpoint(
                        "record salary",
                        (t, token) -> t.recordSalary(token, designation, "20000", t.firstOfMonth(), null)),
                new Endpoint("policy", (t, token) -> t.get(BASE + "/policy", token)),
                new Endpoint(
                        "record policy",
                        (t, token) -> t.recordPolicy(token, "2099-01-01", "0.5", "NEAREST_RUPEE")));
    }

    @Test
    void adminAndDirectorSucceedOnEveryEndpoint() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String token = signInAs(role).token();
            for (Endpoint endpoint : endpoints(designation)) {
                int status = endpoint.call().apply(this, token).status();
                assertThat(status).as(role + " " + endpoint.name()).isIn(200, 201);
            }
        }
    }

    @Test
    void zoneManagerTeacherAndSystemGet403OnEveryEndpointAndHaveNoMenuItem() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (Endpoint endpoint : endpoints(designation)) {
                assertThat(endpoint.call().apply(this, token).status())
                        .as(role + " " + endpoint.name())
                        .isEqualTo(403);
            }
            assertThat(get("/api/v1/me/access-model", token).body()).as(role + " menu").doesNotContain(ITEM);
        }
        // and the refused writes stored nothing
        assertThat(historyOf(get(BASE + "/designations/" + designation, admin))).isEmpty();
    }

    @Test
    void adminAndDirectorSeeTheMenuItemUnderOperations() {
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String body = get("/api/v1/me/access-model", signInAs(role).token()).body();
            assertThat(body).as(role + " menu").contains(ITEM).contains("Salary Structures");
            assertThat(body).contains("\"SALARY_STRUCTURES\":\"ORG_WIDE\"");
        }
    }

    @Test
    void aCallerWithNoTokenIsRefused() {
        assertThat(get(BASE + "/policy", "").status()).isIn(401, 403);
    }
}
