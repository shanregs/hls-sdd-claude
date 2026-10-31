package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 014a US2: the Payroll report equals the run total, the run lines and the dashboard tile; Zone Manager and Teacher are refused. */
class PayrollReportTest extends PayrollRunTestBase {

    private static final String REPORT = "/api/v1/reports/payroll";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal totalCell(Resp resp, String sectionKey, int index) {
        Map<String, Object> totals = (Map<String, Object>) section(resp, sectionKey).get("totals");
        return new BigDecimal(String.valueOf(((List<Map<String, Object>>) totals.get("cells")).get(index).get("value")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTotalsEqualTheRunTheRunLinesAndTheDashboardTile() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        Map<String, Object> totals = (Map<String, Object>) runOf(admin, draft.run()).get("totals");

        Resp resp = get(REPORT + "?month=" + month, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("monthState")).isEqualTo("DRAFT");
        assertThat(resp.body()).contains("not approved");
        for (String key : List.of("kinds", "zones", "schools")) {
            assertThat(totalCell(resp, key, 2)).as(key + " gross").isEqualByComparingTo(new BigDecimal(String.valueOf(totals.get("earned"))));
            assertThat(totalCell(resp, key, 3)).as(key + " lop").isEqualByComparingTo(new BigDecimal(String.valueOf(totals.get("deduction"))));
            assertThat(totalCell(resp, key, 5)).as(key + " net").isEqualByComparingTo(new BigDecimal(String.valueOf(totals.get("net"))));
        }
        Resp tile = get("/api/v1/dashboard/areas/PAYROLL/tiles/payroll.run?period=" + month, admin);
        List<Map<String, Object>> tileValues = (List<Map<String, Object>>) tile.map().get("values");
        BigDecimal tileNet = tileValues.stream()
                .filter(v -> "Total net".equals(v.get("label")))
                .map(v -> new BigDecimal(String.valueOf(v.get("value"))))
                .findFirst()
                .orElseThrow();
        assertThat(totalCell(resp, "kinds", 5)).isEqualByComparingTo(tileNet);
        BigDecimal lineNet = allLines(admin, draft.run()).stream()
                .filter(l -> l.get("net") != null)
                .map(l -> new BigDecimal(String.valueOf(l.get("net"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalCell(resp, "kinds", 5)).isEqualByComparingTo(lineNet);

        Resp filtered = get(REPORT + "?month=" + month + "&schoolId=" + draft.site().schoolId(), admin);
        assertThat(filtered.status()).as(filtered.body()).isEqualTo(200);
        BigDecimal twoTeachers = allLines(admin, draft.run()).stream()
                .filter(l -> draft.teacher().toString().equals(l.get("personId")) || draft.otherTeacher().toString().equals(l.get("personId")))
                .map(l -> new BigDecimal(String.valueOf(l.get("net"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalCell(filtered, "kinds", 5)).isEqualByComparingTo(twoTeachers);
        assertThat(twoTeachers).isEqualByComparingTo("57200");

        submitAndApprove(admin, draft.run());
        assertThat(get(REPORT + "?month=" + month, admin).map().get("monthState")).isEqualTo("APPROVED");
        assertThat(get(REPORT, admin).map().get("month")).isEqualTo(month.toString());
    }

    @Test
    void aMonthWithNoRunIsNoDataNeverZero() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = get(REPORT + "?month=" + month, admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(resp.map().get("monthState")).isEqualTo("NONE");
        assertThat(resp.body()).contains("No pay run for this month yet.");
        assertThat(section(resp, "kinds").get("totals")).isNull();
        assertThat(resp.body()).contains("NO_DATA").doesNotContain("\"value\":0");
    }

    @Test
    void aZoneManagerAndATeacherAreRefusedAndHaveNoMenuItem() {
        String admin = signInAs(Role.ADMIN).token();
        String manager = site(admin).managerToken();
        String teacher = signInAs(Role.TEACHER).token();

        for (String token : List.of(manager, teacher)) {
            assertThat(get(REPORT + "?month=" + month, token).status()).isEqualTo(403);
            assertThat(get(REPORT + "/export?month=" + month, token).status()).isEqualTo(403);
            assertThat(get("/api/v1/reports", token).body()).doesNotContain("\"key\":\"payroll\"");
        }
        assertThat(get("/api/v1/reports", admin).body()).contains("\"key\":\"payroll\"");
        assertThat(get("/api/v1/me/access-model", manager).body()).doesNotContain("Payroll Report");
        assertThat(get("/api/v1/me/access-model", admin).body()).contains("Payroll Report");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theExportEqualsTheScreenAndIsAudited() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);

        Resp csv = get(REPORT + "/export?month=" + month, admin);

        assertThat(csv.status()).as(csv.body()).isEqualTo(200);
        assertThat(csv.body()).startsWith("﻿Report,Payroll cost").contains("Month state,DRAFT");
        Map<String, Object> totals = (Map<String, Object>) runOf(admin, draft.run()).get("totals");
        String totalLine = java.util.Arrays.stream(csv.body().split("\\R")).filter(l -> l.startsWith("Total,")).findFirst().orElseThrow();
        String[] fields = totalLine.split(",");
        assertThat(new BigDecimal(fields[3])).isEqualByComparingTo(new BigDecimal(String.valueOf(totals.get("earned"))));
        assertThat(new BigDecimal(fields[6])).isEqualByComparingTo(new BigDecimal(String.valueOf(totals.get("net"))));
        assertChangeRecorded(admin, "REPORT_EXPORT_PAYROLL", "payroll", "exported");
    }
}
