package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.ReceivablesTestBase;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec 014b US3: one row per School; each figure is the owning module's figure. Columns: zone(0), placed(1), contractEnd(2),
 * percent(3), coverSubstitute(4), coverManager(5), coverOpen(6), observations(7), openIssues(8), billed(9), collected(10),
 * outstanding(11), stage(12).
 */
class SchoolReportTest extends ReceivablesTestBase {

    private static final String REPORT = "/api/v1/reports/school";

    private Resp report(String token, String query) {
        Resp resp = get(REPORT + "?month=" + thisMonth() + query, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> "schools".equals(s.get("key")))
                .map(s -> (List<Map<String, Object>>) s.get("rows"))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> rowOf(Resp resp, UUID schoolId) {
        return rows(resp).stream().filter(r -> schoolId.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cell(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index);
    }

    @Test
    void placedBilledAndStageComeFromTheOwningModules() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "12000", "13000", "14000");
        for (int i = 0; i < 3; i++) {
            mapTeacher(admin, f, i, thisMonth().atDay(1));
        }
        raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(collect(admin, collectionBody(f.schoolId(), "5000")).status()).isEqualTo(201);

        Map<String, Object> row = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());

        assertThat(cell(row, 1).get("value")).isEqualTo(3);
        assertThat(cell(row, 2).get("state")).as("open-ended contract has no end").isIn("VALUE", "NO_DATA");
        assertThat(new BigDecimal(String.valueOf(cell(row, 10).get("value")))).isEqualByComparingTo("5000");
        assertThat(cell(row, 12).get("value")).as("no prospect record").isEqualTo("Not applicable");
    }

    @Test
    void aSchoolWithNoTeachersHasNoDataForAttendance() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "12000");

        Map<String, Object> row = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());

        assertThat(cell(row, 1).get("value")).isEqualTo(0);
        assertThat(cell(row, 3).get("state")).isEqualTo("NO_DATA");
        assertThat(cell(row, 9).get("state")).as("never billed").isEqualTo("NO_DATA");
    }

    @Test
    void aZoneManagerSeesOnlyTheirSchools() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = billedSchool(admin, "15000");
        Fixture theirs = billedSchool(admin, "16000");

        Resp resp = report(mine.manager().token(), "");

        assertThat(rowOf(resp, mine.schoolId())).isNotNull();
        assertThat(resp.body()).doesNotContain(theirs.schoolId().toString());
    }

    private static BigDecimal number(Map<String, Object> cell) {
        assertThat(cell.get("state")).as(cell.toString()).isEqualTo("VALUE");
        return new BigDecimal(String.valueOf(cell.get("value")));
    }

    private static Map<String, Object> rowIn(Resp resp, String section, UUID schoolId) {
        return rowsOf(resp, section).stream().filter(r -> schoolId.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Resp resp, String section) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> section.equals(s.get("key")))
                .map(s -> (List<Map<String, Object>>) s.get("rows"))
                .findFirst()
                .orElseThrow();
    }

    private void lockMonth(UUID teacher, int working, int worked, int leave) {
        jdbc.update(
                "insert into attendance_teacher_month (id, teacher_id, year_month, state, working_days, days_worked,"
                        + " days_leave, training_available, training_attended, unmarked, weighted_total, changed_at, version)"
                        + " values (?, ?, ?, 'LOCKED', ?, ?, ?, 0, 0, 0, ?, now(), 0)",
                UUID.randomUUID(), teacher, thisMonth().toString(), working, worked, leave, worked);
    }

    @Test
    void theContractEndIsTheContractsEndDate() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = fixture(admin);
        java.time.LocalDate ends = thisMonth().atEndOfMonth().plusDays(60);
        Map<String, Object> body = startedDaysAgo(perTeacherBody(f, director().userId(), "12000"), 40);
        body.put("endsOn", ends.toString());
        contractId(createContract(admin, f.schoolId(), body));

        Map<String, Object> row = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());

        assertThat(cell(row, 2).get("state")).isEqualTo("VALUE");
        assertThat(cell(row, 2).get("value")).isEqualTo(ends.toString());
    }

    @Test
    void attendancePercentEqualsTheAttendanceReportSchoolsRow() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "12000", "13000");
        UUID first = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID second = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        lockMonth(first, 20, 15, 2);
        lockMonth(second, 25, 25, 0);

        Map<String, Object> mine = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());
        Resp attendance = get("/api/v1/reports/attendance?month=" + thisMonth(), admin);
        assertThat(attendance.status()).as(attendance.body()).isEqualTo(200);
        Map<String, Object> theirs = rowIn(attendance, "schools", f.schoolId());

        // Attendance report Schools columns: teachers(0), working(1), present(2), absent(3), leave(4), lop(5), percent(6)
        BigDecimal reportPercent = number(cell(theirs, 6));
        assertThat(number(cell(mine, 3))).isEqualByComparingTo(reportPercent);
        assertThat(reportPercent).as("40 present of 45 working").isEqualByComparingTo("88.9");
    }

    @Test
    void substitutionDaysEqualTheSubstitutionsScreen() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "12000", "13000", "14000", "15000");
        UUID a = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID b = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        UUID c = mapTeacher(admin, f, 2, thisMonth().atDay(1));
        UUID d = mapTeacher(admin, f, 3, thisMonth().atDay(1));
        java.time.LocalDate day = thisMonth().atDay(1);
        requirement(a, f.schoolId(), day, "ASSIGNED", "1.0", b);
        requirement(c, f.schoolId(), day.plusDays(1), "ASSIGNED", "0.5", b);
        requirement(c, f.schoolId(), day.plusDays(2), "MANAGER_COVER", "1.0", null);
        requirement(d, f.schoolId(), day.plusDays(3), "OPEN", "1.0", null);
        requirement(d, f.schoolId(), day.plusDays(4), "CANCELLED", "1.0", null);

        Map<String, Object> row = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());

        // columns: coverSubstitute(4), coverManager(5), coverOpen(6)
        assertThat(number(cell(row, 4))).isEqualByComparingTo(screenDays(admin, f, "ASSIGNED")).isEqualByComparingTo("1.5");
        assertThat(number(cell(row, 5))).isEqualByComparingTo(screenDays(admin, f, "MANAGER_COVER")).isEqualByComparingTo("1.0");
        assertThat(number(cell(row, 6))).isEqualByComparingTo(screenDays(admin, f, "OPEN")).isEqualByComparingTo("1.0");
    }

    private void requirement(UUID teacher, UUID school, java.time.LocalDate day, String status, String dayValue, UUID substitute) {
        jdbc.update(
                "insert into substitution_requirement (id, teacher_id, school_id, duty_date, day_value, kind, status,"
                        + " substitute_teacher_id, cover_user_id, raised_at, updated_at, version)"
                        + " values (gen_random_uuid(), ?, ?, ?, ?::numeric, 'ABSENT', ?, ?, null, now(), now(), 0)",
                teacher, school, day, dayValue, status, substitute);
    }

    /** The day values the Substitutions screen lists for the School, month and status. */
    @SuppressWarnings("unchecked")
    private BigDecimal screenDays(String token, Fixture f, String status) {
        Resp resp = get("/api/v1/substitutions?status=" + status + "&schoolId=" + f.schoolId() + "&from=" + thisMonth().atDay(1)
                + "&to=" + thisMonth().atEndOfMonth() + "&size=100", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((List<Map<String, Object>>) resp.map().get("content")).stream()
                .map(r -> new BigDecimal(String.valueOf(r.get("dayValue"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void observationsAndOpenIssuesEqualTeacherPerformance() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "12000", "13000");
        UUID first = mapTeacher(admin, f, 0, thisMonth().atDay(1));
        UUID second = mapTeacher(admin, f, 1, thisMonth().atDay(1));
        String manager = f.manager().token();
        observe(manager, first, "GOOD");
        observe(manager, second, "NEEDS_IMPROVEMENT");

        Map<String, Object> row = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());

        String perf = "/api/v1/teacher-performance";
        Resp observations = get(perf + "/observations?schoolId=" + f.schoolId() + "&status=SUBMITTED&from=" + thisMonth().atDay(1)
                + "&to=" + thisMonth().atEndOfMonth(), admin);
        Resp issues = get(perf + "/issues?view=open&schoolId=" + f.schoolId(), admin);
        assertThat(observations.status()).as(observations.body()).isEqualTo(200);
        assertThat(issues.status()).as(issues.body()).isEqualTo(200);
        long screenObservations = ((Number) observations.map().get("totalElements")).longValue();
        long screenIssues = ((Number) issues.map().get("totalElements")).longValue();
        assertThat(screenObservations).isEqualTo(2);
        assertThat(screenIssues).isEqualTo(1);
        assertThat(number(cell(row, 7))).isEqualByComparingTo(BigDecimal.valueOf(screenObservations));
        assertThat(number(cell(row, 8))).isEqualByComparingTo(BigDecimal.valueOf(screenIssues));
    }

    private void observe(String token, UUID teacher, String result) {
        Resp started = post("/api/v1/teacher-performance/observations", token, Map.of("teacherId", teacher));
        assertThat(started.status()).as(started.body()).isEqualTo(201);
        String id = (String) started.map().get("id");
        Object version = get("/api/v1/teacher-performance/observations/" + id, token).map().get("version");
        Map<String, Integer> ratings = new LinkedHashMap<>();
        for (String area : List.of("ENGLISH_COMMUNICATION", "TEACHING_ABILITY", "CONTENT_DELIVERY", "CLASSROOM_MANAGEMENT",
                "STUDENT_ENGAGEMENT", "PREPARATION", "CONFIDENCE", "PUNCTUALITY")) {
            ratings.put(area, 4);
        }
        Map<String, Object> form = new HashMap<>();
        form.put("className", "Class 5");
        form.put("subject", "English");
        form.put("durationMinutes", 40);
        form.put("ratings", ratings);
        form.put("overallResult", result);
        form.put("strengths", "Clear voice and good rapport with the pupils");
        form.put("improvements", result.equals("GOOD") ? null : "Plan the lesson in steps and check understanding");
        form.put("version", version);
        Resp submitted = post("/api/v1/teacher-performance/observations/" + id + "/submit", token, form);
        assertThat(submitted.status()).as(submitted.body()).isEqualTo(200);
    }

    @Test
    void billedCollectedAndOutstandingEqualTheCollectionsReport() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "60000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(collect(admin, collectionBody(f.schoolId(), "25000")).status()).isEqualTo(201);

        Map<String, Object> mine = rowOf(report(admin, "&schoolId=" + f.schoolId()), f.schoolId());
        Resp collections = get("/api/v1/reports/collections?month=" + thisMonth() + "&schoolId=" + f.schoolId(), admin);
        assertThat(collections.status()).as(collections.body()).isEqualTo(200);
        Map<String, Object> theirs = rowIn(collections, "month", f.schoolId());

        // Collections month columns: zone(0), billed(1), collected(2), outstanding(3)
        assertThat(number(cell(mine, 9))).isEqualByComparingTo(number(cell(theirs, 1))).isEqualByComparingTo("60000");
        assertThat(number(cell(mine, 10))).isEqualByComparingTo(number(cell(theirs, 2))).isEqualByComparingTo("25000");
        assertThat(number(cell(mine, 11))).isEqualByComparingTo(number(cell(theirs, 3))).isEqualByComparingTo("35000");
    }

    @Test
    void aTeacherWhoMovedSchoolsCountsAtTheLastDaySchoolWhileAttendanceFollowsTheAttendanceReport() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture from = billedSchool(admin, "12000");
        Fixture to = billedSchool(admin, "13000");
        UUID teacher = mapTeacher(admin, from, 0, thisMonth().atDay(1));
        assertThat(assign(admin, teacher, to.schoolId(), positionId(admin, to.schoolId(), 0), thisMonth().atDay(2)).status()).isEqualTo(200);
        lockMonth(teacher, 20, 15, 0);

        Resp resp = report(admin, "");
        Map<String, Object> fromRow = rowOf(resp, from.schoolId());
        Map<String, Object> toRow = rowOf(resp, to.schoolId());
        Resp attendance = get("/api/v1/reports/attendance?month=" + thisMonth(), admin);
        assertThat(attendance.status()).as(attendance.body()).isEqualTo(200);

        assertThat(cell(fromRow, 1).get("value")).as("placed at the old School on the last day").isEqualTo(0);
        assertThat(cell(toRow, 1).get("value")).as("placed at the new School on the last day").isEqualTo(1);
        assertThat(number(cell(toRow, 3))).isEqualByComparingTo(number(cell(rowIn(attendance, "schools", to.schoolId()), 6)))
                .isEqualByComparingTo("75");
        assertThat(cell(fromRow, 3).get("state")).as("the Attendance report has no row for the old School").isEqualTo("NO_DATA");
        assertThat(rowsOf(attendance, "schools").stream().map(r -> r.get("id")).toList()).doesNotContain(from.schoolId().toString());
    }
}
