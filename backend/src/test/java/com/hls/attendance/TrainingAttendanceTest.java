package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.api.TrainingAttendance;
import com.hls.identity.user.Role;
import com.hls.school.api.ConflictException;
import com.hls.support.MasterDataTestBase;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Amendment A5 to spec 008: induction days are marked before the Teacher has a School. */
class TrainingAttendanceTest extends MasterDataTestBase {

    @Autowired
    TrainingAttendance training;

    @Autowired
    TeacherRegistry registry;

    private UUID actor() {
        return signInAs(Role.ADMIN).userId();
    }

    private UUID trainee() {
        String phone = "9" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L);
        return registry.createTrainee(actor(), new TeacherRegistry.Candidate("Trainee " + phone, phone, null, null));
    }

    private List<Object[]> marks(UUID teacher) {
        return jdbc.query(
                "select m.mark_date, m.day_value, m.school_id, c.short_code from attendance_mark m"
                        + " join attendance_status_code c on c.id = m.status_code_id where m.teacher_id = ? order by m.mark_date",
                (rs, i) -> new Object[] {rs.getDate(1).toLocalDate(), rs.getBigDecimal(2), rs.getObject(3), rs.getString(4)},
                teacher);
    }

    @Test
    void aTrainingDayIsMarkedWithNoSchoolAndKeptInTheHistory() {
        UUID teacher = trainee();
        LocalDate day = LocalDate.now().minusDays(2);

        training.markTrainingDay(actor(), teacher, day, new BigDecimal("1.00"));

        List<Object[]> rows = marks(teacher);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[2]).isNull();
        assertThat(rows.get(0)[3]).isEqualTo("T");
        Integer history = jdbc.queryForObject(
                "select count(*) from attendance_mark_history where teacher_id = ? and action = 'CREATED' and school_id is null",
                Integer.class,
                teacher);
        assertThat(history).isEqualTo(1);
    }

    @Test
    void markingAgainChangesTheValueAndClearingRemovesTheMark() {
        UUID teacher = trainee();
        LocalDate day = LocalDate.now().minusDays(1);

        training.markTrainingDay(actor(), teacher, day, new BigDecimal("1.00"));
        training.markTrainingDay(actor(), teacher, day, new BigDecimal("0.50"));

        assertThat(marks(teacher)).hasSize(1);
        assertThat((BigDecimal) marks(teacher).get(0)[1]).isEqualByComparingTo("0.50");

        training.clearTrainingDay(actor(), teacher, day);

        assertThat(marks(teacher)).isEmpty();
        Integer cleared = jdbc.queryForObject(
                "select count(*) from attendance_mark_history where teacher_id = ? and action = 'CLEARED'", Integer.class, teacher);
        assertThat(cleared).isEqualTo(1);
    }

    @Test
    void aDayValueOtherThanWholeOrHalfIsRefused() {
        UUID teacher = trainee();

        assertThatThrownBy(() -> training.markTrainingDay(actor(), teacher, LocalDate.now(), new BigDecimal("0.75")))
                .isInstanceOf(RuntimeException.class);
        assertThat(marks(teacher)).isEmpty();
    }

    @Test
    void aFutureDayIsRefused() {
        UUID teacher = trainee();

        assertThatThrownBy(() -> training.markTrainingDay(actor(), teacher, LocalDate.now().plusDays(3), new BigDecimal("1.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aLockedMonthRefusesTheMarkWithTheLockReason() {
        UUID teacher = trainee();
        LocalDate day = LocalDate.now().minusMonths(2).withDayOfMonth(10);
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 0, 0, 0, 0, 0, 0, 0, now(), 0)",
                UUID.randomUUID(),
                teacher,
                java.time.YearMonth.from(day).toString());

        assertThatThrownBy(() -> training.markTrainingDay(actor(), teacher, day, new BigDecimal("1.00")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("locked");
        assertThat(marks(teacher)).isEmpty();
    }

    @Test
    void anOrdinaryMarkStillNeedsASchool() {
        UUID teacher = trainee();
        UUID present = jdbc.queryForObject("select id from attendance_status_code where short_code = 'P'", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into attendance_mark (id, teacher_id, mark_date, status_code_id, day_value, school_id,"
                                + " set_by_user_id, set_by_kind, set_at, version)"
                                + " values (?, ?, ?, ?, 1.00, null, ?, 'SUPERVISOR', now(), 0)",
                        UUID.randomUUID(),
                        teacher,
                        LocalDate.now().minusDays(1),
                        present,
                        actor()))
                .hasMessageContaining("needs a School");
    }

    @Test
    void anotherKindOfMarkOnTheSameDayIsNotOverwritten() {
        UUID teacher = trainee();
        LocalDate day = LocalDate.now().minusDays(4);
        training.markTrainingDay(actor(), teacher, day, new BigDecimal("1.00"));
        jdbc.update(
                "update attendance_mark set status_code_id = (select id from attendance_status_code where short_code = 'L'),"
                        + " school_id = ? where teacher_id = ?",
                UUID.randomUUID(),
                teacher);

        assertThatThrownBy(() -> training.markTrainingDay(actor(), teacher, day, new BigDecimal("1.00")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> training.clearTrainingDay(actor(), teacher, day)).isInstanceOf(ConflictException.class);
    }
}
