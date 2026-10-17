package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** Spec 013b T055: the CSV export of a run for the accountant (SC-008): run state in the file, safe cells, audited, frozen. */
class PayRunCsvTest extends PayrollRunTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private record Download(int status, HttpHeaders headers, byte[] bytes) {
        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        /** The rows, the byte-order mark removed; only for files whose cells hold no line breaks. */
        List<String> rows() {
            return Arrays.asList(text().replace("﻿", "").split("\r\n"));
        }
    }

    private Download export(UUID run, String token) {
        var request = client.get().uri(RUNS + "/" + run + "/export");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        var result = request.exchange().returnResult(byte[].class);
        byte[] bytes = result.getResponseBody();
        return new Download(result.getStatus().value(), result.getResponseHeaders(), bytes == null ? new byte[0] : bytes);
    }

    private record Scenario(String admin, UUID run, UUID paid, UUID leftOut) {}

    /** Teacher A is paid; Teacher B has a salary but an unlocked month, so has a cannot-pay line. */
    private Scenario draftRun() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID paid = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, paid, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID leftOut = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, leftOut, "31200", LocalDate.now().minusDays(300));
        return new Scenario(admin, startAndCompute(admin), paid, leftOut);
    }

    private static List<String> cells(String row) {
        return Arrays.asList(row.split(",", -1));
    }

    private static String cell(List<String> header, List<String> row, String column) {
        return row.get(header.indexOf(column));
    }

    private static List<String> rowOf(Download file, String name) {
        return file.rows().stream().filter(r -> r.contains(name)).map(PayRunCsvTest::cells).findFirst().orElseThrow();
    }

    @Test
    void theFirstTwoRowsStateTheMonthAndTheRunStatusAndTheFileIsAnAttachmentWithABom() {
        Scenario s = draftRun();

        Download draft = export(s.run(), s.admin());

        assertThat(draft.status()).isEqualTo(200);
        assertThat(draft.headers().getContentType().toString()).startsWith("text/csv").containsIgnoringCase("utf-8");
        assertThat(draft.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains(month.toString())
                .contains("draft")
                .contains(".csv");
        assertThat(draft.headers().getCacheControl()).contains("no-store");
        assertThat(draft.bytes()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(draft.rows().get(0)).isEqualTo("Month," + month);
        assertThat(draft.rows().get(1)).isEqualTo("Run status,Draft");

        submitAndApprove(s.admin(), s.run());
        Download approved = export(s.run(), s.admin());
        assertThat(approved.rows().get(1)).isEqualTo("Run status,Approved");
        assertThat(approved.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("approved");
    }

    @Test
    void thereIsOneRowPerPersonAndACannotPayPersonHasTheReasonAndNoAmounts() {
        Scenario s = draftRun();
        List<Map<String, Object>> lines = allLines(s.admin(), s.run());

        Download file = export(s.run(), s.admin());

        List<String> header = cells(file.rows().get(2));
        assertThat(header).containsExactly(
                "Month", "Run number", "Run status", "Person kind", "Person name", "Designation", "Salary", "Payable days",
                "Working days", "Unpaid days", "Earned pay", "Deduction", "Adjustments", "Net pay", "Payment status",
                "Paid on", "Payment mode", "Payment reference", "Reason when not paid");
        assertThat(file.rows()).hasSize(3 + lines.size());
        Map<String, Object> cannotPay = lineOf(s.admin(), s.run(), s.leftOut());
        List<String> row = rowOf(file, (String) cannotPay.get("name"));
        assertThat(cell(header, row, "Reason when not paid")).isNotBlank();
        for (String column : List.of("Salary", "Earned pay", "Deduction", "Adjustments", "Net pay", "Payable days")) {
            assertThat(cell(header, row, column)).as(column).isEmpty();
        }
    }

    @Test
    void theApprovedFilesFiguresEqualTheLineColumnsForEveryPerson() {
        Scenario s = draftRun();
        submitAndApprove(s.admin(), s.run());
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");
        String today = LocalDate.now().toString();
        assertThat(put(RUNS + "/" + s.run() + "/lines/" + lineId + "/payment", s.admin(),
                        Map.of("paid", true, "paidOn", today, "mode", "UPI", "reference", "R-9"))
                .status()).isEqualTo(200);

        Download file = export(s.run(), s.admin());

        List<String> header = cells(file.rows().get(2));
        for (Map<String, Object> line : allLines(s.admin(), s.run())) {
            if (!"PAYABLE".equals(line.get("state"))) {
                continue;
            }
            List<String> row = rowOf(file, (String) line.get("name"));
            assertThat(cell(header, row, "Salary")).as("salary").isEqualTo(line.get("salary"));
            assertThat(cell(header, row, "Earned pay")).isEqualTo(line.get("earned"));
            assertThat(cell(header, row, "Deduction")).isEqualTo(line.get("deduction"));
            assertThat(cell(header, row, "Adjustments")).isEqualTo(line.get("adjustments"));
            assertThat(cell(header, row, "Net pay")).isEqualTo(line.get("net"));
            assertThat(cell(header, row, "Payable days")).isEqualTo(String.valueOf(line.get("payableDays")));
        }
        List<String> paidRow = rowOf(file, (String) lineOf(s.admin(), s.run(), s.paid()).get("name"));
        assertThat(cell(header, paidRow, "Payment status")).isEqualTo("Paid");
        assertThat(cell(header, paidRow, "Paid on")).isEqualTo(today);
        assertThat(cell(header, paidRow, "Payment mode")).isEqualTo("UPI");
        assertThat(cell(header, paidRow, "Payment reference")).isEqualTo("R-9");
    }

    @Test
    void aNameThatCouldRunAsAFormulaIsPrefixedAndCommasQuotesAndLineBreaksAreEscaped() {
        Scenario s = draftRun();
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");
        String nasty = "=HYPERLINK(\"http://x\"),y\nz";
        jdbc.update("update payroll_run_line set person_name = ? where id = ?::uuid", nasty, lineId.toString());

        String csv = export(s.run(), s.admin()).text();

        // the apostrophe goes first, the whole cell is quoted, inner quotes are doubled, the line break stays inside the quotes
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\"),y\nz\"");
        for (String start : List.of("+1", "-1", "@x")) {
            jdbc.update("update payroll_run_line set person_name = ? where id = ?::uuid", start, lineId.toString());
            assertThat(export(s.run(), s.admin()).text()).contains(",'" + start + ",");
        }
        // a negative number is a number: it is never given an apostrophe
        jdbc.update("update payroll_run_line set adjustments = -500.00, net = net - 500.00 where id = ?::uuid", lineId.toString());
        assertThat(export(s.run(), s.admin()).text()).contains(",-500.00,").doesNotContain("'-500.00");
    }

    @Test
    void everyExportIsAuditedWithWhoWhichRunAndWhen() {
        Signed admin = signInAs(Role.ADMIN);
        Site site = site(admin.token());
        UUID paid = teacherAt(admin.token(), site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin.token(), paid, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID run = startAndCompute(admin.token());
        assertThat(export(run, admin.token()).status()).isEqualTo(200);

        List<Map<String, Object>> entries = changeEntries(admin.token(), "PAYROLL_EXPORT", run, 1);

        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e).containsEntry("field", "exported");
            assertThat(e).containsEntry("actorUserId", admin.userId().toString());
            assertThat((String) e.get("afterValue")).contains("Draft").contains(month.toString());
        });
    }

    @Test
    void anApprovedRunsFileIsIdenticalBeforeAndAfterACalendarChange() {
        Scenario s = draftRun();
        submitAndApprove(s.admin(), s.run());
        byte[] first = export(s.run(), s.admin()).bytes();

        jdbc.update(
                "insert into attendance_non_working_date (id, on_date, description, created_by) values (?, ?, 'CSV test holiday', ?)",
                UUID.randomUUID(),
                workingDays(null).get(4),
                UUID.randomUUID());
        try {
            assertThat(export(s.run(), s.admin()).bytes()).isEqualTo(first);
        } finally {
            jdbc.update("delete from attendance_non_working_date where description = 'CSV test holiday'");
        }
    }

    @Test
    void theCallerNeedsExportAndAnUnknownRunIs404() {
        Scenario s = draftRun();
        String director = signInAs(Role.DIRECTOR).token();
        String manager = signInAs(Role.MANAGER).token();
        String teacher = signInAs(Role.TEACHER).token();

        assertThat(export(s.run(), director).status()).isEqualTo(200);
        assertThat(export(s.run(), manager).status()).isEqualTo(403);
        assertThat(export(s.run(), teacher).status()).isEqualTo(403);
        assertThat(export(s.run(), null).status()).isEqualTo(401);
        assertThat(export(UUID.randomUUID(), s.admin()).status()).isEqualTo(404);

        UUID actor = UUID.randomUUID();
        assertThat(matrix.updateGrant(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.EXPORT, false, actor).success())
                .isTrue();
        try {
            Download refused = export(s.run(), s.admin());
            assertThat(refused.status()).isEqualTo(403);
            assertThat(refused.text()).doesNotContain("Run status");
            assertThat(get(RUNS + "/" + s.run(), s.admin()).status()).as("viewing still works").isEqualTo(200);
        } finally {
            matrix.updateGrant(Role.ADMIN, PermissionModule.PAYROLL, PermissionAction.EXPORT, true, actor);
        }
        assertThat(export(s.run(), s.admin()).status()).isEqualTo(200);
    }
}
