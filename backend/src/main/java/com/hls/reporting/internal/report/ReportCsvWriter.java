package com.hls.reporting.internal.report;

import com.hls.reporting.api.Cell;
import com.hls.reporting.api.ReportPage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Writes a report page as CSV (spec 014a US3): a header block (report, month, filter, month state, time made), then each
 * section with its columns, rows and totals. Rows are written one at a time to the writer. Text that starts with {@code =},
 * {@code +}, {@code -} or {@code @} is prefixed so a spreadsheet cannot run it as a formula; numbers are written as numbers.
 */
public final class ReportCsvWriter {

    private static final String CRLF = "\r\n";

    private ReportCsvWriter() {}

    public static void write(Writer out, ReportPage page, String filterText, Instant madeAt) {
        try {
            out.write("﻿");
            line(out, List.of("Report", page.title()));
            line(out, List.of("Month", page.month()));
            line(out, List.of("Filter", filterText == null || filterText.isBlank() ? "None" : filterText));
            line(out, List.of("Month state", page.monthState().name()));
            line(out, List.of("Made at", madeAt.toString()));
            for (String note : page.notes()) {
                line(out, List.of("Note", note));
            }
            for (ReportPage.Section section : page.sections()) {
                out.write(CRLF);
                line(out, List.of(section.title()));
                if (section.failed()) {
                    line(out, List.of("Could not be read", section.note() == null ? "" : section.note()));
                    continue;
                }
                List<String> header = new java.util.ArrayList<>();
                header.add("Name");
                section.columns().forEach(c -> header.add(c.label()));
                line(out, header);
                for (ReportPage.Row row : section.rows()) {
                    line(out, rowText(row));
                }
                if (section.totals() != null) {
                    line(out, rowText(section.totals()));
                }
            }
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> rowText(ReportPage.Row row) {
        List<String> cells = new java.util.ArrayList<>();
        cells.add(row.label());
        row.cells().forEach(c -> cells.add(text(c)));
        return cells;
    }

    static String text(Cell cell) {
        return switch (cell.state()) {
            case VALUE -> cell.value() instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(cell.value());
            case NO_DATA -> "No data";
            case NOT_AVAILABLE -> "Not available";
            case NOT_MATCHED -> "Not matched";
        };
    }

    private static void line(Writer out, List<String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
        sb.append(CRLF);
        out.write(sb.toString());
    }

    /** Quotes a field and neutralises a leading formula character. */
    static String escape(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty()) {
            char first = text.charAt(0);
            boolean number = isPlainNumber(text);
            if (!number && (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r')) {
                text = "'" + text;
            }
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static boolean isPlainNumber(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
