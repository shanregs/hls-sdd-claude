package com.hls.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.TaskTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Spec 040 US3: a Manager logs own work for today; final at once; never waits for verification. */
class SameDayTaskApiTest extends TaskTestBase {

    private Map<String, Object> sameDayBody(String status, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SCHOOL_VISIT");
        body.put("title", "Visit " + uniqueName("School"));
        body.put("status", status);
        if (note != null) {
            body.put("completionNote", note);
        }
        return body;
    }

    private Resp postKeyed(String token, UUID key, Object body) {
        var spec = client.method(HttpMethod.POST).uri(BASE + "/same-day").header("Authorization", "Bearer " + token);
        spec.header("Idempotency-Key", key.toString());
        var result = spec.body(body).exchange().returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    private List<String> events(String id) {
        return jdbc.queryForList("select event from task_history where task_id = ? order by at, id", String.class, UUID.fromString(id));
    }

    @Test
    void aManagerLogsATaskInProgressForToday() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);

        Resp resp = post(BASE + "/same-day", manager.token(), sameDayBody("IN_PROGRESS", null));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"IN_PROGRESS\"").contains("\"sameDay\":true");
    }

    @Test
    void aCompletedSameDayTaskIsFinalAtOnceAndNeverAwaitsVerification() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);

        Resp resp = post(BASE + "/same-day", manager.token(), sameDayBody("COMPLETED", "Met the Principal and the Teacher"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        assertThat(resp.body()).contains("\"status\":\"VERIFIED\"");
        String id = (String) resp.map().get("id");
        assertThat(events(id)).containsSubsequence("CREATED", "COMPLETED", "VERIFIED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_history where task_id = ? and note like '%sameDay=true%auto-accepted%'",
                        Integer.class,
                        UUID.fromString(id)))
                .isEqualTo(1);
        Resp awaiting = get(BASE + "?view=awaiting", admin);
        assertThat(awaiting.body()).doesNotContain(id);
        Resp sameDayOnly = get(BASE + "?sameDay=true", admin);
        assertThat(sameDayOnly.body()).contains(id).contains("\"sameDay\":true");
    }

    @Test
    void aShortCompletionNoteIsRefused() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);

        Resp resp = post(BASE + "/same-day", manager.token(), sameDayBody("COMPLETED", "too short"));

        assertThat(resp.status()).as(resp.body()).isEqualTo(400);
    }

    @Test
    void adminAndDirectorCannotLogSameDayWorkForAnotherManager() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();

        assertThat(post(BASE + "/same-day", admin, sameDayBody("IN_PROGRESS", null)).status()).isEqualTo(403);
        assertThat(post(BASE + "/same-day", director().token(), sameDayBody("IN_PROGRESS", null)).status()).isEqualTo(403);
    }

    @Test
    void aTaskPlannedForTodayIsMarkedDoneOnTheSpotAndNeedsNoVerification() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID task = newTask(admin, manager.signed().userId(), today());

        Resp done = post(BASE + "/" + task + "/done-on-the-spot", manager.token(), Map.of("note", "Done on the spot at the School"));

        assertThat(done.status()).as(done.body()).isEqualTo(200);
        assertThat(statusOf(admin, task)).isEqualTo("VERIFIED");
        assertThat(get(BASE + "?view=awaiting", admin).body()).doesNotContain(task.toString());
        assertThat(jdbc.queryForObject("select created_by from task where id = ?", UUID.class, task)).isNotEqualTo(manager.signed().userId());
    }

    @Test
    void aTaskNotDueTodayOrAssignedToSomeoneElseCannotBeMarkedDoneOnTheSpot() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        ManagerCtx other = manager(admin);
        UUID later = newTask(admin, manager.signed().userId(), today().plusDays(3));
        UUID mine = newTask(admin, manager.signed().userId(), today());

        assertThat(post(BASE + "/" + later + "/done-on-the-spot", manager.token(), Map.of("note", "Done on the spot at the School")).status())
                .isEqualTo(409);
        assertThat(post(BASE + "/" + mine + "/done-on-the-spot", other.token(), Map.of("note", "Done on the spot at the School")).status())
                .isIn(403, 404);
    }

    @Test
    void aRepeatedPhoneKeyCreatesOneTaskAndAStaleDateIsRefused() {
        String admin = signInAs(com.hls.identity.user.Role.ADMIN).token();
        ManagerCtx manager = manager(admin);
        UUID key = UUID.randomUUID();
        Map<String, Object> body = sameDayBody("COMPLETED", "Met the Principal and the Teacher");
        body.put("capturedOn", today().minusDays(2).toString());

        Resp first = postKeyed(manager.token(), key, body);
        Resp again = postKeyed(manager.token(), key, body);

        assertThat(first.status()).as(first.body()).isEqualTo(201);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.map().get("id")).isEqualTo(first.map().get("id"));
        assertThat(jdbc.queryForObject("select count(*) from task where client_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select due_on from task where client_key = ?", java.sql.Date.class, key).toLocalDate())
                .isEqualTo(today().minusDays(2));

        Map<String, Object> stale = sameDayBody("IN_PROGRESS", null);
        stale.put("capturedOn", today().minusDays(8).toString());
        assertThat(postKeyed(manager.token(), UUID.randomUUID(), stale).status()).isEqualTo(409);
        Map<String, Object> future = sameDayBody("IN_PROGRESS", null);
        future.put("capturedOn", today().plusDays(1).toString());
        assertThat(postKeyed(manager.token(), UUID.randomUUID(), future).status()).isEqualTo(409);
    }
}
