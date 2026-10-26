# Quickstart: validating Manager Incentives

Prerequisites: local stack per `docs/running-locally.md`; demo data with a Zone Manager (Zone A), a second Zone Manager (Zone B), an Admin,
a Director, a won prospect owned by the Zone A Manager, and a payroll month with locked attendance.

1. **Earn.** Record the MoU for the won prospect's School (012), submit and approve it (031) as the Director. Expect one incentive,
   PENDING_APPROVAL, Rs 5,000, closure date today, earner the prospect owner. Approve the MoU again after a document change: still one.
2. **No earner.** Approve a MoU for a School with no won prospect, and for a School whose prospect owner is the Director: both appear in
   Incentives, No earner, with reasons `NO_PROSPECT` and `OWNER_NOT_MANAGER`. A legacy "approved (before approval step)" School appears
   as `APPROVED_BEFORE_APPROVAL_STEP` and has no incentive.
3. **Approve.** As Admin: approve is refused. As the Zone B Manager: the incentive is not visible (404). As the Zone A Manager (also the earner):
   approve; the row shows "approved by the earner"; Change History names earner and approver. Reject another with and without a reason.
4. **Pay.** As Admin start the month's run in Payroll: the Manager's line shows an Incentive adjustment of Rs 5,000 and the net pay includes it.
   Submit and approve as Director. Incentive shows PAID_IN_RUN with the month; it cannot change. Recompute or return a run before approval: still one line.
5. **Hold.** Approve an incentive, then replace the MoU document (MoU goes pending). The incentive becomes HELD and a new run does not take it.
   Approve the MoU again: it returns to APPROVED and the next run takes it. Repeat with a run awaiting approval: incentive keeps `mouChanged` and the Director is notified.
6. **Lists.** Director sees all; filter by Zone, status, month; totals per Manager and month; CSV export matches. A Manager's `/mine` shows only own.
7. **Dashboard and inbox.** Pending incentives appear in the Director approvals inbox and the 028 tile.
8. **Regression.** Run the 013b and 015 payroll tests (unchanged results) and the access-model tests for the new menu and permission.
9. Keyboard and screen-reader pass; dark mode; phone width.

Commands: backend tests `./gradlew test --tests "com.hls.incentive.*"` (or the Maven equivalent used by the repo) and the payroll tests; frontend `npm test -- incentives`.
