package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.AttendanceTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Spec 014c US3: the Teacher report lists the right Teachers once, marks the ones who exited in the month, shows "not placed"
 * for a Teacher without a placement, counts by status exactly what it lists, and scopes a Zone Manager by the placement on the
 * last day of the month.
 */
class TeacherReportTest extends AttendanceTestBase {

    private static final String REPORT = "/api/v1/reports/teacher";

    private YearMonth month() {
        return YearMonth.from(today().minusDays(5));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String key) {
        return (List<Map<String, Object>>) section(resp, key).get("rows");
    }

    private static List<String> teacherIds(Resp resp) {
        return rows(resp, "teachers").stream().map(r -> (String) r.get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Object cellValue(Map<String, Object> row, int index) {
        return ((List<Map<String, Object>>) row.get("cells")).get(index).get("value");
    }

    @SuppressWarnings("unchecked")
    private static String cellState(Map<String, Object> row, int index) {
        return (String) ((List<Map<String, Object>>) row.get("cells")).get(index).get("state");
    }

    private static Map<String, Object> row(Resp resp, UUID teacher) {
        return rows(resp, "teachers").stream().filter(r -> teacher.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    private UUID inTraining(String admin) {
        Resp resp = post("/api/v1/teachers", admin, Map.of("name", uniqueName("Trainee"), "phone", "9444444444", "status", "IN_TRAINING"));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    @Test
    void statusCountsAddUpToTheRowsAndExitedInTheMonthIsListedOnceAndMarked() {
        World w = newWorld();
        UUID exitedInMonth = newTeacher(w.admin(), w.schoolA(), 60).teacherId();
        assertThat(exitTeacher(w.admin(), exitedInMonth, today().minusDays(5)).status()).isEqualTo(200);
        UUID exitedEarlier = newTeacher(w.admin(), w.schoolA(), 120).teacherId();
        assertThat(exitTeacher(w.admin(), exitedEarlier, today().minusDays(70)).status()).isEqualTo(200);
        UUID trainee = inTraining(w.admin());

        Resp resp = get(REPORT + "?month=" + month(), w.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<String> ids = teacherIds(resp);
        assertThat(ids).contains(w.teacherA().teacherId().toString(), w.teacherB().teacherId().toString(), exitedInMonth.toString(), trainee.toString())
                .doesNotContain(exitedEarlier.toString());
        assertThat(ids).doesNotHaveDuplicates();
        Map<String, Object> exited = row(resp, exitedInMonth);
        assertThat(cellValue(exited, 0)).isEqualTo("Exited");
        assertThat(cellValue(exited, 1)).isEqualTo("Exited");
        assertThat(cellValue(exited, 2)).isNotEqualTo("Not placed");
        assertThat(cellValue(row(resp, w.teacherA().teacherId()), 0)).isEqualTo("Active");
        Map<String, Object> notPlaced = row(resp, trainee);
        assertThat(cellValue(notPlaced, 0)).isEqualTo("In training");
        assertThat(cellValue(notPlaced, 2)).isEqualTo("Not placed");
        assertThat(cellValue(notPlaced, 3)).isEqualTo("Not placed");
        // status counts add up to the rows shown
        long counted = rows(resp, "byStatus").stream().mapToLong(r -> ((Number) cellValue(r, 0)).longValue()).sum();
        assertThat(counted).isEqualTo(ids.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) section(resp, "byStatus").get("totals");
        assertThat(((Number) cellValue(totals, 0)).longValue()).isEqualTo(ids.size());
        Map<String, Long> byStatus = rows(resp, "teachers").stream().collect(Collectors.groupingBy(r -> (String) cellValue(r, 0), Collectors.counting()));
        for (Map<String, Object> r : rows(resp, "byStatus")) {
            assertThat(((Number) cellValue(r, 0)).longValue()).as(r.get("label").toString())
                    .isEqualTo(byStatus.getOrDefault((String) r.get("label"), 0L));
        }
        // tenure, performance, movements and training are real numbers, not blanks
        Map<String, Object> active = row(resp, w.teacherA().teacherId());
        assertThat(cellState(active, 4)).isEqualTo("VALUE");
        assertThat(((Number) cellValue(active, 4)).longValue()).isGreaterThanOrEqualTo(50);
        for (int i = 5; i <= 9; i++) {
            assertThat(cellState(active, i)).as("column " + i).isEqualTo("VALUE");
        }
    }

    @Test
    void aZoneManagerSeesOnlyTheirTeachersAndAMidMonthMoverAppearsOnceForTheLastPlacement() {
        World w = newWorld();
        UUID mover = newTeacher(w.admin(), w.schoolA(), 60).teacherId();
        assertThat(placeTeacherRaw(w.admin(), mover, w.schoolB(), today().minusDays(3)).status()).isEqualTo(200);
        String a = w.managerA().token();
        String b = w.managerB().token();
        String month = YearMonth.from(today().minusDays(3)).toString();

        Resp asA = get(REPORT + "?month=" + month, a);
        Resp asB = get(REPORT + "?month=" + month, b);
        Resp all = get(REPORT + "?month=" + month, w.admin());

        assertThat(asA.status()).as(asA.body()).isEqualTo(200);
        assertThat(teacherIds(asA)).contains(w.teacherA().teacherId().toString())
                .doesNotContain(w.teacherB().teacherId().toString(), mover.toString());
        assertThat(teacherIds(asB)).contains(w.teacherB().teacherId().toString(), mover.toString())
                .doesNotContain(w.teacherA().teacherId().toString());
        assertThat(teacherIds(all).stream().filter(mover.toString()::equals).count()).isEqualTo(1);
        assertThat(cellValue(row(all, mover), 2)).isEqualTo(cellValue(row(asB, w.teacherB().teacherId()), 2));
        // totals of the status counts cover only the Manager's own Teachers
        long counted = rows(asA, "byStatus").stream().mapToLong(r -> ((Number) cellValue(r, 0)).longValue()).sum();
        assertThat(counted).isEqualTo(teacherIds(asA).size());
        assertThat(asA.body()).doesNotContain(w.teacherB().teacherId().toString()).doesNotContain(mover.toString());
        // filters outside the scope are refused
        assertThat(get(REPORT + "?month=" + month + "&zoneId=" + w.zoneB(), a).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month + "&schoolId=" + w.schoolB(), a).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month + "&teacherId=" + w.teacherB().teacherId(), a).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month + "&managerId=" + w.managerB().managerId(), a).status()).isEqualTo(403);
        assertThat(get(REPORT + "?month=" + month + "&teacherId=" + w.teacherA().teacherId(), a).status()).isEqualTo(200);
    }

    @Test
    void theZoneSchoolAndManagerFiltersNarrowTheListForAdmin() {
        World w = newWorld();
        String month = month().toString();

        List<String> byZone = teacherIds(get(REPORT + "?month=" + month + "&zoneId=" + w.zoneA(), w.admin()));
        List<String> bySchool = teacherIds(get(REPORT + "?month=" + month + "&schoolId=" + w.schoolB(), w.admin()));
        List<String> byManager = teacherIds(get(REPORT + "?month=" + month + "&managerId=" + w.managerA().managerId(), w.admin()));

        assertThat(byZone).contains(w.teacherA().teacherId().toString()).doesNotContain(w.teacherB().teacherId().toString());
        assertThat(bySchool).contains(w.teacherB().teacherId().toString()).doesNotContain(w.teacherA().teacherId().toString());
        assertThat(byManager).contains(w.teacherA().teacherId().toString()).doesNotContain(w.teacherB().teacherId().toString());
    }

    @Test
    void teacherAndSystemAreRefusedAndHaveNoMenuItem() {
        World w = newWorld();

        assertThat(get(REPORT, w.teacherA().token()).status()).isEqualTo(403);
        assertThat(get("/api/v1/reports", w.teacherA().token()).body()).doesNotContain("\"key\":\"teacher\"");
        assertThat(get("/api/v1/reports", w.managerA().token()).body()).contains("\"key\":\"teacher\"");
    }
}
