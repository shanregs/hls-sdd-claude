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

    private static final java.util.concurrent.atomic.AtomicInteger MINUTES = new java.util.concurrent.atomic.AtomicInteger(1);

    /** A second Saturday pattern at a start time no other test uses (the same rule and time is refused as a duplicate). */
    protected Map<String, Object> patternBody(LocalDate startMonth) {
        int n = MINUTES.getAndIncrement() + (int) (System.nanoTime() % 600);
        java.time.LocalTime start = java.time.LocalTime.of(6 + (n / 60) % 12, n % 60);
        Map<String, Object> body = new HashMap<>();
        body.put("ruleKind", "NTH_WEEKDAY");
        body.put("nth", 2);
        body.put("weekday", 6);
        body.put("startsAt", start.toString());
        body.put("endsAt", start.plusMinutes(90).toString());
        body.put("topic", uniqueName("Monthly"));
        body.put("venueKind", "PHYSICAL");
        body.put("venue", "Head office");
        body.put("startMonth", startMonth.withDayOfMonth(1).toString());
        return body;
    }

    protected UUID createPattern(String token, Map<String, Object> body) {
        Resp resp = post(API + "/patterns", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    /** The session ids of a pattern, oldest month first. */
    protected List<UUID> patternSessions(UUID pattern) {
        return jdbc.queryForList(
                "select id from training_session where pattern_id = ? order by pattern_month", UUID.class, pattern);
    }

    protected long patternVersion(String token, UUID pattern) {
        return list(get(API + "/patterns", token)).stream()
                .filter(p -> pattern.toString().equals(p.get("id")))
                .map(p -> ((Number) p.get("version")).longValue())
                .findFirst()
                .orElseThrow();
    }

    /** An ACTIVE Teacher (no login) placed at the School since a month ago. */
    protected UUID placedTeacher(String admin, UUID school) {
        UUID teacher = teacher(admin);
        assertThat(placeTeacherRaw(admin, teacher, school, today().minusDays(30)).status()).isEqualTo(200);
        return teacher;
    }

    /** Registers a Teacher directly in the database (the registration API is tested on its own). */
    protected void registerRow(UUID session, UUID teacher) {
        jdbc.update(
                "insert into training_registration (id, session_id, teacher_id, status, registered_by, registered_at, version)"
                        + " values (?, ?, ?, 'REGISTERED', ?, now(), 0)",
                UUID.randomUUID(), session, teacher, UUID.randomUUID());
    }

    protected Map<String, Object> row(String kind, UUID person, String outcome, Double dayValue, String reason) {
        Map<String, Object> row = new HashMap<>();
        row.put("personKind", kind);
        row.put("personId", person);
        row.put("outcome", outcome);
        row.put("dayValue", dayValue);
        row.put("reason", reason);
        return row;
    }

    protected Resp saveAttendance(String token, UUID session, Map<String, Object>... rows) {
        return put(API + "/sessions/" + session + "/attendance", token, Map.of("rows", List.of(rows)));
    }

    /** The status of one person's row in a save response. */
    @SuppressWarnings("unchecked")
    protected static String resultOf(Resp resp, UUID person) {
        return ((List<Map<String, Object>>) resp.map().get("results")).stream()
                .filter(r -> person.toString().equals(r.get("personId")))
                .map(r -> (String) r.get("status") + (r.get("code") == null ? "" : ":" + r.get("code")))
                .findFirst()
                .orElseThrow();
    }

    /** The 008 training-day value for a Teacher on a date, or null when the day has no training mark. */
    protected java.math.BigDecimal trainingDay(UUID teacher, LocalDate date) {
        List<java.math.BigDecimal> found = jdbc.queryForList(
                "select m.day_value from attendance_mark m join attendance_status_code c on c.id = m.status_code_id"
                        + " where m.teacher_id = ? and m.mark_date = ? and c.short_code = 'T'",
                java.math.BigDecimal.class, teacher, date);
        return found.isEmpty() ? null : found.get(0);
    }

    protected void lockMonth(UUID teacher, LocalDate date) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked, days_leave,"
                        + " training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 0, 0, 0, 0, 0, 0, 0, now(), 0)"
                        + " on conflict (teacher_id, year_month) do update set state = 'LOCKED'",
                UUID.randomUUID(), teacher, java.time.YearMonth.from(date).toString());
    }
}
