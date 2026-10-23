# Quickstart: Expenses

Run guide for proving the feature end to end. Endpoints are in [contracts/expenses-api.md](./contracts/expenses-api.md);
tables in [data-model.md](./data-model.md).

## Prerequisites

- Backend and frontend running with demo data (`hls.seed.demo-data=true`); the dev seeder adds claims in every status.
- Demo users: an Admin, a Director, a Manager and a Teacher (the existing seeded accounts).
- A draft payroll run is possible for the month of the claim (spec 013b), with locked attendance for the claimant.

## Scenarios

1. **Raise and submit (US1, US7)**. As the Manager open OPERATIONS → My Expenses, add a Travel and Fuel claim of 850.00
   dated yesterday, Zone = the Manager's Zone, paid by me, one jpg bill, submit. Status Submitted. As another Manager the
   claim is "not found". Expected: Admin and Director get a notification.
2. **Validation**. Submit amount 0, a future date, a date 61 days back, category Other without a reason, no bill and no
   reason, a `.docx` file, an 11 MB file, a sixth bill. Each is refused with its specific reason.
3. **Retry**. Send the same `clientRequestId` twice: one claim. Same Manager, date, amount, category submitted again:
   409 with the match; confirm and see the confirmation in the history.
4. **Links (US2)**. Training Stay without a batch refused; with a batch accepted and the batch name and dates stored.
   Travel and Fuel linked to the Manager's own visit shows kind, date, place; cancel the visit and the claim still shows it.
5. **Decide (US3)**. As Director approve the claim; as Admin try to approve an Admin's own claim (refused), the Director
   can. Reject without a reason (refused), then with one; as the claimant correct and resubmit; the history holds both.
   Two approvers deciding together: the second sees who decided first.
6. **Payment (US4)**. Approve a 1,200.00 claim paid by the Manager; start the Manager's pay run: one Reimbursement line of
   1,200.00; cancel the run: the claim is still Approved and offered again; run, approve: "Paid with payroll" with the
   month. A second claim: record it paid by transfer with a reference; payroll stops offering it. Reverse an approved
   unpaid claim (back to Submitted, offer gone); try to reverse each paid claim (refused). A "paid by company" claim is
   Paid by company at approval and never reaches payroll.
7. **Budget (US5)**. Set 20,000.00 for a Zone and month; approve 12,000.00, leave 5,000.00 pending: budget 20,000, actual
   12,000, pending 5,000, remaining 8,000; approve 10,000 more: over by 2,000. An area with no budget shows "No budget set".
   A Manager sees no budgets and no other claims.
8. **Pending and costs (US6)**. Ageing buckets match the claims' submission dates. With 30,000.00 of approved Marketing
   and linked Travel and Fuel and three Schools won in the period: 10,000.00 per School. With 60,000.00 of Training Stay
   for a batch of 12 completers: 5,000.00 per Teacher. A period with no wins: the cost and "No Schools acquired".
   Export the month as CSV and check the columns.
9. **Access**. System has no menu entry and every path is refused; Teacher reaches only My Expenses; the Teacher menu
   shows My Expenses.
10. **After paid**. Try to change any field of a paid claim through the API and directly in the database: refused by the
    service and by the trigger. Every status change appears in the audit trail.

## Automated checks

- Backend: `mvn -q test -Dtest='com.hls.expenses.*Test,PayrollRun*Reimbursement*Test'` from `backend/`; the full suite
  last (run slow classes alone, with `run_in_background`).
- Frontend: `npm test` in `frontend/` (My Expenses and Expenses pages per role, axe in both themes).
- Postman: folder `015 Expenses` (added with the spec); not run by CI.

## Financial review (Principle IX)

For the owner to confirm before the feature is called done; each point lists the fixture that demonstrates it.

1. **Actual** = Approved, Paid and Paid by company claims by expense date in the month (budget fixture, scenario 7).
2. **Pending** = Submitted claims by expense date; a reversed approval moves from actual to pending.
3. **Remaining** = budget minus actual, pending shown beside it; over means actual above budget.
4. **A claim reaches payroll once**: run, cancel, re-run, approve; and the paid-outside route withdraws the offer
   (scenario 6).
5. **Cost per School** counts Marketing plus linked Travel and Fuel (marketing visit, meeting, call, campus drive) over
   Schools won in the period; overhead categories excluded.
6. **Cost per Teacher** counts Training Stay of batches that ended in the period over the Teachers who completed them.
7. **Rounding**: claims are exact two-decimal amounts; costs per School and per Teacher round half-up to two decimals and
   say so.
