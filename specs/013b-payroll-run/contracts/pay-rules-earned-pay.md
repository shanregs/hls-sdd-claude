# Contract: the addition to `PayRules`, and the events

Server-side only; nothing here is exposed over HTTP. Everything else in `PayRules` (spec 013a) is unchanged.

## `PayRules.earnedPay`

```text
/** FR-004 of spec 013b: what the person earned for the month. Mirror of totalLossOfPay. */
EarnedPay earnedPay(PersonRef person, YearMonth month);

sealed interface EarnedPay {
    /**
     * One payable date valued on its own day: the salary in effect that day over the working days of its month on
     * the calendar of the School the person was in that day (the default calendar for a Manager or staff member).
     * amount is unrounded, ten decimal places.
     */
    record DayValue(LocalDate date, BigDecimal salary, int workingDays, BigDecimal amount) {}

    /** rounded is the sum rounded once by the policy in effect on the month's last day; unrounded is the sum before. */
    record Found(BigDecimal rounded, BigDecimal unrounded, List<DayValue> days) implements EarnedPay {}

    /** No payable date has a salary, or a date has no working days: the reason, never a partial figure. */
    record Cannot(String reason) implements EarnedPay {}
}
```

Rules (each has a test first):
- The payable dates are exactly those of `payableDays`; the salary of a date is `salaryOn(person, date)`; the divisor of a
  date is the same number `totalLossOfPay` uses for the same date (one `Computed`, one place).
- A date with no salary gives `Cannot` naming the date and the reason (as `totalLossOfPay` does); a month with no working
  days for a date gives `Cannot`; a person with no payable dates gives `Found` with zero days only if the caller asked for a
  person the run decided is payable, so the run never calls it for such a person.
- Rounding uses `LossOfPayCalculator.round` with the policy of the month's last day, once, on the sum.
- Same inputs, same answer. One query set however it is called; the run calls it once per person from a virtual thread.

Fixture table for the hand-computed tests (salary / working days of the month, amounts to the rupee): a full month at a
constant salary equals the salary; a joiner on the 11th of a 20-working-day month with 10 payable dates; an exit on the
15th; a raise on the 16th (two salary rows, each date at its own); a half-day leave (earned unaffected, deduction 0.5); a
Loss-of-Pay day; a Teacher who changed School mid-month with different divisors; a Manager with unpaid days; a Director
(no unpaid days).

## Events (package `com.hls.payroll.api`)

| Event | Fields | Published by | Heard by |
| ----- | ------ | ------------ | -------- |
| `PayrollRunSubmitted` | runId, month, kind, actorUserId | `RunService.submit` | notification (tells every user holding `PAYROLL.APPROVE`) |
| `PayrollRunApproved` | runId, month, kind, actorUserId, submittedBy | `RunService.approve` | notification (tells the submitter) |
| `PayrollRunReturned` | runId, month, kind, actorUserId, submittedBy, reason | `RunService.returnToDraft` | notification (tells the submitter, with the reason) |

Events carry no amounts and no names. Published inside the transaction so they fire only if it commits (the existing
event publication registry).

## Calls the run makes to other modules

| To | Call | Why |
| -- | ---- | --- |
| `attendance.api.AttendanceReadApi` | `isLocked(teacherId, month)` | a Teacher's month must be locked |
| `attendance.api.ManagerAttendanceReadApi` | `months(managerIds, month)` | `Found.locked`, `NoJoiningDate`, `Unknown`; the figures frozen at the lock |
| `files.api.FileStore` | `store(actor, "PAYSLIP", lineId, name, bytes)` and `open(fileId)` | payslip PDFs |
| `audit` | `EntityChanged` and the standard audit entry | audit trail |

The unpaid days themselves come from `PayRules.unpaidDays`, which already reads the Teacher marks and the Manager
read API; the run does not read attendance for the figures, only for the lock state.
