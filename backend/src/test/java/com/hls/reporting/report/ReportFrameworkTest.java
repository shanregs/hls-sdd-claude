package com.hls.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage;
import com.hls.reporting.api.ReportSource;
import com.hls.support.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spec 014a SC-006 and SC-005: a report added as a bean appears in the menu, reads, exports and is audited with no change to
 * the framework; a section that throws becomes one failed section while the others still load.
 */
@Import(ReportFrameworkTest.Fixtures.class)
class ReportFrameworkTest extends IntegrationTestBase {

    @TestConfiguration
    static class Fixtures {
        @Bean
        ReportSource fixtureReport() {
            return new ReportSource() {
                public ReportDefinition definition() {
                    return new ReportDefinition(
                            "fixture", "Fixture report", PermissionModule.REPORT_ATTENDANCE, Set.of(ReportFilter.ZONE), false, true);
                }

                public List<SectionSource> sections() {
                    return List.of(section("good", false), section("bad", true));
                }
            };
        }

        private static ReportSource.SectionSource section(String key, boolean throwing) {
            List<ReportPage.Column> columns = List.of(new ReportPage.Column("n", "Count", ReportPage.Unit.COUNT));
            return new ReportSource.SectionSource() {
                public String key() {
                    return key;
                }

                public String title() {
                    return key + " section";
                }

                public List<ReportPage.Column> columns() {
                    return columns;
                }

                public ReportPage.Section read(ReportContext ctx) {
                    if (throwing) {
                        throw new IllegalStateException("source is down");
                    }
                    return ReportPage.Section.of(
                            key, title(), columns, List.of(new ReportPage.Row("1", "Only row", List.of(Cell.of(7L)))), null);
                }
            };
        }
    }

    @Test
    void aNewReportAppearsInTheMenuAndReadsWithNoFrameworkChange() {
        String admin = signInAs(Role.ADMIN).token();

        Resp menu = get("/api/v1/reports", admin);
        Resp page = get("/api/v1/reports/fixture", admin);

        assertThat(menu.body()).contains("\"key\":\"fixture\"");
        assertThat(page.status()).as(page.body()).isEqualTo(200);
        assertThat(page.body()).contains("Only row");
    }

    @Test
    void aSectionThatThrowsFailsAloneAndTheOtherSectionStillLoads() {
        String admin = signInAs(Role.ADMIN).token();

        Resp page = get("/api/v1/reports/fixture", admin);

        assertThat(page.status()).as(page.body()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) page.map().get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).get("failed")).isEqualTo(false);
        assertThat(sections.get(1).get("failed")).isEqualTo(true);
        assertThat((List<?>) sections.get(1).get("rows")).isEmpty();
        assertThat(sections.get(1).get("note")).isEqualTo("Could not be read");
    }

    @Test
    void everyExportWritesOneAuditRow() {
        String admin = signInAs(Role.ADMIN).token();

        Resp csv = get("/api/v1/reports/fixture/export", admin);

        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).contains("Only row").contains("bad section").contains("Could not be read");
        await("REPORT_EXPORT_FIXTURE", admin);
    }

    private void await(String entityType, String admin) {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            Resp resp = get("/api/v1/audit/change-history?entityType=" + entityType + "&entityId=fixture&size=100", admin);
            assertThat(resp.body()).contains("\"field\":\"exported\"");
        });
    }

    @Test
    void aCallerWithoutTheViewGrantGetsForbiddenAndNoMenuItem() {
        String teacher = signInAs(Role.TEACHER).token();

        assertThat(get("/api/v1/reports/fixture", teacher).status()).isEqualTo(403);
        assertThat(get("/api/v1/reports", teacher).body()).doesNotContain("fixture");
    }
}
