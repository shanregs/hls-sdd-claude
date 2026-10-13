package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 037 US2: the grid's default year, filters, search, sort, paging, scope and a constant number of statements. */
class WorkingDaysGridTest extends WorkingDaysTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @Autowired
    private SessionFactory sessionFactory;

    private String schoolName(String admin, UUID school) {
        return (String) get("/api/v1/schools/" + school, admin).map().get("name");
    }

    private static List<Object> field(List<Map<String, Object>> rows, String name) {
        return rows.stream().map(r -> r.get(name)).toList();
    }

    @Test
    void theDefaultViewIsTheCurrentYear() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] a = schoolInNewZone(admin);
        UUID thisYearRow = createRowOk(admin, a[2], thisYear(), 3, 26);
        UUID otherYearRow = createRowOk(admin, a[2], thisYear() + 1, 3, 25);

        Resp page = get(BASE + "?schoolId=" + a[2], admin);
        assertThat(field(items(page), "id")).containsExactly(thisYearRow.toString());
        assertThat(field(items(listRows(admin, thisYear() + 1, a[2])), "id")).containsExactly(otherYearRow.toString());
        assertThat(items(page).get(0))
                .containsKeys("schoolName", "zoneName", "daysInMonth", "calendarDerived", "updatedBy", "updatedAt");
    }

    @Test
    void filtersByYearSchoolZoneAndSearchByNameAreAppliedTogether() {
        String admin = signInAs(Role.ADMIN).token();
        UUID[] a = schoolInNewZone(admin);
        UUID[] b = schoolInNewZone(admin);
        UUID rowA = createRowOk(admin, a[2], thisYear(), 3, 26);
        UUID rowB = createRowOk(admin, b[2], thisYear(), 3, 24);

        assertThat(field(items(get(BASE + "?size=100&zoneId=" + a[0], admin)), "id"))
                .containsExactly(rowA.toString());
        assertThat(field(items(get(BASE + "?size=100&schoolId=" + b[2], admin)), "id"))
                .containsExactly(rowB.toString());
        assertThat(field(items(get(BASE + "?size=100&schoolId=" + b[2] + "&zoneId=" + a[0], admin)), "id")).isEmpty();

        String name = schoolName(admin, a[2]);
        String fragment = name.substring(name.length() - 6).toUpperCase();
        List<Object> found = field(items(get(BASE + "?size=100&q=" + fragment, admin)), "id");
        assertThat(found).contains(rowA.toString()).doesNotContain(rowB.toString());
    }

    @Test
    void sortsByWorkingDaysMonthAndSchoolInEitherDirection() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        createRowOk(admin, school, thisYear(), 5, 20);
        createRowOk(admin, school, thisYear(), 2, 10);
        createRowOk(admin, school, thisYear(), 8, 15.5);

        String base = BASE + "?size=100&schoolId=" + school + "&sort=";
        assertThat(field(items(get(base + "workingDays,asc", admin)), "month")).containsExactly(2, 8, 5);
        assertThat(field(items(get(base + "workingDays,desc", admin)), "month")).containsExactly(5, 8, 2);
        assertThat(field(items(get(base + "month", admin)), "month")).containsExactly(2, 5, 8);
        assertThat(field(items(get(base + "month,desc", admin)), "month")).containsExactly(8, 5, 2);
        for (String sort : List.of("school", "zone", "updatedAt,desc")) {
            assertThat(items(get(base + sort, admin))).hasSize(3);
        }
        assertThat(get(base + "nonsense", admin).status()).isEqualTo(400);
    }

    @Test
    void pagingReturnsTheTotalAndKeepsTheFilters() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        for (int month = 1; month <= 5; month++) {
            createRowOk(admin, school, thisYear(), month, 20);
        }
        String base = BASE + "?schoolId=" + school + "&sort=month&size=2&page=";
        Resp first = get(base + 0, admin);
        Resp third = get(base + 2, admin);
        assertThat(((Number) first.map().get("total")).intValue()).isEqualTo(5);
        assertThat(field(items(first), "month")).containsExactly(1, 2);
        assertThat(field(items(get(base + 1, admin)), "month")).containsExactly(3, 4);
        assertThat(field(items(third), "month")).containsExactly(5);
        assertThat(get(BASE + "?size=101", admin).status()).isEqualTo(400);
    }

    @Test
    void aYearWithNoRowsIsAnEmptyList() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        Resp page = listRows(admin, thisYear() + 4, school);
        assertThat(items(page)).isEmpty();
        assertThat(((Number) page.map().get("total")).intValue()).isZero();
    }

    @Test
    void aManagerGrantedViewSeesOnlyTheirSchoolsInTheItemsAndTheTotal() {
        World w = newWorld();
        createRowOk(w.admin(), w.schoolA(), thisYear(), 3, 26);
        createRowOk(w.admin(), w.schoolB(), thisYear(), 3, 24);
        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor);
        try {
            Resp page = get(BASE + "?size=100", w.managerA().token());
            assertThat(field(items(page), "schoolId")).containsOnly(w.schoolA().toString());
            assertThat(((Number) page.map().get("total")).intValue()).isEqualTo(1);
            assertThat(field(items(get(BASE + "?size=100&zoneId=" + w.zoneB(), w.managerA().token())), "id")).isEmpty();
        } finally {
            matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, false, actor);
        }
    }

    @Test
    void theNumberOfStatementsAndTheTimeDoNotGrowWithTheNumberOfSchools() {
        String admin = signInAs(Role.ADMIN).token();
        int fewYear = thisYear() + 1;
        int manyYear = thisYear() + 2;
        List<UUID> schools = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            schools.add(schoolInNewZone(admin)[2]);
        }
        for (int month = 1; month <= 12; month++) {
            for (UUID school : schools) {
                jdbc.update(
                        "insert into school_working_days (id, school_id, year, month, working_days, version, created_by,"
                                + " created_at, updated_by, updated_at) values (?, ?, ?, ?, 20, 0, ?, now(), ?, now())",
                        UUID.randomUUID(), school, manyYear, month, UUID.randomUUID(), UUID.randomUUID());
            }
        }
        createRowOk(admin, schools.get(0), fewYear, 3, 20);
        createRowOk(admin, schools.get(1), fewYear, 3, 20);

        get(BASE + "?year=" + fewYear + "&size=25", admin); // warm caches
        var stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        assertThat(get(BASE + "?year=" + fewYear + "&size=25", admin).status()).isEqualTo(200);
        long few = stats.getPrepareStatementCount();
        stats.clear();
        long started = System.nanoTime();
        Resp many = get(BASE + "?year=" + manyYear + "&size=25", admin);
        long millis = (System.nanoTime() - started) / 1_000_000;
        long manyStatements = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);

        assertThat(many.status()).isEqualTo(200);
        assertThat(((Number) many.map().get("total")).intValue()).isEqualTo(40 * 12);
        assertThat(manyStatements).isEqualTo(few);
        assertThat(millis).as("grid page time in ms").isLessThan(2000);
    }
}
