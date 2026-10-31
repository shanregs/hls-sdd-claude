package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.schoolbilling.ReceivablesTestBase;
import java.math.BigDecimal;
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
}
