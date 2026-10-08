# Contract: `payroll.api.PayRules` (Java interface, for spec 013b and reports)

Server-side only; no HTTP exposure. Results are sealed types so a caller must handle the "no answer" case and can show
its reason (FR-008, FR-009, SC-007). No method returns zero to mean "missing". Bulk forms take a collection of
`PersonRef` and answer in a fixed number of queries.

```java
public interface PayRules {

    enum Kind { TEACHER, MANAGER }
    record PersonRef(Kind kind, UUID id) {}

    /** FR-008: the monthly salary in effect on a date. */
    SalaryInEffect salaryOn(PersonRef person, LocalDate date);

    /** FR-009: what one day and a half day of loss of pay are worth on a date. Unrounded. */
    LossOfPayValue lossOfPayValueOn(PersonRef person, LocalDate date);

    /** FR-010, FR-011: the dates of the month the person is payable for. */
    PayableDays payableDays(PersonRef person, YearMonth month);

    /** FR-012: the payable dates the person was unpaid on, with their day values. */
    UnpaidDays unpaidDays(PersonRef person, YearMonth month);

    /** FR-006, FR-007: the policy version in effect on a date. */
    PayPolicyView policyOn(LocalDate date);

    /** The month total deducted: each unpaid day valued on its own day, summed, rounded once by the month's policy. */
    TotalLossOfPay totalLossOfPay(PersonRef person, YearMonth month);

    Map<PersonRef, SalaryInEffect> salariesOn(Collection<PersonRef> people, LocalDate date);
    Map<PersonRef, PayableDays> payableDays(Collection<PersonRef> people, YearMonth month);
}
```

## Result types

```java
sealed interface SalaryInEffect {
    record Found(BigDecimal monthly, LocalDate effectiveOn, Source source) implements SalaryInEffect {}
    record NoSalary(Reason reason) implements SalaryInEffect {}   // NO_DESIGNATION, DESIGNATION_HAS_NO_SALARY,
                                                                  //   TEACHER_HAS_NO_RECORDED_SALARY, NOT_EMPLOYED
    enum Source { DESIGNATION, TEACHER_RECORD }
}

sealed interface LossOfPayValue {
    record Found(BigDecimal fullDay, BigDecimal halfDay, int workingDays) implements LossOfPayValue {}
    record NoSalary(SalaryInEffect.NoSalary cause) implements LossOfPayValue {}
    record NoWorkingDays(YearMonth month) implements LossOfPayValue {}
}

record PayableDays(YearMonth month, List<LocalDate> dates, int workingDaysInMonth, List<Span> spans) {}
record Span(UUID schoolId /* null for a Manager */, LocalDate from, LocalDate to, int workingDays) {}

sealed interface UnpaidDays {
    record Found(List<UnpaidDay> days) implements UnpaidDays {}   // date, dayValue (1 or the half-day fraction)
    record Unavailable(String reason) implements UnpaidDays {}    // e.g. Manager attendance not recorded yet
}

sealed interface TotalLossOfPay {
    record Found(BigDecimal rounded, BigDecimal unrounded, List<UnpaidDayValue> days) implements TotalLossOfPay {}
    record Cannot(String reason) implements TotalLossOfPay {}     // a day without a salary or working days, or Unavailable
}
```

## Guarantees (each is a test)

1. The same inputs and the same stored rows always give the same answer (pure calculators, no clock inside).
2. `salaryOn` equals the latest row on or before the date, ties to the row recorded last (SC-001, 10+ cases).
3. A Teacher's answer never uses the designation's salary (FR-005, clarified).
4. `lossOfPayValueOn` for 26 working days and ₹26,000 is a full day ₹1,000 and a half day ₹500 (US3 scenario 3), with no
   rounding applied.
5. `payableDays` counts each date at most once across Schools and never counts training days, days after exit, or days
   with no current School (SC-003).
6. `totalLossOfPay` rounds exactly once, with the policy in effect for the month; each unpaid day is valued at the
   salary in effect on that day (clarified). If any unpaid day has no salary, the whole answer is `Cannot`; it never
   pays a partial figure.
7. A month with no working days gives `NoWorkingDays`; no division is attempted.
8. `unpaidDays` for a Manager is `Unavailable` until spec 032; it never returns an empty `Found`.

## What this interface reads

`teacher.api.TeacherDirectory` (placements, exit date, new `salaryOn`), 005a's Manager and designation interfaces,
`attendance.api.AttendanceReadApi` (marks by category), new `attendance.api.WorkingCalendar`, and the Loss-of-Pay leave
type from spec 009 amendment A3. It does not read any other module's tables.
