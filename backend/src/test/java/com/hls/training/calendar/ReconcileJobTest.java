package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.training.calendar.internal.ReconcileJob;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T038: people who have left are taken off sessions that have not been held. */
class ReconcileJobTest extends TrainingCalendarTestBase {

    @Autowired
    ReconcileJob job;

    private String assignmentStatus(UUID session, UUID person) {
        return jdbc.queryForObject(
                "select status from training_assignment where session_id = ? and person_id = ?", String.class, session, person);
    }

    @Test
    void anExitedTeacherIsRemovedFromNotHeldSessionsOnly() {
        World w = newWorld();
        UUID open = publishedSession(w.admin(), "WEEKEND", in(10), 5);
        UUID held = publishedSession(w.admin(), "MONTHLY", in(12), null);
        for (UUID s : new UUID[] {open, held}) {
            assignRow(s, "TEACHER", w.teacherA().teacherId());
            assignRow(s, "TEACHER", w.teacherB().teacherId());
        }
        assertThat(register(w.teacherB().token(), open).status()).isEqualTo(201);
        jdbc.update("update training_session set status = 'HELD' where id = ?", held);
        int before = jdbc.queryForObject("select count(*) from notification where type = 'TRAINING_CANNOT_ATTEND'", Integer.class);

        setTeacherStatus(w.teacherB().teacherId(), "EXITED");
        job.run();

        assertThat(assignmentStatus(open, w.teacherB().teacherId())).isEqualTo("REMOVED");
        assertThat(assignmentStatus(open, w.teacherA().teacherId())).isEqualTo("REQUIRED");
        assertThat(assignmentStatus(held, w.teacherB().teacherId())).isEqualTo("REQUIRED");
        assertThat(registeredCount(open)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'TRAINING_CANNOT_ATTEND'", Integer.class))
                .isGreaterThan(before);
        // running again finds nothing more to do
        assertThat(job.run()).isZero();
    }
}
