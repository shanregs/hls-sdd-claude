package com.hls.expenses.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The month of claims as CSV (spec 015 FR-025): one row per claim by expense date, never a Draft. UTF-8 with a byte-order
 * mark; text that starts with {@code = + - @} gets an apostrophe in front so a spreadsheet does not run it as a formula.
 * Every export is written to the audit trail.
 */
@Service
public class ClaimCsvWriter {

    static final String ENTITY = "EXPENSE_EXPORT";
    static final String[] COLUMNS = {
        "Claim id", "Claimant", "Expense date", "Category", "Area", "Amount", "Status", "Paid by", "Approver", "Decision date",
        "Decision reason", "Linked work", "Bills", "No-bill reason", "Payment", "Paid on", "Payment mode", "Payment reference",
        "Payroll month", "Note"
    };

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** The file and the name it is saved under. */
    public record CsvFile(String fileName, byte[] bytes) {}

    private final ExpenseClaimRepository claims;
    private final ExpenseBillRepository bills;
    private final ClaimViews views;
    private final com.hls.school.api.SchoolDirectory zones;
    private final ChangeRecorder audit;
    private final Clock clock;

    ClaimCsvWriter(
            ExpenseClaimRepository claims,
            ExpenseBillRepository bills,
            ClaimViews views,
            com.hls.school.api.SchoolDirectory zones,
            ChangeRecorder audit,
            Clock clock) {
        this.claims = claims;
        this.bills = bills;
        this.views = views;
        this.zones = zones;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CsvFile export(UUID actor, YearMonth month) {
        Specification<ExpenseClaim> spec = (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), ClaimStatus.DRAFT),
                cb.between(root.get("expenseDate"), month.atDay(1), month.atEndOfMonth()));
        List<ExpenseClaim> found = claims.findAll(spec, Sort.by("expenseDate", "createdAt"));
        Map<UUID, String> names = views.names(found.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.getClaimantId(), c.getDecidedBy()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, String> zoneNames = zones.allZones().stream()
                .collect(Collectors.toMap(z -> z.id(), z -> z.name()));
        Map<UUID, Long> billCounts = bills.findByClaimIdInOrderByAddedAt(found.stream().map(ExpenseClaim::getId).toList()).stream()
                .filter(ExpenseBill::isLive)
                .collect(Collectors.groupingBy(ExpenseBill::getClaimId, Collectors.counting()));
        StringBuilder out = new StringBuilder("﻿");
        row(out, (Object[]) COLUMNS);
        for (ExpenseClaim c : found) {
            row(
                    out,
                    c.getId().toString(),
                    text(names.getOrDefault(c.getClaimantId(), "")),
                    c.getExpenseDate().format(DATE),
                    c.getCategory().label(),
                    text(c.getZoneId() == null ? ClaimViews.HEAD_OFFICE : zoneNames.getOrDefault(c.getZoneId(), "Unknown Zone")),
                    c.getAmount().setScale(2).toPlainString(),
                    c.getStatus().label(),
                    c.getPaidBy() == PaidBy.COMPANY ? "Company" : "Claimant",
                    text(c.getDecidedBy() == null ? "" : names.getOrDefault(c.getDecidedBy(), "")),
                    c.getDecidedAt() == null ? "" : c.getDecidedAt().atZone(IST).format(DATE),
                    text(c.getDecisionReason()),
                    text(c.getLinkLabel() == null ? "" : LinkKinds.label(c.getLinkKind()) + ": " + c.getLinkLabel()),
                    String.valueOf(billCounts.getOrDefault(c.getId(), 0L)),
                    text(c.getNoBillReason()),
                    c.getPaidHow() == null ? "" : c.getPaidHow() == PaidHow.PAYROLL ? "With payroll" : "Outside payroll",
                    c.getPaidOn() == null ? "" : c.getPaidOn().format(DATE),
                    c.getPaidMode() == null ? "" : c.getPaidMode().name(),
                    text(c.getPaidReference()),
                    c.getPayrollMonth() == null ? "" : YearMonth.from(c.getPayrollMonth()).toString(),
                    text(c.getNote()));
        }
        audit.recordLifecycle(actor, ENTITY, UUID.nameUUIDFromBytes(month.toString().getBytes(StandardCharsets.UTF_8)), "exported",
                month + ": " + found.size() + " claims at " + clock.instant());
        return new CsvFile("expenses-" + month + ".csv", out.toString().getBytes(StandardCharsets.UTF_8));
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

    static void requireMonth(YearMonth month) {
        if (month == null) {
            throw new NotFoundException("Month not found.");
        }
    }
}
