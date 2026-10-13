package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.payroll.api.LossOfPayValue;
import com.hls.payroll.api.PayRules;
import com.hls.payroll.api.PayRules.Kind;
import com.hls.payroll.api.PayRules.PersonRef;
import com.hls.payroll.api.PayableDays;
import com.hls.payroll.api.SalaryInEffect;
import com.hls.payroll.api.SalaryInEffect.Reason;
import com.hls.payroll.api.TotalLossOfPay;
import com.hls.payroll.api.UnpaidDays;
import com.hls.support.PayrollTestBase;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 013a US3 (T031): the hand-computed fixtures of quickstart section 4 through the public interface. All data is
 * in the previous month, so no other test's policy or holiday in the current month can change the answers.
 */
class PayRulesTest extends PayrollTestBase {

    @Autowired
    private PayRules payRules;

    private YearMonth month;

    @BeforeEach
    void previousBusinessMonth() {
        month = YearMonth.from(today()).minusMonths(1);
    }

    private record Site(UUID schoolId, String managerToken) {}

    // ---------------------------------------------------------------- fixtures

    private Site site(String admin) {
        UUID[] ids = schoolInNewZone(admin);
        ManagerCtx manager = newManager(admin, ids[0]);
        assignSchoolManager(admin, ids[2], manager.managerId());
        return new Site(ids[2], manager.token());
    }

    private UUID teacherAt(String admin, UUID schoolId, LocalDate from) {
        UUID teacher = teacher(admin);
        Resp placed = placeTeacherRaw(admin, teacher, schoolId, from);
        assertThat(placed.status()).as(placed.body()).isEqualTo(200);
        return teacher;
    }

    private void teacherSalary(String admin, UUID teacher, String amount, LocalDate effectiveOn) {
        Resp resp = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal(amount), "effectiveOn", effectiveOn.toString()));
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
    }

    private void mark(Site site, UUID teacher, LocalDate date, String code, double value) {
        Resp resp = put(
                "/api/v1/attendance/teachers/" + teacher + "/marks/" + date,
                site.managerToken(),
                Map.of("statusCode", code, "dayValue", value));
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
    }

    /** A salary dated in the past, which the endpoint refuses by design; direct SQL is the only way to set one up. */
    private void designationSalaryInThePast(UUID designation, String amount, LocalDate effectiveOn) {
        jdbc.update(
                "insert into designation_salary (id, designation_id, amount, effective_on, note, recorded_by, recorded_at)"
                        + " values (?, ?, ?::numeric, ?, null, ?, ?)",
                UUID.randomUUID(),
                designation,
                amount,
                effectiveOn,
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));
    }

    private void personSalaryInThePast(String kind, UUID personId, String amount, LocalDate effectiveOn) {
        jdbc.update(
                "insert into person_salary (id, person_kind, person_id, amount, effective_on, note, recorded_by, recorded_at)"
                        + " values (?, ?, ?, ?::numeric, ?, null, ?, ?)",
                UUID.randomUUID(),
                kind,
                personId,
                amount,
                effectiveOn,
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));
    }

    private static PersonRef staffRef(UUID userId) {
        return new PersonRef(Kind.STAFF, userId);
    }

    private List<LocalDate> workingDays(UUID schoolIdOrNull) {
        return workingCalendar.workingDays(schoolIdOrNull, month.atDay(1), month.atEndOfMonth());
    }

    private static BigDecimal perDay(String salary, int workingDays) {
        return new BigDecimal(salary).divide(BigDecimal.valueOf(workingDays), 10, RoundingMode.HALF_EVEN);
    }

    private static PersonRef teacherRef(UUID id) {
        return new PersonRef(Kind.TEACHER, id);
    }

    private static PersonRef managerRef(UUID id) {
        return new PersonRef(Kind.MANAGER, id);
    }

    // ----------------------------------------------------------------- Teacher

    @Test
    void aFullMonthTeacherIsPayableForEveryWorkingDayAndHasNoDeduction() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        PersonRef person = teacherRef(teacher);
        List<LocalDate> working = workingDays(site.schoolId());

        PayableDays payable = payRules.payableDays(person, month);
        assertThat(payable.dates()).containsExactlyElementsOf(working);
        assertThat(payable.workingDaysInMonth()).isEqualTo(working.size());
        assertThat(payable.spans()).hasSize(1);
        assertThat(payable.spans().get(0).schoolId()).isEqualTo(site.schoolId());

        SalaryInEffect salary = payRules.salaryOn(person, month.atDay(15));
        assertThat(salary).isInstanceOf(SalaryInEffect.Found.class);
        assertThat(((SalaryInEffect.Found) salary).monthly()).isEqualByComparingTo("26000");
        assertThat(((SalaryInEffect.Found) salary).source()).isEqualTo(SalaryInEffect.Source.TEACHER_RECORD);

        LossOfPayValue value = payRules.lossOfPayValueOn(person, month.atDay(15));
        BigDecimal full = perDay("26000", working.size());
        assertThat(((LossOfPayValue.Found) value).fullDay()).isEqualByComparingTo(full);
        assertThat(((LossOfPayValue.Found) value).halfDay())
                .isEqualByComparingTo(full.multiply(new BigDecimal("0.5")).setScale(10, RoundingMode.HALF_EVEN));
        assertThat(((LossOfPayValue.Found) value).workingDays()).isEqualTo(working.size());

        assertThat(payRules.unpaidDays(person, month)).isEqualTo(new UnpaidDays.Found(List.of()));
        TotalLossOfPay total = payRules.totalLossOfPay(person, month);
        assertThat(((TotalLossOfPay.Found) total).rounded()).isEqualByComparingTo("0");
    }

    @Test
    void absencesAndAHalfDayAreDeductedAndRoundedOnceOnTheTotal() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "30000", LocalDate.now().minusDays(300));
        List<LocalDate> working = workingDays(site.schoolId());
        mark(site, teacher, working.get(1), "A", 1);
        mark(site, teacher, working.get(2), "A", 1);
        mark(site, teacher, working.get(3), "A", 0.5);
        mark(site, teacher, working.get(4), "P", 1);
        PersonRef person = teacherRef(teacher);

        UnpaidDays unpaid = payRules.unpaidDays(person, month);
        List<com.hls.payroll.api.UnpaidDay> days = ((UnpaidDays.Found) unpaid).days();
        assertThat(days).extracting(com.hls.payroll.api.UnpaidDay::date).containsExactly(working.get(1), working.get(2), working.get(3));
        assertThat(days.get(0).dayValue()).isEqualByComparingTo("1");
        assertThat(days.get(2).dayValue()).isEqualByComparingTo("0.5");

        BigDecimal day = perDay("30000", working.size());
        BigDecimal expectedUnrounded = day.multiply(new BigDecimal("2.5")).setScale(10, RoundingMode.HALF_EVEN);
        TotalLossOfPay.Found total = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);
        assertThat(total.unrounded()).isEqualByComparingTo(expectedUnrounded);
        assertThat(total.rounded()).isEqualByComparingTo(expectedUnrounded.setScale(0, RoundingMode.HALF_UP));
        assertThat(total.days()).hasSize(3);
    }

    @Test
    void aMidMonthRaiseValuesEachUnpaidDayAtItsOwnDaysSalary() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, teacher, "26000", LocalDate.now().minusDays(300));
        teacherSalary(admin, teacher, "31200", month.atDay(16));
        List<LocalDate> working = workingDays(site.schoolId());
        LocalDate before = working.get(0);
        LocalDate after = working.get(working.size() - 1);
        mark(site, teacher, before, "A", 1);
        mark(site, teacher, after, "A", 1);

        TotalLossOfPay.Found total = (TotalLossOfPay.Found) payRules.totalLossOfPay(teacherRef(teacher), month);
        BigDecimal expected = perDay("26000", working.size()).add(perDay("31200", working.size()));
        assertThat(total.unrounded()).isEqualByComparingTo(expected);
        assertThat(total.rounded()).isEqualByComparingTo(expected.setScale(0, RoundingMode.HALF_UP));
        assertThat(total.days().get(0).salary()).isEqualByComparingTo("26000");
        assertThat(total.days().get(1).salary()).isEqualByComparingTo("31200");
    }

    @Test
    void aTeacherWithNoRecordedSalaryHasNoSalaryEvenWhenTheirDesignationHasOne() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID designation = designation(admin, "TEACHER");
        designationSalaryInThePast(designation, "45000", LocalDate.now().minusDays(400));
        UUID teacher = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        assertThat(teacherEmployment(admin, teacher, designation, null).status()).isEqualTo(200);
        PersonRef person = teacherRef(teacher);

        SalaryInEffect salary = payRules.salaryOn(person, month.atDay(15));
        assertThat(salary).isEqualTo(new SalaryInEffect.NoSalary(Reason.TEACHER_HAS_NO_RECORDED_SALARY));
        assertThat(payRules.lossOfPayValueOn(person, month.atDay(15))).isInstanceOf(LossOfPayValue.NoSalary.class);

        mark(site, teacher, workingDays(site.schoolId()).get(1), "A", 1);
        TotalLossOfPay total = payRules.totalLossOfPay(person, month);
        assertThat(total).isInstanceOf(TotalLossOfPay.Cannot.class);
        assertThat(((TotalLossOfPay.Cannot) total).reason()).contains("no salary");
    }

    @Test
    void aTeacherWhoseFirstAssignmentIsOnThe11thIsNotPayableBefore() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID teacher = teacherAt(admin, site.schoolId(), month.atDay(11));
        PayableDays payable = payRules.payableDays(teacherRef(teacher), month);
        assertThat(payable.dates()).isNotEmpty().allMatch(d -> !d.isBefore(month.atDay(11)));
        assertThat(payable.dates())
                .containsExactlyElementsOf(workingDays(site.schoolId()).stream()
                        .filter(d -> !d.isBefore(month.atDay(11)))
                        .toList());
    }

    @Test
    void aTransferBetweenSchoolsCountsEveryDateOnceAndLosesNone() {
        String admin = signInAs(Role.ADMIN).token();
        Site first = site(admin);
        Site second = site(admin);
        UUID teacher = teacherAt(admin, first.schoolId(), LocalDate.now().minusDays(200));
        Resp moved = placeTeacherRaw(admin, teacher, second.schoolId(), month.atDay(16));
        assertThat(moved.status()).as(moved.body()).isEqualTo(200);

        PayableDays payable = payRules.payableDays(teacherRef(teacher), month);
        assertThat(payable.dates()).doesNotHaveDuplicates();
        assertThat(payable.spans()).hasSize(2);
        assertThat(payable.spans().stream().mapToInt(com.hls.payroll.api.Span::workingDays).sum())
                .isEqualTo(payable.dates().size());
        // both Schools use the default calendar here, so no working day of the month is lost at the transfer
        assertThat(payable.dates()).containsExactlyElementsOf(workingDays(first.schoolId()));
    }

    // ----------------------------------------------------------------- Manager

    @Test
    void aManagerIsPaidTheirOwnSalaryAndTheirMonthStartsAtJoining() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx manager = newManager(admin);
        assertThat(designate(admin, manager.managerId(), designation, LocalDate.now().minusDays(300)).status())
                .isEqualTo(200);
        LocalDate joining = month.atDay(10);
        assertThat(employment(admin, manager.managerId(), uniqueEmployeeId(), joining, null).status())
                .isEqualTo(200);
        personSalaryInThePast("MANAGER", manager.managerId(), "40000", LocalDate.now().minusDays(400));
        PersonRef person = managerRef(manager.managerId());

        SalaryInEffect found = payRules.salaryOn(person, month.atDay(20));
        assertThat(found).isInstanceOf(SalaryInEffect.Found.class);
        assertThat(((SalaryInEffect.Found) found).monthly()).isEqualByComparingTo("40000");
        assertThat(((SalaryInEffect.Found) found).source()).isEqualTo(SalaryInEffect.Source.PERSON_RECORD);
        assertThat(payRules.salaryOn(person, month.atDay(9))).isEqualTo(new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED));

        PayableDays payable = payRules.payableDays(person, month);
        assertThat(payable.dates())
                .containsExactlyElementsOf(workingDays(null).stream().filter(d -> !d.isBefore(joining)).toList());
        assertThat(payable.spans().get(0).schoolId()).isNull();

        LossOfPayValue value = payRules.lossOfPayValueOn(person, month.atDay(20));
        assertThat(((LossOfPayValue.Found) value).fullDay()).isEqualByComparingTo(perDay("40000", workingDays(null).size()));
    }

    @Test
    void twoManagersOnOneDesignationAreEachPaidTheirOwnSalaryAndADesignationDefaultChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        designationSalaryInThePast(designation, "30000", LocalDate.now().minusDays(400)); // a suggested default only
        ManagerCtx first = newManager(admin);
        ManagerCtx second = newManager(admin);
        ManagerCtx third = newManager(admin);
        for (ManagerCtx m : List.of(first, second, third)) {
            assertThat(designate(admin, m.managerId(), designation, LocalDate.now().minusDays(300)).status())
                    .isEqualTo(200);
        }
        personSalaryInThePast("MANAGER", first.managerId(), "50000", LocalDate.now().minusDays(400));
        personSalaryInThePast("MANAGER", second.managerId(), "65000", LocalDate.now().minusDays(400));
        // the third has no salary of their own: the designation's default is not a fallback
        LocalDate date = month.atDay(15);

        assertThat(((SalaryInEffect.Found) payRules.salaryOn(managerRef(first.managerId()), date)).monthly())
                .isEqualByComparingTo("50000");
        assertThat(((SalaryInEffect.Found) payRules.salaryOn(managerRef(second.managerId()), date)).monthly())
                .isEqualByComparingTo("65000");
        assertThat(payRules.salaryOn(managerRef(third.managerId()), date))
                .isEqualTo(new SalaryInEffect.NoSalary(Reason.PERSON_HAS_NO_RECORDED_SALARY));

        // a newer default for the designation, and a raise for the first Manager only, move nobody else
        designationSalaryInThePast(designation, "99000", LocalDate.now().minusDays(100));
        personSalaryInThePast("MANAGER", first.managerId(), "58000", LocalDate.now().minusDays(100));
        assertThat(((SalaryInEffect.Found) payRules.salaryOn(managerRef(first.managerId()), date)).monthly())
                .isEqualByComparingTo("58000");
        assertThat(((SalaryInEffect.Found) payRules.salaryOn(managerRef(second.managerId()), date)).monthly())
                .isEqualByComparingTo("65000");
        assertThat(payRules.salaryOn(managerRef(third.managerId()), date))
                .isEqualTo(new SalaryInEffect.NoSalary(Reason.PERSON_HAS_NO_RECORDED_SALARY));
    }

    @Test
    void aManagerWithNoSalaryOfTheirOwnGetsAStatedReasonNeverZero() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx none = newManager(admin);
        assertThat(payRules.salaryOn(managerRef(none.managerId()), month.atDay(15)))
                .isEqualTo(new SalaryInEffect.NoSalary(Reason.PERSON_HAS_NO_RECORDED_SALARY));
        assertThat(payRules.lossOfPayValueOn(managerRef(none.managerId()), month.atDay(15)))
                .isInstanceOf(LossOfPayValue.NoSalary.class);
    }

    /** A check-in for a Manager on a date, written directly. */
    private void managerCheckIn(UUID managerId, LocalDate date) {
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, day_type, check_in_at, check_in_lat, check_in_lng, version)"
                        + " values (?, ?, ?, 'OFFICE', ?, 12.97, 77.59, 0)",
                UUID.randomUUID(),
                managerId,
                date,
                Timestamp.from(date.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()));
    }

    private void managerLeave(UUID managerId, LocalDate date, String fraction, boolean unpaid) {
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, leave_request_id, leave_fraction, leave_unpaid, version)"
                        + " values (?, ?, ?, ?, ?::numeric, ?, 0)",
                UUID.randomUUID(),
                managerId,
                date,
                UUID.randomUUID(),
                fraction,
                unpaid);
    }

    /** A Manager who joined well before the month with a recorded salary. */
    private ManagerCtx paidManager(String admin, String salary) {
        ManagerCtx manager = newManager(admin);
        assertThat(employment(admin, manager.managerId(), uniqueEmployeeId(), month.atDay(1).minusMonths(3), null).status())
                .isEqualTo(200);
        if (salary != null) {
            personSalaryInThePast("MANAGER", manager.managerId(), salary, LocalDate.now().minusDays(400));
        }
        return manager;
    }

    @Test
    void aManagersUnpaidDaysAreTheirAbsencesAndLossOfPayLeaveWithEachDaysFraction() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "30000");
        List<LocalDate> working = workingDays(null);
        for (int i = 0; i < working.size(); i++) {
            if (i > 3) {
                managerCheckIn(manager.managerId(), working.get(i));
            }
        }
        // 0: no check-in and no leave; 1: Loss-of-Pay leave; 2: paid leave; 3: paid half day with no check-in
        managerLeave(manager.managerId(), working.get(1), "1.0", true);
        managerLeave(manager.managerId(), working.get(2), "1.0", false);
        managerLeave(manager.managerId(), working.get(3), "0.5", false);
        PersonRef person = managerRef(manager.managerId());

        List<com.hls.payroll.api.UnpaidDay> days = ((UnpaidDays.Found) payRules.unpaidDays(person, month)).days();

        assertThat(days).extracting(com.hls.payroll.api.UnpaidDay::date).containsExactly(working.get(0), working.get(1), working.get(3));
        assertThat(days.get(0).dayValue()).isEqualByComparingTo("1");
        assertThat(days.get(1).dayValue()).isEqualByComparingTo("1");
        assertThat(days.get(2).dayValue()).isEqualByComparingTo("0.5");
    }

    @Test
    void aManagersTotalLossOfPayIsEachUnpaidDayAtThatDaysSalaryRoundedOnce() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "30000");
        personSalaryInThePast("MANAGER", manager.managerId(), "36000", month.atDay(16));
        List<LocalDate> working = workingDays(null);
        LocalDate before = working.stream().filter(d -> d.getDayOfMonth() < 16).findFirst().orElseThrow();
        LocalDate after = working.stream().filter(d -> d.getDayOfMonth() >= 16).reduce((a, b) -> b).orElseThrow();
        for (LocalDate d : working) {
            if (!d.equals(before) && !d.equals(after)) {
                managerCheckIn(manager.managerId(), d);
            }
        }
        PersonRef person = managerRef(manager.managerId());

        TotalLossOfPay.Found total = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);

        BigDecimal expected = perDay("30000", working.size()).add(perDay("36000", working.size()));
        assertThat(total.unrounded()).isEqualByComparingTo(expected);
        assertThat(total.rounded()).isEqualByComparingTo(expected.setScale(0, RoundingMode.HALF_UP));
        assertThat(total.days()).extracting(TotalLossOfPay.UnpaidDayValue::date).containsExactly(before, after);
        assertThat(total.days().get(0).salary()).isEqualByComparingTo("30000");
        assertThat(total.days().get(1).salary()).isEqualByComparingTo("36000");
    }

    @Test
    void aManagerWithNoAbsenceHasNoUnpaidDaysAndNothingDeducted() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "30000");
        workingDays(null).forEach(d -> managerCheckIn(manager.managerId(), d));
        PersonRef person = managerRef(manager.managerId());

        assertThat(((UnpaidDays.Found) payRules.unpaidDays(person, month)).days()).isEmpty();
        TotalLossOfPay.Found total = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);
        assertThat(total.rounded()).isEqualByComparingTo("0");
    }

    @Test
    void aManagerWithAnUnpaidDayAndNoSalaryGetsCannotNeverZero() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, null);
        PersonRef person = managerRef(manager.managerId());

        assertThat(((UnpaidDays.Found) payRules.unpaidDays(person, month)).days()).isNotEmpty();
        TotalLossOfPay total = payRules.totalLossOfPay(person, month);
        assertThat(total).isInstanceOf(TotalLossOfPay.Cannot.class);
        assertThat(((TotalLossOfPay.Cannot) total).reason()).contains("salary");
    }

    @Test
    void aManagerWithNoJoiningDateOrNoRecordGetsAStatedReasonNotZero() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx none = newManager(admin);

        UnpaidDays noJoining = payRules.unpaidDays(managerRef(none.managerId()), month);
        assertThat(noJoining).isInstanceOf(UnpaidDays.Unavailable.class);
        assertThat(((UnpaidDays.Unavailable) noJoining).reason()).contains("joining date");
        assertThat(payRules.totalLossOfPay(managerRef(none.managerId()), month)).isInstanceOf(TotalLossOfPay.Cannot.class);

        UnpaidDays unknown = payRules.unpaidDays(managerRef(UUID.randomUUID()), month);
        assertThat(unknown).isEqualTo(new UnpaidDays.Unavailable("The Manager is not known."));
        assertThat(payRules.totalLossOfPay(managerRef(UUID.randomUUID()), month))
                .isEqualTo(new TotalLossOfPay.Cannot("The Manager is not known."));
    }

    @Test
    void theOldManagerAttendanceNotRecordedAnswerIsGone() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = paidManager(admin, "30000");
        PersonRef person = managerRef(manager.managerId());

        assertThat(payRules.unpaidDays(person, month)).isInstanceOf(UnpaidDays.Found.class);
        assertThat(String.valueOf(payRules.totalLossOfPay(person, month))).doesNotContain("not recorded yet");
    }

    // ------------------------------------------------------------------- staff

    @Test
    void aStaffMemberIsPaidTheirOwnSalaryBetweenTheirJoiningAndExitDates() {
        Signed signed = signInAs(Role.ADMIN);
        String admin = signed.token();
        UUID userId = signed.userId();
        LocalDate joining = month.atDay(6);
        LocalDate exit = month.atDay(24);
        Resp dates = put(
                BASE + "/people/STAFF/" + userId + "/employment",
                admin,
                Map.of("joiningDate", joining.toString(), "exitDate", exit.toString()));
        assertThat(dates.status()).as(dates.body()).isEqualTo(200);
        personSalaryInThePast("STAFF", userId, "90000", LocalDate.now().minusDays(400));
        PersonRef person = staffRef(userId);

        SalaryInEffect found = payRules.salaryOn(person, month.atDay(15));
        assertThat(((SalaryInEffect.Found) found).monthly()).isEqualByComparingTo("90000");
        assertThat(((SalaryInEffect.Found) found).source()).isEqualTo(SalaryInEffect.Source.PERSON_RECORD);
        assertThat(payRules.salaryOn(person, joining.minusDays(1))).isEqualTo(new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED));
        assertThat(payRules.salaryOn(person, exit.plusDays(1))).isEqualTo(new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED));

        PayableDays payable = payRules.payableDays(person, month);
        assertThat(payable.dates())
                .containsExactlyElementsOf(workingDays(null).stream()
                        .filter(d -> !d.isBefore(joining) && !d.isAfter(exit))
                        .toList());
        assertThat(((LossOfPayValue.Found) payRules.lossOfPayValueOn(person, month.atDay(15))).fullDay())
                .isEqualByComparingTo(perDay("90000", workingDays(null).size()));
    }

    @Test
    void aStaffMemberWithNoSalaryGetsAStatedReasonButHasNoUnpaidDaysByRule() {
        Signed signed = signInAs(Role.DIRECTOR);
        PersonRef person = staffRef(signed.userId());
        assertThat(payRules.salaryOn(person, month.atDay(15)))
                .isEqualTo(new SalaryInEffect.NoSalary(Reason.PERSON_HAS_NO_RECORDED_SALARY));
        // an Admin or Director has no attendance: "no unpaid days" is the rule, not a missing answer
        assertThat(payRules.unpaidDays(person, month)).isEqualTo(new UnpaidDays.Found(List.of()));
        TotalLossOfPay.Found total = (TotalLossOfPay.Found) payRules.totalLossOfPay(person, month);
        assertThat(total.rounded()).isEqualByComparingTo("0");
        assertThat(total.days()).isEmpty();
    }

    @Test
    void aUserWhoIsNotStaffIsNotPaidAsStaff() {
        // a Teacher's login is not a payroll person of kind STAFF
        Signed teacherLogin = signInAs(Role.TEACHER);
        assertThat(payRules.salaryOn(staffRef(teacherLogin.userId()), month.atDay(15)))
                .isEqualTo(new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED));
        assertThat(payRules.payableDays(staffRef(teacherLogin.userId()), month).dates()).isEmpty();
    }

    // ----------------------------------------------------- repeatable and bulk

    @Test
    void theSameInputsGiveTheSameAnswerAndTheBulkFormsEqualTheSingleForms() {
        String admin = signInAs(Role.ADMIN).token();
        Site site = site(admin);
        UUID withSalary = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        teacherSalary(admin, withSalary, "28000", LocalDate.now().minusDays(300));
        UUID withoutSalary = teacherAt(admin, site.schoolId(), LocalDate.now().minusDays(200));
        ManagerCtx manager = newManager(admin);
        personSalaryInThePast("MANAGER", manager.managerId(), "50000", LocalDate.now().minusDays(400));
        Signed staff = signInAs(Role.ADMIN);
        personSalaryInThePast("STAFF", staff.userId(), "75000", LocalDate.now().minusDays(400));
        List<PersonRef> people = List.of(
                teacherRef(withSalary),
                teacherRef(withoutSalary),
                managerRef(manager.managerId()),
                staffRef(staff.userId()),
                teacherRef(UUID.randomUUID()),
                managerRef(UUID.randomUUID()),
                staffRef(UUID.randomUUID()));
        LocalDate date = month.atDay(15);

        Map<PersonRef, SalaryInEffect> bulk = payRules.salariesOn(people, date);
        assertThat(bulk).containsOnlyKeys(people);
        for (PersonRef person : people) {
            assertThat(payRules.salaryOn(person, date)).as(person.toString()).isEqualTo(bulk.get(person));
            assertThat(payRules.salaryOn(person, date)).isEqualTo(payRules.salaryOn(person, date));
        }
        // a person who does not exist is "not employed", never a salary of zero
        for (int unknown : new int[] {4, 5, 6}) {
            assertThat(bulk.get(people.get(unknown))).isEqualTo(new SalaryInEffect.NoSalary(Reason.NOT_EMPLOYED));
        }

        Map<PersonRef, PayableDays> payableBulk = payRules.payableDays(people, month);
        for (PersonRef person : people) {
            assertThat(payRules.payableDays(person, month)).as(person.toString()).isEqualTo(payableBulk.get(person));
        }
        assertThat(payableBulk.get(people.get(4)).dates()).isEmpty();
    }
}
