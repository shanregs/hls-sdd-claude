package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.TaskTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 025 US1 and US2: the lifecycle, the scope, the lists and the audit trail. */
class TaskLifecycleApiTest extends TaskTestBase {

    @Test
    void aManagerWorksATaskFromAssignedToVerified() {
        String admin = signInAs(Role.ADMIN).token();
        String director = director().token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(director, manager.signed().userId(), today().plusDays(2));

        assertThat(statusOf(manager.token(), task)).isEqualTo("ASSIGNED");
        assertThat(notificationCount(manager.signed().userId(), "TASK_ASSIGNED")).isEqualTo(1);

        // cannot start before acknowledging, cannot complete without acknowledging
        assertThat(act(manager.token(), task, "start", null).status()).isEqualTo(409);
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(409);

        assertThat(act(manager.token(), task, "acknowledge", null).status()).isEqualTo(200);
        assertThat(act(manager.token(), task, "start", null).status()).isEqualTo(200);

        // a note of at least 10 characters is the evidence
        assertThat(act(manager.token(), task, "complete", Map.of("note", "done")).status()).isEqualTo(400);
        assertThat(statusOf(manager.token(), task)).isEqualTo("IN_PROGRESS");
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(200);
        assertThat(statusOf(manager.token(), task)).isEqualTo("COMPLETED");

        // a completed task is not the Manager's to change; only a verifier acts next
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(409);
        assertThat(act(manager.token(), task, "verify", null).status()).isEqualTo(403);

        assertThat(act(director, task, "verify", null).status()).isEqualTo(200);
        assertThat(statusOf(director, task)).isEqualTo("VERIFIED");

        Resp detail = get(BASE + "/" + task, manager.token());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) detail.map().get("history");
        assertThat(history).extracting(h -> h.get("event")).containsExactly("CREATED", "ACKNOWLEDGED", "STARTED", "COMPLETED", "VERIFIED");
        assertChangeRecorded(admin, "TASK", task, "status");
    }

    @Test
    void anotherManagerCannotSeeOrTouchTheTask() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx a = manager(admin);
        ManagerCtx b = manager(admin);
        UUID task = newTask(admin, a.signed().userId(), today().plusDays(1));

        assertThat(get(BASE + "/" + task, b.token()).status()).isEqualTo(404);
        assertThat(act(b.token(), task, "acknowledge", null).status()).isEqualTo(404);
        assertThat(ids(get(BASE + "?view=all", b.token()))).doesNotContain(task.toString());
        assertThat(ids(get(BASE + "?view=all&assigneeUserId=" + a.signed().userId(), b.token()))).doesNotContain(task.toString());
        assertThat(ids(get(BASE + "?view=all", a.token()))).contains(task.toString());
    }

    @Test
    void verificationCanBeSentBackWithAReasonAndTheAssigneeCannotVerifyOwnWork() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        startTask(manager.token(), task);
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(200);

        assertThat(act(admin, task, "send-back", Map.of("reason", "")).status()).isEqualTo(400);
        assertThat(act(admin, task, "send-back", Map.of("reason", "Photo missing")).status()).isEqualTo(200);
        assertThat(statusOf(admin, task)).isEqualTo("IN_PROGRESS");
        assertThat(completeTask(manager.token(), task).status()).isEqualTo(200);
        assertThat(act(admin, task, "verify", null).status()).isEqualTo(200);
    }

    @Test
    void adminEditsReassignsAndCancelsAndEveryChangeIsRecorded() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx first = manager(admin);
        ManagerCtx second = manager(admin);
        UUID task = newTask(admin, first.signed().userId(), today().plusDays(3));
        act(first.token(), task, "acknowledge", null);

        Resp detail = get(BASE + "/" + task, admin);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("assigneeUserId", second.signed().userId());
        body.put("dueOn", today().plusDays(6).toString());
        body.put("title", "Renamed visit");
        body.put("version", detail.map().get("version"));
        Resp updated = put(BASE + "/" + task, admin, body);
        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(statusOf(admin, task)).isEqualTo("ASSIGNED"); // back to assigned for the new Manager
        assertThat(notificationCount(second.signed().userId(), "TASK_ASSIGNED")).isEqualTo(1);
        assertThat(get(BASE + "/" + task, first.token()).status()).isEqualTo(404);
        assertChangeRecorded(admin, "TASK", task, "assigneeUserId");
        assertChangeRecorded(admin, "TASK", task, "dueOn");

        // a stale version is refused
        assertThat(put(BASE + "/" + task, admin, body).status()).isEqualTo(409);

        assertThat(act(admin, task, "cancel", Map.of("reason", "")).status()).isEqualTo(400);
        assertThat(act(admin, task, "cancel", Map.of("reason", "No longer needed")).status()).isEqualTo(200);
        assertThat(statusOf(admin, task)).isEqualTo("CANCELLED");
        assertThat(act(admin, task, "cancel", Map.of("reason", "Again")).status()).isEqualTo(409);
    }

    @Test
    void creationRulesAreEnforced() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        ManagerCtx other = manager(admin);
        UUID teacherUser = signInAs(Role.TEACHER).userId();

        assertThat(createTask(admin, manager.signed().userId(), today().minusDays(1)).status()).isEqualTo(400);
        assertThat(createTask(admin, teacherUser, today()).status()).isEqualTo(400);
        assertThat(createTask(admin, UUID.randomUUID(), today()).status()).isEqualTo(400);
        Map<String, Object> shortTitle = taskBody(manager.signed().userId(), today());
        shortTitle.put("title", "ab");
        assertThat(post(BASE, admin, shortTitle).status()).isEqualTo(400);
        Map<String, Object> badDays = taskBody(manager.signed().userId(), today());
        badDays.put("escalateAfterDays", 31);
        assertThat(post(BASE, admin, badDays).status()).isEqualTo(400);

        // a Manager makes personal tasks only
        assertThat(createTask(manager.token(), other.signed().userId(), today()).status()).isEqualTo(403);
        assertThat(createTask(manager.token(), manager.signed().userId(), today()).status()).isEqualTo(201);
        assertThat(get(BASE + "/assignees", manager.token()).body()).contains(manager.signed().userId().toString());
        assertThat(get(BASE + "/assignees", admin).body()).contains(other.signed().userId().toString());
    }

    @Test
    void listsSplitTodayUpcomingOverdueAwaitingAndDoneWithCounts() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID m = manager.signed().userId();
        UUID dueToday = newTask(admin, m, today());
        UUID upcoming = newTask(admin, m, today().plusDays(4));
        UUID overdue = newTask(admin, m, today().plusDays(1));
        moveDueDate(overdue, today().minusDays(1));
        UUID awaiting = newTask(admin, m, today().plusDays(2));
        startTask(manager.token(), awaiting);
        completeTask(manager.token(), awaiting);
        UUID done = newTask(admin, m, today().plusDays(2));
        startTask(manager.token(), done);
        completeTask(manager.token(), done);
        act(admin, done, "verify", null);

        String t = manager.token();
        assertThat(ids(get(BASE + "?view=today", t))).containsExactly(dueToday.toString());
        assertThat(ids(get(BASE + "?view=upcoming", t))).containsExactly(upcoming.toString());
        assertThat(ids(get(BASE + "?view=overdue", t))).containsExactly(overdue.toString());
        assertThat(ids(get(BASE + "?view=awaiting", t))).containsExactly(awaiting.toString());
        assertThat(ids(get(BASE + "?view=done", t))).containsExactly(done.toString());
        assertThat(ids(get(BASE + "?view=all&type=SCHOOL_VISIT", t))).hasSize(5);
        assertThat(ids(get(BASE + "?view=all&type=TRAINING", t))).isEmpty();
        assertThat(get(BASE + "?view=sideways", t).status()).isEqualTo(400);

        Map<String, Object> summary = get(BASE + "/summary", t).map();
        assertThat(summary).containsEntry("today", 1).containsEntry("upcoming", 1).containsEntry("overdue", 1)
                .containsEntry("awaiting", 1).containsEntry("done", 1);

        // management filters by Manager
        assertThat(ids(get(BASE + "?view=overdue&assigneeUserId=" + m, admin))).containsExactly(overdue.toString());
        Map<?, ?> row = content(get(BASE + "?view=overdue", t)).get(0);
        assertThat(row.get("overdue")).isEqualTo(true);
        assertThat(((Number) row.get("daysOverdue")).intValue()).isEqualTo(1);
    }

    @Test
    void evidenceFilesAttachToATaskAndStayWithinTheLimit() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        ManagerCtx other = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today().plusDays(1));
        assertThat(upload(manager.token(), task, "photo.txt").status()).isEqualTo(409); // not acknowledged yet
        startTask(manager.token(), task);
        for (int i = 0; i < 5; i++) {
            assertThat(upload(manager.token(), task, "proof" + i + ".txt").status()).isEqualTo(201);
        }
        assertThat(upload(manager.token(), task, "proof6.txt").status()).isEqualTo(409);
        assertThat(upload(other.token(), task, "x.txt").status()).isEqualTo(404);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) get(BASE + "/" + task, admin).map().get("evidence");
        assertThat(files).hasSize(5);
        String fileId = (String) files.get(0).get("fileId");
        assertThat(get(BASE + "/files/" + fileId, admin).status()).isEqualTo(200);
        assertThat(get(BASE + "/files/" + fileId, manager.token()).status()).isEqualTo(200);
        assertThat(get(BASE + "/files/" + fileId, other.token()).status()).isEqualTo(404);
    }

    private Resp upload(String token, UUID task, String name) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = String.valueOf((char) 13) + (char) 10;
        byte[] content = "evidence text".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] head = ("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"" + crlf
                        + "Content-Type: application/octet-stream" + crlf + crlf)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = (crlf + "--" + boundary + "--" + crlf).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        var result = client.post()
                .uri(BASE + "/" + task + "/evidence")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(body)
                .exchange()
                .returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }
}
