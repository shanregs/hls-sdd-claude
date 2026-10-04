package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 008 US4: the organization-wide grid with filters, corrections and its authorization. */
class AttendanceGridControllerTest extends AttendanceTestBase {

    private static final String GRID = "/api/v1/attendance/grid";

    private String month() {
        return YearMonth.from(today()).toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> teacherIds(String token, String url) {
        Resp resp = get(url, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return ((List<Map<String, Object>>) resp.map().get("content"))
                .stream().map(r -> (String) r.get("teacherId")).toList();
    }

    @Test
    void adminAndDirectorSeeTeachersAcrossZonesAndCanFilterBySchoolZoneAndManager() {
        World w = newWorld();
        String a = w.teacherA().teacherId().toString();
        String b = w.teacherB().teacherId().toString();

        for (String token : List.of(w.admin(), w.director())) {
            assertThat(teacherIds(token, GRID + "?month=" + month() + "&size=100&schoolId=" + w.schoolA()))
                    .containsExactly(a);
            assertThat(teacherIds(token, GRID + "?month=" + month() + "&zoneId=" + w.zoneB())).containsExactly(b);
            assertThat(teacherIds(token, GRID + "?month=" + month() + "&managerId=" + w.managerA().managerId()))
                    .containsExactly(a);
        }
    }

    @Test
    void filtersCombineWithAnd() {
        World w = newWorld();

        List<String> matching = teacherIds(
                w.admin(), GRID + "?month=" + month() + "&schoolId=" + w.schoolA() + "&managerId=" + w.managerA().managerId());
        List<String> conflicting = teacherIds(
                w.admin(), GRID + "?month=" + month() + "&schoolId=" + w.schoolA() + "&managerId=" + w.managerB().managerId());

        assertThat(matching).containsExactly(w.teacherA().teacherId().toString());
        assertThat(conflicting).isEmpty();
    }

    @Test
    void searchByNameAndFilterByStatusNarrowTheGridAndTheTotals() {
        World w = newWorld();
        UUID teacherId = w.teacherA().teacherId();
        String name = (String) get("/api/v1/teachers/" + teacherId, w.admin()).map().get("name");

        Resp byName = get(GRID + "?month=" + month() + "&query=" + name.substring(name.lastIndexOf(' ') + 1), w.admin());
        Resp wrongStatus = get(GRID + "?month=" + month() + "&schoolId=" + w.schoolA() + "&status=EXITED", w.admin());
        Resp rightStatus = get(GRID + "?month=" + month() + "&schoolId=" + w.schoolA() + "&status=ACTIVE", w.admin());

        assertThat(byName.map()).containsEntry("totalElements", 1);
        assertThat(wrongStatus.map()).containsEntry("totalElements", 0);
        assertThat(rightStatus.map()).containsEntry("totalElements", 1);
    }

    @Test
    void anAdminCorrectionIsAttributedToTheAdminAndVisibleToTheTeacher() {
        World w = newWorld();
        LocalDate date = today().minusDays(9);

        Resp marked = put(
                "/api/v1/attendance/teachers/" + w.teacherA().teacherId() + "/marks/" + date,
                w.admin(),
                Map.of("statusCode", "P", "dayValue", 1));
        Resp teacherView = get("/api/v1/attendance/me?month=" + YearMonth.from(date), w.teacherA().token());

        assertThat(marked.status()).as(marked.body()).isEqualTo(200);
        assertThat(teacherView.body()).contains("\"setByKind\":\"SUPERVISOR\"");
        assertThat(marked.map().get("setByUserId")).isNotEqualTo(w.teacherA().signed().userId().toString());
    }

    @Test
    void managerTeacherAndSystemCannotUseTheOrganizationGrid() {
        World w = newWorld();
        for (String token : List.of(w.managerA().token(), w.teacherA().token(), signInAs(Role.SYSTEM).token())) {
            assertThat(get(GRID + "?month=" + month(), token).status()).isEqualTo(403);
        }
        assertThat(get(GRID + "?month=" + month(), null).status()).isEqualTo(401);
    }

    @Test
    void thePageSizeIsCappedAndAMonthWithNobodyPlacedIsAnEmptyPageNotAnError() {
        World w = newWorld();

        Resp big = get(GRID + "?month=" + month() + "&size=1000", w.admin());
        Resp empty = get(GRID + "?month=2019-01", w.admin());

        assertThat(big.map()).containsEntry("size", 100);
        assertThat(empty.status()).isEqualTo(200);
        assertThat(empty.map()).containsEntry("totalElements", 0).containsEntry("days", 31);
        assertThat(get(GRID + "?month=not-a-month", w.admin()).status()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyMarkedCellCarriesItsStatusCategoryForColouring() {
        World w = newWorld();
        YearMonth month = YearMonth.from(today()).minusMonths(1);
        String teacher = w.teacherA().teacherId().toString();
        for (Object[] mark : new Object[][] {{10, "P"}, {11, "L"}, {12, "A"}, {13, "H"}, {14, "S"}}) {
            Resp resp = put(
                    "/api/v1/attendance/teachers/" + teacher + "/marks/" + month.atDay((Integer) mark[0]),
                    w.managerA().token(),
                    Map.of("statusCode", mark[1], "dayValue", 1));
            assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        }

        Resp resp = get(GRID + "?month=" + month + "&schoolId=" + w.schoolA(), w.admin());

        List<Map<String, Object>> cells = (List<Map<String, Object>>)
                ((Map<String, Object>) ((List<Object>) resp.map().get("content")).get(0)).get("cells");
        Map<String, String> categoryByCode = new java.util.HashMap<>();
        for (Map<String, Object> cell : cells) {
            if (cell.get("code") != null) {
                categoryByCode.put((String) cell.get("code"), (String) cell.get("category"));
            } else {
                assertThat(cell.get("category")).as("unmarked cell %s", cell.get("date")).isNull();
            }
        }
        assertThat(categoryByCode)
                .containsEntry("P", "WORKED")
                .containsEntry("S", "WORKED")
                .containsEntry("L", "LEAVE")
                .containsEntry("A", "LEAVE")
                .containsEntry("H", "NON_WORKING");
    }

    @Test
    void aPageOf500TeachersLoadsQuicklyWithBulkQueries() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        jdbc.update(
                "insert into teacher (id, name, phone, status, status_effective_on, version, created_at, updated_at)"
                        + " select gen_random_uuid(), 'Bulk ' || n, null, 'ACTIVE', current_date - 60, 0, now(), now()"
                        + " from generate_series(1, 500) n");
        jdbc.update(
                "insert into teacher_placement (id, teacher_id, school_id, starts_on, status, created_at)"
                        + " select gen_random_uuid(), t.id, ?, current_date - 60, 'ACTIVE', now()"
                        + " from teacher t where t.name like 'Bulk %' and not exists"
                        + " (select 1 from teacher_placement p where p.teacher_id = t.id)",
                school);

        long started = System.nanoTime();
        Resp page = get(GRID + "?month=" + month() + "&size=50&schoolId=" + school, admin);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(page.status()).isEqualTo(200);
        assertThat(page.map().get("totalElements")).isEqualTo(500);
        assertThat(millis).as("first page of a 500-teacher grid").isLessThan(3000);
        // Remove the bulk rows so other tests' org-wide counts stay small.
        jdbc.update("delete from teacher_placement where teacher_id in (select id from teacher where name like 'Bulk %')");
        jdbc.update("delete from teacher where name like 'Bulk %'");
    }
}
