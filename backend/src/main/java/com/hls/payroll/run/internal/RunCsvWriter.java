package com.hls.payroll.run.internal;

import com.hls.payroll.run.internal.PaymentService.EntryView;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a pay run as CSV for the accountant (spec 013b US8, research.md section 14). Pure: it formats what it is
 * given, never reads another module, so an approved run's file cannot change after approval. UTF-8 with a byte-order
 * mark so Excel reads rupee signs and Indian names. Text that starts with {@code = + - @} gets an apostrophe in front so
 * a spreadsheet does not run it as a formula; numeric cells are never changed.
 */
final class RunCsvWriter {

    static final String[] COLUMNS = {
        "Month",
        "Run number",
        "Run status",
        "Person kind",
        "Person name",
        "Designation",
        "Salary",
        "Payable days",
        "Working days",
        "Unpaid days",
        "Earned pay",
        "Deduction",
        "Adjustments",
        "Net pay",
        "Payment status",
        "Paid on",
        "Payment mode",
        "Payment reference",
        "Reason when not paid"
    };

    private static final String BOM = "﻿";
    private static final String EOL = "\r\n";

    private RunCsvWriter() {}

    /** The status as the screens word it, so the file says plainly whether it is final. */
    static String statusText(PayRun.Status status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case AWAITING_APPROVAL -> "Awaiting approval";
            case APPROVED -> "Approved";
            case CANCELLED -> "Cancelled";
        };
    }

    static byte[] write(PayRun run, List<PayRunLine> lines, Map<UUID, EntryView> payments) {
        StringBuilder out = new StringBuilder(BOM);
        row(out, "Month", run.getMonth().toString());
        row(out, "Run status", statusText(run.getStatus()));
        row(out, (Object[]) COLUMNS);
        for (PayRunLine line : lines) {
            boolean payable = line.getState() == PersonResult.State.PAYABLE;
            EntryView payment = payments.get(line.getId());
            boolean paid = payment != null && payment.paid();
            row(
                    out,
                    run.getMonth().toString(),
                    runNumber(run),
                    statusText(run.getStatus()),
                    line.getPersonKind().name(),
                    text(line.getPersonName()),
                    text(line.getPersonLabel()),
                    number(line.getSalary()),
                    line.getPayableDays() == null ? "" : String.valueOf(line.getPayableDays()),
                    plain(line.getWorkingDays()),
                    plain(line.getUnpaidDays()),
                    number(line.getEarned()),
                    number(line.getDeduction()),
                    number(line.getAdjustments()),
                    number(line.getNet()),
                    paymentStatus(run, payable, paid),
                    paid ? payment.paidOn().toString() : "",
                    paid ? payment.mode().name() : "",
                    paid ? text(payment.reference()) : "",
                    payable ? "" : text(line.getReason()));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String runNumber(PayRun run) {
        return run.getKind() == PayRun.Kind.PRIMARY ? "Primary" : "Supplementary " + run.getSequence();
    }

    private static String paymentStatus(PayRun run, boolean payable, boolean paid) {
        if (!payable) {
            return "";
        }
        if (run.getStatus() != PayRun.Status.APPROVED) {
            return "Not approved yet";
        }
        return paid ? "Paid" : "Not paid";
    }

    /** A money cell: two places, unchanged even when negative (an adjustment can be). */
    private static String number(BigDecimal value) {
        return value == null ? "" : RunService.money(value);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** A text cell that cannot start a spreadsheet formula. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value
                : value;
    }

    private static void row(StringBuilder out, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(String.valueOf(cells[i])));
        }
        out.append(EOL);
    }

    private static String escape(String cell) {
        if (cell.indexOf(',') < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0 && cell.indexOf('\r') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
