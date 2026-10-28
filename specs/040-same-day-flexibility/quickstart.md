# Quickstart: Validating Same-Day Flexibility

Contracts: [contracts/same-day-api.md](./contracts/same-day-api.md). Data: [data-model.md](./data-model.md).

## Prerequisites

- Local stack up with the dev seed (demo Teacher placed at a School whose Manager is a demo Manager; demo Admin and
  Director; a working day today - on a weekly off or holiday, use the "not a working day" scenario only).
- Backend tests: `JAVA_HOME` set to JDK 25; `mvn -q test -Dtest='SameDay*,ManagerCover*,TeacherLeaveDeduction*'` in
  `backend/`. Frontend: `npx vitest run src/features/leave src/features/tasks src/features/substitution` in `frontend/`.
  Mobile: `npx jest mobile/__tests__/sameDay*` in `mobile/`.

## 1. Same-day leave takes effect at once (US1, SC-001)

1. Sign in as the demo Teacher. Apply Leave: first and last date today, any type, a reason. The page says it takes
   effect at once. Save.
2. Expect: status Approved, chip "Same-day", no Pending step; My Attendance shows L for today; the Manager has a
   notification; Substitution lists an open requirement for today.
3. As the Manager: Leave Management, "Record same-day leave" for another Teacher in scope. Expect the same result and
   "Recorded by" showing the Manager. For a Teacher outside scope expect "not found".

## 2. Range that also covers later days (FR-005)

Apply from today to the day after tomorrow. Expect two parts in History: today Approved (Same-day), the later days
Pending with the link between them. Reject the later part as the Manager: today stays Approved.

## 3. Guards (FR-004)

On a locked month, a non-working day, and a day the Manager marked Present: each refuses with its reason. A day the
Manager marked Absent: accepted, the mark becomes leave, the earlier Absent is in the day's history, and the payroll
unpaid days for the month count the date once.

## 4. Pay (FR-003, SC-003)

For a Teacher with an approved Casual leave day (full) and an approved half day, run payroll for the month: unpaid days
are 1.5 days' worth by hand-computed figures from the month's pay policy, whatever the types. Compare with
`TeacherLeaveDeductionTest`.

## 5. Manager cover (US2, SC-004)

On the open requirement from step 1, as the Manager choose "I covered" and add the note. Expect Covered by Manager and a
row in Substitution > Manager covers (label "Manager cover", chip "Same-day"). Assign a Teacher substitute instead: the
cover row shows Ended, history kept, and the Teacher gets the credit. A Teacher's credit list never shows the cover.

## 6. Tasks and visits (US3, SC-002)

As the Manager: My Tasks > Log for today, type School visit, note, Completed. Expect it under Done, not under Awaiting
verification, with chip "Same-day". Log an Observation for a Teacher with an open observation due: the due clears. On a
task Admin planned for today choose "Done on the spot": final at once, assigner kept in History. Log a visit from
Visits: Completed, in Activity Calendar with the chip.

## 7. Offline (FR-017)

On the phone, switch to airplane mode, log a task for today, reconnect. Expect it to sync once (send twice via the retry
button: still one record) with the day it was logged as its date.

## 8. Visibility (US4, SC-005)

As Admin: filter Leave Management, Substitution, Manager Tasks, Visits and Activity Calendar by Same-day: exactly the
items above. Change History for one of them shows who recorded it, for whom, when and "auto-accepted". Open
`/reports/same-day` for this month: counts per Manager and School match.

## 9. Concurrency (SC-006)

Run `SameDayLeaveConcurrencyTest`: two simultaneous recordings for one Teacher and date - one 201, one 409.

## 10. Regression

A Teacher applying for tomorrow is still Pending and goes through approval; a Manager task assigned by Admin still needs
Acknowledge, Complete and Verify; Manager attendance unpaid days are unchanged.
