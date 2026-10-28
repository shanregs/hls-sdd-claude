package com.hls.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportContext;
import com.hls.reporting.api.ReportDefinition;
import com.hls.reporting.api.ReportFilter;
import com.hls.reporting.api.ReportPage;
import com.hls.reporting.api.ReportRequest;
import com.hls.reporting.api.ReportSource;
import com.hls.support.IntegrationTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spec 014c A0: the season filter is parsed from the query, refused where a report does not offer it, and changes nothing else. */
@Import(ReportSeasonFilterTest.Fixtures.class)
class ReportSeasonFilterTest extends IntegrationTestBase {

    @TestConfiguration
    static class Fixtures {
        @Bean
        ReportSource seasonFixtureReport() {
            return new ReportSource() {
                public ReportDefinition definition() {
                    return new ReportDefinition(
                            "seasonfixture", "Season fixture", PermissionModule.REPORT_ATTENDANCE, Set.of(ReportFilter.SEASON), false, true);
                }

                public List<SectionSource> sections() {
                    List<ReportPage.Column> columns = List.of(new ReportPage.Column("n", "Count", ReportPage.Unit.COUNT));
                    return List.of(new SectionSource() {
                        public String key() {
                            return "s";
                        }

                        public String title() {
                            return "S";
                        }

                        public List<ReportPage.Column> columns() {
                            return columns;
                        }

                        public ReportPage.Section read(ReportContext ctx) {
                            String season = ctx.request().season();
                            return ReportPage.Section.of(
                                    "s", "S", columns, List.of(new ReportPage.Row("1", "season=" + season, List.of(Cell.of(1L)))), null);
                        }
                    });
                }
            };
        }
    }

    @Test
    void theSeasonIsParsedFromTheQuery() {
        String admin = signInAs(Role.ADMIN).token();

        Resp page = get("/api/v1/reports/seasonfixture?season=2026-27", admin);

        assertThat(page.status()).as(page.body()).isEqualTo(200);
        assertThat(page.body()).contains("season=2026-27\"");
        assertThat(get("/api/v1/reports/seasonfixture", admin).body()).contains("season=null");
    }

    @Test
    void aReportWithoutTheSeasonFilterRefusesIt() {
        String admin = signInAs(Role.ADMIN).token();

        Resp resp = get("/api/v1/reports/attendance?season=2026-27", admin);

        assertThat(resp.status()).isEqualTo(400);
    }

    @Test
    void theOldFiveArgumentRequestStillWorksWithNoSeason() {
        ReportRequest old = new ReportRequest(YearMonth.of(2026, 9), null, null, null, null);

        assertThat(old.season()).isNull();
        assertThat(old.forMonth(YearMonth.of(2026, 10)).month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(new ReportRequest(YearMonth.of(2026, 9), null, null, null, null, "S").forMonth(YearMonth.of(2026, 10)).season())
                .isEqualTo("S");
    }
}
