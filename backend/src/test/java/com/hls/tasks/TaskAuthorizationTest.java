package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionEligibility;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/** Spec 025 SC-004 and FR-016/FR-017: every endpoint refuses Teacher and System; a Manager never gets management actions. */
class TaskAuthorizationTest extends TaskTestBase {

    private record Endpoint(String name, BiFunction<TaskAuthorizationTest, String, Resp> call) {}

    private List<Endpoint> endpoints(UUID task, UUID assignee) {
        return List.of(
                new Endpoint("list", (t, token) -> t.get(BASE, token)),
                new Endpoint("summary", (t, token) -> t.get(BASE + "/summary", token)),
                new Endpoint("types", (t, token) -> t.get(BASE + "/types", token)),
                new Endpoint("assignees", (t, token) -> t.get(BASE + "/assignees", token)),
                new Endpoint("detail", (t, token) -> t.get(BASE + "/" + task, token)),
                new Endpoint("create", (t, token) -> t.createTask(token, assignee, today())),
                new Endpoint("update", (t, token) -> t.put(BASE + "/" + task, token, Map.of("title", "x", "version", 0))),
                new Endpoint("acknowledge", (t, token) -> t.act(token, task, "acknowledge", null)),
                new Endpoint("start", (t, token) -> t.act(token, task, "start", null)),
                new Endpoint("overdue-reason", (t, token) -> t.act(token, task, "overdue-reason", Map.of("reason", "late"))),
                new Endpoint("complete", (t, token) -> t.act(token, task, "complete", Map.of("note", "done well enough"))),
                new Endpoint("verify", (t, token) -> t.act(token, task, "verify", null)),
                new Endpoint("send-back", (t, token) -> t.act(token, task, "send-back", Map.of("reason", "no"))),
                new Endpoint("cancel", (t, token) -> t.act(token, task, "cancel", Map.of("reason", "no longer"))),
                new Endpoint("file", (t, token) -> t.get(BASE + "/files/" + UUID.randomUUID(), token)));
    }

    @Test
    void teacherAndSystemGet403OnEveryEndpointAndHaveNoMenuItem() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            for (Endpoint endpoint : endpoints(task, manager.signed().userId())) {
                assertThat(endpoint.call().apply(this, token).status()).as(role + " " + endpoint.name()).isEqualTo(403);
            }
            assertThat(get("/api/v1/me/access-model", token).body()).as(role + " menu").doesNotContain("/operations/tasks");
        }
        assertThat(statusOf(admin, task)).isEqualTo("ASSIGNED");
    }

    @Test
    void aManagerHasNoApproveOrManagementActionsAndSeesMyTasks() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        String token = manager.token();

        assertThat(act(token, task, "verify", null).status()).isEqualTo(403);
        assertThat(act(token, task, "send-back", Map.of("reason", "no")).status()).isEqualTo(403);
        assertThat(act(token, task, "cancel", Map.of("reason", "no longer")).status()).isEqualTo(403);
        Resp detail = get(BASE + "/" + task, token);
        assertThat(put(BASE + "/" + task, token, Map.of("title", "Renamed", "version", detail.map().get("version"))).status())
                .isEqualTo(403);
        assertThat(statusOf(admin, task)).isEqualTo("ASSIGNED");

        String access = get("/api/v1/me/access-model", token).body();
        assertThat(access).contains("My Tasks", "/operations/tasks").doesNotContain("Manager Tasks");
    }

    @Test
    void adminAndDirectorSeeManagerTasksAndMayAct() {
        String admin = signInAs(Role.ADMIN).token();
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR)) {
            String token = signInAs(role).token();
            assertThat(get("/api/v1/me/access-model", token).body()).contains("Manager Tasks", "/operations/tasks");
            ManagerCtx manager = manager(admin);
            assertThat(createTask(token, manager.signed().userId(), today()).status()).isEqualTo(201);
            assertThat(get(BASE + "?view=all", token).status()).isEqualTo(200);
        }
    }

    @Test
    void eligibilityMatchesTheDefaults() {
        for (Role role : List.of(Role.TEACHER, Role.SYSTEM)) {
            assertThat(PermissionEligibility.actionsFor(role, PermissionModule.TASKS)).isEmpty();
        }
        assertThat(PermissionEligibility.actionsFor(Role.MANAGER, PermissionModule.TASKS))
                .containsExactlyInAnyOrder(PermissionAction.VIEW, PermissionAction.CREATE, PermissionAction.EDIT);
        assertThat(PermissionEligibility.actionsFor(Role.ADMIN, PermissionModule.TASKS)).contains(PermissionAction.APPROVE);
        assertThat(PermissionEligibility.actionsFor(Role.DIRECTOR, PermissionModule.TASKS)).contains(PermissionAction.APPROVE);
    }
}
