package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.api.ManagerColumns;
import com.hls.reporting.api.TileContext;
import com.hls.support.ExpensesTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spec 014c SC-005 for the Manager report: a column source that is down shows "not available" for its own columns only. */
@Import(ManagerReportSourceDownTest.Fixtures.class)
class ManagerReportSourceDownTest extends ExpensesTestBase {

    @TestConfiguration
    static class Fixtures {
        @Bean
        ManagerColumns downColumns() {
            return new ManagerColumns() {
                public List<Column> columns() {
                    return List.of(new Column("fixture.down", "Fixture down", null));
                }

                public Map<UUID, Map<String, Long>> figures(TileContext ctx, Set<UUID> managerUserIds) {
                    throw new IllegalStateException("source is down");
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void aFailingColumnSourceIsNotAvailableAndTheOtherColumnsStillLoad() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));

        Resp resp = get("/api/v1/reports/manager?month=" + YearMonth.from(today().minusDays(1)), admin);

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        Map<String, Object> section = ((List<Map<String, Object>>) resp.map().get("sections")).get(0);
        assertThat(section.get("failed")).isEqualTo(false);
        List<Map<String, Object>> columns = (List<Map<String, Object>>) section.get("columns");
        Map<String, Object> row = ((List<Map<String, Object>>) section.get("rows")).stream()
                .filter(r -> manager.managerId().toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> cells = (List<Map<String, Object>>) row.get("cells");
        for (int i = 0; i < columns.size(); i++) {
            String label = (String) columns.get(i).get("label");
            String state = (String) cells.get(i).get("state");
            if ("Fixture down".equals(label)) {
                assertThat(state).isEqualTo("NOT_AVAILABLE");
            } else if (label.startsWith("Tasks") || "Overdue".equals(label)) {
                assertThat(state).as(label).isEqualTo("VALUE");
            }
        }
    }
}
