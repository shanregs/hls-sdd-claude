package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 025 tests: tasks through the API, and moving a task's dates for the overdue cases. */
public abstract class TaskTestBase extends MasterDataTestBase {

    protected static final String BASE = "/api/v1/tasks";

    protected static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    protected Map<String, Object> taskBody(UUID assignee, LocalDate due) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SCHOOL_VISIT");
        body.put("title", "Visit " + uniqueName("School"));
        body.put("assigneeUserId", assignee);
        body.put("dueOn", due.toString());
        return body;
    }

    protected Resp createTask(String token, UUID assignee, LocalDate due) {
        return post(BASE, token, taskBody(assignee, due));
    }

    protected UUID newTask(String token, UUID assignee, LocalDate due) {
        Resp resp = createTask(token, assignee, due);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) ((Map<?, ?>) resp.map().get("task")).get("id"));
    }

    protected String statusOf(String token, UUID task) {
        Resp resp = get(BASE + "/" + task, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (String) ((Map<?, ?>) resp.map().get("task")).get("status");
    }

    protected Resp act(String token, UUID task, String action, Object body) {
        return post(BASE + "/" + task + "/" + action, token, body == null ? Map.of() : body);
    }

    protected void moveDueDate(UUID task, LocalDate due) {
        jdbc.update("update task set due_on = ? where id = ?", due, task);
    }

    /** Takes a task through acknowledge and start. */
    protected void startTask(String managerToken, UUID task) {
        assertThat(act(managerToken, task, "acknowledge", null).status()).isEqualTo(200);
        assertThat(act(managerToken, task, "start", null).status()).isEqualTo(200);
    }

    protected Resp completeTask(String managerToken, UUID task) {
        return act(managerToken, task, "complete", Map.of("note", "Visited and met the Principal"));
    }

    protected ManagerCtx manager(String adminToken) {
        return newManager(adminToken);
    }

    protected int notificationCount(UUID user, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    /** Notifications of a type about one task (other tests leave their own tasks in the shared database). */
    protected int notificationCountForTask(UUID user, String type, UUID task) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ? and link = ?",
                Integer.class,
                user,
                type,
                "/operations/tasks/" + task);
    }

    protected static List<String> ids(Resp list) {
        return content(list).stream().map(m -> (String) m.get("id")).toList();
    }

    protected Signed director() {
        return signInAs(Role.DIRECTOR);
    }
}
