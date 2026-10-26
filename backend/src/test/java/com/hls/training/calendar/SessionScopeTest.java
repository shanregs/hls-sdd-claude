package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 024 T018 (SC-006): who sees which session. */
class SessionScopeTest extends TrainingCalendarTestBase {

    private String range(int fromDays, int toDays) {
        return "?from=" + in(fromDays) + "&to=" + in(toDays);
    }

    @Test
    void aTeacherSeesOnlyOpenSessionsAndTheirOwn() {
        World w = newWorld();
        UUID open = publishedSession(w.admin(), "WEEKEND", in(300), 5);
        UUID draft = draftSession(w.admin(), "WEEKEND", in(301));
        UUID monthlyForOther = publishedSession(w.admin(), "MONTHLY", in(302), null);
        UUID monthlyForMe = publishedSession(w.admin(), "MONTHLY", in(303), null);
        assignRow(monthlyForOther, "TEACHER", w.teacherB().teacherId());
        assignRow(monthlyForMe, "TEACHER", w.teacherA().teacherId());

        var seen = list(get(API + "/sessions" + range(299, 305), w.teacherA().token()));

        assertThat(hasId(seen, open)).isTrue();
        assertThat(hasId(seen, monthlyForMe)).isTrue();
        assertThat(hasId(seen, draft)).isFalse();
        assertThat(hasId(seen, monthlyForOther)).isFalse();
        assertThat(session(w.teacherA().token(), monthlyForOther).status()).isEqualTo(404);
        assertThat(session(w.teacherA().token(), draft).status()).isEqualTo(404);
        assertThat(session(w.teacherA().token(), open).status()).isEqualTo(200);
        // a Teacher's view carries no counts and no roster
        assertThat(session(w.teacherA().token(), monthlyForMe).map().get("counts")).isNull();
    }

    @Test
    void aManagerSeesOnlySessionsThatIncludeTheirTeachersOrThemselves() {
        World w = newWorld();
        UUID forA = publishedSession(w.admin(), "MONTHLY", in(310), null);
        UUID forB = publishedSession(w.admin(), "MONTHLY", in(311), null);
        UUID managerSelf = publishedSession(w.admin(), "MONTHLY", in(312), null);
        UUID weekendWithA = publishedSession(w.admin(), "WEEKEND", in(313), 5);
        UUID nobody = publishedSession(w.admin(), "MONTHLY", in(314), null);
        UUID draftForA = draftSession(w.admin(), "MONTHLY", in(315));
        assignRow(forA, "TEACHER", w.teacherA().teacherId());
        assignRow(forB, "TEACHER", w.teacherB().teacherId());
        assignRow(managerSelf, "MANAGER", w.managerA().managerId());
        assignRow(draftForA, "TEACHER", w.teacherA().teacherId());
        register(w.teacherA().token(), weekendWithA);

        var seenByA = list(get(API + "/sessions" + range(309, 316), w.managerA().token()));

        assertThat(hasId(seenByA, forA)).isTrue();
        assertThat(hasId(seenByA, managerSelf)).isTrue();
        assertThat(hasId(seenByA, weekendWithA)).isTrue();
        assertThat(hasId(seenByA, forB)).isFalse();
        assertThat(hasId(seenByA, nobody)).isFalse();
        assertThat(hasId(seenByA, draftForA)).isFalse();

        var seenByB = list(get(API + "/sessions" + range(309, 316), w.managerB().token()));
        assertThat(hasId(seenByB, forB)).isTrue();
        assertThat(hasId(seenByB, forA)).isFalse();
        assertThat(hasId(seenByB, weekendWithA)).isFalse();
    }

    @Test
    void anotherManagersSessionAnswersNotFound() {
        World w = newWorld();
        UUID forB = publishedSession(w.admin(), "MONTHLY", in(320), null);
        assignRow(forB, "TEACHER", w.teacherB().teacherId());

        assertThat(session(w.managerA().token(), forB).status()).isEqualTo(404);
        assertThat(session(w.managerB().token(), forB).status()).isEqualTo(200);
        assertThat(session(w.director(), forB).status()).isEqualTo(200);
        assertThat(get(API + "/sessions/" + UUID.randomUUID(), w.admin()).status()).isEqualTo(404);
    }

    @Test
    void personIdFilterIsForAdminAndDirectorOnly() {
        World w = newWorld();
        UUID forA = publishedSession(w.admin(), "MONTHLY", in(330), null);
        UUID other = publishedSession(w.admin(), "MONTHLY", in(331), null);
        assignRow(forA, "TEACHER", w.teacherA().teacherId());

        var filtered = list(get(API + "/sessions" + range(329, 332) + "&personId=" + w.teacherA().teacherId(), w.admin()));
        assertThat(hasId(filtered, forA)).isTrue();
        assertThat(hasId(filtered, other)).isFalse();
        assertThat(get(API + "/sessions" + range(329, 332) + "&personId=" + w.teacherA().teacherId(), w.managerA().token()).status())
                .isEqualTo(403);
    }
}
