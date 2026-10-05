package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 009 US2: a Teacher's own history, and cancelling a Pending request. */
class LeaveHistoryTest extends LeaveTestBase {

    @Test
    void aTeacherSeesOnlyTheirOwnRequestsNewestFirst() {
        World w = newWorld();
        LocalDate mon = monday();
        String first = apply(w.teacherA(), mon, mon.plusDays(1));
        String second = apply(w.teacherA(), mon.plusDays(7), mon.plusDays(8));
        apply(w.teacherB(), mon, mon.plusDays(1));

        Resp mine = get(ME, w.teacherA().token());

        assertThat(mine.status()).as(mine.body()).isEqualTo(200);
        assertThat(mine.body()).contains(first).contains(second);
        assertThat(mine.body().indexOf(second)).isLessThan(mine.body().indexOf(first));
        assertThat(mine.body()).contains("\"totalElements\":2");

        Resp theirs = get(ME, w.teacherB().token());
        assertThat(theirs.body()).doesNotContain(first).doesNotContain(second).contains("\"totalElements\":1");
    }

    @Test
    void theStatusFilterAndPagingWork() {
        World w = newWorld();
        LocalDate mon = monday();
        String cancelled = apply(w.teacherA(), mon, mon.plusDays(1));
        apply(w.teacherA(), mon.plusDays(7), mon.plusDays(8));
        assertThat(post(ME + "/" + cancelled + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);

        assertThat(get(ME + "?status=CANCELLED", w.teacherA().token()).body()).contains("\"totalElements\":1");
        assertThat(get(ME + "?status=PENDING", w.teacherA().token()).body()).contains("\"totalElements\":1");
        Resp paged = get(ME + "?size=1&page=1", w.teacherA().token());
        assertThat(paged.body()).contains("\"totalElements\":2").contains("\"page\":1");
        assertThat(get(ME + "?status=NOPE", w.teacherA().token()).status()).isEqualTo(400);
    }

    @Test
    void cancellingAPendingRequestMarksItCancelledAndFreesTheDates() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        Resp cancelled = post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of());

        assertThat(cancelled.status()).as(cancelled.body()).isEqualTo(200);
        assertThat(field(cancelled.body(), "status")).isEqualTo("CANCELLED");
        assertThat(field(cancelled.body(), "cancelledBy")).isEqualTo("TEACHER");
        assertThat(cancelled.body()).contains("\"allowedActions\":[]");
        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(409);
    }

    @Test
    void aRejectedRequestCannotBeCancelled() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));
        jdbc.update(
                "update leave_request set status = 'REJECTED', decision_note = 'Exams' where id = ?::uuid", id);

        assertThat(post(ME + "/" + id + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(409);
    }

    @Test
    void anotherTeachersRequestIsNotFound() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        assertThat(post(ME + "/" + id + "/cancel", w.teacherB().token(), Map.of()).status()).isEqualTo(404);
        assertThat(field(get(ME, w.teacherA().token()).body(), "status")).isEqualTo("PENDING");
    }

    @Test
    void onlyTeachersMayCancel() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon.plusDays(1));

        for (String token : new String[] {w.admin(), w.director(), w.managerA().token()}) {
            assertThat(post(ME + "/" + id + "/cancel", token, Map.of()).status()).isEqualTo(403);
        }
    }
}
