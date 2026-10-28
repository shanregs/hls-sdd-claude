package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.hls.expenses.api.ExpenseQueries;
import com.hls.identity.user.Role;
import com.hls.performance.api.PerformanceSummary;
import com.hls.recruitment.api.DemandFigures;
import com.hls.support.AttendanceTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec 014c SC-005 for the Teacher, Expenses and Recruitment reports (the Manager report has its own test): a source that is
 * down shows "not available" for its own figures or its own section, and everything else on the page still loads. The
 * Manager report is covered by {@link ManagerReportSourceDownTest}.
 */
class ReportSourceDownTest extends AttendanceTestBase {

    @MockitoSpyBean
    private PerformanceSummary performance;

    @MockitoSpyBean
    private ExpenseQueries expenseQueries;

    @MockitoSpyBean
    private DemandFigures demand;

    @AfterEach
    void restore() {
        reset(performance, expenseQueries, demand);
    }

    private YearMonth month() {
        return YearMonth.from(today().minusDays(1));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Resp resp, String key) {
        return ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> key.equals(s.get("key")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("rows");
    }

    @SuppressWarnings("unchecked")
    private static String state(Map<String, Object> row, int index) {
        return (String) ((List<Map<String, Object>>) row.get("cells")).get(index).get("state");
    }

    @Test
    void aTeacherReportWhosePerformanceSourceIsDownShowsNotAvailableForThoseColumnsOnly() {
        World w = newWorld();
        UUID teacher = newTeacher(w.admin(), w.schoolA(), 150).teacherId();
        doThrow(new IllegalStateException("source is down")).when(performance).byTeacher(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        Resp resp = get("/api/v1/reports/teacher?month=" + month() + "&schoolId=" + w.schoolA(), w.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> listed = section(resp, "teachers");
        assertThat(listed.get("failed")).isEqualTo(false);
        Map<String, Object> row = rows(listed).stream().filter(r -> teacher.toString().equals(r.get("id"))).findFirst().orElseThrow();
        // columns: status, event, school, zone, tenure, observations (5), issues (6), transfers, replacements, training
        assertThat(state(row, 5)).isEqualTo("NOT_AVAILABLE");
        assertThat(state(row, 6)).isEqualTo("NOT_AVAILABLE");
        assertThat(state(row, 2)).isEqualTo("VALUE");
        assertThat(state(row, 4)).isEqualTo("VALUE");
        assertThat(state(row, 7)).isEqualTo("VALUE");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aManagerReportWhoseExpenseSourceIsDownShowsNotAvailableForThatColumnOnly() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        doThrow(new IllegalStateException("source is down")).when(expenseQueries)
                .claimedBy(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        Resp resp = get("/api/v1/reports/manager?month=" + month(), admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> listed = ((List<Map<String, Object>>) resp.map().get("sections")).get(0);
        assertThat(listed.get("failed")).isEqualTo(false);
        List<Map<String, Object>> columns = (List<Map<String, Object>>) listed.get("columns");
        Map<String, Object> row = rows(listed).stream().filter(r -> manager.managerId().toString().equals(r.get("id"))).findFirst().orElseThrow();
        for (int i = 0; i < columns.size(); i++) {
            String label = (String) columns.get(i).get("label");
            if ("Expense claimed".equals(label)) {
                assertThat(state(row, i)).isEqualTo("NOT_AVAILABLE");
            } else if ("Zone".equals(label)) {
                assertThat(state(row, i)).isEqualTo("VALUE");
            }
        }
    }

    @Test
    void anExpensesReportWhoseBudgetSourceIsDownFailsThatSectionAloneAndSpendStillLoads() {
        String admin = signInAs(Role.ADMIN).token();
        doThrow(new IllegalStateException("source is down")).when(expenseQueries).budgetVsActual(org.mockito.ArgumentMatchers.any());

        Resp resp = get("/api/v1/reports/expenses?month=" + month(), admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(section(resp, "budget").get("failed")).isEqualTo(true);
        assertThat(section(resp, "spend").get("failed")).isEqualTo(false);
        assertThat(section(resp, "pending").get("failed")).isEqualTo(false);
    }

    @Test
    void aRecruitmentReportWhoseDemandSourceIsDownShowsDemandAndShortfallAsNotAvailableAndTheColleges() {
        String admin = signInAs(Role.ADMIN).token();
        doThrow(new IllegalStateException("source is down")).when(demand).vacantPositionsOfWonSchools();

        Resp resp = get("/api/v1/reports/recruitment", admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(section(resp, "colleges").get("failed")).isEqualTo(false);
        Map<String, Object> totals = section(resp, "totals");
        assertThat(totals.get("failed")).isEqualTo(false);
        for (Map<String, Object> row : rows(totals)) {
            if ("demand".equals(row.get("id")) || "shortfall".equals(row.get("id"))) {
                assertThat(state(row, 0)).as(String.valueOf(row.get("id"))).isEqualTo("NOT_AVAILABLE");
            }
        }
        assertThat(rows(totals)).extracting(r -> r.get("id")).contains("demand", "shortfall");
    }
}
