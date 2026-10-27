package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 014a US4: a Teacher sees only their own attendance, leave and payslip history; there is no way to ask for another Teacher. */
class MyReportTest extends PayrollRunTestBase {

    private static final String REPORT = "/api/v1/reports/my";

    @BeforeEach
    void clean() {
        resetRuns();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Resp resp, String section) {
        return (List<Map<String, Object>>) ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> section.equals(s.get("key")))
                .findFirst()
                .orElseThrow()
                .get("rows");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTeacherSeesTheirOwnAttendanceAndPayslipHistoryOnly() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        Signed me = signInAs(Role.TEACHER);
        Resp linked = put("/api/v1/teachers/" + draft.teacher() + "/user", admin, Map.of("userId", me.userId()));
        assertThat(linked.status()).as(linked.body()).isEqualTo(200);
        submitAndApprove(admin, draft.run());
        BigDecimal net = new BigDecimal(String.valueOf(lineOf(admin, draft.run(), draft.teacher()).get("net")));

        Resp resp = get(REPORT + "?month=" + month, me.token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(rows(resp, "attendance")).hasSize(6);
        Map<String, Object> thisMonth = rows(resp, "attendance").get(0);
        assertThat(thisMonth.get("id")).isEqualTo(month.toString());
        List<Map<String, Object>> cells = (List<Map<String, Object>>) thisMonth.get("cells");
        assertThat(new BigDecimal(String.valueOf(cells.get(0).get("value")))).isEqualByComparingTo("20");
        assertThat(new BigDecimal(String.valueOf(cells.get(1).get("value")))).isEqualByComparingTo("20");
        assertThat(rows(resp, "payslips")).hasSize(1);
        List<Map<String, Object>> slipCells = (List<Map<String, Object>>) rows(resp, "payslips").get(0).get("cells");
        assertThat(slipCells.get(0).get("value")).isEqualTo("APPROVED");
        assertThat(new BigDecimal(String.valueOf(slipCells.get(4).get("value")))).isEqualByComparingTo(net);
        assertThat(resp.body()).doesNotContain(draft.otherTeacher().toString());
    }

    @Test
    void aTeacherCannotReachAnotherTeacherByFilterOrExport() {
        String admin = signInAs(Role.ADMIN).token();
        Draft draft = readyDraft(admin);
        String me = signInAs(Role.TEACHER).token();

        assertThat(get(REPORT + "?month=" + month + "&teacherId=" + draft.otherTeacher(), me).status()).isEqualTo(400);
        assertThat(get(REPORT + "?month=" + month + "&zoneId=" + java.util.UUID.randomUUID(), me).status()).isEqualTo(400);
        assertThat(get(REPORT + "/export?month=" + month, me).status()).isEqualTo(403);
        assertThat(get("/api/v1/reports/attendance?month=" + month, me).status()).isEqualTo(403);
        Resp own = get(REPORT + "?month=" + month, me);
        assertThat(own.status()).as(own.body()).isEqualTo(200);
        assertThat(rows(own, "payslips")).isEmpty();
        assertThat(get("/api/v1/reports", me).body()).contains("\"key\":\"my\"");
        assertThat(get("/api/v1/reports", admin).body()).doesNotContain("\"key\":\"my\"");
    }
}
