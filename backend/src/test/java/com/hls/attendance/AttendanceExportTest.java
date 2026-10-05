package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Spec 008 US8: the CSV export of the filtered month grid, its authorization and its audit entry. */
class AttendanceExportTest extends AttendanceTestBase {

    private static final String EXPORT = "/api/v1/attendance/export";

    private record Csv(int status, HttpHeaders headers, String body) {
        List<String> lines() {
            String text = body.startsWith("﻿") ? body.substring(1) : body;
            return Arrays.stream(text.split("\r\n")).filter(l -> !l.isEmpty()).toList();
        }
    }

    private Csv download(String url, String token) {
        var request = client.get().uri(url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        var result = request.exchange().returnResult(byte[].class);
        byte[] bytes = result.getResponseBody();
        return new Csv(
                result.getStatus().value(),
                result.getResponseHeaders(),
                bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8));
    }

    private YearMonth previousMonth() {
        return YearMonth.from(today()).minusMonths(1);
    }

    private void supervisorMark(String token, UUID teacherId, LocalDate date, String code, double value) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + teacherId + "/marks/" + date,
                token,
                Map.of("statusCode", code, "dayValue", value));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    @Test
    void adminAndDirectorDownloadExactlyTheFilteredTeachersWithRollupAndDayColumns() {
        World w = newWorld();
        YearMonth month = previousMonth();
        supervisorMark(w.managerA().token(), w.teacherA().teacherId(), month.atDay(10), "P", 1);
        supervisorMark(w.managerA().token(), w.teacherA().teacherId(), month.atDay(11), "P", 0.5);
        supervisorMark(w.managerA().token(), w.teacherA().teacherId(), month.atDay(12), "L", 1);
        String url = EXPORT + "?month=" + month + "&schoolId=" + w.schoolA();

        for (String token : List.of(w.admin(), w.director())) {
            Csv csv = download(url, token);

            assertThat(csv.status()).isEqualTo(200);
            assertThat(csv.headers().getContentType().toString()).startsWith("text/csv");
            assertThat(csv.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .contains("attachment")
                    .contains("attendance-" + month + ".csv");
            List<String> lines = csv.lines();
            assertThat(lines).hasSize(2);
            List<String> header = Arrays.asList(lines.get(0).split(",", -1));
            assertThat(header.subList(0, 4)).containsExactly("Teacher", "Status", "School", "Manager");
            assertThat(header).contains("Working days", "Days worked", "Unmarked", "Weighted total");
            assertThat(header.subList(12, header.size()))
                    .hasSize(month.lengthOfMonth())
                    .startsWith(month.atDay(1).toString())
                    .endsWith(month.atEndOfMonth().toString());
            List<String> row = Arrays.asList(lines.get(1).split(",", -1));
            assertThat(row).hasSameSizeAs(header);
            assertThat(row.get(12 + 9)).isEqualTo("P");
            assertThat(row.get(12 + 10)).isEqualTo("P0.5");
            assertThat(row.get(12 + 11)).isEqualTo("L");
        }
    }

    @Test
    void weeklyOffDaysShowTheWeekdayAndMarkedHolidayCodesShowH() {
        World w = newWorld();
        YearMonth month = previousMonth();
        LocalDate sunday = month.atDay(1);
        while (sunday.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            sunday = sunday.plusDays(1);
        }
        LocalDate holiday = sunday.plusDays(2);
        supervisorMark(w.managerA().token(), w.teacherA().teacherId(), holiday, "H", 1);

        Csv csv = download(EXPORT + "?month=" + month + "&schoolId=" + w.schoolA(), w.admin());

        List<String> row = Arrays.asList(csv.lines().get(1).split(",", -1));
        assertThat(row.get(12 + sunday.getDayOfMonth() - 1)).isEqualTo("Sun");
        assertThat(row.get(12 + holiday.getDayOfMonth() - 1)).isEqualTo("H");
    }

    @Test
    void theFilterLeavesOutTheOtherManagersTeachers() {
        World w = newWorld();
        YearMonth month = previousMonth();

        Csv forA = download(EXPORT + "?month=" + month + "&schoolId=" + w.schoolA(), w.admin());
        Csv forZoneB = download(EXPORT + "?month=" + month + "&zoneId=" + w.zoneB(), w.admin());

        assertThat(forA.lines()).hasSize(2);
        assertThat(forZoneB.lines()).hasSize(2);
        assertThat(forA.lines().get(1)).isNotEqualTo(forZoneB.lines().get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRollupFiguresInTheFileMatchTheGrid() {
        World w = newWorld();
        YearMonth month = previousMonth();
        supervisorMark(w.managerA().token(), w.teacherA().teacherId(), month.atDay(10), "P", 1);

        Csv csv = download(EXPORT + "?month=" + month + "&schoolId=" + w.schoolA(), w.admin());
        Resp grid = get("/api/v1/attendance/grid?month=" + month + "&schoolId=" + w.schoolA(), w.admin());

        Map<String, Object> first = ((List<Map<String, Object>>) grid.map().get("content")).get(0);
        Map<String, Object> rollup = (Map<String, Object>) first.get("rollup");
        List<String> row = Arrays.asList(csv.lines().get(1).split(",", -1));
        assertThat(Double.parseDouble(row.get(4))).isEqualTo(((Number) rollup.get("workingDays")).doubleValue());
        assertThat(Double.parseDouble(row.get(5))).isEqualTo(((Number) rollup.get("daysWorked")).doubleValue());
        assertThat(Integer.parseInt(row.get(9))).isEqualTo(((Number) rollup.get("unmarked")).intValue());
        assertThat(Double.parseDouble(row.get(10))).isEqualTo(((Number) rollup.get("weightedTotal")).doubleValue());
    }

    @Test
    void managerTeacherSystemAndAnonymousAreRefused() {
        World w = newWorld();
        String url = EXPORT + "?month=" + previousMonth();

        assertThat(download(url, w.managerA().token()).status()).isEqualTo(403);
        assertThat(download(url, w.teacherA().token()).status()).isEqualTo(403);
        assertThat(download(url, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(download(url, null).status()).isEqualTo(401);
        assertThat(download(EXPORT + "?month=not-a-month", w.admin()).status()).isEqualTo(400);
    }

    @Test
    void eachExportWritesOneChangeHistoryEventVisibleToAdminButNotSystem() {
        World w = newWorld();
        String system = signInAs(Role.SYSTEM).token();
        YearMonth month = previousMonth();

        download(EXPORT + "?month=" + month + "&schoolId=" + w.schoolA(), w.admin());

        assertChangeRecorded(w.admin(), "ATTENDANCE_EXPORT", month, "exported");
        Resp asAdmin = get("/api/v1/audit/change-history?entityType=ATTENDANCE_EXPORT&size=100", w.admin());
        String school = w.schoolA().toString();
        assertThat(asAdmin.body().split(school, -1).length - 1).as("events naming this filter").isEqualTo(1);
        assertThat(get("/api/v1/audit/change-history?size=100", system).body()).doesNotContain("ATTENDANCE_EXPORT");
        // A refused export leaves no event.
        download(EXPORT + "?month=" + month + "&schoolId=" + w.schoolB(), w.managerA().token());
        assertThat(get("/api/v1/audit/change-history?entityType=ATTENDANCE_EXPORT&size=100", w.admin()).body())
                .doesNotContain(w.schoolB().toString());
    }

    @Test
    void namesStartingWithFormulaCharactersAreNeutralised() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        for (String name : List.of("=HYPERLINK(\"http://x\")", "+cmd", "-1+1", "@SUM(A1)")) {
            Resp created = post(
                    "/api/v1/teachers", admin, Map.of("name", name, "phone", "9444444444", "status", "ACTIVE"));
            assertThat(created.status()).as(created.body()).isEqualTo(201);
            placeTeacher(admin, created.id(), school);
        }

        Csv csv = download(EXPORT + "?month=" + YearMonth.from(today()) + "&schoolId=" + school, admin);

        List<String> rows = csv.lines().stream().skip(1).toList();
        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(l -> assertThat(l.replaceFirst("^\"", "")).startsWith("'"));
        // Embedded quotes are doubled so the field stays one CSV value.
        assertThat(csv.body()).contains("'=HYPERLINK(\"\"http://x\"\")");
    }

    @Test
    void aMonthWithNoPlacedTeachersExportsOnlyTheHeader() {
        String admin = signInAs(Role.ADMIN).token();

        Csv csv = download(EXPORT + "?month=2001-01", admin);

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.lines()).hasSize(1);
    }

    @Test
    void anExportOf500TeachersStaysWithinTheBudget() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        jdbc.update(
                "insert into teacher (id, name, phone, status, status_effective_on, version, created_at, updated_at)"
                        + " select gen_random_uuid(), 'Export ' || n, null, 'ACTIVE', current_date - 60, 0, now(), now()"
                        + " from generate_series(1, 500) n");
        jdbc.update(
                "insert into contract_assignment (id, teacher_id, school_id, starts_on, status, created_at)"
                        + " select gen_random_uuid(), t.id, ?, current_date - 60, 'ACTIVE', now()"
                        + " from teacher t where t.name like 'Export %' and not exists"
                        + " (select 1 from contract_assignment p where p.teacher_id = t.id)",
                school);
        try {
            long started = System.nanoTime();
            Csv csv = download(EXPORT + "?month=" + YearMonth.from(today()) + "&schoolId=" + school, admin);
            long millis = (System.nanoTime() - started) / 1_000_000;

            assertThat(csv.status()).isEqualTo(200);
            assertThat(csv.lines()).hasSize(501);
            assertThat(millis).as("500-teacher export").isLessThan(3000);
        } finally {
            jdbc.update("delete from contract_assignment where teacher_id in (select id from teacher where name like 'Export %')");
            jdbc.update("delete from teacher where name like 'Export %'");
        }
    }
}
