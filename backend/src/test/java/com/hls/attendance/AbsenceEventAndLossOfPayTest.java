package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.LossOfPayDay;
import com.hls.attendance.api.LossOfPayDays;
import com.hls.attendance.api.TeacherAbsenceRecorded;
import com.hls.attendance.api.TeacherAbsenceWithdrawn;
import com.hls.support.SubstitutionTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.hls.support.AbsenceEventRecorder;

/** Spec 008 amendment A2: the absence event, the loss-of-pay flag on each day and the public read of those days. */
class AbsenceEventAndLossOfPayTest extends SubstitutionTestBase {

    @Autowired
    private AbsenceEventRecorder events;

    @Autowired
    private LossOfPayDays lossOfPayDays;

    private List<TeacherAbsenceRecorded> recorded(UUID teacher, LocalDate date) {
        return events.recorded(teacher, date);
    }

    private List<TeacherAbsenceWithdrawn> withdrawn(UUID teacher, LocalDate date) {
        return events.withdrawn(teacher, date);
    }

    private boolean flag(UUID teacher, LocalDate date) {
        return jdbc.queryForObject(
                "select loss_of_pay from attendance_mark where teacher_id = ? and mark_date = ?", Boolean.class, teacher, date);
    }

    @Test
    void anAbsenceIsPublishedWithItsSchoolDayValueAndTheLossOfPayFlag() {
        World w = newWorld();
        LocalDate day = monday();

        Resp resp = markAbsent(w.managerA().token(), w.teacherA().teacherId(), day, 0.5);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.body()).contains("\"lossOfPay\":true");
        List<TeacherAbsenceRecorded> sent = recorded(w.teacherA().teacherId(), day);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).schoolId()).isEqualTo(w.schoolA());
        assertThat(sent.get(0).dayValue()).isEqualByComparingTo("0.5");
        assertThat(sent.get(0).kind()).isEqualTo("ABSENT");
        assertThat(sent.get(0).lossOfPay()).isTrue();
        assertThat(sent.get(0).actorUserId()).isEqualTo(w.managerA().signed().userId());
        assertThat(flag(w.teacherA().teacherId(), day)).isTrue();
    }

    @Test
    void writingTheSameAbsenceAgainPublishesNothingNew() {
        World w = newWorld();
        LocalDate day = monday();
        absent(w.managerA().token(), w.teacherA().teacherId(), day);
        absent(w.managerA().token(), w.teacherA().teacherId(), day);

        assertThat(recorded(w.teacherA().teacherId(), day)).hasSize(1);
    }

    @Test
    void aPresentDayPublishesNothingAndCorrectingAnAbsenceToPresentWithdrawsIt() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        assertThat(markAs(w.managerA().token(), teacher, day, "P", 1).status()).isEqualTo(200);
        assertThat(recorded(teacher, day)).isEmpty();
        assertThat(flag(teacher, day)).isFalse();

        absent(w.managerA().token(), teacher, day);
        assertThat(flag(teacher, day)).isTrue();
        assertThat(markAs(w.managerA().token(), teacher, day, "P", 1).status()).isEqualTo(200);

        assertThat(withdrawn(teacher, day)).hasSize(1);
        assertThat(flag(teacher, day)).as("the flag follows the status").isFalse();
    }

    @Test
    void clearingAnAbsenceWithdrawsItButClearingAPresentDayDoesNot() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate day = monday();
        absent(w.managerA().token(), teacher, day);
        assertThat(delete("/api/v1/attendance/teachers/" + teacher + "/marks/" + day, w.managerA().token()).status())
                .isEqualTo(204);
        assertThat(withdrawn(teacher, day)).hasSize(1);

        LocalDate next = day.plusDays(1);
        markAs(w.managerA().token(), teacher, next, "P", 1);
        delete("/api/v1/attendance/teachers/" + teacher + "/marks/" + next, w.managerA().token());
        assertThat(withdrawn(teacher, next)).isEmpty();
    }

    @Test
    void anAbsenceOnAWeeklyOffDayIsNotAnAbsenceForCover() {
        World w = newWorld();
        LocalDate sunday = monday().minusDays(1);

        Resp resp = markAbsent(w.managerA().token(), w.teacherA().teacherId(), sunday, 1);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(recorded(w.teacherA().teacherId(), sunday)).isEmpty();
    }

    @Test
    void approvedCasualLeaveIsPublishedAsLeaveAndIsNotLossOfPay() {
        World w = newWorld();
        LocalDate mon = monday();
        String id = apply(w.teacherA(), mon, mon);

        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);

        List<TeacherAbsenceRecorded> sent = recorded(w.teacherA().teacherId(), mon);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).kind()).isEqualTo("LEAVE");
        assertThat(sent.get(0).lossOfPay()).isFalse();
        assertThat(flag(w.teacherA().teacherId(), mon)).isFalse();

        Resp revoked = post(SUP + "/" + id + "/revoke", w.managerA().token(), Map.of("reason", "Needed", "version", 1));
        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        assertThat(withdrawn(w.teacherA().teacherId(), mon)).hasSize(1);
    }

    @Test
    void approvedLossOfPayLeaveIsFlaggedAsLossOfPay() {
        World w = newWorld();
        LocalDate mon = monday();
        UUID lop = jdbc.queryForObject("select id from leave_type where code = 'LOP'", UUID.class);
        Map<String, Object> body = new HashMap<>(draft(mon, mon));
        body.put("leaveTypeId", lop.toString());
        Resp applied = post(ME, w.teacherA().token(), body);
        assertThat(applied.status()).as(applied.body()).isEqualTo(201);

        Resp approved = post(SUP + "/" + field(applied.body(), "id") + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);

        assertThat(flag(w.teacherA().teacherId(), mon)).isTrue();
        assertThat(recorded(w.teacherA().teacherId(), mon).get(0).lossOfPay()).isTrue();
    }

    @Test
    void thePublicReadListsOnlyUnpaidDaysOfTheMonthAndInBulk() {
        World w = newWorld();
        UUID a = w.teacherA().teacherId();
        UUID b = w.teacherB().teacherId();
        LocalDate day = monday();
        absent(w.managerA().token(), a, day);
        markAs(w.managerA().token(), a, day.plusDays(1), "P", 1);
        Resp half = markAbsent(w.managerA().token(), a, day.plusDays(2), 0.5);
        assertThat(half.status()).isEqualTo(200);
        absent(w.managerB().token(), b, day);

        List<LossOfPayDay> days = lossOfPayDays.lossOfPayDaysOf(a, YearMonth.from(day));
        assertThat(days).extracting(LossOfPayDay::date).contains(day, day.plusDays(2)).doesNotContain(day.plusDays(1));
        assertThat(days.stream().filter(d -> d.date().equals(day.plusDays(2))).findFirst().orElseThrow().dayValue())
                .isEqualByComparingTo(new BigDecimal("0.50"));

        Map<UUID, List<LossOfPayDay>> bulk = lossOfPayDays.lossOfPayDaysOf(List.of(a, b), day, day.plusDays(3));
        assertThat(bulk.get(a)).hasSize(2);
        assertThat(bulk.get(b)).extracting(LossOfPayDay::date).containsExactly(day);
        assertThat(lossOfPayDays.lossOfPayDaysOf(List.of(), day, day)).isEmpty();
    }
}
