package com.hls.attendance.internal;

import com.hls.attendance.internal.AttendanceGridService.Cell;
import com.hls.attendance.internal.AttendanceGridService.Filter;
import com.hls.attendance.internal.AttendanceGridService.Row;
import com.hls.identity.user.Role;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports one month of the filtered attendance grid as CSV (spec 008 US8): one row per Teacher with the
 * rollup figures and one column per calendar day. Teachers are loaded in page-sized batches, never one
 * query per Teacher, and every export is recorded in Change History.
 */
@Service
public class CsvExporter {

    /** The finished file and the name to download it as. */
    public record Export(String filename, byte[] content) {}

    private static final int BATCH_SIZE = AttendanceGridService.MAX_PAGE_SIZE;
    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";

    private final AttendanceGridService grids;
    private final AttendanceAudit audit;

    public CsvExporter(AttendanceGridService grids, AttendanceAudit audit) {
        this.grids = grids;
        this.audit = audit;
    }

    @Transactional
    public Export export(UUID actorUserId, Set<Role> roles, YearMonth month, Filter filter) {
        List<UUID> ids = grids.filteredTeacherIds(actorUserId, roles, month, filter);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // The BOM lets Excel read non-ASCII (for example Tamil) names correctly.
        write(out, BOM);
        write(out, header(month));
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<UUID> batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            for (Row row : grids.rowsFor(batch, month)) {
                write(out, line(row));
            }
        }
        audit.lifecycle(actorUserId, AttendanceAudit.EXPORT, month, "exported", describe(month, filter, ids.size()));
        return new Export("attendance-" + month + ".csv", out.toByteArray());
    }

    private static String header(YearMonth month) {
        List<String> cols = new ArrayList<>(List.of(
                "Teacher", "Status", "School", "Manager", "Working days", "Days worked", "Days leave",
                "Training available", "Training attended", "Unmarked", "Weighted total", "Locked"));
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            cols.add(month.atDay(day).toString());
        }
        return join(cols);
    }

    private static String line(Row row) {
        var rollup = row.rollup();
        List<String> cols = new ArrayList<>();
        cols.add(text(row.name()));
        cols.add(text(row.status()));
        cols.add(text(row.school() == null ? "" : row.school().name()));
        cols.add(text(row.manager() == null ? "" : row.manager().name()));
        cols.add(number(rollup.workingDays()));
        cols.add(number(rollup.daysWorked()));
        cols.add(number(rollup.daysLeave()));
        cols.add(number(rollup.trainingAvailable()));
        cols.add(number(rollup.trainingAttended()));
        cols.add(String.valueOf(rollup.unmarked()));
        cols.add(number(rollup.weightedTotal()));
        cols.add(row.locked() ? "Yes" : "No");
        for (Cell cell : row.cells()) {
            cols.add(dayText(cell));
        }
        return join(cols);
    }

    /** A marked day shows its code (half days get a 0.5 suffix); off days are labelled; the rest are blank. */
    static String dayText(Cell cell) {
        return switch (cell.state()) {
            case MARKED -> cell.code() + (cell.dayValue().compareTo(BigDecimal.ONE) < 0 ? cell.dayValue().stripTrailingZeros().toPlainString() : "");
            case WEEKLY_OFF -> "Off";
            case NON_WORKING -> "Hol";
            default -> "";
        };
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * Free text from users (names). A value starting with =, +, - or @ would run as a formula when the
     * file is opened in a spreadsheet, so it is prefixed with an apostrophe (OWASP CSV injection).
     */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        boolean risky = first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
        return risky ? "'" + value : value;
    }

    /** RFC 4180: quote a field containing a comma, quote or line break, doubling inner quotes. */
    static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static String join(List<String> cols) {
        return String.join(",", cols.stream().map(CsvExporter::escape).toList()) + CRLF;
    }

    private static String describe(YearMonth month, Filter filter, int teachers) {
        List<String> parts = new ArrayList<>();
        parts.add("month " + month);
        if (filter.query() != null && !filter.query().isBlank()) {
            parts.add("search '" + filter.query().trim() + "'");
        }
        if (filter.zoneId() != null) {
            parts.add("zone " + filter.zoneId());
        }
        if (filter.schoolId() != null) {
            parts.add("school " + filter.schoolId());
        }
        if (filter.managerId() != null) {
            parts.add("manager " + filter.managerId());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            parts.add("status " + filter.status());
        }
        parts.add(teachers + " teachers");
        return String.join(", ", parts);
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
