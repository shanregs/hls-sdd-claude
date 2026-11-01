package com.hls.substitution.payout;

import com.hls.identity.user.Role;
import com.hls.school.api.ChangeRecorder;
import com.hls.substitution.payout.DayQueryService.DayView;
import com.hls.substitution.payout.DayQueryService.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The substitute day list as CSV (spec 017b FR-022, SC-007): the same rows the list shows for the same filter in the
 * caller's scope, dates as DD/MM/YYYY and amounts as plain numbers. UTF-8 with a byte-order mark; text that starts with
 * {@code = + - @} gets an apostrophe in front so a spreadsheet does not run it as a formula. Every export is audited.
 */
@Service
public class PayoutCsv {

    static final String[] COLUMNS = {
        "Date", "Substitute", "School", "Zone", "Day", "Attendance", "Status", "Rate", "Amount", "Paid amount", "Pay month",
        "Needs review"
    };

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** The file and the name it is saved under. */
    public record CsvFile(String fileName, byte[] bytes) {}

    private final DayQueryService queries;
    private final ChangeRecorder audit;
    private final Clock clock;

    PayoutCsv(DayQueryService queries, ChangeRecorder audit, Clock clock) {
        this.queries = queries;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CsvFile export(UUID actor, Set<Role> roles, Filter filter) {
        List<DayView> rows = queries.all(actor, roles, filter);
        StringBuilder out = new StringBuilder("﻿");
        row(out, (Object[]) COLUMNS);
        for (DayView d : rows) {
            row(
                    out,
                    d.date().format(DATE),
                    text(d.substituteName()),
                    text(d.schoolName()),
                    text(d.zoneName()),
                    d.dayValue().compareTo(java.math.BigDecimal.ONE) == 0 ? "Full" : "Half",
                    d.attendance(),
                    text(d.statusText()),
                    d.rate() == null ? "" : d.rate().setScale(2).toPlainString(),
                    d.amount() == null ? "" : d.amount().setScale(2).toPlainString(),
                    d.paidAmount() == null ? "" : d.paidAmount().setScale(2).toPlainString(),
                    d.runMonth() == null ? "" : d.runMonth().getYear() + "-" + String.format("%02d", d.runMonth().getMonthValue()),
                    d.needsReview() ? "Yes" : "");
        }
        audit.recordLifecycle(
                actor,
                "SUBSTITUTE_PAYOUT_EXPORT",
                UUID.nameUUIDFromBytes(("substitute-payouts-" + clock.instant()).getBytes(StandardCharsets.UTF_8)),
                "exported",
                rows.size() + " days at " + clock.instant());
        return new CsvFile("substitute-payouts.csv", out.toString().getBytes(StandardCharsets.UTF_8));
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
