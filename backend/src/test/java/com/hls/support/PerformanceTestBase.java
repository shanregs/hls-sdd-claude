package com.hls.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 026 tests: a Manager with a School and a placed, active Teacher, and the performance API. */
public abstract class PerformanceTestBase extends TaskTestBase {

    protected static final String PERF = "/api/v1/teacher-performance";

    /** A Manager A who runs {@code schoolId} (in {@code zoneId}) where {@code teacherId} is placed today. */
    protected record Setup(String admin, String director, ManagerCtx manager, UUID zoneId, UUID schoolId, UUID teacherId) {
        public String managerToken() {
            return manager.token();
        }

        public UUID managerUserId() {
            return manager.signed().userId();
        }
    }

    protected Setup setup() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID[] where = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, where[0]);
        assignSchoolManager(admin, where[2], manager.managerId());
        UUID teacher = teacher(admin);
        placeTeacher(admin, teacher, where[2]);
        return new Setup(admin, director, manager, where[0], where[2], teacher);
    }

    /** Another Teacher placed at the same School. */
    protected UUID anotherTeacher(Setup s) {
        UUID teacher = teacher(s.admin());
        placeTeacher(s.admin(), teacher, s.schoolId());
        return teacher;
    }

    protected Map<String, Integer> ratings(int value) {
        Map<String, Integer> ratings = new LinkedHashMap<>();
        for (String area : List.of("ENGLISH_COMMUNICATION", "TEACHING_ABILITY", "CONTENT_DELIVERY", "CLASSROOM_MANAGEMENT", "STUDENT_ENGAGEMENT",
                "PREPARATION", "CONFIDENCE", "PUNCTUALITY")) {
            ratings.put(area, value);
        }
        return ratings;
    }

    /** A full, submittable form body with the given result and the same rating in every area. */
    protected Map<String, Object> form(String result, int rating) {
        Map<String, Object> body = new HashMap<>();
        body.put("className", "Class 5");
        body.put("subject", "English");
        body.put("durationMinutes", 40);
        body.put("ratings", ratings(rating));
        body.put("overallResult", result);
        body.put("strengths", "Clear voice and good rapport with the pupils");
        body.put("improvements", result.equals("GOOD") ? null : "Plan the lesson in steps and check understanding");
        return body;
    }

    protected Map<String, Object> withVersion(Map<String, Object> body, Object version) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put("version", version);
        return copy;
    }

    protected Resp startObservation(String token, UUID teacher) {
        return post(PERF + "/observations", token, Map.of("teacherId", teacher));
    }

    /** Starts an observation and returns its id. */
    protected UUID newDraft(String token, UUID teacher) {
        Resp resp = startObservation(token, teacher);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) resp.map().get("id"));
    }

    protected Resp submitObservation(String token, UUID observation, Map<String, Object> form) {
        Object version = get(PERF + "/observations/" + observation, token).map().get("version");
        return post(PERF + "/observations/" + observation + "/submit", token, withVersion(form, version));
    }

    /** Starts and submits an observation; returns the submitted view. */
    protected Map<String, Object> observe(String token, UUID teacher, String result, int rating) {
        UUID draft = newDraft(token, teacher);
        Resp resp = submitObservation(token, draft, form(result, rating));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp.map();
    }

    protected UUID id(Map<String, Object> view) {
        return UUID.fromString((String) view.get("id"));
    }

    protected Resp plan(String token, UUID teacher, LocalDate on, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherId", teacher);
        body.put("plannedOn", on.toString());
        body.put("reason", reason);
        return post(PERF + "/plans", token, body);
    }

    protected int openObservationTasks(UUID teacher) {
        return jdbc.queryForObject(
                "select count(*) from task where type = 'OBSERVATION' and linked_id = ? and status in ('ASSIGNED','ACKNOWLEDGED','IN_PROGRESS')",
                Integer.class,
                teacher);
    }

    protected String issueStatusOf(UUID teacher) {
        List<String> rows = jdbc.queryForList(
                "select status from performance_issue where teacher_id = ? order by created_at desc", String.class, teacher);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected UUID openIssueOf(UUID teacher) {
        return jdbc.queryForObject(
                "select id from performance_issue where teacher_id = ? and status in ('OPEN','UNDER_CORRECTION','REFERRED')", UUID.class, teacher);
    }

    protected Resp upload(String token, UUID observation, String name) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = String.valueOf((char) 13) + (char) 10;
        byte[] content = "observation notes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] head = ("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"" + crlf
                        + "Content-Type: application/octet-stream" + crlf + crlf)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = (crlf + "--" + boundary + "--" + crlf).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        var result = client.post()
                .uri(PERF + "/observations/" + observation + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(body)
                .exchange()
                .returnResult(String.class);
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }
}
