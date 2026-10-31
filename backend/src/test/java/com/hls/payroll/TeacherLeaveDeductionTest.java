package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.LeaveTestBase;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.UnpaidDay;
import com.hls.payroll.api.UnpaidDays;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 040 FR-003 / SC-003 (amends 013a, 013b): every Teacher leave day is an unpaid day at the payroll run, whatever the leave
 * type, a half day counting as the policy's half-day fraction. A day with no leave is not unpaid.
 */
class TeacherLeaveDeductionTest extends LeaveTestBase {

    @Autowired
    private PayRules payRules;

    private String approve(World w, String requestId) {
        Resp approved = post(SUP + "/" + requestId + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        return requestId;
    }

    private String request(World w, UUID type, LocalDate day, boolean half) {
        Map<String, Object> body = new HashMap<>(draft(day, day));
        body.put("leaveTypeId", type.toString());
        body.put("halfDayStart", half);
        Resp resp = post(ME, w.teacherA().token(), body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return field(resp.body(), "id");
    }

    @Test
    void approvedLeaveOfAnyTypeIsDeductedAndALeaveFreeDayIsNot() {
        World w = newWorld();
        LocalDate mon = monday();
        UUID casual = jdbc.queryForObject("select id from leave_type where code = 'CASUAL'", UUID.class);
        UUID sick = jdbc.queryForObject("select id from leave_type where code = 'SICK'", UUID.class);
        approve(w, request(w, casual, mon, false));
        approve(w, request(w, sick, mon.plusDays(1), true));

        UnpaidDays unpaid = payRules.unpaidDays(new PersonRef(PayRules.Kind.TEACHER, w.teacherA().teacherId()), YearMonth.from(mon));

        assertThat(unpaid).isInstanceOf(UnpaidDays.Found.class);
        List<UnpaidDay> days = ((UnpaidDays.Found) unpaid).days();
        assertThat(days).extracting(UnpaidDay::date).contains(mon, mon.plusDays(1));
        assertThat(days.stream().filter(d -> d.date().equals(mon)).findFirst().orElseThrow().dayValue())
                .isEqualByComparingTo("1");
        assertThat(days.stream().filter(d -> d.date().equals(mon.plusDays(1))).findFirst().orElseThrow().dayValue())
                .isLessThan(java.math.BigDecimal.ONE);
        assertThat(days).extracting(UnpaidDay::date).doesNotContain(mon.plusDays(2));
    }

    @Test
    void anAbsentMarkReplacedBySameDayLeaveIsDeductedOnce() {
        World w = newWorld();
        LocalDate day = monday();
        UUID casual = jdbc.queryForObject("select id from leave_type where code = 'CASUAL'", UUID.class);
        // an Absent mark already stands for the day, then leave is approved over it
        jdbc.update(
                "insert into attendance_mark (id, teacher_id, mark_date, status_code_id, day_value, school_id,"
                        + " set_by_user_id, set_by_kind, set_at, version)"
                        + " select ?, ?, ?, c.id, 1.00, ?, ?, 'SUPERVISOR', now(), 0 from attendance_status_code c"
                        + " where c.short_code = 'A'",
                UUID.randomUUID(), w.teacherA().teacherId(), day, w.schoolA(), w.managerA().signed().userId());
        String id = request(w, casual, day, false);
        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));
        // the supervisor-set Absent blocks ordinary approval (spec 009); either way the date is counted at most once
        UnpaidDays unpaid = payRules.unpaidDays(new PersonRef(PayRules.Kind.TEACHER, w.teacherA().teacherId()), YearMonth.from(day));

        assertThat(approved.status()).isIn(200, 409);
        List<UnpaidDay> days = ((UnpaidDays.Found) unpaid).days();
        assertThat(days.stream().filter(d -> d.date().equals(day)).count()).isEqualTo(1);
    }
}
