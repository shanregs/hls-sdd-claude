package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for the spec 024 tests: sessions through the API, registrations, direct rows for assignments and a few counters.
 * The world of {@link AttendanceTestBase} gives an Admin, a Director, two Managers and a placed Teacher under each.
 */
public abstract class TrainingCalendarTestBase extends AttendanceTestBase {

    protected static final String API = "/api/v1/training";

    protected LocalDate in(int days) {
        return today().plusDays(days);
    }

    protected Map<String, Object> sessionBody(String type, LocalDate date, Integer seats) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("topic", uniqueName("Topic"));
        body.put("date", date.toString());
        body.put("startsAt", "10:00");
        body.put("endsAt", "13:00");
        body.put("trainerName", "Trainer T");
        body.put("trainerContact", "trainer@example.org");
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("seatLimit", seats);
        body.put("description", "Practice session");
        return body;
    }

    protected Resp createSession(String token, Map<String, Object> body) {
        return post(API + "/sessions", token, body);
    }

    /** A published session; the caller is Admin or Director. */
    protected UUID publishedSession(String token, String type, LocalDate date, Integer seats) {
        Map<String, Object> body = sessionBody(type, date, seats);
        body.put("publish", true);
        Resp resp = createSession(token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID draftSession(String token, String type, LocalDate date) {
        Resp resp = createSession(token, sessionBody(type, date, null));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected Resp session(String token, UUID id) {
        return get(API + "/sessions/" + id, token);
    }

    protected long versionOf(String token, UUID id) {
        return ((Number) session(token, id).map().get("version")).longValue();
    }

    protected Resp register(String token, UUID session) {
        return post(API + "/sessions/" + session + "/registrations", token, Map.of());
    }

    protected Resp registerOnBehalf(String token, UUID session, UUID teacherId) {
        return post(API + "/sessions/" + session + "/registrations", token, Map.of("teacherId", teacherId));
    }

    protected Resp cancelRegistration(String token, UUID session) {
        return delete(API + "/sessions/" + session + "/registrations/me", token);
    }

    protected int registeredCount(UUID session) {
        return jdbc.queryForObject(
                "select count(*) from training_registration where session_id = ? and status = 'REGISTERED'", Integer.class, session);
    }

    /** Writes an assignment row directly (the assignment API is tested on its own). */
    protected void assignRow(UUID session, String kind, UUID person) {
        jdbc.update(
                "insert into training_assignment (id, session_id, person_kind, person_id, source, status, assigned_by, assigned_at, version)"
                        + " values (?, ?, ?, ?, 'INDIVIDUAL', 'REQUIRED', ?, now(), 0) on conflict do nothing",
                UUID.randomUUID(), session, kind, person, UUID.randomUUID());
    }

    protected int notifications(UUID user, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Integer.class, user, type);
    }

    protected void setTeacherStatus(UUID teacherId, String status) {
        jdbc.update("update teacher set status = ? where id = ?", status, teacherId);
    }

    /** Moves a session into the past without going through the API (which would refuse), so "started" paths can be tested. */
    protected void startSessionAlready(UUID session) {
        jdbc.update("update training_session set session_date = ? where id = ?", today().minusDays(1), session);
    }

    /** The body of a list response (a JSON array of objects). */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> list(Resp resp) {
        return (List<Map<String, Object>>) (Object) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(resp.body());
    }

    protected static boolean hasId(List<Map<String, Object>> sessions, UUID id) {
        return sessions.stream().anyMatch(s -> id.toString().equals(s.get("id")));
    }
}
