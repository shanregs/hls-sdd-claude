package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T017: the session endpoints by role, the move rules, stale versions and the Change History (SC-009). */
class SessionApiTest extends TrainingCalendarTestBase {

    @Test
    void adminAndDirectorCreateEditPublishAndDeleteADraft() {
        World w = newWorld();
        for (String token : List.of(w.admin(), w.director())) {
            Map<String, Object> body = sessionBody("MONTHLY", in(20), null);
            Resp created = createSession(token, body);
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            assertThat(created.map()).containsEntry("status", "DRAFT").containsEntry("type", "MONTHLY");
            UUID id = created.id();

            Map<String, Object> edit = sessionBody("MONTHLY", in(21), null);
            edit.put("topic", "Edited topic");
            edit.put("version", versionOf(token, id));
            Resp edited = put(API + "/sessions/" + id, token, edit);
            assertThat(edited.status()).as(edited.body()).isEqualTo(200);
            assertThat(edited.map()).containsEntry("topic", "Edited topic").containsEntry("date", in(21).toString());

            Resp published = post(API + "/sessions/" + id + "/publish", token, Map.of("version", versionOf(token, id)));
            assertThat(published.status()).as(published.body()).isEqualTo(200);
            assertThat(published.map()).containsEntry("status", "PUBLISHED");
            // a published session is no longer deletable
            assertThat(delete(API + "/sessions/" + id, token).status()).isEqualTo(409);

            UUID draft = draftSession(token, "WEEKEND", in(30));
            assertThat(delete(API + "/sessions/" + draft, token).status()).isEqualTo(204);
            assertThat(session(token, draft).status()).isEqualTo(404);
        }
    }

    @Test
    void managersAndTeachersCannotWriteAndSystemSeesNothing() {
        World w = newWorld();
        UUID id = publishedSession(w.admin(), "WEEKEND", in(10), 5);
        Map<String, Object> body = sessionBody("WEEKEND", in(10), 5);
        String manager = w.managerA().token();
        String teacher = w.teacherA().token();
        String system = signInAs(Role.SYSTEM).token();

        for (String token : List.of(manager, teacher, system)) {
            assertThat(createSession(token, body).status()).isEqualTo(403);
            body.put("version", 0);
            assertThat(put(API + "/sessions/" + id, token, body).status()).isEqualTo(403);
            assertThat(post(API + "/sessions/" + id + "/publish", token, Map.of("version", 0)).status()).isEqualTo(403);
            assertThat(post(API + "/sessions/" + id + "/cancel", token, Map.of("reason", "x", "version", 0)).status()).isEqualTo(403);
            assertThat(delete(API + "/sessions/" + id, token).status()).isEqualTo(403);
        }
        assertThat(get(API + "/sessions", system).status()).isEqualTo(403);
        assertThat(get(API + "/sessions", null).status()).isEqualTo(401);
    }

    @Test
    void movingNeedsAReasonKeepsRegistrationsAndKeepsTheEarlierDate() {
        World w = newWorld();
        TeacherCtx teacher = newTeacher(w.admin(), w.schoolA(), 60);
        UUID id = publishedSession(w.admin(), "WEEKEND", in(10), 5);
        assertThat(register(teacher.token(), id).status()).isEqualTo(201);

        Map<String, Object> move = sessionBody("WEEKEND", in(17), 5);
        move.put("topic", session(w.admin(), id).map().get("topic"));
        move.put("version", versionOf(w.admin(), id));
        Resp noReason = put(API + "/sessions/" + id, w.admin(), move);
        assertThat(noReason.status()).isEqualTo(400);
        assertThat(noReason.body()).contains("reason");

        move.put("reason", "Venue not available");
        Resp moved = put(API + "/sessions/" + id, w.admin(), move);
        assertThat(moved.status()).as(moved.body()).isEqualTo(200);
        assertThat(moved.map()).containsEntry("date", in(17).toString());
        assertThat(registeredCount(id)).isEqualTo(1);
        assertThat(notifications(teacher.signed().userId(), "TRAINING_CHANGED")).isEqualTo(1);

        Map<String, Object> detail = session(w.admin(), id).map();
        assertThat(detail.get("earlierDates").toString()).contains(in(10).toString());
        assertThat(detail.get("history").toString()).contains("Venue not available");
        assertThat(jdbc.queryForObject(
                        "select before_value from training_session_history where session_id = ? and field = 'date'", String.class, id))
                .isEqualTo(in(10).toString());
    }

    @Test
    void aTrainerChangeKeepsTheEarlierTrainerInHistory() {
        World w = newWorld();
        UUID id = publishedSession(w.admin(), "MONTHLY", in(10), null);
        Map<String, Object> edit = sessionBody("MONTHLY", in(10), null);
        edit.put("topic", session(w.admin(), id).map().get("topic"));
        edit.put("trainerName", "New Trainer");
        edit.put("version", versionOf(w.admin(), id));
        assertThat(put(API + "/sessions/" + id, w.admin(), edit).status()).isEqualTo(200);

        assertThat(jdbc.queryForObject(
                        "select before_value from training_session_history where session_id = ? and field = 'trainer'", String.class, id))
                .isEqualTo("Trainer T");
    }

    @Test
    void cancellingNeedsAReasonAndTellsTheRegisteredPeople() {
        World w = newWorld();
        TeacherCtx teacher = newTeacher(w.admin(), w.schoolA(), 60);
        UUID id = publishedSession(w.admin(), "WEEKEND", in(10), 5);
        register(teacher.token(), id);

        Resp noReason = post(API + "/sessions/" + id + "/cancel", w.director(), Map.of("version", versionOf(w.admin(), id)));
        assertThat(noReason.status()).isEqualTo(400);

        Resp cancelled = post(API + "/sessions/" + id + "/cancel", w.director(), Map.of("reason", "Trainer unwell", "version", versionOf(w.admin(), id)));
        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(cancelled.map()).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "Trainer unwell");
        assertThat(notifications(teacher.signed().userId(), "TRAINING_CHANGED")).isEqualTo(1);
        // registrations are kept as history and a cancelled session is final
        assertThat(registeredCount(id)).isEqualTo(1);
        Map<String, Object> edit = sessionBody("WEEKEND", in(10), 5);
        edit.put("version", versionOf(w.admin(), id));
        assertThat(put(API + "/sessions/" + id, w.admin(), edit).status()).isEqualTo(409);
        assertThat(post(API + "/sessions/" + id + "/cancel", w.admin(), Map.of("reason", "again", "version", versionOf(w.admin(), id))).status()).isEqualTo(409);
    }

    @Test
    void aStaleVersionIsRefused() {
        World w = newWorld();
        UUID id = draftSession(w.admin(), "MONTHLY", in(10));
        long version = versionOf(w.admin(), id);
        Map<String, Object> edit = sessionBody("MONTHLY", in(10), null);
        edit.put("version", version);
        assertThat(put(API + "/sessions/" + id, w.admin(), edit).status()).isEqualTo(200);

        Resp stale = put(API + "/sessions/" + id, w.director(), edit);
        assertThat(stale.status()).isEqualTo(409);
        edit.remove("version");
        assertThat(put(API + "/sessions/" + id, w.director(), edit).status()).isEqualTo(400);
    }

    @Test
    void invalidInputIsRefusedWithAClearMessage() {
        World w = newWorld();
        Map<String, Object> body = sessionBody("MONTHLY", in(10), null);
        body.put("endsAt", "09:00");
        assertThat(createSession(w.admin(), body).status()).isEqualTo(400);

        body = sessionBody("MONTHLY", in(10), null);
        body.put("venueKind", "VIRTUAL");
        body.put("venue", "http://insecure.example.org");
        assertThat(createSession(w.admin(), body).status()).isEqualTo(400);

        body = sessionBody("MONTHLY", in(10), null);
        body.put("topic", "x".repeat(201));
        assertThat(createSession(w.admin(), body).status()).isEqualTo(400);

        body = sessionBody("OTHER", in(10), null);
        assertThat(createSession(w.admin(), body).status()).isEqualTo(400);

        body = sessionBody("WEEKEND", in(10), 0);
        assertThat(createSession(w.admin(), body).status()).isEqualTo(400);

        body = sessionBody("WEEKEND", in(10), 4);
        body.put("venueKind", "VIRTUAL");
        body.put("venue", "https://meet.example.org/room");
        assertThat(createSession(w.admin(), body).status()).isEqualTo(201);
    }

    @Test
    void aSeatLimitCannotDropBelowTheRegisteredPeople() {
        World w = newWorld();
        UUID id = publishedSession(w.admin(), "WEEKEND", in(10), 3);
        register(newTeacher(w.admin(), w.schoolA(), 60).token(), id);
        register(newTeacher(w.admin(), w.schoolA(), 60).token(), id);

        Map<String, Object> edit = sessionBody("WEEKEND", in(10), 1);
        edit.put("topic", session(w.admin(), id).map().get("topic"));
        edit.put("version", versionOf(w.admin(), id));
        assertThat(put(API + "/sessions/" + id, w.admin(), edit).status()).isEqualTo(409);
        edit.put("seatLimit", 2);
        assertThat(put(API + "/sessions/" + id, w.admin(), edit).status()).isEqualTo(200);
    }

    @Test
    void everyWriteProducesAChangeHistoryEntry() {
        World w = newWorld();
        UUID id = draftSession(w.admin(), "MONTHLY", in(10));
        assertChangeRecorded(w.admin(), "TRAINING_SESSION", id, "created");

        post(API + "/sessions/" + id + "/publish", w.admin(), Map.of("version", versionOf(w.admin(), id)));
        assertChangeRecorded(w.admin(), "TRAINING_SESSION", id, "status");

        Map<String, Object> move = sessionBody("MONTHLY", in(14), null);
        move.put("topic", session(w.admin(), id).map().get("topic"));
        move.put("reason", "Clash with exams");
        move.put("version", versionOf(w.admin(), id));
        assertThat(put(API + "/sessions/" + id, w.admin(), move).status()).isEqualTo(200);
        assertChangeRecorded(w.admin(), "TRAINING_SESSION", id, "date");

        UUID draft = draftSession(w.admin(), "WEEKEND", in(40));
        delete(API + "/sessions/" + draft, w.admin());
        assertChangeRecorded(w.admin(), "TRAINING_SESSION", draft, "deleted");
    }

    @Test
    void theListFiltersByDateTypeAndStatus() {
        World w = newWorld();
        LocalDate day = in(400);
        UUID weekend = publishedSession(w.admin(), "WEEKEND", day, 3);
        UUID monthly = publishedSession(w.admin(), "MONTHLY", day.plusDays(1), null);
        UUID draft = draftSession(w.admin(), "MONTHLY", day.plusDays(2));

        var all = list(get(API + "/sessions?from=" + day + "&to=" + day.plusDays(5), w.admin()));
        assertThat(hasId(all, weekend) && hasId(all, monthly) && hasId(all, draft)).isTrue();
        var weekends = list(get(API + "/sessions?from=" + day + "&to=" + day.plusDays(5) + "&type=WEEKEND", w.admin()));
        assertThat(hasId(weekends, weekend)).isTrue();
        assertThat(hasId(weekends, monthly)).isFalse();
        var drafts = list(get(API + "/sessions?from=" + day + "&to=" + day.plusDays(5) + "&status=DRAFT", w.director()));
        assertThat(drafts).hasSize(1);
        assertThat(hasId(drafts, draft)).isTrue();
        assertThat(get(API + "/sessions?from=" + day + "&to=" + day.minusDays(1), w.admin()).status()).isEqualTo(400);
    }
}
