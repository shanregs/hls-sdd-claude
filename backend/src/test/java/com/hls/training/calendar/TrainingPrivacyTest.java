package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/** Spec 024 T082: nothing private reaches a Teacher, no pay field exists anywhere (FR-030), text is safe, and links are https. */
class TrainingPrivacyTest extends TrainingCalendarTestBase {

    private static final Pattern PAY_WORD = Pattern.compile("pay|stipend|salary|payroll|amount|wage|cost", Pattern.CASE_INSENSITIVE);

    /** Every key of every object in a JSON body. */
    @SuppressWarnings("unchecked")
    private static List<String> keysOf(String json) {
        List<String> keys = new ArrayList<>();
        Object parsed = json.trim().startsWith("[")
                ? JsonParserFactory.getJsonParser().parseList(json)
                : JsonParserFactory.getJsonParser().parseMap(json);
        walk(parsed, keys);
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, List<String> keys) {
        if (node instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).forEach((k, v) -> {
                keys.add(k);
                walk(v, keys);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(v -> walk(v, keys));
        }
    }

    private UUID recorded(World w, String topic, String remark) {
        Map<String, Object> body = sessionBody("MONTHLY", today(), null);
        body.put("topic", topic);
        body.put("publish", true);
        Resp created = createSession(w.admin(), body);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        UUID session = created.id();
        assignRow(session, "TEACHER", w.teacherA().teacherId());
        Map<String, Object> row = row("TEACHER", w.teacherA().teacherId(), "ABSENT", null, "Did not come");
        row.put("internalRemark", remark);
        row.put("certificateNote", "Please attend the next one");
        assertThat(saveAttendance(w.admin(), session, row).status()).isEqualTo(200);
        return session;
    }

    @Test
    void noResponseToATeacherCarriesRemarksExceptionsOrOtherPeoplesRows() {
        World w = newWorld();
        String secret = "SECRET-REMARK-" + UUID.randomUUID();
        UUID session = recorded(w, uniqueName("Privacy"), secret);
        UUID other = placedTeacher(w.admin(), w.schoolA());
        assignRow(session, "TEACHER", other);
        String teacher = w.teacherA().token();

        List<Resp> responses = List.of(
                session(teacher, session),
                get(API + "/sessions?from=" + today() + "&to=" + today(), teacher),
                get(API + "/me/upcoming", teacher),
                get(API + "/me/history?year=" + today().getYear(), teacher));
        for (Resp r : responses) {
            assertThat(r.status()).as(r.body()).isEqualTo(200);
            assertThat(r.body()).doesNotContain(secret).doesNotContain(other.toString());
        }
        // the Teacher's own history shows no exception and no remark, only the certificate note
        assertThat(responses.get(3).body()).contains("Please attend the next one");
        assertThat(session(teacher, session).map().get("counts")).isNull();
        // and the routes that would carry them are closed
        assertThat(get(API + "/exceptions", teacher).status()).isEqualTo(403);
        assertThat(get(API + "/sessions/" + session + "/attendance", teacher).status()).isEqualTo(403);
        assertThat(get(API + "/sessions/" + session + "/assignments", teacher).status()).isEqualTo(403);
        assertThat(get(API + "/sessions/" + session + "/registrations", teacher).status()).isEqualTo(403);
    }

    @Test
    void noResponseAnywhereHasAPayStipendOrPayrollField() {
        World w = newWorld();
        UUID session = recorded(w, uniqueName("NoPay"), "remark");
        createPattern(w.admin(), patternBody(java.time.LocalDate.of(2041, 1, 1)));
        List<String> bodies = new ArrayList<>();
        for (String path : List.of(
                "/sessions?from=" + today() + "&to=" + today(),
                "/sessions/" + session,
                "/sessions/" + session + "/attendance",
                "/sessions/" + session + "/assignments",
                "/sessions/" + session + "/registrations",
                "/patterns",
                "/exceptions",
                "/people/TEACHER/" + w.teacherA().teacherId() + "/history",
                "/me/upcoming")) {
            Resp r = get(API + path, w.admin());
            assertThat(r.status()).as(path + " " + r.body()).isEqualTo(200);
            bodies.add(r.body());
        }
        for (String body : bodies) {
            assertThat(keysOf(body)).as(body).noneMatch(k -> PAY_WORD.matcher(k).find());
        }
    }

    @Test
    void textIsStoredAsDataAndSpreadsheetFormulasAreDefusedInTheCsv() {
        World w = newWorld();
        String script = "<script>alert(1)</script>";
        UUID session = recorded(w, "=HYPERLINK(\"http://x\")" + script, "remark");
        Resp detail = session(w.admin(), session);
        // JSON carries the characters as data (escaped by the client when shown); nothing is executed server side
        assertThat(detail.map().get("topic")).isEqualTo("=HYPERLINK(\"http://x\")" + script);
        Resp csv = get(API + "/people/TEACHER/" + w.teacherA().teacherId() + "/history/export?year=" + today().getYear(), w.admin());
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body().toLowerCase(Locale.ROOT)).contains("\"'=hyperlink");
    }

    @Test
    void aVirtualVenueMustBeAnHttpsLink() {
        World w = newWorld();
        for (String bad : List.of("http://meet.example.org/x", "ftp://meet.example.org/x", "javascript:alert(1)", "meet.example.org")) {
            Map<String, Object> body = sessionBody("MONTHLY", in(10), null);
            body.put("venueKind", "VIRTUAL");
            body.put("venue", bad);
            assertThat(createSession(w.admin(), body).status()).as(bad).isEqualTo(400);
        }
        Map<String, Object> good = sessionBody("MONTHLY", in(10), null);
        good.put("venueKind", "VIRTUAL");
        good.put("venue", "https://meet.example.org/room");
        assertThat(createSession(w.admin(), good).status()).isEqualTo(201);

        Map<String, Object> pattern = new HashMap<>(patternBody(java.time.LocalDate.of(2041, 1, 1)));
        pattern.put("venueKind", "VIRTUAL");
        pattern.put("venue", "http://meet.example.org/x");
        assertThat(post(API + "/patterns", w.admin(), pattern).status()).isEqualTo(400);
    }
}
