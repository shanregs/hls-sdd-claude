package com.hls.payroll.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.hls.identity.user.Role;
import com.hls.payroll.api.PayRules;
import com.hls.support.MasterDataTestBase.ManagerCtx;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Spec 013b T017: what the batch computes for each person, against hand-computed figures. The database is shared with
 * every other test class, so each test looks up its own people by id and never counts the whole run.
 */
class PayRunComputationTest extends PayrollRunTestBase {

    @MockitoSpyBean
    private PayRules payRules;

    @BeforeEach
    void clean() {
        resetRuns();
        reset(payRules);
    }

    private static BigDecimal rupees(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    private static BigDecimal amount(Object text) {
        return new BigDecimal((String) text);
    }

    @Test
    void aTeacherAManagerAndADirectorGetTheHandComputedLines() {
        Signed adminSigned = signInAs(Role.ADMIN);
        String admin = adminSigned.token();
        Site site = site(admin);
        List<LocalDate> schoolDays = workingDays(site.schoolId());

        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        mark(site, teacher, schoolDays.get(1), "A", 1);
        lockTeacherMonth(teacher);

        ManagerCtx manager = paidManager(admin, "30000");
        lockWorkedManagerMonth(admin, manager.managerId());

        Signed director = signInAs(Role.DIRECTOR);
        personSalaryInThePast("STAFF", director.userId(), "40000", LocalDate.now().minusDays(400));

        UUID run = startAndCompute(admin);

        Map<String, Object> teacherLine = lineOf(admin, run, teacher);
        BigDecimal teacherDeduction = rupees(perDay("26000", schoolDays.size()));
        assertThat(teacherLine.get("state")).isEqualTo("PAYABLE");
        assertThat(amount(teacherLine.get("earned"))).isEqualByComparingTo("26000.00");
        assertThat(amount(teacherLine.get("deduction"))).isEqualByComparingTo(teacherDeduction);
        assertThat(amount(teacherLine.get("net"))).isEqualByComparingTo(new BigDecimal("26000").subtract(teacherDeduction));
        assertThat(amount(teacherLine.get("unpaidDays"))).isEqualByComparingTo("1");
        assertThat(amount(teacherLine.get("workingDays"))).isEqualByComparingTo(String.valueOf(schoolDays.size()));
        assertThat(teacherLine.get("payableDays")).isEqualTo(schoolDays.size());

        Map<String, Object> managerLine = lineOf(admin, run, manager.managerId());
        assertThat(managerLine.get("state")).as(String.valueOf(managerLine)).isEqualTo("PAYABLE");
        assertThat(amount(managerLine.get("earned"))).isEqualByComparingTo("30000.00");
        assertThat(amount(managerLine.get("deduction"))).isEqualByComparingTo("0.00");
        assertThat(amount(managerLine.get("net"))).isEqualByComparingTo("30000.00");

        Map<String, Object> directorLine = lineOf(admin, run, director.userId());
        assertThat(directorLine.get("state")).isEqualTo("PAYABLE");
        assertThat(amount(directorLine.get("earned"))).isEqualByComparingTo("40000.00");
        assertThat(amount(directorLine.get("net"))).isEqualByComparingTo("40000.00");
    }

    @Test
    void aTeacherWhoseMonthIsNotLockedIsListedApartWithTheReasonAndNoAmountsWhileOthersAreComputed() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID open = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, open, "20000", LocalDate.now().minusDays(300));
        UUID locked = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, locked, "22000", LocalDate.now().minusDays(300));
        lockTeacherMonth(locked);

        UUID run = startAndCompute(admin);

        Map<String, Object> openLine = lineOf(admin, run, open);
        assertThat(openLine.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) openLine.get("reason")).contains("not locked");
        assertThat(openLine.get("earned")).isNull();
        assertThat(openLine.get("deduction")).isNull();
        assertThat(openLine.get("net")).isNull();
        assertThat(lineOf(admin, run, locked).get("state")).isEqualTo("PAYABLE");
    }

    @Test
    void aManagerWhoseMonthIsNotLockedAndAManagerWithNoJoiningDateAreListedApartWithTheirReasons() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx unlocked = paidManager(admin, "30000");
        ManagerCtx noJoining = newManager(admin);

        UUID run = startAndCompute(admin);

        Map<String, Object> unlockedLine = lineOf(admin, run, unlocked.managerId());
        assertThat(unlockedLine.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) unlockedLine.get("reason")).contains("not locked");
        assertThat(unlockedLine.get("net")).isNull();
        Map<String, Object> noJoiningLine = lineOf(admin, run, noJoining.managerId());
        assertThat(noJoiningLine).as("a Manager with no joining date must not vanish from the run").isNotNull();
        assertThat(noJoiningLine.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) noJoiningLine.get("reason")).containsIgnoringCase("joining date");
        assertThat(noJoiningLine.get("net")).isNull();
    }

    @Test
    void aPersonWithNoSalaryOnAPayableDateIsListedApartWithTheDateAndNeverShownWithZero() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        lockTeacherMonth(teacher);

        UUID run = startAndCompute(admin);

        Map<String, Object> line = lineOf(admin, run, teacher);
        assertThat(line.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) line.get("reason")).contains("No salary on");
        assertThat(line.get("earned")).isNull();
        assertThat(line.get("net")).isNull();
    }

    @Test
    void aPersonWithNoPayableDateAndNoDefectIsNotInTheRun() {
        String admin = signInAs(Role.ADMIN).token();
        UUID unplaced = teacher(admin);
        lockTeacherMonth(unplaced);

        UUID run = startAndCompute(admin);

        assertThat(lineOf(admin, run, unplaced)).isNull();
    }

    @Test
    void aFailureForOnePersonBecomesACannotPayLineAndTheOthersFinish() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID broken = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, broken, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(broken);
        UUID fine = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, fine, "21000", LocalDate.now().minusDays(300));
        lockTeacherMonth(fine);
        doThrow(new IllegalStateException("boom")).when(payRules).earnedPay(eq(teacherRef(broken)), any());

        UUID run = startAndCompute(admin);

        Map<String, Object> brokenLine = lineOf(admin, run, broken);
        assertThat(brokenLine.get("state")).isEqualTo("CANNOT_PAY");
        assertThat((String) brokenLine.get("reason")).startsWith("Could not be worked out").contains("boom");
        assertThat(brokenLine.get("net")).isNull();
        assertThat(lineOf(admin, run, fine).get("state")).isEqualTo("PAYABLE");
    }

    @Test
    void theCountsAndTotalsEqualTheSumsOfTheLines() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID a = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, a, "20000", LocalDate.now().minusDays(300));
        lockTeacherMonth(a);
        UUID b = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, b, "21500", LocalDate.now().minusDays(300));
        lockTeacherMonth(b);

        UUID run = startAndCompute(admin);

        Map<String, Object> header = get(RUNS + "/" + run, admin).map();
        List<Map<String, Object>> lines = allLines(admin, run);
        List<Map<String, Object>> payable = lines.stream().filter(l -> "PAYABLE".equals(l.get("state"))).toList();
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) header.get("totals");
        assertThat(header.get("payableCount")).isEqualTo(payable.size());
        assertThat(header.get("cannotPayCount")).isEqualTo(lines.size() - payable.size());
        assertThat(amount(totals.get("earned")))
                .isEqualByComparingTo(payable.stream().map(l -> amount(l.get("earned"))).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(amount(totals.get("deduction")))
                .isEqualByComparingTo(payable.stream().map(l -> amount(l.get("deduction"))).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(amount(totals.get("net")))
                .isEqualByComparingTo(payable.stream().map(l -> amount(l.get("net"))).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(header.get("progressDone")).isEqualTo(lines.size());
        assertThat(header.get("progressTotal")).isEqualTo(lines.size());
    }
}
