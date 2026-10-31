package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.PerformanceTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 014c US3: observations done and open issues in the Teacher report equal the Teacher screen of spec 026. */
class TeacherReportPerformanceTest extends PerformanceTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> teacherRow(Resp resp, UUID teacher) {
        Map<String, Object> section = ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> "teachers".equals(s.get("key")))
                .findFirst()
                .orElseThrow();
        return ((List<Map<String, Object>>) section.get("rows")).stream()
                .filter(r -> teacher.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static long cell(Map<String, Object> row, int index) {
        return ((Number) ((List<Map<String, Object>>) row.get("cells")).get(index).get("value")).longValue();
    }

    @Test
    void observationsAndOpenIssuesEqualTheTeacherScreen() {
        Setup s = setup();
        UUID second = anotherTeacher(s);
        observe(s.managerToken(), s.teacherId(), "GOOD", 4);
        observe(s.managerToken(), second, "NEEDS_IMPROVEMENT", 3);
        observe(s.managerToken(), second, "GOOD", 5);

        Resp resp = get("/api/v1/reports/teacher?month=" + YearMonth.from(today()), s.admin());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        for (UUID teacher : List.of(s.teacherId(), second)) {
            Map<String, Object> view = get(PERF + "/teachers/" + teacher, s.managerToken()).map();
            Map<String, Object> row = teacherRow(resp, teacher);
            assertThat(cell(row, 5)).as("observations").isEqualTo(((List<?>) view.get("observations")).size());
            long openIssues = ((List<Map<String, Object>>) view.get("issues")).stream()
                    .filter(i -> List.of("OPEN", "UNDER_CORRECTION").contains(i.get("status")))
                    .count();
            assertThat(cell(row, 6)).as("open issues").isEqualTo(openIssues);
        }
        assertThat(cell(teacherRow(resp, second), 5)).isEqualTo(2);
        assertThat(cell(teacherRow(resp, second), 6)).isEqualTo(1);
        // the Manager sees the same numbers for their own Teachers
        Resp asManager = get("/api/v1/reports/teacher?month=" + YearMonth.from(today()), s.managerToken());
        assertThat(cell(teacherRow(asManager, second), 5)).isEqualTo(2);
    }
}
