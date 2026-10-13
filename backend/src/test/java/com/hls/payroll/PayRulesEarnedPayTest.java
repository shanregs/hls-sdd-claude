package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.EarnedPay;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.support.PayrollRunTestBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 013b T011: {@code PayRules.earnedPay} through the public interface, with the hand-computed fixtures of
 * contracts/pay-rules-earned-pay.md. All data is in the previous month, so no other test's holiday in the current
 * month changes the answers.
 */
class PayRulesEarnedPayTest extends PayrollRunTestBase {

    @Autowired
    private PayRules payRules;

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal rupees(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    @Test
    void aFullMonthAtAConstantSalaryEarnsExactlyTheSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        List<LocalDate> working = workingDays(site.schoolId());

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(teacherRef(teacher), month);

        assertThat(earned.rounded()).isEqualByComparingTo("26000");
        assertThat(earned.days()).extracting(EarnedPay.DayValue::date).containsExactlyElementsOf(working);
        assertThat(earned.days()).allSatisfy(d -> {
            assertThat(d.salary()).isEqualByComparingTo("26000");
            assertThat(d.workingDays()).isEqualTo(working.size());
            assertThat(d.amount()).isEqualByComparingTo(perDay("26000", working.size()));
        });
        // the unrounded sum is the sum of the days, within the ten-decimal rounding of each day
        assertThat(earned.unrounded()).isEqualByComparingTo(sum(earned.days().stream().map(EarnedPay.DayValue::amount).toList()));
        assertThat(earned.unrounded().subtract(new BigDecimal("26000")).abs()).isLessThan(new BigDecimal("0.000001"));
    }

    @Test
    void aJoinerOnThe11thEarnsOnlyTheirPayableDates() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), month.atDay(11));
        teacherSalary(admin, teacher, "24000", LocalDate.now().minusDays(300));
        List<LocalDate> working = workingDays(site.schoolId());
        List<LocalDate> payable =
                working.stream().filter(d -> !d.isBefore(month.atDay(11))).toList();

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(teacherRef(teacher), month);

        assertThat(earned.days()).extracting(EarnedPay.DayValue::date).containsExactlyElementsOf(payable);
        BigDecimal expected = perDay("24000", working.size()).multiply(BigDecimal.valueOf(payable.size()));
        assertThat(earned.unrounded()).isEqualByComparingTo(expected);
        assertThat(earned.rounded()).isEqualByComparingTo(rupees(expected));
        assertThat(earned.rounded()).isLessThan(new BigDecimal("24000"));
    }

    @Test
    void aStaffMemberWhoLeftOnThe15thEarnsOnlyUntilThen() {
        Signed director = signInAs(Role.DIRECTOR);
        LocalDate exit = month.atDay(15);
        Resp dates = put(
                BASE + "/people/STAFF/" + director.userId() + "/employment",
                signInAs(Role.ADMIN).token(),
                Map.of("joiningDate", month.atDay(1).minusMonths(3).toString(), "exitDate", exit.toString()));
        assertThat(dates.status()).as(dates.body()).isEqualTo(200);
        personSalaryInThePast("STAFF", director.userId(), "30000", LocalDate.now().minusDays(400));
        List<LocalDate> working = workingDays(null);
        List<LocalDate> payable = working.stream().filter(d -> !d.isAfter(exit)).toList();

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(staffRef(director.userId()), month);

        assertThat(earned.days()).extracting(EarnedPay.DayValue::date).containsExactlyElementsOf(payable);
        BigDecimal expected = perDay("30000", working.size()).multiply(BigDecimal.valueOf(payable.size()));
        assertThat(earned.unrounded()).isEqualByComparingTo(expected);
        assertThat(earned.rounded()).isEqualByComparingTo(rupees(expected));
    }

    @Test
    void aMidMonthRaiseValuesEachDateAtItsOwnDaysSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        teacherSalary(admin, teacher, "31200", month.atDay(16));
        List<LocalDate> working = workingDays(site.schoolId());
        long before = working.stream().filter(d -> d.getDayOfMonth() < 16).count();
        long after = working.size() - before;

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(teacherRef(teacher), month);

        BigDecimal expected = perDay("26000", working.size())
                .multiply(BigDecimal.valueOf(before))
                .add(perDay("31200", working.size()).multiply(BigDecimal.valueOf(after)));
        assertThat(earned.unrounded()).isEqualByComparingTo(expected);
        assertThat(earned.rounded()).isEqualByComparingTo(rupees(expected));
        assertThat(earned.days().stream().filter(d -> d.date().getDayOfMonth() < 16))
                .allSatisfy(d -> assertThat(d.salary()).isEqualByComparingTo("26000"));
        assertThat(earned.days().stream().filter(d -> d.date().getDayOfMonth() >= 16))
                .allSatisfy(d -> assertThat(d.salary()).isEqualByComparingTo("31200"));
    }

    @Test
    void absencesAndAHalfDayDoNotChangeEarnedPayOnlyTheDeduction() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "30000", LocalDate.now().minusDays(300));
        List<LocalDate> working = workingDays(site.schoolId());
        mark(site, teacher, working.get(1), "A", 1);
        mark(site, teacher, working.get(2), "A", 0.5);
        PersonRef person = teacherRef(teacher);

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(person, month);
        TotalLossOfPay.Found deduction = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);

        assertThat(earned.rounded()).isEqualByComparingTo("30000");
        BigDecimal expectedDeduction = perDay("30000", working.size()).multiply(new BigDecimal("1.5"));
        assertThat(deduction.unrounded()).isEqualByComparingTo(expectedDeduction);
        // the net a run would pay is earned less the deduction
        assertThat(earned.rounded().subtract(deduction.rounded()))
                .isEqualByComparingTo(new BigDecimal("30000").subtract(rupees(expectedDeduction)));
    }

    @Test
    void theDivisorOfEveryDateIsTheOneTheDeductionUsesForTheSameDate() {
        String admin = signInAs(Role.ADMIN).token();
        Site first = site(admin);
        Site second = site(admin);
        UUID teacher = teacherAt(admin, first.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "28000", LocalDate.now().minusDays(300));
        List<LocalDate> working = workingDays(first.schoolId());
        mark(first, teacher, working.get(0), "A", 1);
        assertThat(placeTeacherRaw(admin, teacher, second.schoolId(), month.atDay(16)).status()).isEqualTo(200);
        mark(second, teacher, working.get(working.size() - 1), "A", 1);
        PersonRef person = teacherRef(teacher);

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(person, month);
        TotalLossOfPay.Found deduction = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);

        assertThat(deduction.days()).hasSize(2);
        for (TotalLossOfPay.UnpaidDayValue unpaid : deduction.days()) {
            EarnedPay.DayValue same = earned.days().stream()
                    .filter(d -> d.date().equals(unpaid.date()))
                    .findFirst()
                    .orElseThrow();
            assertThat(same.workingDays()).as(unpaid.date().toString()).isEqualTo(unpaid.workingDays());
            assertThat(same.salary()).isEqualByComparingTo(unpaid.salary());
        }
        assertThat(earned.days()).extracting(EarnedPay.DayValue::date).doesNotHaveDuplicates();
    }

    @Test
    void aStaffMemberEarnsTheirSalaryOverTheirPayableDates() {
        Signed director = signInAs(Role.DIRECTOR);
        personSalaryInThePast("STAFF", director.userId(), "90000", LocalDate.now().minusDays(400));
        Resp dates = put(
                BASE + "/people/STAFF/" + director.userId() + "/employment",
                signInAs(Role.ADMIN).token(),
                Map.of("joiningDate", month.atDay(1).minusMonths(2).toString()));
        assertThat(dates.status()).as(dates.body()).isEqualTo(200);

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(staffRef(director.userId()), month);

        assertThat(earned.rounded()).isEqualByComparingTo("90000");
        assertThat(earned.days()).hasSize(workingDays(null).size());
    }

    @Test
    void aPersonWithNoSalaryOnADateGetsCannotNamingTheDateNeverAPartialFigure() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        PersonRef person = teacherRef(teacher);

        EarnedPay earned = payRules.earnedPay(person, month);

        assertThat(earned).isInstanceOf(EarnedPay.Cannot.class);
        String reason = ((EarnedPay.Cannot) earned).reason();
        assertThat(reason).contains("No salary on").contains("salary");
        assertThat(reason).containsPattern("\\d{2}/\\d{2}/\\d{4}");
    }

    @Test
    void aManagerWithNoSalaryOfTheirOwnGetsCannot() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, null);

        assertThat(payRules.earnedPay(managerRef(manager.managerId()), month)).isInstanceOf(EarnedPay.Cannot.class);
    }

    @Test
    void anUnknownPersonGetsCannotNotZero() {
        assertThat(payRules.earnedPay(teacherRef(UUID.randomUUID()), month)).isInstanceOf(EarnedPay.Cannot.class);
        assertThat(payRules.earnedPay(managerRef(UUID.randomUUID()), month)).isInstanceOf(EarnedPay.Cannot.class);
        assertThat(payRules.earnedPay(staffRef(UUID.randomUUID()), month)).isInstanceOf(EarnedPay.Cannot.class);
    }

    @Test
    void theSumIsRoundedOnceByThePolicyOfTheMonthsLastDay() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "33333");
        List<LocalDate> working = workingDays(null);
        BigDecimal unrounded = perDay("33333", working.size()).multiply(BigDecimal.valueOf(working.size()));

        EarnedPay.Found earned = (EarnedPay.Found) payRules.earnedPay(managerRef(manager.managerId()), month);

        // the policy in force on the month's last day is the standard one (nearest rupee) unless a test changed it
        var policy = payRules.policyOn(month.atEndOfMonth());
        BigDecimal expected = switch (policy.rounding()) {
            case NEAREST_RUPEE -> unrounded.setScale(0, RoundingMode.HALF_UP);
            case UP -> unrounded.setScale(0, RoundingMode.CEILING);
            case DOWN -> unrounded.setScale(0, RoundingMode.FLOOR);
        };
        assertThat(earned.rounded()).isEqualByComparingTo(expected);
        assertThat(earned.unrounded()).isEqualByComparingTo(unrounded);
    }

    @Test
    void theSameInputsGiveTheSameAnswerOnRepeat() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "41000");
        PersonRef person = managerRef(manager.managerId());

        assertThat(payRules.earnedPay(person, month)).isEqualTo(payRules.earnedPay(person, month));
    }
}
