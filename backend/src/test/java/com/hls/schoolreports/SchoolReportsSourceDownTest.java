package com.hls.schoolreports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayrollFigures;
import com.hls.performance.api.PerformanceSummary;
import com.hls.recruitment.api.SchoolStages;
import com.hls.schoolbilling.ReceivablesTestBase;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.schoolbilling.api.SchoolReceivables;
import com.hls.substitution.api.SubstitutionCover;
import com.hls.teacher.api.TeacherDirectory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec 014b SC-005: a source that is down shows "not available" for its own figures or its own section, and everything else
 * on the page still loads. School report columns: zone(0), placed(1), contractEnd(2), percent(3), coverSubstitute(4),
 * coverManager(5), coverOpen(6), observations(7), openIssues(8), billed(9), collected(10), outstanding(11), stage(12).
 */
class SchoolReportsSourceDownTest extends ReceivablesTestBase {

    @MockitoSpyBean
    private PayrollFigures payroll;

    @MockitoSpyBean
    private SchoolReceivables receivables;

    @MockitoSpyBean
    private SubstitutionCover cover;

    @MockitoSpyBean
    private SchoolStages stages;

    @MockitoSpyBean
    private SchoolContracts contracts;

    @MockitoSpyBean
    private PerformanceSummary performance;

    @MockitoSpyBean
    private TeacherDirectory teachers;

    @AfterEach
    void restore() {
        reset(payroll, receivables, cover, stages, contracts, performance, teachers);
    }

    private static final IllegalStateException DOWN = new IllegalStateException("source is down");

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

    private Fixture billedWithTeacher(String admin) {
        Fixture f = billedSchool(admin, "15000");
        mapTeacher(admin, f, 0, thisMonth().atDay(1));
        raiseOne(admin, thisMonth(), f.schoolId());
        return f;
    }

    private Resp open(String report, String admin, UUID schoolId) {
        Resp resp = get("/api/v1/reports/" + report + "?month=" + thisMonth() + "&schoolId=" + schoolId, admin);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return resp;
    }

    private Map<String, Object> schoolRow(Resp resp, UUID schoolId) {
        return rows(section(resp, "schools")).stream()
                .filter(r -> schoolId.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void payrollDownShowsMarginPayAsNotAvailableWhileTheFeeStillShows() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedWithTeacher(admin);
        doThrow(DOWN).when(payroll).runOf(any());
        doThrow(DOWN).when(payroll).peopleOf(any());

        Resp resp = open("margin", admin, f.schoolId());

        Map<String, Object> teacherRow = rows(section(resp, "teachers")).get(0);
        // teachers columns: school(0), zone(1), days(2), fee(3), pay(4), margin(5), status(6)
        assertThat(state(teacherRow, 3)).isEqualTo("VALUE");
        assertThat(state(teacherRow, 4)).isEqualTo("NOT_AVAILABLE");
        assertThat(state(teacherRow, 5)).isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void ageingDownFailsOnlyTheAgeingSectionOfCollections() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedWithTeacher(admin);
        doThrow(DOWN).when(receivables).ageing(any(), any());

        Resp resp = open("collections", admin, f.schoolId());

        assertThat(section(resp, "ageing").get("failed")).isEqualTo(true);
        assertThat(section(resp, "month").get("failed")).isEqualTo(false);
        assertThat(rows(section(resp, "month"))).isNotEmpty();
    }

    @Test
    void substitutionDownShowsNotAvailableForTheCoverColumnsOnly() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedWithTeacher(admin);
        doThrow(DOWN).when(cover).coverBySchool(any(), any(), any());

        Map<String, Object> row = schoolRow(open("school", admin, f.schoolId()), f.schoolId());

        for (int i : new int[] {4, 5, 6}) {
            assertThat(state(row, i)).as("cover column " + i).isEqualTo("NOT_AVAILABLE");
        }
        for (int i : new int[] {0, 1, 9, 10, 11}) {
            assertThat(state(row, i)).as("column " + i).isEqualTo("VALUE");
        }
    }

    @Test
    void stagesContractsPerformanceAndPlacedCountDownEachFailOnlyTheirOwnColumns() {
        String admin = signInAs(Role.ADMIN).token();
        Fixture f = billedWithTeacher(admin);
        Map<Integer, Runnable> down = Map.of(
                12, () -> doThrow(DOWN).when(stages).stageOf(any()),
                2, () -> doThrow(DOWN).when(contracts).contractsOfAll(any(), any()),
                7, () -> doThrow(DOWN).when(performance).bySchool(any(), any()),
                1, () -> doThrow(DOWN).when(teachers).placedCountBySchool(any(), any()));

        for (var entry : down.entrySet()) {
            reset(stages, contracts, performance, teachers);
            entry.getValue().run();

            Map<String, Object> row = schoolRow(open("school", admin, f.schoolId()), f.schoolId());

            assertThat(state(row, entry.getKey())).as("column " + entry.getKey()).isEqualTo("NOT_AVAILABLE");
            assertThat(state(row, 0)).as("zone with column " + entry.getKey() + " down").isEqualTo("VALUE");
            assertThat(state(row, 9)).as("billed with column " + entry.getKey() + " down").isEqualTo("VALUE");
        }
    }
}
