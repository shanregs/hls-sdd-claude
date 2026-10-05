package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.internal.MarkService;
import com.hls.attendance.internal.SetByKind;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The dev demo seeder writes marks without notifying anyone; the normal path still does. */
class QuietMarkTest extends AttendanceTestBase {

    @Autowired
    private MarkService marks;

    private int notificationsOf(UUID userId) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ?", Integer.class, userId);
    }

    private int marksOn(UUID teacherId, LocalDate day) {
        return jdbc.queryForObject(
                "select count(*) from attendance_mark where teacher_id = ? and mark_date = ?",
                Integer.class,
                teacherId,
                day);
    }

    @Test
    void aQuietSupervisorMarkIsSavedWithHistoryButNotifiesNobody() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID manager = w.managerA().signed().userId();
        LocalDate day = today().minusDays(2);

        marks.setMarkWithoutNotifying(manager, teacher, day, "P", BigDecimal.ONE, null, null, SetByKind.SUPERVISOR);

        assertThat(marksOn(teacher, day)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from attendance_mark_history where teacher_id = ? and mark_date = ?",
                        Integer.class,
                        teacher,
                        day))
                .isEqualTo(1);
        assertThat(notificationsOf(w.teacherA().signed().userId())).isZero();
    }

    @Test
    void theNormalSupervisorMarkStillNotifiesTheTeacher() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID manager = w.managerA().signed().userId();

        marks.setMark(
                manager, teacher, today().minusDays(2), "P", BigDecimal.ONE, null, null, SetByKind.SUPERVISOR);

        assertThat(notificationsOf(w.teacherA().signed().userId())).isEqualTo(1);
    }

    @Test
    void aQuietMarkStillRefusesALockedMonth() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = today().minusDays(1);
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                java.time.YearMonth.from(day).toString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> marks.setMarkWithoutNotifying(
                        w.managerA().signed().userId(), teacher, day, "P", BigDecimal.ONE, null, null, SetByKind.SUPERVISOR))
                .hasMessageContaining("locked");
        assertThat(marksOn(teacher, day)).isZero();
    }
}
