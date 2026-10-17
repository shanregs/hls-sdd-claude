package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/** Spec 013b T051: manual payment records on an approved run, the run's paid figures and a person's payroll history. */
class PayPaymentTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    /** Teacher A is paid; Teacher B has a salary but an unlocked month, so has a cannot-pay line. */
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

    private static String paymentUri(UUID run, Object lineId) {
        return RUNS + "/" + run + "/lines/" + lineId + "/payment";
    }

    private Resp pay(String token, UUID run, Object lineId, Object paidOn, String mode, String reference) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("paid", true);
        body.put("paidOn", paidOn);
        body.put("mode", mode);
        body.put("reference", reference);
        return put(paymentUri(run, lineId), token, body);
    }

    private Object lineId(Scenario s, UUID person) {
        return lineOf(s.admin(), s.run(), person).get("id");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Resp resp) {
        return (List<Map<String, Object>>) (List<?>) JsonParserFactory.getJsonParser().parseList(resp.body());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> current(Resp resp) {
        return (Map<String, Object>) resp.map().get("current");
    }

    @Test
    void aPaidEntryIsSavedWithWhoAndWhenAndShowsInTheLineAndTheRunFigures() {
        Scenario s = approvedRun();
        Object lineId = lineId(s, s.paid());
        String today = LocalDate.now().toString();

        Resp saved = pay(s.admin(), s.run(), lineId, today, "BANK_TRANSFER", "  UTR 1234  ");

        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        Map<String, Object> entry = current(saved);
        assertThat(entry).containsEntry("paid", true).containsEntry("mode", "BANK_TRANSFER").containsEntry("reference", "UTR 1234");
        assertThat(entry.get("paidOn")).isEqualTo(today);
        assertThat(entry.get("enteredAt")).isNotNull();
        assertThat(entry.get("enteredByName")).isNotNull();

        Map<String, Object> line = get(RUNS + "/" + s.run() + "/lines/" + lineId, s.admin()).map();
        assertThat(line.get("payment")).isNotNull();
        Map<String, Object> run = runOf(s.admin(), s.run());
        // other tests share the database, so more people may be in the run: only this line is marked paid
        assertThat(run.get("paidCount")).isEqualTo(1);
        assertThat((Integer) run.get("unpaidCount")).isEqualTo((Integer) run.get("payableCount") - 1);
        assertThat(run.get("paidAmount")).isEqualTo(line.get("net"));
    }

    @Test
    void aFutureDateAnUnknownModeAndALongReferenceAreEachRefusedNamingTheField() {
        Scenario s = approvedRun();
        Object lineId = lineId(s, s.paid());

        Resp future = pay(s.admin(), s.run(), lineId, LocalDate.now().plusDays(2).toString(), "CASH", null);
        Resp mode = pay(s.admin(), s.run(), lineId, LocalDate.now().toString(), "BITCOIN", null);
        Resp noMode = pay(s.admin(), s.run(), lineId, LocalDate.now().toString(), null, null);
        Resp noDate = pay(s.admin(), s.run(), lineId, null, "CASH", null);
        Resp reference = pay(s.admin(), s.run(), lineId, LocalDate.now().toString(), "CASH", "x".repeat(101));

        assertThat(future.status()).isEqualTo(400);
        assertThat(future.body()).containsIgnoringCase("payment date");
        assertThat(mode.status()).isEqualTo(400);
        assertThat(mode.body()).containsIgnoringCase("payment mode");
        assertThat(noMode.status()).isEqualTo(400);
        assertThat(noDate.status()).isEqualTo(400);
        assertThat(noDate.body()).containsIgnoringCase("payment date");
        assertThat(reference.status()).isEqualTo(400);
        assertThat(reference.body()).containsIgnoringCase("reference");
        assertThat(get(paymentUri(s.run(), lineId), s.admin()).map().get("current")).as("nothing was saved").isNull();
    }

    @Test
    void aCorrectionAddsAnEntryKeepsTheOldOnesAndNeverChangesTheAmounts() {
        Scenario s = approvedRun();
        Object lineId = lineId(s, s.paid());
        Map<String, Object> lineBefore = get(RUNS + "/" + s.run() + "/lines/" + lineId, s.admin()).map();
        Map<String, Object> runBefore = runOf(s.admin(), s.run());
        String yesterday = LocalDate.now().minusDays(1).toString();
        pay(s.admin(), s.run(), lineId, yesterday, "CASH", "first");

        Resp corrected = pay(s.admin(), s.run(), lineId, LocalDate.now().toString(), "UPI", "second");

        assertThat(corrected.status()).isEqualTo(200);
        assertThat(current(corrected)).containsEntry("mode", "UPI").containsEntry("reference", "second");
        List<?> earlier = (List<?>) corrected.map().get("earlier");
        assertThat(earlier).hasSize(1);
        assertThat(((Map<?, ?>) earlier.get(0)).get("reference")).isEqualTo("first");
        Resp read = get(paymentUri(s.run(), lineId), s.admin());
        assertThat(current(read)).containsEntry("reference", "second");

        // marking it not paid again is also an entry, and the earlier two stay
        Resp unpaid = put(paymentUri(s.run(), lineId), s.admin(), Map.of("paid", false));
        assertThat(unpaid.status()).as(unpaid.body()).isEqualTo(200);
        assertThat(current(unpaid)).containsEntry("paid", false);
        assertThat((List<?>) unpaid.map().get("earlier")).hasSize(2);
        assertThat(runOf(s.admin(), s.run()).get("paidCount")).isEqualTo(0);

        Map<String, Object> lineAfter = get(RUNS + "/" + s.run() + "/lines/" + lineId, s.admin()).map();
        Map<String, Object> runAfter = runOf(s.admin(), s.run());
        for (String key : List.of("salary", "earned", "deduction", "adjustments", "net")) {
            assertThat(lineAfter.get(key)).as(key).isEqualTo(lineBefore.get(key));
        }
        assertThat(runAfter.get("totals")).isEqualTo(runBefore.get("totals"));
    }

    @Test
    void aDraftAwaitingRunAndACannotPayLineRefusePayment() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(teacher);
        UUID run = startAndCompute(admin);
        Object lineId = lineOf(admin, run, teacher).get("id");
        String today = LocalDate.now().toString();

        assertThat(pay(admin, run, lineId, today, "CASH", null).status()).as("draft").isEqualTo(409);
        assertThat(submitRun(admin, run).status()).isEqualTo(200);
        assertThat(pay(admin, run, lineId, today, "CASH", null).status()).as("awaiting").isEqualTo(409);

        Scenario s = approvedRunAfterReset();
        Object cannotPay = lineId(s, s.leftOut());
        assertThat(pay(s.admin(), s.run(), cannotPay, today, "CASH", null).status()).as("cannot pay").isEqualTo(409);
    }

    private Scenario approvedRunAfterReset() {
        resetRuns();
        return approvedRun();
    }

    @Test
    void theRunsPaidAndUnpaidFiguresAreTheSumsOfTheLatestEntries() {
        Scenario s = approvedRun();
        Map<String, Object> runBefore = runOf(s.admin(), s.run());
        assertThat(runBefore.get("paidCount")).isEqualTo(0);
        assertThat(runBefore.get("unpaidCount")).isEqualTo(runBefore.get("payableCount"));
        assertThat(runBefore.get("unpaidAmount")).isEqualTo(((Map<?, ?>) runBefore.get("totals")).get("net"));

        pay(s.admin(), s.run(), lineId(s, s.paid()), LocalDate.now().toString(), "CHEQUE", "No. 77");

        Map<String, Object> run = runOf(s.admin(), s.run());
        assertThat((Integer) run.get("paidCount") + (Integer) run.get("unpaidCount")).isEqualTo(run.get("payableCount"));
        assertThat(new java.math.BigDecimal((String) run.get("paidAmount")).add(new java.math.BigDecimal((String) run.get("unpaidAmount"))))
                .isEqualByComparingTo(new java.math.BigDecimal((String) ((Map<?, ?>) run.get("totals")).get("net")));
    }

    @Test
    void aPersonsHistoryListsEachRunWithMonthNetPayStatusAndThePaymentEntry() {
        Scenario s = approvedRun();
        Object lineId = lineId(s, s.paid());
        pay(s.admin(), s.run(), lineId, LocalDate.now().toString(), "UPI", "ref-1");

        Resp history = get("/api/v1/payroll/people/TEACHER/" + s.paid() + "/history", s.admin());

        assertThat(history.status()).as(history.body()).isEqualTo(200);
        List<Map<String, Object>> rows = list(history);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("month")).isEqualTo(month.toString());
        assertThat(row.get("runKind")).isEqualTo("PRIMARY");
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("net")).isEqualTo(get(RUNS + "/" + s.run() + "/lines/" + lineId, s.admin()).map().get("net"));
        assertThat(((Map<?, ?>) row.get("payment")).get("reference")).isEqualTo("ref-1");

        List<Map<String, Object>> leftOut = list(get("/api/v1/payroll/people/TEACHER/" + s.leftOut() + "/history", s.admin()));
        assertThat(leftOut).hasSize(1);
        assertThat(leftOut.get(0).get("net")).isNull();
        assertThat(leftOut.get(0).get("payment")).isNull();
        assertThat(list(get("/api/v1/payroll/people/TEACHER/" + UUID.randomUUID() + "/history", s.admin()))).isEmpty();
        assertThat(get("/api/v1/payroll/people/ROBOT/" + s.paid() + "/history", s.admin()).status()).isEqualTo(400);
    }

    @Test
    void aLineOrRunThatDoesNotExistAnswers404() {
        Scenario s = approvedRun();

        assertThat(pay(s.admin(), s.run(), UUID.randomUUID(), LocalDate.now().toString(), "CASH", null).status()).isEqualTo(404);
        assertThat(pay(s.admin(), UUID.randomUUID(), lineId(s, s.paid()), LocalDate.now().toString(), "CASH", null).status())
                .isEqualTo(404);
        assertThat(get(paymentUri(s.run(), UUID.randomUUID()), s.admin()).status()).isEqualTo(404);
    }
}
