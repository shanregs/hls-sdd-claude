package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T026: editing one session, editing the pattern from a month, stopping it, duplicates and holidays. */
class PatternEditTest extends TrainingCalendarTestBase {

    private String status(UUID session) {
        return jdbc.queryForObject("select status from training_session where id = ?", String.class, session);
    }

    private boolean exists(UUID session) {
        return jdbc.queryForObject("select count(*) from training_session where id = ?", Integer.class, session) > 0;
    }

    private String topicOf(UUID session) {
        return jdbc.queryForObject("select topic from training_session where id = ?", String.class, session);
    }

    @Test
    void editingOneGeneratedSessionLeavesThePatternAndOtherMonthsAlone() {
        World w = newWorld();
        Map<String, Object> body = patternBody(today());
        UUID pattern = createPattern(w.admin(), body);
        List<UUID> sessions = patternSessions(pattern);
        UUID second = sessions.get(1);
        Map<String, Object> view = session(w.admin(), second).map();

        Map<String, Object> edit = new HashMap<>();
        edit.put("type", "MONTHLY");
        edit.put("topic", "One-off topic");
        edit.put("date", view.get("date"));
        edit.put("startsAt", view.get("startsAt"));
        edit.put("endsAt", view.get("endsAt"));
        edit.put("venueKind", "PHYSICAL");
        edit.put("venue", "Another room");
        edit.put("version", view.get("version"));
        assertThat(put(API + "/sessions/" + second, w.admin(), edit).status()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select hand_edited from training_session where id = ?", Boolean.class, second)).isTrue();
        assertThat(topicOf(sessions.get(0))).isEqualTo(body.get("topic"));
        assertThat(topicOf(sessions.get(2))).isEqualTo(body.get("topic"));
        assertThat(jdbc.queryForObject("select topic from training_pattern where id = ?", String.class, pattern)).isEqualTo(body.get("topic"));
    }

    @Test
    void editingThePatternFromAMonthRewritesOnlyUntouchedSessionsAndKeepsPeople() {
        World w = newWorld();
        Map<String, Object> body = patternBody(today());
        UUID pattern = createPattern(w.admin(), body);
        List<UUID> sessions = patternSessions(pattern);
        String original = (String) body.get("topic");
        // the third month is hand edited, the fourth has an assignment
        jdbc.update("update training_session set hand_edited = true where id = ?", sessions.get(2));
        assignRow(sessions.get(3), "TEACHER", w.teacherA().teacherId());
        LocalDate from = jdbc.queryForObject("select pattern_month from training_session where id = ?", LocalDate.class, sessions.get(1));

        Map<String, Object> edit = new HashMap<>(body);
        edit.put("topic", "Rewritten topic");
        edit.put("weekday", 7);
        edit.put("fromMonth", from.toString());
        edit.put("version", patternVersion(w.admin(), pattern));
        Resp resp = put(API + "/patterns/" + pattern, w.admin(), edit);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);

        assertThat(topicOf(sessions.get(0))).isEqualTo(original);
        assertThat(topicOf(sessions.get(1))).isEqualTo("Rewritten topic");
        assertThat(topicOf(sessions.get(2))).isEqualTo(original);
        assertThat(topicOf(sessions.get(3))).isEqualTo("Rewritten topic");
        assertThat(jdbc.queryForObject("select extract(isodow from session_date)::int from training_session where id = ?", Integer.class, sessions.get(1)))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from training_assignment where session_id = ?", Integer.class, sessions.get(3))).isEqualTo(1);
    }

    @Test
    void stoppingFromAMonthRemovesUntouchedLaterSessionsAndKeepsHeldAndHandEdited() {
        World w = newWorld();
        UUID pattern = createPattern(w.admin(), patternBody(today()));
        List<UUID> sessions = patternSessions(pattern);
        jdbc.update("update training_session set hand_edited = true where id = ?", sessions.get(2));
        jdbc.update("update training_session set status = 'HELD' where id = ?", sessions.get(3));
        LocalDate from = jdbc.queryForObject("select pattern_month from training_session where id = ?", LocalDate.class, sessions.get(1));

        Resp stopped = post(API + "/patterns/" + pattern + "/stop", w.admin(), Map.of("fromMonth", from.toString()));
        assertThat(stopped.status()).as(stopped.body()).isEqualTo(200);

        assertThat(exists(sessions.get(0))).isTrue();
        assertThat(exists(sessions.get(1))).isFalse();
        assertThat(exists(sessions.get(2))).isTrue();
        assertThat(status(sessions.get(3))).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("select end_month from training_pattern where id = ?", LocalDate.class, pattern))
                .isEqualTo(from.minusMonths(1));
    }

    @Test
    void aDuplicateRuleAndTimeIsRefused() {
        World w = newWorld();
        Map<String, Object> body = patternBody(today());
        createPattern(w.admin(), body);
        assertThat(post(API + "/patterns", w.admin(), body).status()).isEqualTo(409);
    }

    @Test
    void aPatternOnAHolidayStillCreatesTheSessionAndTheDetailCarriesTheNotice() {
        World w = newWorld();
        Map<String, Object> body = patternBody(today());
        body.put("nth", 1);
        body.put("weekday", 7); // a Sunday, not a working day
        UUID pattern = createPattern(w.admin(), body);
        UUID first = patternSessions(pattern).get(0);
        assertThat(session(w.admin(), first).map()).containsEntry("holidayNotice", true);
    }

    @Test
    void onlyAdminAndDirectorWritePatterns() {
        World w = newWorld();
        Map<String, Object> body = patternBody(today());
        assertThat(post(API + "/patterns", w.managerA().token(), body).status()).isEqualTo(403);
        assertThat(post(API + "/patterns", w.teacherA().token(), body).status()).isEqualTo(403);
        assertThat(post(API + "/patterns/preview", w.director(), body).status()).isEqualTo(200);
        assertThat(get(API + "/patterns", w.managerA().token()).status()).isEqualTo(200); // Managers hold TRAINING View
        assertThat(get(API + "/patterns", w.teacherA().token()).status()).isEqualTo(403);
    }
}
