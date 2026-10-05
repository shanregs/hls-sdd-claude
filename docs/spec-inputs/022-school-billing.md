# 022 School Billing: `/speckit-specify` input

2026-10-05. Split out of the first draft of spec 012 (kept for reference in `docs/spec-inputs/022-school-billing-draft/`: its
spec, research, data model, API contract, plan and tasks, written before the split). Depends on **012 School
Contracts (MoU)** and on 008 (attendance), 003 (audit), 005 (scope) and 010 (notifications). Feeds 013 (payroll), 014
(reports) and 017 (substitution). The numbering is only an identifier; 022 is delivered right after 012.

The business process this spec completes: **MoU contract (012) → Teacher mapping (012) → attendance capture (008) →
month-end billing and salary computation (this spec for the School's bill; 013 for the Teacher's pay)**.

## Feature description (paste as the argument to `/speckit-specify`)

022-school-billing: Month-end billing of Schools, driven by the MoU contracts of spec 012 and the attendance of spec
008, and the collection of what Schools pay. Module `schoolbilling` (it already holds contracts). Billing for a
School starts from its contract: nothing is billed for a School without a signed MoU, and the first billable month is
the month the MoU starts.

1. **The month-end bill.** For each School and month, once the Teachers' attendance for that month is locked
   (spec 008), the system works out the amount the School owes: for each Teacher mapped to a position of the
   School's contract, the position's monthly salary multiplied by the Teacher's attendance for the month (days worked
   plus approved leave, out of the month's working days, using the same attendance figures payroll uses). Absent
   days reduce the bill. A month still open shows a provisional figure that is clearly marked and is replaced by the
   final one at lock. Each figure shows how it was worked out (Teacher, position, salary, days, amount).
2. **Contract changes inside a month** are billed on the contract in effect for each part of the month.
   Teachers at the School who are not mapped to a position are listed as "not mapped" and add nothing. Vacant
   positions add nothing.
3. **Payments.** A Zone Manager (own Schools) or an Admin records a payment for a School and month: date, mode (Bank,
   Cash, Cheque, UPI), receiver, amount, comment. Several payments per month, including part-payments. Payments are
   never edited or deleted; a correction is a reversing entry with a reason, booked in the open month. A payment
   above the balance, or for a month not yet billed, is kept as an advance. A duplicate (same School, month, amount,
   date) is warned about, not refused.
4. **Balance** per School and month, and carried forward, computed from bills and payments; nobody types it.
5. **Views.** Admin and Director: expected, collected and outstanding for a month, organization-wide and per Zone
   Manager and per School; Zone Manager: own Schools only. CSV export. A list of Schools with no signed MoU or with
   Teachers not mapped, so nothing is silently unbilled.
6. **Month close.** An Admin closes a billing month when every Teacher billed in it has locked attendance. Closing
   freezes the bills. Corrections afterwards are adjustment lines in an open month.
7. **Overdue.** A School is overdue when its balance is above zero more than a set number of days after the month's
   expected payment day (both configurable). One in-app notification per School and month to the Zone Manager and
   the Directors (spec 010); it clears when paid.
8. **Public interface** for payroll (013) and reports (014): the salary the School pays for a Teacher's position,
   the Teacher's billed line for a month, and each School's collected amount and balance, so payroll can compute
   margin and flag a month where the School has not paid.
9. New permission module `SCHOOL_BILLING` (View, Create, Edit, Approve, Export) and OPERATIONS → School Billing;
   every bill, adjustment, payment, reversal and close is audited (spec 003).

Out of scope: invoice PDFs and sending invoices, tax and GST, bank reconciliation, payment gateways, other
currencies, billing cycles other than monthly, mobile screens, and the Teacher's own pay and margin (013).

## Decisions already made (carry them into the spec)

- **Bill by attendance** (user, 2026-10-05): the bill follows the Teacher's captured attendance, after the lock, as in
  the school-payment sheet. Not by assigned working days.
- **Billing starts from the contract** (user, 2026-10-05): from the contract's start month, not a fixed go-live month.
  The earlier "never bill before go-live" rule is dropped.
- Working days and attendance come from the Teacher's own attendance (spec 008), which follows the School's calendar;
  billing keeps no School calendar of its own.
- Only an Admin closes a month (the Approve action), and only after attendance is locked for every billed Teacher.
- After a Zone Manager change, only the current Zone Manager sees a School's billing, past months included.
- A payment is append-only in the code and in the database (a trigger rejects update and delete).

## Points for `/speckit-clarify`

- Exactly which attendance figure bills: the rollup's weighted total (as payroll does), or only days worked plus
  approved leave? How are half days, training days and unpaid leave treated?
- When a Teacher's attendance is locked on different days, is a School's bill final once all its Teachers are locked,
  or Teacher by Teacher?
- A mid-month change of contract or School: how are the Teacher's attendance days split between two positions or
  Schools (marks carry a School in spec 008)?
- Does a School owe for vacant positions it has not yet filled (the draft says no)?
- The expected payment day: one setting, or per MoU?
- A reversal of a payment from a closed month: confirm it is booked in the open month so closed totals never move.
- Does closing need the previous month closed first, and what about a month with no bill?
- Overdue: both the Zone Manager and the Directors every time, or Directors only when there is no Zone Manager?
- Is the Director's default grant on `SCHOOL_BILLING` to include Approve (the constitution's default grants say yes)?

## Review findings from the first draft to resolve in this spec

From `/speckit-analyze` on the combined draft: add audit-entry tests for every change; make "payment for a Teacher"
precise (payments are per School; the Teacher gets a billed line); performance targets need a seeded-volume test;
a review of the money logic and the migration before the PR; an end-contract rule that is testable.
