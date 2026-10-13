package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 037 US5: copy a School's figures from one year to another without overwriting anything. */
class YearCopyTest extends WorkingDaysTestBase {

    private static final String COPY = BASE + "/copy";

    private Resp copy(String token, UUID school, int from, int to) {
        return post(COPY, token, Map.of("schoolId", school, "fromYear", from, "toYear", to));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp, String key) {
        return (List<Map<String, Object>>) resp.map().get(key);
    }

    @Test
    void copiesOnlyMonthsWithoutARowAndReportsTheRestWithTheirReason() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int from = thisYear();
        int to = thisYear() + 1;
        createRowOk(admin, school, from, 1, 26);
        createRowOk(admin, school, from, 3, 25);
        createRowOk(admin, school, from, 12, 24.5);
        UUID existing = createRowOk(admin, school, to, 3, 20);

        Resp result = copy(admin, school, from, to);
        assertThat(result.status()).as(result.body()).isEqualTo(200);
        assertThat(list(result, "created")).extracting(c -> c.get("month")).containsExactly(1, 12);
        assertThat(list(result, "skipped")).hasSize(1);
        assertThat(list(result, "skipped").get(0)).containsEntry("month", 3).containsEntry("reason", "EXISTS");

        List<Map<String, Object>> rows = items(listRows(admin, to, school));
        assertThat(rows).extracting(r -> r.get("month")).containsExactlyInAnyOrder(1, 3, 12);
        Map<String, Object> march = rows.stream().filter(r -> r.get("month").equals(3)).findFirst().orElseThrow();
        assertThat(march).containsEntry("id", existing.toString());
        assertThat(((Number) march.get("workingDays")).doubleValue()).isEqualTo(20.0);
    }

    @Test
    void aFigureThatDoesNotFitTheTargetMonthIsSkippedAndReported() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        // 2028 is a leap year (29 days in February); 2027 and 2029 are not. Source 2028, target 2029 (inside any window
        // that includes both), so 29 working days do not fit February 2029 but 29 does fit February 2028.
        int leap = 2028;
        int ordinary = leap + 1;
        assumeTrue(
                Math.abs(thisYear() - leap) <= 5 && Math.abs(thisYear() - ordinary) <= 5,
                "the leap year pair must fall inside the 5 year window around today");
        createRowOk(admin, school, leap, 2, 29);
        createRowOk(admin, school, leap, 4, 30);

        Resp result = copy(admin, school, leap, ordinary);
        assertThat(list(result, "created")).extracting(c -> c.get("month")).containsExactly(4);
        assertThat(list(result, "skipped")).hasSize(1);
        assertThat(list(result, "skipped").get(0))
                .containsEntry("month", 2)
                .containsEntry("reason", "EXCEEDS_DAYS")
                .containsEntry("daysInMonth", 28);

        UUID other = schoolInNewZone(admin)[2];
        createRowOk(admin, other, ordinary, 2, 28);
        Resp back = copy(admin, other, ordinary, leap);
        assertThat(list(back, "created")).extracting(c -> c.get("month")).containsExactly(2);
    }

    @Test
    void eachCreatedRowIsAuditedAndRecordedAsACopyAndSkippedRowsAreNot() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int from = thisYear();
        int to = thisYear() + 1;
        createRowOk(admin, school, from, 1, 20);
        createRowOk(admin, school, from, 2, 20);
        createRowOk(admin, school, to, 2, 15);
        long before = historyCount(school);

        Resp result = copy(admin, school, from, to);
        assertThat(list(result, "created")).hasSize(1);
        assertThat(historyCount(school)).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from school_working_days_history where school_id = ? and action = 'COPY'",
                        Long.class,
                        school))
                .isEqualTo(1);
        UUID copied = UUID.fromString((String) items(listRows(admin, to, school)).stream()
                .filter(r -> r.get("month").equals(1))
                .findFirst()
                .orElseThrow()
                .get("id"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(auditCount(copied)).isEqualTo(1));
    }

    @Test
    void badRequestsAreRefusedAndWriteNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear();
        createRowOk(admin, school, year, 1, 20);
        long before = historyCount(school);

        assertThat(copy(admin, school, year, year).status()).isEqualTo(400);
        assertThat(copy(admin, school, year, year + 6).status()).isEqualTo(400);
        assertThat(copy(admin, school, year + 2, year + 1).status()).isEqualTo(400); // nothing to copy from
        assertThat(copy(admin, UUID.randomUUID(), year, year + 1).status()).isEqualTo(400);
        assertThat(post(COPY, admin, Map.of("schoolId", school, "fromYear", year)).status()).isEqualTo(400);
        assertThat(historyCount(school)).isEqualTo(before);
    }

    @Test
    void createPermissionIsRequiredAndTheDirectorMayCopy() {
        World w = newWorld();
        int year = thisYear();
        createRowOk(w.admin(), w.schoolA(), year, 1, 20);

        assertThat(copy(w.director(), w.schoolA(), year, year + 1).status()).isEqualTo(200);
        assertThat(copy(w.managerA().token(), w.schoolA(), year, year + 2).status()).isEqualTo(403);
        assertThat(copy(signInAs(Role.TEACHER).token(), w.schoolA(), year, year + 2).status()).isEqualTo(403);
        assertThat(copy(signInAs(Role.SYSTEM).token(), w.schoolA(), year, year + 2).status()).isEqualTo(403);
        assertThat(items(listRows(w.admin(), year + 2, w.schoolA()))).isEmpty();
    }
}
