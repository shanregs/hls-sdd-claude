package com.hls.incentive.internal;

import com.hls.incentive.internal.IncentiveAccess.Caller;
import com.hls.incentive.internal.IncentiveDtos.Row;
import com.hls.incentive.internal.IncentiveQueries.Filter;
import com.hls.school.api.ChangeRecorder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The incentive list as CSV (spec 030 D-13): the same rows the list shows for the same filter in the caller's scope. UTF-8
 * with a byte-order mark; text that starts with {@code = + - @} gets an apostrophe in front so a spreadsheet does not run it
 * as a formula. Every export is written to the audit trail.
 */
@Service
public class IncentiveCsv {

    static final String[] COLUMNS = {
        "School", "Zone", "Manager", "Closure date", "Amount", "Status", "Approved by", "Decision date", "Approved by the earner",
        "Reject reason", "Hold reason", "Payroll month"
    };

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** The file and the name it is saved under. */
    public record CsvFile(String fileName, byte[] bytes) {}

    private final IncentiveQueries queries;
    private final ChangeRecorder audit;
    private final Clock clock;

    IncentiveCsv(IncentiveQueries queries, ChangeRecorder audit, Clock clock) {
        this.queries = queries;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CsvFile export(Caller caller, Filter filter) {
        List<Row> rows = queries.all(caller, filter);
        StringBuilder out = new StringBuilder("﻿");
        row(out, (Object[]) COLUMNS);
        for (Row r : rows) {
            row(
                    out,
                    text(r.schoolName()),
                    text(r.zoneName()),
                    text(r.earnerName()),
                    r.closureDate().format(DATE),
                    r.amount().setScale(2).toPlainString(),
                    r.status(),
                    text(r.decidedBy()),
                    r.decidedAt() == null ? "" : r.decidedAt().atZone(IncentiveRecorder.BUSINESS_ZONE).format(DATE),
                    r.approvedByEarner() ? "Yes" : "",
                    text(r.rejectReason()),
                    text(r.holdReason()),
                    r.runMonth() == null ? "" : r.runMonth().getYear() + "-" + String.format("%02d", r.runMonth().getMonthValue()));
        }
        audit.recordLifecycle(
                caller.userId(),
                "INCENTIVE_EXPORT",
                UUID.nameUUIDFromBytes(("incentives-" + clock.instant()).getBytes(StandardCharsets.UTF_8)),
                "exported",
                rows.size() + " incentives at " + clock.instant());
        return new CsvFile("incentives.csv", out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Text cell: an apostrophe in front of a leading formula character; null becomes empty. */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        return !v.isEmpty() && "=+-@\t".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
    }

    private static void row(StringBuilder out, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = String.valueOf(cells[i]);
            boolean quote = cell.contains(",") || cell.contains("\"");
            out.append(quote ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell);
        }
        out.append("\r\n");
    }
}
