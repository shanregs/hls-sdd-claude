# Quickstart: validating Manager Attendance (spec 032)

Prerequisites: specs 005, 005a (joining dates), 008, 009, 010, 013a merged; the dev profile with the demo seed (Asha
Admin, Divya Director, Manoj Manager with a joining date); PostgreSQL through Testcontainers or the compose file. Use the
Postman folder "032 Manager Attendance"; paths are in [contracts/manager-attendance-api.md](./contracts/manager-attendance-api.md).

## 1. Check in and out (US1, SC-001)

1. Sign in as Manoj and open MY ATTENDANCE → Check-in. On a working day, check in as Office; the browser asks for the
   location and the day shows as worked with the time.
2. Try a second check-in the same day, and a check-in with the location blocked: both are refused with the reason.
3. Check out in the evening: the day shows both times. Leave another day without a check-out: it shows "no check-out" and
   still counts as worked and paid.
4. As Divya, complete that missing check-out with a reason; the day's history shows the original and the completion.

## 2. Field days (US2)

1. Check in as Field with no purpose: refused. With a purpose: accepted and shown in the month and the Director's grid.
2. Add a photo to the check-in: it shows on the day for Manoj, Asha and Divya; a Teacher cannot open it.

## 3. Leave (US3, SC-002)

1. As Manoj request two days of casual leave; as Divya approve; the days show as Leave and count as paid.
2. Request one Loss-of-Pay day and approve it: it counts as unpaid. Request a half day and check in the other half:
   half leave plus half worked.
3. As Manoj try to approve your own request, and as Asha try to approve a request of Asha's: both refused.
4. Try to check in on an approved full-day leave day: refused with the leave shown; revoke it and check in.

## 4. Missed check-in (US4, SC-005)

With the cut-off set in the past on a working day and a Manager with no check-in and no leave, the next job run
notifies the Manager, Divya and Asha once. Check in later: the day is marked late and leaves the missed list; the job
does not notify again.

## 5. Month, corrections, forgotten check-in (US5)

1. As Asha open the grid, filter to one Zone, and correct a day with a reason; the original stays in the history and the
   Manager is told.
2. As Manoj ask for a forgotten check-in on a past day of an unlocked month; as Divya approve it: the day is worked and
   marked "added later". Ask for a day in a locked month: refused.
3. A Manager with no joining date cannot check in or request leave and shows "no joining date" in the figures.

## 6. Lock (US6, SC-003)

Leave a request pending for the month: the lock is refused and lists it. Decide it, lock all Managers: further
check-ins, corrections and leave for the month are refused. Reopen one Manager with a reason: changes are allowed again.

## 7. Payroll reads the month (US7, SC-002, SC-008)

Run `ManagerMonthCalculatorTest` and `ManagerAttendanceReadApiTest`: a full month, paid leave, Loss-of-Pay leave, a day
with no check-in, a half-day leave, a holiday, a weekly off and a mid-month joiner match the hand-computed figures. Run
`PayRulesTest`: a Manager's `unpaidDays` and `totalLossOfPay` are answered, and an Admin's or Director's are an explicit
"none".

## 8. Access (SC-004)

Run the authorization tests: Manager reaches only their own data; Teacher and System get 403 on every endpoint;
Admin and Director succeed. In the browser, sign in as Manoj and open `/operations/manager-attendance` directly: "not
authorized".

## Results

Record the outcome of each step in `quickstart-results.md` when the walkthrough is done, as for earlier specs.
