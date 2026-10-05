package com.hls.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.RollupView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InductionAttendanceTest extends TrainingTestBase {

    @Autowired
    AttendanceReadApi attendanceRead;

    private record Setup(String director, UUID batch, UUID teacher, UUID enrolment) {}

    private Setup setup() {
        String admin = admin();
        String director = directorToken();
        UUID batch = runningBatch(director, 5);
        UUID teacher = recruit(admin, director);
        return new Setup(director, batch, teacher, enrolmentOf(batch, teacher));
    }

    @Test
    void aPresentOrHalfDayWritesATrainingMarkWithNoSchoolAndAnAbsenceWritesNone() {
        Setup s = setup();
        LocalDate d1 = today.minusDays(4);
        LocalDate d2 = today.minusDays(3);
        LocalDate d3 = today.minusDays(2);

        assertThat(attend(s.director(), s.enrolment(), d1, "PRESENT", null).status()).isEqualTo(200);
        assertThat(attend(s.director(), s.enrolment(), d2, "HALF", null).status()).isEqualTo(200);
        Resp absent = attend(s.director(), s.enrolment(), d3, "ABSENT", "Unwell");
        assertThat(absent.status()).as(absent.body()).isEqualTo(200);

        Integer marks = jdbc.queryForObject(
                "select count(*) from attendance_mark where teacher_id = ? and school_id is null", Integer.class, s.teacher());
        assertThat(marks).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ? and mark_date = ?", Integer.class, s.teacher(), d3)).isZero();
        assertThat(jdbc.queryForObject("select reason from induction_absence where enrolment_id = ? and absent_on = ?", String.class, s.enrolment(), d3))
                .isEqualTo("Unwell");
        assertThat(attend(s.director(), s.enrolment(), today.minusDays(1), "ABSENT", " ").status()).isEqualTo(400);
        assertThat(attend(s.director(), s.enrolment(), today.minusDays(1), "LATE", null).status()).isEqualTo(400);
    }

    @Test
    void aCorrectionKeepsTheEarlierValueInTheMarkHistory() {
        Setup s = setup();
        LocalDate day = today.minusDays(2);

        attend(s.director(), s.enrolment(), day, "PRESENT", null);
        attend(s.director(), s.enrolment(), day, "HALF", null);
        attend(s.director(), s.enrolment(), day, "ABSENT", "Left early");

        Integer rows = jdbc.queryForObject(
                "select count(*) from attendance_mark_history where teacher_id = ? and mark_date = ?", Integer.class, s.teacher(), day);
        assertThat(rows).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from attendance_mark where teacher_id = ? and mark_date = ?", Integer.class, s.teacher(), day)).isZero();
        // and back to present again clears the absence
        attend(s.director(), s.enrolment(), day, "PRESENT", null);
        assertThat(jdbc.queryForObject("select count(*) from induction_absence where enrolment_id = ?", Integer.class, s.enrolment())).isZero();
    }

    @Test
    void theRecruitsRollupShowsTheSameTrainingDaysAsTheInductionRoster() {
        Setup s = setup();
        for (int back = 4; back >= 2; back--) {
            assertThat(attend(s.director(), s.enrolment(), today.minusDays(back), "PRESENT", null).status()).isEqualTo(200);
        }
        attend(s.director(), s.enrolment(), today.minusDays(1), "HALF", null);

        RollupView rollup = attendanceRead.rollupOf(s.teacher(), YearMonth.from(today.minusDays(1)));
        Resp roster = get(IND + "/batches/" + s.batch() + "/roster", s.director());

        long trainingDaysThisMonth = java.util.stream.Stream.of(4, 3, 2, 1)
                .filter(back -> YearMonth.from(today.minusDays(back)).equals(YearMonth.from(today.minusDays(1))))
                .count();
        assertThat(rollup.trainingAttended().doubleValue()).isGreaterThanOrEqualTo(trainingDaysThisMonth - 0.5);
        assertThat(roster.status()).isEqualTo(200);
        assertThat(roster.body()).contains(s.teacher().toString(), "PRESENT", "HALF");
    }

    @Test
    void aLockedMonthRefusesTheDayWithTheLockReason() {
        Setup s = setup();
        YearMonth month = YearMonth.from(today.minusDays(2));
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 0, 0, 0, 0, 0, 0, 0, now(), 0)",
                UUID.randomUUID(),
                s.teacher(),
                month.toString());

        Resp refused = attend(s.director(), s.enrolment(), today.minusDays(2), "PRESENT", null);

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("locked");
    }

    @Test
    void dateRulesAndSignedOffEnrolments() {
        Setup s = setup();

        assertThat(attend(s.director(), s.enrolment(), today.minusDays(30), "PRESENT", null).status()).isEqualTo(400);
        assertThat(attend(s.director(), s.enrolment(), today.plusDays(3), "PRESENT", null).status()).isEqualTo(409);
        post(IND + "/enrolments/" + s.enrolment() + "/signoff", s.director(), java.util.Map.of("result", "COMPLETED"));
        assertThat(attend(s.director(), s.enrolment(), today.minusDays(1), "PRESENT", null).status()).isEqualTo(409);
    }
}
