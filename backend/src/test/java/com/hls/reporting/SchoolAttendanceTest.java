package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.SchoolAttendance;
import com.hls.attendance.api.SchoolAttendance.SchoolRollup;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014b A6: the School rollup equals the Attendance report's Schools row for the same School and month. */
class SchoolAttendanceTest extends AttendanceTestBase {

    @Autowired
    private SchoolAttendance schoolAttendance;

    private YearMonth month() {
        return YearMonth.from(today()).minusMonths(1);
    }

    private void lock(UUID teacher, int working, int worked, int leave) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', ?, ?, ?, 0, 0, 0, ?, now(), 0)",
                UUID.randomUUID(), teacher, month().toString(), working, worked, leave, worked);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String key) {
        List<Map<String, Object>> sections = (List<Map<String, Object>>) resp.map().get("sections");
        return (List<Map<String, Object>>) sections.stream().filter(s -> key.equals(s.get("key"))).findFirst().orElseThrow().get("rows");
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal cell(Map<String, Object> row, int index) {
        return new BigDecimal(String.valueOf(((List<Map<String, Object>>) row.get("cells")).get(index).get("value")));
    }

    @Test
    void theSchoolRollupEqualsTheAttendanceReportSchoolsRow() {
        World w = newWorld();
        UUID first = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        UUID second = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        lock(first, 25, 20, 3);
        lock(second, 25, 25, 0);

        Map<UUID, SchoolRollup> rollup = schoolAttendance.rollupsBySchool(Set.of(w.schoolA(), w.schoolB()), month());
        Resp report = get("/api/v1/reports/attendance?month=" + month(), w.admin());
        Map<String, Object> row = rows(report, "schools").stream()
                .filter(r -> w.schoolA().toString().equals(r.get("id"))).findFirst().orElseThrow();

        SchoolRollup a = rollup.get(w.schoolA());
        assertThat(a.teachers()).isGreaterThanOrEqualTo(2);
        assertThat(BigDecimal.valueOf(a.teachers())).isEqualByComparingTo(cell(row, 0));
        assertThat(a.working()).isEqualByComparingTo(cell(row, 1));
        assertThat(a.present()).isEqualByComparingTo(cell(row, 2));
        assertThat(a.absent()).isEqualByComparingTo(cell(row, 3));
        assertThat(a.leave()).isEqualByComparingTo(cell(row, 4));
        assertThat(a.lossOfPay()).isEqualByComparingTo(cell(row, 5));
    }

    @Test
    void aSchoolWithNoTeachersAndAnEmptySetHaveNoEntry() {
        World w = newWorld();
        assertThat(schoolAttendance.rollupsBySchool(Set.of(UUID.randomUUID()), month())).isEmpty();
        assertThat(schoolAttendance.rollupsBySchool(Set.of(), month())).isEmpty();
    }
}
