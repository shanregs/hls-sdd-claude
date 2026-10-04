package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.attendance.api.LeaveAttendance.LeaveDay;
import com.hls.attendance.api.LeaveAttendance.RemoveResult;
import com.hls.school.api.ConflictException;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 009 T008: the attendance side of leave (working days, problems, apply, remove). */
class LeaveAttendanceTest extends AttendanceTestBase {

    @Autowired
    private LeaveAttendance leave;

    /** A Monday about 40 days ago, inside the 60-day placement the test Teachers have. */
    private LocalDate monday() {
        return today().minusDays(40).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void supervisorMark(World w, LocalDate date, String code) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + date,
                w.managerA().token(),
                Map.of("statusCode", code, "dayValue", 1));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    private Map<String, Object> markRow(UUID teacher, LocalDate date) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select m.leave_request_id, m.set_by_kind, m.day_value, c.short_code from attendance_mark m"
                        + " join attendance_status_code c on c.id = m.status_code_id"
                        + " where m.teacher_id = ? and m.mark_date = ?",
                teacher,
                date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void lockMonth(UUID teacher, YearMonth month) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', 20, 7, 1, 0, 0, 0, 7, now(), 0)",
                UUID.randomUUID(),
                teacher,
                month.toString());
    }

    @Test
    void workingDaysSkipSundaysAndHolidaysAndUnplacedDays() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();

        assertThat(leave.workingDays(teacher, mon, mon.plusDays(6))).hasSize(6);

        LocalDate holiday = mon.plusDays(2);
        jdbc.update(
                "insert into attendance_non_working_date (id, on_date, description, created_by) values (?, ?, 'Test holiday', ?)"
                        + " on conflict (on_date) do nothing",
                UUID.randomUUID(),
                holiday,
                UUID.randomUUID());
        try {
            List<LeaveDay> days = leave.workingDays(teacher, mon, mon.plusDays(6));
            assertThat(days).extracting(LeaveDay::date).doesNotContain(holiday, mon.plusDays(6));
            assertThat(days).hasSize(5);
            assertThat(days).allSatisfy(d -> assertThat(d.schoolId()).isEqualTo(w.schoolA()));
            assertThat(days).allSatisfy(d -> assertThat(d.value()).isEqualByComparingTo("1"));
        } finally {
            jdbc.update("delete from attendance_non_working_date where on_date = ? and description = 'Test holiday'", holiday);
        }

        assertThat(leave.workingDays(teacher, today().minusDays(90), today().minusDays(80))).isEmpty();
    }

    @Test
    void problemsNameLockedMonthsAndSupervisorSetDaysButNotTeacherSetOrLeaveDays() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        List<LeaveDay> days = leave.workingDays(teacher, mon, mon.plusDays(2));
        assertThat(leave.problems(teacher, days)).isEmpty();

        supervisorMark(w, mon, "P");
        assertThat(leave.problems(teacher, days)).singleElement().asString().contains("set by a supervisor").contains(mon.toString());

        jdbc.update("update attendance_mark set set_by_kind = 'SELF' where teacher_id = ? and mark_date = ?", teacher, mon);
        assertThat(leave.problems(teacher, days)).isEmpty();

        supervisorMark(w, mon.plusDays(1), "L");
        assertThat(leave.problems(teacher, days)).isEmpty();

        lockMonth(teacher, YearMonth.from(mon));
        assertThat(leave.problems(teacher, days)).anyMatch(p -> p.equals("month locked: " + YearMonth.from(mon)));
    }

    @Test
    void applyMarksLeaveReplacesATeacherSetMarkAndTagsTheRequest() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID approver = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        LocalDate mon = monday();
        supervisorMark(w, mon, "P");
        jdbc.update("update attendance_mark set set_by_kind = 'SELF' where teacher_id = ? and mark_date = ?", teacher, mon);

        List<LeaveDay> days = List.of(
                new LeaveDay(mon, w.schoolA(), new BigDecimal("0.50")),
                new LeaveDay(mon.plusDays(1), w.schoolA(), new BigDecimal("1.00")));
        leave.apply(request, approver, teacher, days);

        Map<String, Object> first = markRow(teacher, mon);
        assertThat(first.get("short_code")).isEqualTo("L");
        assertThat(first.get("set_by_kind")).isEqualTo("SUPERVISOR");
        assertThat((BigDecimal) first.get("day_value")).isEqualByComparingTo("0.5");
        assertThat(first.get("leave_request_id")).isEqualTo(request);
        assertThat(markRow(teacher, mon.plusDays(1)).get("leave_request_id")).isEqualTo(request);
        Integer earlier = jdbc.queryForObject(
                "select count(*) from attendance_mark_history h join attendance_status_code c on c.id = h.status_code_id"
                        + " where h.teacher_id = ? and h.mark_date = ? and c.short_code = 'P'",
                Integer.class,
                teacher,
                mon);
        assertThat(earlier).isEqualTo(1);
    }

    @Test
    void applyMarksFutureDaysToo() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID request = UUID.randomUUID();
        LocalDate future = today().plusDays(10);

        leave.apply(request, UUID.randomUUID(), teacher, List.of(new LeaveDay(future, w.schoolA(), new BigDecimal("1.00"))));

        assertThat(markRow(teacher, future).get("short_code")).isEqualTo("L");
    }

    @Test
    void applyIsAllOrNothingWhenASupervisorSetADay() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        LocalDate mon = monday();
        supervisorMark(w, mon.plusDays(1), "T");
        List<LeaveDay> days = leave.workingDays(teacher, mon, mon.plusDays(2));

        assertThatThrownBy(() -> leave.apply(UUID.randomUUID(), UUID.randomUUID(), teacher, days))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("set by a supervisor")
                .hasMessageContaining(mon.plusDays(1).toString());

        assertThat(markRow(teacher, mon)).isNull();
        assertThat(markRow(teacher, mon.plusDays(2))).isNull();
        assertThat(markRow(teacher, mon.plusDays(1)).get("short_code")).isEqualTo("T");
    }

    @Test
    void applyAndRemoveAreRefusedInALockedMonth() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID request = UUID.randomUUID();
        LocalDate mon = monday();
        List<LeaveDay> days = leave.workingDays(teacher, mon, mon.plusDays(1));
        leave.apply(request, UUID.randomUUID(), teacher, days);

        lockMonth(teacher, YearMonth.from(mon));

        assertThatThrownBy(() -> leave.remove(request, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("month locked");
        assertThat(markRow(teacher, mon)).isNotNull();
        assertThatThrownBy(() -> leave.apply(UUID.randomUUID(), UUID.randomUUID(), teacher, leave.workingDays(teacher, mon.plusDays(2), mon.plusDays(3))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("month locked");
    }

    @Test
    void removeClearsLeaveMarksButLeavesADayThatWasChangedByHand() {
        World w = newWorld();
        UUID teacher = w.teacherA().teacherId();
        UUID request = UUID.randomUUID();
        LocalDate mon = monday();
        leave.apply(request, UUID.randomUUID(), teacher, leave.workingDays(teacher, mon, mon.plusDays(2)));

        supervisorMark(w, mon.plusDays(1), "P");
        assertThat(markRow(teacher, mon.plusDays(1)).get("leave_request_id")).isNull();

        RemoveResult result = leave.remove(request, UUID.randomUUID());

        assertThat(result.removed()).containsExactly(mon, mon.plusDays(2));
        assertThat(result.keptBecauseChanged()).containsExactly(mon.plusDays(1));
        assertThat(markRow(teacher, mon)).isNull();
        assertThat(markRow(teacher, mon.plusDays(2))).isNull();
        assertThat(markRow(teacher, mon.plusDays(1)).get("short_code")).isEqualTo("P");
    }
}
