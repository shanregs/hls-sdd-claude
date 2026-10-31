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
 * Spec 014b US2: the Collections report equals School Billing: billed less collected is outstanding, the ageing buckets add up
 * to outstanding, totals are the sums of the School rows, and a Zone Manager sees only their Schools.
 */
class CollectionsReportTest extends ReceivablesTestBase {

    private static final String REPORT = "/api/v1/reports/collections";

    private Resp collections(String token, String query) {
        Resp resp = get(REPORT + "?month=" + thisMonth() + query, token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp;
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

    @SuppressWarnings("unchecked")
    private static BigDecimal value(Map<String, Object> row, int index) {
        Map<String, Object> cell = ((List<Map<String, Object>>) row.get("cells")).get(index);
        assertThat(cell.get("state")).as(cell.toString()).isEqualTo("VALUE");
        return new BigDecimal(String.valueOf(cell.get("value")));
    }

    private static Map<String, Object> rowOf(List<Map<String, Object>> rows, UUID schoolId) {
        return rows.stream().filter(r -> schoolId.toString().equals(r.get("id"))).findFirst().orElseThrow();
    }

    // month columns: zone(0), billed(1), collected(2), outstanding(3), dueOn(4)
    // ageing columns: zone(0), outstanding(1), notYetDue(2), 0-30(3), 31-60(4), 61-90(5), over90(6)

    @Test
    void billedLessCollectedIsOutstanding() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "60000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        assertThat(collect(admin, collectionBody(f.schoolId(), "25000")).status()).isEqualTo(201);

        Map<String, Object> row = rowOf(rows(collections(admin, "&schoolId=" + f.schoolId()), "month"), f.schoolId());

        assertThat(value(row, 1)).isEqualByComparingTo("60000");
        assertThat(value(row, 2)).isEqualByComparingTo("25000");
        assertThat(value(row, 3)).isEqualByComparingTo("35000");
    }

    @Test
    void ageingBucketsAddUpAndZoneAndOrganizationTotalsAreTheSumOfSchools() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchoolSince(admin, 80, "10000");
        mapTeacher(admin, f, 0, thisMonth().minusMonths(1).atDay(1));
        UUID previous = raiseOne(admin, thisMonth().minusMonths(1), f.schoolId());
        UUID current = raiseOne(admin, thisMonth(), f.schoolId());
        makeOverdue(previous, 45);
        makeOverdue(current, -10);

        Resp resp = collections(admin, "&schoolId=" + f.schoolId());
        Map<String, Object> row = rowOf(rows(resp, "ageing"), f.schoolId());

        assertThat(value(row, 2)).as("not yet due").isEqualByComparingTo("10000");
        assertThat(value(row, 4)).as("31 to 60").isEqualByComparingTo("10000");
        BigDecimal parts = BigDecimal.ZERO;
        for (int i = 2; i <= 6; i++) {
            parts = parts.add(value(row, i));
        }
        assertThat(parts).isEqualByComparingTo(value(row, 1));
        @SuppressWarnings("unchecked")
        Map<String, Object> total = (Map<String, Object>) section(resp, "ageing").get("totals");
        assertThat(value(total, 1)).isEqualByComparingTo(value(row, 1));
    }

    @Test
    void aSchoolNeverBilledHasNoRow() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedSchool(admin, "15000");

        Resp resp = collections(admin, "&schoolId=" + f.schoolId());

        assertThat(rows(resp, "month")).isEmpty();
        assertThat(rows(resp, "ageing")).isEmpty();
    }

    @Test
    void aZoneManagerSeesOnlyTheirSchools() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture mine = billedSchool(admin, "15000");
        Fixture theirs = billedSchool(admin, "16000");
        mapTeacher(admin, mine, 0, thisMonth().atDay(1));
        mapTeacher(admin, theirs, 0, thisMonth().atDay(1));
        raise(admin, thisMonth(), mine.schoolId(), theirs.schoolId());

        String manager = mine.manager().token();
        Resp resp = collections(manager, "");

        assertThat(rowOf(rows(resp, "month"), mine.schoolId())).isNotNull();
        assertThat(resp.body()).doesNotContain(theirs.schoolId().toString());
        assertThat(get(REPORT + "?month=" + thisMonth() + "&zoneId=" + theirs.zoneId(), manager).status()).isIn(403, 404);
    }
}
