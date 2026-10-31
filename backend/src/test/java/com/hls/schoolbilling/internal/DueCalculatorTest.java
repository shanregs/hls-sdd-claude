package com.hls.schoolbilling.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hls.schoolbilling.api.BillingCalendar;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests of {@link DueCalculator} (spec 022 T006): repositories are mocks and the School calendar is a Monday to
 * Friday stub, so no database or container is needed. November 2026 has 21 working days (1 November is a Sunday).
 */
class DueCalculatorTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final LocalDate FIRST = NOV.atDay(1);
    private static final LocalDate LAST = NOV.atEndOfMonth();

    private final UUID school = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private ContractAssignmentRepository assignments;
    private ContractPositionRepository positions;
    private ContractRepository contracts;
    private BillingCalendar calendar;
    private DueCalculator calculator;

    private final List<ContractAssignment> assignmentRows = new ArrayList<>();
    private final List<ContractPosition> positionRows = new ArrayList<>();
    private final List<Contract> contractRows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        assignments = mock(ContractAssignmentRepository.class);
        positions = mock(ContractPositionRepository.class);
        contracts = mock(ContractRepository.class);
        calendar = new WeekdayCalendar();
        calculator = new DueCalculator(assignments, positions, contracts, calendar);
        when(assignments.atSchoolOverlapping(eq(school), any(), any())).thenReturn(assignmentRows);
        when(positions.findAllById(any())).thenReturn(positionRows);
        when(contracts.findAllById(any())).thenReturn(contractRows);
        when(contracts.liveOverlapping(anyCollection(), any(), any())).thenReturn(contractRows);
    }

    /** Monday to Friday are working days. */
    private static final class WeekdayCalendar implements BillingCalendar {
        @Override
        public BigDecimal monthWorkingDays(UUID schoolId, YearMonth month) {
            return BigDecimal.valueOf(workingDaysIn(month.atDay(1), month.atEndOfMonth()));
        }

        @Override
        public int workingDaysBetween(UUID schoolId, LocalDate from, LocalDate to) {
            return workingDaysIn(from, to);
        }

        private static int workingDaysIn(LocalDate from, LocalDate to) {
            int n = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    n++;
                }
            }
            return n;
        }
    }

    private Contract activeContract(LocalDate startsOn, LocalDate endsOn) {
        Contract c = Contract.signed(school, SalaryMode.PER_TEACHER, 1, null, startsOn, startsOn, endsOn, actor, Instant.now());
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        contractRows.add(c);
        return c;
    }

    private ContractPosition position(Contract contract, int number, String salary) {
        ContractPosition p = new ContractPosition(contract.getId(), number, "Teacher " + number, new BigDecimal(salary));
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        positionRows.add(p);
        return p;
    }

    private ContractAssignment assign(UUID teacherId, ContractPosition p, LocalDate from, LocalDate to) {
        ContractAssignment a =
                new ContractAssignment(teacherId, school, p == null ? null : p.getId(), from, actor, Instant.now());
        a.setEndsOn(to);
        assignmentRows.add(a);
        return a;
    }

    @Test
    void schoolWithNoAssignmentsOwesNothing() {
        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).isEmpty();
        assertThat(r.notBilled()).isEmpty();
        assertThat(r.total()).isEqualByComparingTo("0");
    }

    @Test
    void wholeMonthBillsTheExactPositionSalary() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 6, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).hasSize(1);
        DueCalculator.Line line = r.lines().get(0);
        assertThat(line.fullMonth()).isTrue();
        assertThat(line.amount()).isEqualByComparingTo("25000.00");
        assertThat(line.workedDays()).isEqualByComparingTo("21.0");
        assertThat(line.monthDays()).isEqualByComparingTo("21.0");
        assertThat(line.from()).isEqualTo(FIRST);
        assertThat(line.to()).isEqualTo(LAST);
        assertThat(DueCalculator.working(line)).isEqualTo("Whole month at 25000.00");
    }

    @Test
    void joinerMidMonthIsProratedOnWorkingDaysNotCalendarDays() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        // 11 Nov to 30 Nov: 3 + 5 + 5 + 1 = 14 of 21 working days
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 11, 11), null);

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.fullMonth()).isFalse();
        assertThat(line.workedDays()).isEqualByComparingTo("14.0");
        assertThat(line.amount()).isEqualByComparingTo("16666.67"); // 25000 x 14 / 21 = 16666.666... half up
        assertThat(line.from()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(line.to()).isEqualTo(LAST);
        assertThat(DueCalculator.working(line)).isEqualTo("25000.00 x 14.0 working days at the School / 21.0 working days in the month");
    }

    @Test
    void exitMidMonthIsProratedToTheLastDay() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        // 1 Nov to 15 Nov: 10 working days (the 1st and 8th/15th are Sundays)
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 15));

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.fullMonth()).isFalse();
        assertThat(line.to()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(line.workedDays()).isEqualByComparingTo("10.0");
        assertThat(line.amount()).isEqualByComparingTo("11904.76"); // 11904.7619
    }

    @Test
    void assignmentEndingOnTheLastDayOfTheMonthIsAFullMonth() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "18000"), LocalDate.of(2026, 1, 1), LAST);

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.fullMonth()).isTrue();
        assertThat(line.amount()).isEqualByComparingTo("18000.00");
    }

    @Test
    void replacementInTheSamePositionBillsTwoLinesThatShareTheMonth() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        ContractPosition p = position(c, 1, "21000");
        UUID replacement = UUID.randomUUID();
        // leaver to Thu 12 Nov (9 working days), replacement from Fri 13 Nov (12 working days)
        assign(teacher, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 12));
        assign(replacement, p, LocalDate.of(2026, 11, 13), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).hasSize(2);
        assertThat(r.lines().get(0).amount()).isEqualByComparingTo("9000.00"); // 21000 x 9 / 21
        assertThat(r.lines().get(1).amount()).isEqualByComparingTo("12000.00"); // 21000 x 12 / 21
        assertThat(r.total()).isEqualByComparingTo("21000.00");
        assertThat(r.lines()).extracting(DueCalculator.Line::positionNumber).containsOnly(1);
    }

    @Test
    void severalTeachersAddUpToOneSchoolTotal() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "20000"), LocalDate.of(2026, 1, 1), null);
        assign(UUID.randomUUID(), position(c, 2, "15000.50"), LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).hasSize(2);
        assertThat(r.total()).isEqualByComparingTo("35000.50");
    }

    @Test
    void assignmentIsClippedToTheContractDates() {
        // the contract only starts on 16 Nov (11 working days) and ends 20 Nov of the same month: 16 to 20 = 5 days
        Contract c = activeContract(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20));
        assign(teacher, position(c, 1, "21000"), LocalDate.of(2026, 1, 1), null);

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.from()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(line.to()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(line.fullMonth()).isFalse();
        assertThat(line.amount()).isEqualByComparingTo("5000.00"); // 21000 x 5 / 21
    }

    @Test
    void absenceAndLeaveNeverReduceTheBill() {
        // the calculator has no attendance input at all: only assignment dates and the School calendar count
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 1, 1), null);

        assertThat(calculator.compute(school, NOV).total()).isEqualByComparingTo("25000.00");
    }

    @Test
    void oneWorkingDayIsOneDayOfTheMonth() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        // Mon 30 Nov only
        assign(teacher, position(c, 1, "21000"), LocalDate.of(2026, 11, 30), null);

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.workedDays()).isEqualByComparingTo("1.0");
        assertThat(line.amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void aSpanWithNoWorkingDayBillsZero() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        // Sat 14 Nov to Sun 15 Nov
        assign(teacher, position(c, 1, "21000"), LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15));

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.workedDays()).isEqualByComparingTo("0.0");
        assertThat(line.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void oneCalendarDayAssignmentOnAWorkingDay() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "21000"), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.from()).isEqualTo(line.to());
        assertThat(line.amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void roundingIsHalfUpToTwoDecimals() {
        BillingCalendar quarter = mock(BillingCalendar.class);
        when(quarter.monthWorkingDays(school, NOV)).thenReturn(BigDecimal.valueOf(4));
        when(quarter.workingDaysBetween(eq(school), any(), any())).thenReturn(1);
        calculator = new DueCalculator(assignments, positions, contracts, quarter);
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        // 0.10 x 1 / 4 = 0.025 exactly: half up gives 0.03 (half even would give 0.02)
        assign(teacher, position(c, 1, "0.10"), LocalDate.of(2026, 11, 20), null);

        assertThat(calculator.compute(school, NOV).lines().get(0).amount()).isEqualByComparingTo("0.03");
    }

    @Test
    void monthWithZeroWorkingDaysBillsAPartMonthAsZeroAndAFullMonthAtSalary() {
        BillingCalendar none = mock(BillingCalendar.class);
        when(none.monthWorkingDays(school, NOV)).thenReturn(BigDecimal.ZERO);
        when(none.workingDaysBetween(eq(school), any(), any())).thenReturn(0);
        calculator = new DueCalculator(assignments, positions, contracts, none);
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        ContractPosition p = position(c, 1, "20000");
        assign(teacher, p, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 10));
        assign(UUID.randomUUID(), p, LocalDate.of(2026, 11, 11), null);
        ContractAssignment whole = assign(UUID.randomUUID(), position(c, 2, "10000"), LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines().get(0).amount()).isEqualByComparingTo("0.00");
        assertThat(r.lines().get(1).amount()).isEqualByComparingTo("0.00");
        // a whole-month assignment is billed the exact salary whatever the calendar says
        assertThat(r.lines().get(2).teacherId()).isEqualTo(whole.getTeacherId());
        assertThat(r.lines().get(2).amount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void workedDaysAreCappedAtTheDaysOfTheMonth() {
        BillingCalendar over = mock(BillingCalendar.class);
        when(over.monthWorkingDays(school, NOV)).thenReturn(BigDecimal.valueOf(20));
        when(over.workingDaysBetween(eq(school), any(), any())).thenReturn(25);
        calculator = new DueCalculator(assignments, positions, contracts, over);
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "20000"), LocalDate.of(2026, 11, 2), null);

        DueCalculator.Line line = calculator.compute(school, NOV).lines().get(0);

        assertThat(line.workedDays()).isEqualByComparingTo("20.0");
        assertThat(line.amount()).isEqualByComparingTo("20000.00");
    }

    @Test
    void assignmentWithoutAPositionIsNotBilledAsNoPosition() {
        assign(teacher, null, LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).isEmpty();
        assertThat(r.notBilled()).containsExactly(new DueCalculator.Unbilled(teacher, DueCalculator.NO_POSITION));
    }

    @Test
    void assignmentWithoutAPositionInASchoolWithAPendingMouIsNotBilledAsMouPending() {
        Contract pending = Contract.pending(school, LocalDate.of(2026, 1, 1), actor, Instant.now());
        ReflectionTestUtils.setField(pending, "id", UUID.randomUUID());
        contractRows.add(pending);
        assign(teacher, null, LocalDate.of(2026, 1, 1), null);

        assertThat(calculator.compute(school, NOV).notBilled())
                .containsExactly(new DueCalculator.Unbilled(teacher, DueCalculator.MOU_PENDING));
    }

    @Test
    void positionOfAPendingContractIsNotBilledAsMouPending() {
        Contract pending = Contract.pending(school, LocalDate.of(2026, 1, 1), actor, Instant.now());
        ReflectionTestUtils.setField(pending, "id", UUID.randomUUID());
        contractRows.add(pending);
        assign(teacher, position(pending, 1, "25000"), LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).isEmpty();
        assertThat(r.notBilled()).containsExactly(new DueCalculator.Unbilled(teacher, DueCalculator.MOU_PENDING));
    }

    @Test
    void cancelledContractIsNotBilledAsNoActiveContract() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        c.setState(ContractState.CANCELLED);
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).isEmpty();
        assertThat(r.notBilled()).containsExactly(new DueCalculator.Unbilled(teacher, DueCalculator.NO_ACTIVE_CONTRACT));
    }

    @Test
    void contractThatEndedBeforeTheAssignmentStartsLeavesNothingToBill() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 5));
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 11, 10), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.lines()).isEmpty();
        assertThat(r.notBilled()).containsExactly(new DueCalculator.Unbilled(teacher, DueCalculator.NO_ACTIVE_CONTRACT));
    }

    @Test
    void aTeacherIsListedOnceInNotBilledEvenWithTwoSpans() {
        assign(teacher, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 10));
        assign(teacher, null, LocalDate.of(2026, 11, 11), null);

        assertThat(calculator.compute(school, NOV).notBilled()).hasSize(1);
    }

    @Test
    void billedAndNotBilledTeachersAppearTogether() {
        Contract c = activeContract(LocalDate.of(2026, 1, 1), null);
        assign(teacher, position(c, 1, "25000"), LocalDate.of(2026, 1, 1), null);
        UUID unmapped = UUID.randomUUID();
        assign(unmapped, null, LocalDate.of(2026, 1, 1), null);

        DueCalculator.Result r = calculator.compute(school, NOV);

        assertThat(r.total()).isEqualByComparingTo("25000.00");
        assertThat(r.notBilled()).extracting(DueCalculator.Unbilled::teacherId).containsExactly(unmapped);
    }
}
