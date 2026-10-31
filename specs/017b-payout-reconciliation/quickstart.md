# Quickstart: validating Substitute Teachers and Payouts

Prerequisites: local stack per `docs/running-locally.md`; demo data: Admin, Director, Zone A and Zone B Managers, a Teacher designation "Substitute Teacher" with a day rate of ₹800 from 01/10/2026, two Teachers marked Substitute (no account), one regular Teacher, an absent Teacher, and a past month ready to lock.

1. **Substitute type.** As Admin set Teacher engagement to Substitute: any account link is cleared; there is no OTP sign-in for that phone. A regular Teacher is no longer offered in 017a "available".
2. **Day rate.** Salary Structures → Designation day rates: add ₹800 from 01/10/2026 and ₹900 from 15/11/2026; zero or negative is refused; history shows both.
3. **Allocate and mark.** Zone A Manager allocates the substitute to a requirement (017a) and marks the day Present; a Zone B Manager gets "not found"; future dates and a cancelled allocation are refused; a correction to Absent and back is in the history.
4. **Lock.** Add a half day and a full day; with one unmarked, lock is refused naming it; mark and lock as Admin; marking now fails; reopen with a reason works and is audited.
5. **Pay.** Compute the month's run as Admin: one line for the substitute, earned = (1.0 + 0.5) x rate, no deduction, working lists each day; a rate change inside the month shows two rows on the payslip; a substitute with no Present day has no line; a designation without a rate is "cannot pay" and does not block the run. Submit and approve as Director: days show Paid in run.
6. **After approval.** Cancel one paid day (change the substitute): "correction needed" under Mismatches; the approved run is unchanged; the Admin raises the Correction; the next run applies it; the row becomes Corrected. A day marked Present after approval is Back-pay waiting and is paid by a supplementary run for that month.
7. **Back-pay.** Seed credits before go-live (one in an open month, one in an approved month, one for a regular Teacher) and run the migration: migrated days appear flagged for review; the regular Teacher's credit is "settle first"; the open month's run pays; a supplementary run pays the approved month; all show their original months.
8. **Views.** Admin and Director see all; Zone Manager sees only own Zone days and cannot lock or raise corrections; a substitute's record shows days and pay; totals: attended = paid + unpaid by state; CSV equals the screen and neutralises a name starting with `=`.
9. **Repair job.** Delete a day record in test data and run `POST /reconcile`: recreated once; second run changes nothing.
10. **Regression.** 013b, 015, 030, 017a and 008 tests unchanged; a run with no substitute is identical to before; access-model tests for the new permission and menu.
11. Keyboard and screen-reader pass; dark mode; phone width; DD/MM/YYYY and Rupee grouping.

Commands: backend tests `./gradlew test --tests "com.hls.substitution.payout.*"` (or the repo's Maven equivalent) plus payroll, teacher, attendance and substitution tests; frontend `npm test -- substitute-payouts`.
