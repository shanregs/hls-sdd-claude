package com.hls.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.teacher.internal.TeacherStatus;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 005 FR-011: the allowed status transitions and the final exit. */
class TeacherStatusMachineTest {

    private static Set<TeacherStatus> allowedFrom(TeacherStatus from) {
        Set<TeacherStatus> allowed = EnumSet.noneOf(TeacherStatus.class);
        for (TeacherStatus to : TeacherStatus.values()) {
            if (from.canMoveTo(to)) {
                allowed.add(to);
            }
        }
        return allowed;
    }

    @Test
    void inTrainingMovesToActiveOrExitedOnly() {
        assertThat(allowedFrom(TeacherStatus.IN_TRAINING))
                .containsExactlyInAnyOrder(TeacherStatus.ACTIVE, TeacherStatus.EXITED);
    }

    @Test
    void activeAndOnLeaveMoveBetweenEachOtherOrExit() {
        assertThat(allowedFrom(TeacherStatus.ACTIVE))
                .containsExactlyInAnyOrder(TeacherStatus.ON_LEAVE, TeacherStatus.EXITED);
        assertThat(allowedFrom(TeacherStatus.ON_LEAVE))
                .containsExactlyInAnyOrder(TeacherStatus.ACTIVE, TeacherStatus.EXITED);
    }

    @Test
    void exitIsFinal() {
        assertThat(allowedFrom(TeacherStatus.EXITED)).isEmpty();
    }

    @Test
    void noOneMovesBackToTrainingOrStaysInPlace() {
        for (TeacherStatus from : TeacherStatus.values()) {
            assertThat(from.canMoveTo(TeacherStatus.IN_TRAINING)).as("%s -> IN_TRAINING", from).isFalse();
            assertThat(from.canMoveTo(from)).as("%s -> itself", from).isFalse();
        }
    }
}
