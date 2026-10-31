package com.hls.reporting.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.reporting.api.Cell;
import com.hls.reporting.api.MonthState;
import com.hls.reporting.api.ReportPage;
import com.hls.reporting.internal.report.DefaultMonth;
import com.hls.reporting.internal.report.ReportCsvWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 014a T007 and T009: the default month, the CSV header block and formula neutralisation. */
class ReportCsvWriterTest {

    private static ReportPage page(String name) {
        var columns = List.of(new ReportPage.Column("n", "Days", ReportPage.Unit.DAYS));
        var row = new ReportPage.Row("1", name, List.of(Cell.of(new BigDecimal("20.0"))));
        var noData = new ReportPage.Row("2", "Other", List.of(Cell.noData()));
        return new ReportPage(
                "attendance", "Attendance and LOP", "2026-09", MonthState.OPEN,
                List.of(ReportPage.Section.of("teachers", "Teachers", columns, List.of(row, noData), null),
                        ReportPage.Section.failed("zones", "Zones", columns, "Could not be read")),
                List.of("Open month"));
    }

    private static String csv(ReportPage page) {
        StringWriter out = new StringWriter();
        ReportCsvWriter.write(out, page, "school=abc", Instant.parse("2026-10-27T05:00:00Z"));
        return out.toString();
    }

    @Test
    void theHeaderBlockNamesTheReportMonthFilterStateAndTime() {
        String text = csv(page("Asha"));
        assertThat(text).startsWith("\uFEFFReport,Attendance and LOP\r\nMonth,2026-09\r\nFilter,school=abc\r\nMonth state,OPEN\r\nMade at,2026-10-27T05:00:00Z\r\n");
        assertThat(text).contains("Note,Open month");
        assertThat(text).contains("Name,Days\r\nAsha,20\r\nOther,No data\r\n");
        assertThat(text).contains("Zones\r\nCould not be read,Could not be read");
    }

    @Test
    void textThatCouldRunAsAFormulaIsNeutralised() {
        for (String evil : List.of("=SUM(A1)", "+cmd", "-2+3x", "@SUM(1)")) {
            assertThat(csv(page(evil))).as(evil).contains("\r\n'" + evil + ",20\r\n");
        }
        assertThat(csv(page("Plain, \"quoted\""))).contains("\"Plain, \"\"quoted\"\"\",20");
    }

    @Test
    void theDefaultMonthIsTheLastFinalOneElseTheCurrentOne() {
        YearMonth current = YearMonth.of(2026, 10);
        assertThat(DefaultMonth.choose(Optional.empty(), current)).isEqualTo(current);
        assertThat(DefaultMonth.choose(Optional.of(YearMonth.of(2026, 9)), current)).isEqualTo(YearMonth.of(2026, 9));
        assertThat(DefaultMonth.choose(Optional.of(YearMonth.of(2027, 1)), current)).isEqualTo(current);
    }
}
