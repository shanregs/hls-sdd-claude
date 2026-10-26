package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T060: what a Teacher and a Manager are offered under Upcoming Training. */
class UpcomingTrainingTest extends TrainingCalendarTestBase {

    private List<Map<String, Object>> upcoming(String token) {
        Resp resp = get(API + "/me/upcoming", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return list(resp);
    }

    @SuppressWarnings("unchecked")
    private static String idOf(Map<String, Object> item) {
        return (String) ((Map<String, Object>) item.get("session")).get("id");
    }

    private boolean has(List<Map<String, Object>> items, UUID session, String label) {
        return items.stream().anyMatch(i -> idOf(i).equals(session.toString()) && label.equals(i.get("label")));
    }

    @Test
    void aTeacherSeesRequiredAndOpenSessionsInDateOrder() {
        World w = newWorld();
        UUID later = publishedSession(w.admin(), "WEEKEND", in(30), 5);
        UUID required = publishedSession(w.admin(), "MONTHLY", in(10), null);
        UUID open = publishedSession(w.admin(), "WEEKEND", in(20), 5);
        UUID notMine = publishedSession(w.admin(), "MONTHLY", in(12), null);
        assignRow(required, "TEACHER", w.teacherA().teacherId());
        assignRow(notMine, "TEACHER", w.teacherB().teacherId());

        List<Map<String, Object>> items = upcoming(w.teacherA().token());
        assertThat(has(items, required, "REQUIRED")).isTrue();
        assertThat(has(items, open, "OPEN")).isTrue();
        assertThat(has(items, later, "OPEN")).isTrue();
        assertThat(items.stream().anyMatch(i -> idOf(i).equals(notMine.toString()))).isFalse();
        List<String> order = items.stream().map(UpcomingTrainingTest::idOf).filter(id -> List.of(required.toString(), open.toString(), later.toString()).contains(id)).toList();
        assertThat(order).containsExactly(required.toString(), open.toString(), later.toString());
    }

    @Test
    void aFullOrStartedOpenSessionIsNotOffered() {
        World w = newWorld();
        UUID full = publishedSession(w.admin(), "WEEKEND", in(10), 1);
        UUID started = publishedSession(w.admin(), "WEEKEND", in(11), 5);
        registerRow(full, w.teacherB().teacherId());
        startSessionAlready(started);

        List<Map<String, Object>> items = upcoming(w.teacherA().token());
        assertThat(items.stream().anyMatch(i -> idOf(i).equals(full.toString()))).isFalse();
        assertThat(items.stream().anyMatch(i -> idOf(i).equals(started.toString()))).isFalse();
    }

    @Test
    void aManagerSeesOnlyTheSessionsTheyAreRequiredAt() {
        World w = newWorld();
        UUID required = publishedSession(w.admin(), "MONTHLY", in(10), null);
        UUID open = publishedSession(w.admin(), "WEEKEND", in(11), 5);
        UUID theirTeacher = publishedSession(w.admin(), "MONTHLY", in(12), null);
        assignRow(required, "MANAGER", w.managerA().managerId());
        assignRow(theirTeacher, "TEACHER", w.teacherA().teacherId());

        List<Map<String, Object>> items = upcoming(w.managerA().token());
        assertThat(has(items, required, "REQUIRED")).isTrue();
        assertThat(items.stream().anyMatch(i -> idOf(i).equals(open.toString()))).isFalse();
        assertThat(items.stream().anyMatch(i -> idOf(i).equals(theirTeacher.toString()))).isFalse();
    }

    @Test
    void staffWithoutARegistrationOrViewGrantAreRefused() {
        World w = newWorld();
        assertThat(get(API + "/me/upcoming", null).status()).isEqualTo(401);
        assertThat(get(API + "/me/upcoming", signInAs(com.hls.identity.user.Role.SYSTEM).token()).status()).isEqualTo(403);
    }
}
