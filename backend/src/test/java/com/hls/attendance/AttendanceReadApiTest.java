package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.RollupView;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 008 read contract: later modules read live or frozen figures without recomputing attendance. */
class AttendanceReadApiTest extends AttendanceTestBase {

    @Autowired
    private AttendanceReadApi attendance;

    private YearMonth previousMonth() {
        return YearMonth.from(today()).minusMonths(1);
    }

    private void mark(World w, int day, String code, double value) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + previousMonth().atDay(day),
                w.managerA().token(),
                Map.of("statusCode", code, "dayValue", value));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    private void insertMonthRow(UUID teacher, String state) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, ?, 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                previousMonth().toString(),
                state);
    }

    @Test
    void anOpenMonthIsComputedLiveAndMarksAreListedOldestFirst() {
        World w = newWorld();
        mark(w, 12, "L", 1);
        mark(w, 10, "P", 1);
        mark(w, 11, "P", 0.5);

        RollupView rollup = attendance.rollupOf(w.teacherA().teacherId(), previousMonth());
        List<MarkView> marks = attendance.marksOf(w.teacherA().teacherId(), previousMonth());

        assertThat(rollup.locked()).isFalse();
        assertThat(rollup.frozen()).isFalse();
        assertThat(rollup.daysWorked()).isEqualByComparingTo("1.5");
        assertThat(rollup.daysLeave()).isEqualByComparingTo("1");
        assertThat(marks).extracting(MarkView::code).containsExactly("P", "P", "L");
        assertThat(marks).extracting(m -> m.date().getDayOfMonth()).containsExactly(10, 11, 12);
        assertThat(attendance.isLocked(w.teacherA().teacherId(), previousMonth())).isFalse();
    }

    @Test
    void aLockedMonthReturnsTheFrozenFiguresEvenIfLiveOnesWouldDiffer() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        mark(w, 10, "P", 1);
        insertMonthRow(teacher, "LOCKED");

        RollupView rollup = attendance.rollupOf(teacher, previousMonth());

        assertThat(rollup.locked()).isTrue();
        assertThat(rollup.frozen()).isTrue();
        assertThat(rollup.daysWorked()).isEqualByComparingTo("7");
        assertThat(rollup.weightedTotal()).isEqualByComparingTo("7");
        assertThat(attendance.isLocked(teacher, previousMonth())).isTrue();
    }

    @Test
    void aReopenedMonthIsNoLongerLocked() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        insertMonthRow(teacher, "OPEN");

        assertThat(attendance.isLocked(teacher, previousMonth())).isFalse();
        assertThat(attendance.rollupOf(teacher, previousMonth()).frozen()).isFalse();
    }

    @Test
    void anUnknownTeacherGivesZeroFiguresAndNoMarksWithoutAnError() {
        UUID nobody = UUID.randomUUID();

        RollupView rollup = attendance.rollupOf(nobody, previousMonth());

        assertThat(rollup.workingDays()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rollup.daysWorked()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rollup.unmarked()).isZero();
        assertThat(rollup.locked()).isFalse();
        assertThat(attendance.marksOf(nobody, previousMonth())).isEmpty();
        assertThat(attendance.isLocked(nobody, previousMonth())).isFalse();
    }
}
