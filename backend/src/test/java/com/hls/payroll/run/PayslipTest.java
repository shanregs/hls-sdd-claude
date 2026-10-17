package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Spec 013b T047: an approved run's payslips download one by one or as a zip, with the approved figures, for Admin and Director only. */
class PayslipTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private record Download(int status, HttpHeaders headers, byte[] bytes) {}

    private Download download(String uri, String token) {
        var request = client.get().uri(uri);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        var result = request.exchange().returnResult(byte[].class);
        byte[] bytes = result.getResponseBody();
        return new Download(result.getStatus().value(), result.getResponseHeaders(), bytes == null ? new byte[0] : bytes);
    }

    private static String text(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The way the file name spells a person's name. */
    private static String safe(String person) {
        return person.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static List<String> zipNames(byte[] zip) {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return names;
    }

    /** Teacher A is paid; Teacher B has a salary but an unlocked month, so has a cannot-pay line and no payslip. */
    private record Scenario(String admin, String director, UUID run, UUID paid, UUID leftOut) {}

    private Scenario approvedRun() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID paid = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, paid, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID leftOut = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, leftOut, "31200", LocalDate.now().minusDays(300));
        UUID run = startAndCompute(admin);
        String director = submitAndApprove(admin, run);
        return new Scenario(admin, director, run, paid, leftOut);
    }

    private static String payslipUri(UUID run, Object lineId) {
        return RUNS + "/" + run + "/lines/" + lineId + "/payslip";
    }

    @Test
    void anApprovedRunsPayslipIsAPdfCarryingTheLinesFigures() {
        Scenario s = approvedRun();
        Map<String, Object> line = lineOf(s.admin(), s.run(), s.paid());

        Download slip = download(payslipUri(s.run(), line.get("id")), s.admin());

        assertThat(slip.status()).isEqualTo(200);
        assertThat(slip.headers().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(slip.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains(".pdf");
        assertThat(slip.headers().getCacheControl()).contains("no-store");
        assertThat(new String(slip.bytes(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String content = text(slip.bytes());
        assertThat(content).contains((String) line.get("name"));
        BigDecimal net = new BigDecimal((String) line.get("net"));
        // the net pay appears in the text, whatever the digit grouping
        assertThat(content.replaceAll("[^0-9.]", "")).contains(net.setScale(2).toPlainString());
    }

    @Test
    void theDirectorCanDownloadToo() {
        Scenario s = approvedRun();
        Map<String, Object> line = lineOf(s.admin(), s.run(), s.paid());

        assertThat(download(payslipUri(s.run(), line.get("id")), s.director()).status()).isEqualTo(200);
    }

    @Test
    void theZipHoldsOnePayslipPerPaidLineAndNoneForACannotPayLine() {
        Scenario s = approvedRun();

        Download zip = download(RUNS + "/" + s.run() + "/payslips", s.admin());

        assertThat(zip.status()).isEqualTo(200);
        assertThat(zip.headers().getContentType().toString()).isEqualTo("application/zip");
        assertThat(zip.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains(".zip");
        List<Map<String, Object>> lines = allLines(s.admin(), s.run());
        List<String> paidNames = lines.stream()
                .filter(l -> "PAYABLE".equals(l.get("state")))
                .map(l -> safe((String) l.get("name")))
                .toList();
        Map<String, Object> cannotPay = lineOf(s.admin(), s.run(), s.leftOut());
        List<String> names = zipNames(zip.bytes());
        assertThat(names).hasSize(paidNames.size()).allSatisfy(name -> assertThat(name).endsWith(".pdf"));
        assertThat(paidNames).allSatisfy(paid -> assertThat(names).anyMatch(n -> n.contains(paid)));
        assertThat(names).noneMatch(n -> n.contains(safe((String) cannotPay.get("name"))));
        assertThat(download(payslipUri(s.run(), cannotPay.get("id")), s.admin()).status())
                .as("a person who was left out has no payslip")
                .isEqualTo(404);
    }

    @Test
    void aDraftOrAwaitingRunHasNoPayslips() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);
        UUID run = startAndCompute(admin);
        Object lineId = lineOf(admin, run, teacher).get("id");

        assertThat(download(payslipUri(run, lineId), admin).status()).as("draft").isEqualTo(404);
        assertThat(download(RUNS + "/" + run + "/payslips", admin).status()).as("draft zip").isEqualTo(404);

        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(download(payslipUri(run, lineId), admin).status()).as("awaiting").isEqualTo(404);
        assertThat(download(RUNS + "/" + run + "/payslips", admin).status()).as("awaiting zip").isEqualTo(404);
    }

    @Test
    void aSecondDownloadIsByteIdenticalAfterTheCalendarChanges() {
        Scenario s = approvedRun();
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");
        byte[] first = download(payslipUri(s.run(), lineId), s.admin()).bytes();

        // a holiday on a working day of the month changes the calendar, but never an approved payslip
        jdbc.update(
                "insert into attendance_non_working_date (id, on_date, description, created_by) values (?, ?, 'Payslip test holiday', ?)",
                UUID.randomUUID(),
                workingDays(null).get(4),
                UUID.randomUUID());

        byte[] second = download(payslipUri(s.run(), lineId), s.admin()).bytes();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void aLineIdOfAnotherRunAnswers404() {
        Scenario s = approvedRun();
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");

        assertThat(download(payslipUri(UUID.randomUUID(), lineId), s.admin()).status()).as("unknown run").isEqualTo(404);
        assertThat(download(payslipUri(s.run(), UUID.randomUUID()), s.admin()).status()).as("unknown line").isEqualTo(404);
        assertThat(download(RUNS + "/" + UUID.randomUUID() + "/payslips", s.admin()).status()).isEqualTo(404);
    }

    @Test
    void aManagerTeacherOrSystemUserGets403AndNoBytes() {
        Scenario s = approvedRun();
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");
        String manager = newManager(s.admin()).token();
        String teacher = signInAs(Role.TEACHER).token();
        String system = signInAs(Role.SYSTEM).token();

        for (String token : List.of(manager, teacher, system)) {
            Download one = download(payslipUri(s.run(), lineId), token);
            Download all = download(RUNS + "/" + s.run() + "/payslips", token);
            assertThat(one.status()).isEqualTo(403);
            assertThat(all.status()).isEqualTo(403);
            assertThat(new String(one.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        }
        assertThat(download(payslipUri(s.run(), lineId), null).status()).as("signed out").isEqualTo(401);
    }

    @Test
    void aDownloadWritesNoState() {
        Scenario s = approvedRun();
        Object lineId = lineOf(s.admin(), s.run(), s.paid()).get("id");
        long events = jdbc.queryForObject("select count(*) from payroll_run_event", Long.class);
        long changes = jdbc.queryForObject("select count(*) from change_history_entry", Long.class);
        long files = jdbc.queryForObject("select count(*) from stored_file", Long.class);

        download(payslipUri(s.run(), lineId), s.admin());
        download(RUNS + "/" + s.run() + "/payslips", s.admin());

        assertThat(jdbc.queryForObject("select count(*) from payroll_run_event", Long.class)).isEqualTo(events);
        assertThat(jdbc.queryForObject("select count(*) from change_history_entry", Long.class)).isEqualTo(changes);
        assertThat(jdbc.queryForObject("select count(*) from stored_file", Long.class)).isEqualTo(files);
    }
}
