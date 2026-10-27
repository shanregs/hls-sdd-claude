package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.RollupView;
import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 014a US1 and US3: the Attendance report equals the grid figures, respects scope and grants, and exports what it shows. */
class AttendanceReportTest extends AttendanceTestBase {

    private static final String REPORT = "/api/v1/reports/attendance";

    @Autowired
    private AttendanceReadApi attendance;

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
    private static Map<String, Object> section(Resp resp, String key) {
        List<Map<String, Object>> sections = (List<Map<String, Object>>) resp.map().get("sections");
        return sections.stream().filter(s -> key.equals(s.get("key"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String key) {
        return (List<Map<String, Object>>) section(resp, key).get("rows");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rowOf(Resp resp, String section, UUID id) {
        return rows(resp, section).stream().filter(r -> id.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Object cell(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index).get("value");
    }

    @SuppressWarnings("unchecked")
    private static String cellState(Map<String, Object> row, int index) {
        return (String) ((List<Map<String, Object>>) row.get("cells")).get(index).get("state");
    }

    private void markDay(String token, UUID teacher, int day, String code) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + month().atDay(day),
                token,
                Map.of("statusCode", code, "dayValue", 1));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @Test
    void figuresEqualTheAttendanceRollupAndAHandCount() {
        World w = newWorld();
        UUID handCounted = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        lock(handCounted, 25, 20, 3);
        UUID live = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        markDay(w.managerA().token(), live, 10, "P");
        markDay(w.managerA().token(), live, 11, "L");

        Resp resp = get(REPORT + "?month=" + month(), w.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> locked = rowOf(resp, "teachers", handCounted);
        assertThat(new BigDecimal(String.valueOf(cell(locked, 2)))).isEqualByComparingTo("25");
        assertThat(new BigDecimal(String.valueOf(cell(locked, 3)))).isEqualByComparingTo("20");
        assertThat(new BigDecimal(String.valueOf(cell(locked, 5)))).isEqualByComparingTo("3");
        assertThat(new BigDecimal(String.valueOf(cell(locked, 4)))).isEqualByComparingTo("2");
        assertThat(new BigDecimal(String.valueOf(cell(locked, 7)))).isEqualByComparingTo("80");
        for (UUID id : List.of(handCounted, live)) {
            RollupView rollup = attendance.rollupOf(id, month());
            Map<String, Object> row = rowOf(resp, "teachers", id);
            assertThat(new BigDecimal(String.valueOf(cell(row, 2)))).isEqualByComparingTo(rollup.workingDays());
            assertThat(new BigDecimal(String.valueOf(cell(row, 3)))).isEqualByComparingTo(rollup.daysWorked());
            assertThat(new BigDecimal(String.valueOf(cell(row, 5)))).isEqualByComparingTo(rollup.daysLeave());
        }
        assertThat(rows(resp, "schools")).isNotEmpty();
        assertThat(rows(resp, "zones")).isNotEmpty();
        assertThat(section(resp, "teachers").get("totals")).isNotNull();
    }

    @Test
    void aMonthWithNoWorkingDaysShowsNoDataForThePercent() {
        World w = newWorld();
        UUID nobodyWorking = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        lock(nobodyWorking, 0, 0, 0);

        Resp resp = get(REPORT + "?month=" + month() + "&teacherId=" + nobodyWorking, w.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> row = rowOf(resp, "teachers", nobodyWorking);
        assertThat(cellState(row, 7)).isEqualTo("NO_DATA");
        assertThat(cell(row, 7)).isNull();
    }

    @Test
    void aMonthIsOpenUntilEveryoneInScopeIsLocked() {
        World w = newWorld();
        UUID teacher = newTeacher(w.admin(), w.schoolA(), 150).teacherId();

        Resp open = get(REPORT + "?month=" + month() + "&teacherId=" + teacher + "&managerId=" + w.managerA().managerId(), w.admin());
        assertThat(open.map().get("monthState")).isEqualTo("OPEN");

        lock(teacher, 20, 20, 0);
        Resp onlyTeacher = get(REPORT + "?month=" + month() + "&teacherId=" + teacher, w.admin());
        assertThat(onlyTeacher.status()).as(onlyTeacher.body()).isEqualTo(200);
    }

    @Test
    void aZoneManagerSeesOnlyTheirTeachersAndCannotAskForAnotherZone() {
        World w = newWorld();

        Resp own = get(REPORT + "?month=" + month(), w.managerA().token());
        assertThat(own.status()).as(own.body()).isEqualTo(200);
        assertThat(rows(own, "teachers")).extracting(r -> r.get("id")).contains(w.teacherA().teacherId().toString());
        assertThat(rows(own, "teachers")).extracting(r -> r.get("id")).doesNotContain(w.teacherB().teacherId().toString());
        assertThat(own.body()).doesNotContain(w.teacherB().teacherId().toString());
        assertThat(rows(own, "managers")).extracting(r -> r.get("id")).containsExactly(w.managerA().managerId().toString());

        assertThat(get(REPORT + "?month=" + month() + "&zoneId=" + w.zoneB(), w.managerA().token()).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month() + "&schoolId=" + w.schoolB(), w.managerA().token()).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month() + "&teacherId=" + w.teacherB().teacherId(), w.managerA().token()).status())
                .isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month() + "&managerId=" + w.managerB().managerId(), w.managerA().token()).status())
                .isEqualTo(403);
    }

    @Test
    void aTeacherHasNoAttendanceReportAndNoMenuItem() {
        World w = newWorld();

        assertThat(get(REPORT + "?month=" + month(), w.teacherA().token()).status()).isEqualTo(403);
        assertThat(get(REPORT + "/export?month=" + month(), w.teacherA().token()).status()).isEqualTo(403);
        Resp menu = get("/api/v1/reports", w.teacherA().token());
        assertThat(menu.status()).isEqualTo(200);
        assertThat(menu.body()).doesNotContain("\"key\":\"attendance\"");
        assertThat(get("/api/v1/reports", w.admin()).body()).contains("\"key\":\"attendance\"");
    }

    @Test
    void anUnknownReportIsNotFound() {
        assertThat(get("/api/v1/reports/nothing", signInAs(Role.ADMIN).token()).status()).isEqualTo(404);
    }

    @Test
    void theExportEqualsTheScreenAndIsAudited() {
        World w = newWorld();
        UUID teacher = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        lock(teacher, 25, 20, 3);
        String query = "?month=" + month() + "&schoolId=" + w.schoolA();

        Resp screen = get(REPORT + query, w.admin());
        Resp csv = get(REPORT + "/export" + query, w.admin());

        assertThat(csv.status()).as(csv.body()).isEqualTo(200);
        for (Map<String, Object> row : rows(screen, "teachers")) {
            assertThat(csv.body()).contains(String.valueOf(row.get("label")));
        }
        assertThat(csv.body()).startsWith("﻿Report,Attendance and LOP");
        assertThat(csv.body()).contains("Month," + month());
        assertThat(csv.body()).contains("Month state,");
        assertThat(csv.body()).contains("80");
        assertChangeRecorded(w.admin(), "REPORT_EXPORT_ATTENDANCE", "attendance", "exported");
    }

    @Test
    void aUserWithoutTheExportGrantIsRefusedTheFileButStillSeesTheScreen() {
        String admin = signInAs(Role.ADMIN).token();
        World w = newWorld();
        Resp off = put("/api/v1/identity/permission-matrix/MANAGER/REPORT_ATTENDANCE/EXPORT", admin, Map.of("granted", false));
        assertThat(off.status()).as(off.body()).isEqualTo(200);
        try {
            assertThat(get(REPORT + "/export?month=" + month(), w.managerA().token()).status()).isEqualTo(403);
            assertThat(get(REPORT + "?month=" + month(), w.managerA().token()).status()).isEqualTo(200);
        } finally {
            put("/api/v1/identity/permission-matrix/MANAGER/REPORT_ATTENDANCE/EXPORT", admin, Map.of("granted", true));
        }
    }

    @Test
    void theDefaultMonthIsTheCurrentOneWhenNothingIsLockedAndTheFiltersAreChecked() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = get(REPORT, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("month")).isNotNull();
        assertThat(get(REPORT + "?month=2026-13", admin).status()).isEqualTo(400);
        assertThat(get(REPORT + "?zoneId=not-a-uuid", admin).status()).isEqualTo(400);
    }
}
