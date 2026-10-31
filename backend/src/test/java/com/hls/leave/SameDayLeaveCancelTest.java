package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 040 FR-016: same-day leave can be cancelled on its own day; the marks and the substitution requirement go. */
class SameDayLeaveCancelTest extends LeaveTestBase {

    private void requireWorkingToday(World w) {
        Resp preview = post(ME + "/preview", w.teacherA().token(), draft(today(), today()));
        assumeTrue(preview.status() == 200 && preview.body().contains("\"problems\":[]"), "today is not a working day");
    }

    @Test
    void theTeacherCancelsTheirOwnSameDayLeaveAndItsEffectsAreWithdrawn() {
        World w = newWorld();
        requireWorkingToday(w);
        String id = apply(w.teacherA(), today(), today());

        Resp cancelled = post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(markRow(w.teacherA().teacherId(), today())).isNull();
        assertThat(jdbc.queryForObject(
                        "select status from substitution_requirement where teacher_id = ? and duty_date = ?",
                        String.class,
                        w.teacherA().teacherId(),
                        today()))
                .isEqualTo("CANCELLED");
    }

    @Test
    void theTeacherCanCancelLeaveAManagerRecordedForThem() {
        World w = newWorld();
        requireWorkingToday(w);
        Map<String, Object> body = new HashMap<>(draft(today(), today()));
        body.put("teacherId", w.teacherA().teacherId().toString());
        Resp recorded = post(SUP + "/same-day", w.managerA().token(), body);
        assertThat(recorded.status()).as(recorded.body()).isEqualTo(201);

        Resp cancelled = post(ME + "/" + field(recorded.body(), "id") + "/cancel", w.teacherA().token(), Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
    }

    @Test
    void aSupervisorStillRevokesSameDayLeave() {
        World w = newWorld();
        requireWorkingToday(w);
        String id = apply(w.teacherA(), today(), today());

        Resp revoked = post(SUP + "/" + id + "/revoke", w.managerA().token(), Map.of("reason", "Came in", "version", 0));

        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        assertThat(markRow(w.teacherA().teacherId(), today())).isNull();
    }
}
