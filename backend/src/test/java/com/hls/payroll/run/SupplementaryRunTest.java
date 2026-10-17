package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 013b T043: people left out of an approved run are shown as owed and are paid once, through a supplementary run. */
class SupplementaryRunTest extends PayrollRunTestBase {

    @BeforeEach
    void clean() {
        resetRuns();
    }

    private Resp startSupplementary(String token) {
        return post(RUNS, token, Map.of("month", month.toString(), "supplementary", true));
    }

    /** Teacher A is paid in the primary run; Teacher B has a salary but an unlocked month, so is left out. */
    private record Scenario(UUID primary, UUID paid, UUID leftOut, Site site, String admin, String director) {}

    private Scenario approvedPrimaryWithOneLeftOut() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID paid = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, paid, "26000", LocalDate.now().minusDays(300));
        lockTeacherMonth(paid);
        UUID leftOut = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, leftOut, "31200", LocalDate.now().minusDays(300));
        UUID primary = startAndCompute(admin);
        assertThat(lineOf(admin, primary, leftOut).get("state")).isEqualTo("CANNOT_PAY");
        String director = submitAndApprove(admin, primary);
        return new Scenario(primary, paid, leftOut, site, admin, director);
    }

    private Map<String, Object> owedEntry(String token, UUID run, UUID person) {
        Resp resp = get(RUNS + "/" + run + "/owed", token);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return owedList(resp).stream()
                .filter(o -> person.toString().equals(o.get("personId")))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> owedList(Resp resp) {
        return (List<Map<String, Object>>) (List<?>) new org.springframework.boot.json.JacksonJsonParser().parseList(resp.body());
    }

    @Test
    void theOwedListShowsEachLeftOutPersonWithTheStoredReasonAndALiveHint() {
        Scenario s = approvedPrimaryWithOneLeftOut();

        Map<String, Object> owed = owedEntry(s.admin(), s.primary(), s.leftOut());

        assertThat(owed).isNotNull();
        assertThat((String) owed.get("reason")).contains("not locked");
        assertThat(owed.get("resolved")).isEqualTo(false);
        assertThat(owedEntry(s.admin(), s.primary(), s.paid())).as("a paid person is not owed").isNull();

        lockTeacherMonth(s.leftOut());

        Map<String, Object> after = owedEntry(s.admin(), s.primary(), s.leftOut());
        assertThat(after.get("resolved")).as("the hint follows the live checks").isEqualTo(true);
        assertThat((String) after.get("reason")).as("the stored reason does not change").contains("not locked");
    }

    @Test
    void aSupplementaryRunIsRefusedWhileThePrimaryIsNotApproved() {
        String admin = signInAs(Role.ADMIN).token();
        UUID primary = startAndCompute(admin);

        Resp draft = startSupplementary(admin);
        submitRun(admin, primary);
        Resp awaiting = startSupplementary(admin);

        assertThat(draft.status()).isEqualTo(409);
        assertThat(draft.body()).contains("must be approved");
        assertThat(awaiting.status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from payroll_run where kind = 'SUPPLEMENTARY'", Long.class)).isZero();
    }

    @Test
    void aSupplementaryRunIsRefusedWhenNobodyIsOwed() {
        Scenario s = approvedPrimaryWithOneLeftOut();
        // everyone left out has since been paid
        for (Map<String, Object> owed : owedList(get(RUNS + "/" + s.primary() + "/owed", s.admin()))) {
            Map<String, Object> line = lineOf(s.admin(), s.primary(), UUID.fromString((String) owed.get("personId")));
            jdbc.update(
                    "insert into payroll_paid_month (month, person_kind, person_id, run_id, line_id) values (?, ?, ?, ?, ?)",
                    java.sql.Date.valueOf(month.atDay(1)),
                    owed.get("kind"),
                    UUID.fromString((String) owed.get("personId")),
                    s.primary(),
                    UUID.fromString((String) line.get("id")));
        }

        Resp refused = startSupplementary(s.admin());

        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.body()).contains("Nobody is owed");
    }

    @Test
    void itContainsOnlyThePeopleNotYetPaidAndListsStillUncomputablePeopleAgain() {
        Scenario s = approvedPrimaryWithOneLeftOut();
        UUID stillBlocked = teacherAt(s.admin(), s.site().schoolId(), LocalDate.now().minusDays(200));
        // not in the primary run when it was computed; a Teacher with a salary but an unlocked month is left out now
        teacherSalary(s.admin(), stillBlocked, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(s.leftOut());

        Resp started = startSupplementary(s.admin());

        assertThat(started.status()).as(started.body()).isEqualTo(202);
        assertThat(started.map().get("kind")).isEqualTo("SUPPLEMENTARY");
        assertThat(started.map().get("sequence")).isEqualTo(1);
        UUID supplementary = started.id();
        awaitComputed(s.admin(), supplementary);
        assertThat(lineOf(s.admin(), supplementary, s.paid())).as("already paid in the primary run").isNull();
        Map<String, Object> line = lineOf(s.admin(), supplementary, s.leftOut());
        assertThat(line.get("state")).isEqualTo("PAYABLE");
        assertThat(new BigDecimal((String) line.get("earned"))).isEqualByComparingTo("31200.00");
        // someone who is still not computable is listed again with the reason
        Map<String, Object> again = lineOf(s.admin(), supplementary, stillBlocked);
        assertThat(again.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) again.get("reason")).contains("not locked");
    }

    @Test
    void itGoesThroughReviewApprovalFreezingAndPayslipsAndEveryPersonIsPaidInExactlyOneRun() {
        Scenario s = approvedPrimaryWithOneLeftOut();
        lockTeacherMonth(s.leftOut());
        List<Map<String, Object>> primaryBefore = allLines(s.admin(), s.primary());
        UUID supplementary = startSupplementary(s.admin()).id();
        awaitComputed(s.admin(), supplementary);

        assertThat(submitRun(s.admin(), supplementary).status()).isEqualTo(200);
        Resp approved = approveRun(s.director(), supplementary);

        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        assertThat(approved.map().get("status")).isEqualTo("APPROVED");
        UUID leftOutLine = UUID.fromString((String) lineOf(s.admin(), supplementary, s.leftOut()).get("id"));
        assertThat(jdbc.queryForObject("select count(*) from payroll_payslip where line_id = ?", Long.class, leftOutLine))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select run_id from payroll_paid_month where person_id = ?", UUID.class, s.leftOut()))
                .isEqualTo(supplementary);
        // the first run is exactly as it was
        assertThat(allLines(s.admin(), s.primary())).isEqualTo(primaryBefore);
        assertThat(runOf(s.admin(), s.primary()).get("status")).isEqualTo("APPROVED");
        // SC-007: nobody is paid in two approved runs of the month
        assertThat(jdbc.queryForObject(
                        "select count(*) from (select l.person_kind, l.person_id from payroll_run_line l"
                                + " join payroll_run r on r.id = l.run_id where r.month = ? and r.status = 'APPROVED'"
                                + " and l.state = 'PAYABLE' group by 1, 2 having count(*) > 1) d",
                        Long.class,
                        java.sql.Date.valueOf(month.atDay(1))))
                .isZero();
        // and the person is no longer owed
        assertThat(owedEntry(s.admin(), supplementary, s.leftOut())).isNull();
    }

    @Test
    void onlyOneSupplementaryRunCanBeOpenAndALaterOneGetsTheNextNumber() {
        Scenario s = approvedPrimaryWithOneLeftOut();
        lockTeacherMonth(s.leftOut());
        UUID first = startSupplementary(s.admin()).id();
        awaitComputed(s.admin(), first);

        Resp second = startSupplementary(s.admin());

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body()).contains("already open");
        // cancelled, it no longer blocks; the next one is number 2
        post(RUNS + "/" + first + "/cancel", s.admin(), Map.of("reason", "Start again"));
        Resp again = startSupplementary(s.admin());
        assertThat(again.status()).as(again.body()).isEqualTo(202);
        assertThat(again.map().get("sequence")).isEqualTo(2);
    }

    @Test
    void aMonthThatHasNoPrimaryRunAtAllRefusesASupplementaryRun() {
        String admin = signInAs(Role.ADMIN).token();

        Resp refused = startSupplementary(admin);

        assertThat(refused.status()).isEqualTo(409);
    }

    @Test
    void theOwedListIsForViewersOnly() {
        Scenario s = approvedPrimaryWithOneLeftOut();
        String manager = signInAs(Role.MANAGER).token();
        String director = signInAs(Role.DIRECTOR).token();

        assertThat(get(RUNS + "/" + s.primary() + "/owed", manager).status()).isEqualTo(403);
        assertThat(get(RUNS + "/" + s.primary() + "/owed", director).status()).isEqualTo(200);
        assertThat(get(RUNS + "/" + UUID.randomUUID() + "/owed", director).status()).isEqualTo(404);
        assertThat(post(RUNS, director, Map.of("month", month.toString(), "supplementary", true)).status()).isEqualTo(403);
    }
}
