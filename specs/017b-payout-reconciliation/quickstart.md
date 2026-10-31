# Quickstart: validating Substitute Payouts

Prerequisites: local stack per `docs/running-locally.md`; demo data with Admin, Director, a Zone Manager (Zone A), another (Zone B),
two Teachers with salary structures (013a), an absent Teacher, a past month with locked attendance, and the go-live month setting at or before it.

1. **Offer and pay.** Absent Teacher marked absent on a day in the locked month; assign the substitute (1.00 day). Open Substitute Payouts: status
   "Waiting for next run". Start the month's run (Admin): the substitute's line carries a Substitute credit of one day's value. Status "In draft run".
   Submit, approve as Director: "Paid in run" with month and amount. Repeat with 0.50: half amount.
2. **No double.** Recompute a draft run and run `POST /reconcile` twice: one adjustment only.
3. **Not locked / before start.** A credit in the open month: "Waiting for month lock". A credit before the go-live month: "Before reconciliation start".
4. **Void before payment.** Change the substitute while the adjustment is waiting or in a draft run: the first credit shows "Voided before payment", the adjustment is withdrawn, the new one is offered.
5. **Void after approval.** Withdraw the absence after the run was approved: "Voided after payment, correction needed" under Mismatches; the approved run is unchanged. Admin raises the correction; next run applies it; row "Corrected".
6. **Cannot price.** A substitute without a salary: "Cannot be priced" with the reason; add a salary, run reconcile: offered.
7. **Scope.** Zone B Manager sees none of Zone A, read-only, no correction button; Teacher sees only own pay status in My Substitutions, no amount before approval; Admin only can raise corrections.
8. **Totals and CSV.** Credited days = paid + unpaid per Teacher, School, month; the CSV equals the screen; a school name starting with `=` is neutralised.
9. **Missed event.** Remove the offer in test data, run the job: it returns once; second run changes nothing.
10. **Regression.** 013b, 015 and 030 payroll tests unchanged; access-model tests for the new permission and menu.
11. Keyboard and screen-reader pass; dark mode; phone width; DD/MM/YYYY and Rupee grouping.

Commands: backend tests `./gradlew test --tests "com.hls.substitution.payout.*"` (or the Maven equivalent the repo uses) plus the payroll tests; frontend `npm test -- substitute-payouts`.
