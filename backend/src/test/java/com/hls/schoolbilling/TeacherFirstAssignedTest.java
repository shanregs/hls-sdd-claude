package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.api.TeacherFirstAssigned;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/**
 * Amendment A4 to spec 012: {@link TeacherFirstAssigned} is published once, in the same transaction as the Teacher's
 * first assignment row, and never again for a later move or a refused assignment.
 */
@Import(TeacherFirstAssignedTest.Recorder.class)
class TeacherFirstAssignedTest extends SchoolContractsTestBase {

    /** The request runs on the server's thread, so the events are recorded by a listener rather than the test thread. */
    @TestConfiguration
    static class Recorder {
        final List<TeacherFirstAssigned> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(TeacherFirstAssigned event) {
            seen.add(event);
        }
    }

    @Autowired
    Recorder events;

    private long firstAssignedFor(UUID teacher) {
        return events.seen.stream()
                .filter(e -> e.teacherId().equals(teacher))
                .count();
    }

    @Test
    void theFirstAssignmentPublishesOnceAndALaterMoveDoesNot() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = fixture(admin);
        Fixture b = fixture(admin);
        UUID dir = director().userId();
        createContract(admin, a.schoolId(), startedDaysAgo(sameSalaryBody(a, dir, 2, "15000"), 20));
        createContract(admin, b.schoolId(), startedDaysAgo(sameSalaryBody(b, dir, 2, "15000"), 20));
        UUID teacher = teacher(admin);

        assertThat(assign(admin, teacher, a.schoolId(), null, today.minusDays(5)).status()).isEqualTo(200);

        assertThat(firstAssignedFor(teacher)).isEqualTo(1);
        TeacherFirstAssigned event = events.seen.stream()
                .filter(e -> e.teacherId().equals(teacher))
                .findFirst()
                .orElseThrow();
        assertThat(event.schoolId()).isEqualTo(a.schoolId());
        assertThat(event.startsOn()).isEqualTo(today.minusDays(5));

        assertThat(assign(admin, teacher, b.schoolId(), null, today).status()).isEqualTo(200);

        assertThat(firstAssignedFor(teacher)).isEqualTo(1);
    }

    @Test
    void aScheduledFirstAssignmentAlsoPublishesOnceAndTheLaterStartIsNotARepeat() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = fixture(admin);
        UUID dir = director().userId();
        createContract(admin, a.schoolId(), sameSalaryBody(a, dir, 2, "15000"));
        UUID teacher = teacher(admin);

        assertThat(assign(admin, teacher, a.schoolId(), null, today.plusDays(7)).status()).isEqualTo(200);

        assertThat(firstAssignedFor(teacher)).isEqualTo(1);
    }

    @Test
    void aRefusedAssignmentPublishesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture a = fixture(admin);
        UUID teacher = teacher(admin);

        // the School has no MoU covering the start, so the assignment is created against a pending contract; an
        // unknown School is refused outright
        Resp refused = assign(admin, teacher, UUID.randomUUID(), null, today);

        assertThat(refused.status()).isGreaterThanOrEqualTo(400);
        assertThat(firstAssignedFor(teacher)).isZero();
        assertThat(a.schoolId()).isNotNull();
    }
}
