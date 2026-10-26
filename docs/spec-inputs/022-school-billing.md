# 022 School Billing and Receivables: `/speckit-specify` input

2026-10-05, rewritten the same day after the Director deck (slide 12) and decisions D5 to D15 in
`docs/spec-roadmap.md`; refreshed 2026-10-26 against the merged specs 012, 025 and 027 (025 now exists, so overdue
follow-up uses its `TaskCreation` interface, not a notification only) and corrected by the owner: this is an
**internal receivables ledger**, dues are not sent to Schools. Depends on **012 School Contracts (MoU)** (merged) and
on 008 (working-day calendar, for pro-rating only, not for the amount), 003 (audit), 005 (scope), 010 (notifications)
and **025 Manager tasks** (follow-up tasks). Feeds 013b (payroll margin inputs), 014 (reports, margin) and 028
(dashboard). The earlier combined draft is kept for reference in `docs/spec-inputs/022-school-billing-draft/`; the
"bill by captured attendance" idea in it is **replaced** (D6).

The business process this spec completes: **MoU contract (012) → Teacher mapping (012) → the monthly receivable per
School and its collection (this spec) and salary (013)**.

## Feature description (paste as the argument to `/speckit-specify`)

022-school-billing: An **internal receivables ledger**: for each School and month, how much the School owes HLS for the
Teachers mapped under the MoUs of spec 012, and what has been collected against it. Nothing is sent to the School;
there is no invoice document. Module `schoolbilling` (it already holds contracts). Version 1 is monthly per Teacher
only, with no GST (decision D10).

1. **The monthly due (receivable).** For each School and month, an Admin or Director raises one monthly due. Each line
   is one Teacher mapped to a position of the School's contract in that month, at the salary of that position. A
   Teacher assigned for the whole month counts in full; a Teacher who joined, was transferred in or out, or exited
   during the month counts for the part of the month at the School, pro-rated by the Teacher's working days at the
   School over the Teacher's working days in the month (spec 008 calendar). Absences do **not** reduce it (D6); an
   agreed deduction is an adjustment (credit note). A vacant position adds nothing. Teachers at the School who are not
   mapped to a position, and Schools with no signed MoU, are listed as "not billed" so they cannot be missed. A raised
   due never changes; it shows how every amount was worked out.
2. **Lifecycle.** A due has a simple internal reference, a raised date, a due date (raised date plus the payment due
   days, a setting), and a status: open, part paid, paid or cancelled. "Overdue" is derived from the due date and the
   balance, not stored. A cancelled due stays visible with its reason; a corrected one can be raised for the month.
3. **Replacements and substitutes keep it continuous.** The position counts whoever fills it: a replacement Teacher
   mapped to the same position continues, and a substitute covering an absence does not change the amount
   (substitute pay is spec 017b).
4. **Adjustments (credit notes).** An adjustment reduces a due by an amount, with a reason, for an agreed deduction. It
   is an append-only entry linked to the due; a mistake is corrected by a reversing entry, never an edit.
5. **Collections (receipts).** The Zone Manager (own Schools), Admin or Director records a collection: amount, date,
   mode (Bank, Cash, Cheque, UPI), reference, receiver, comment. It is allocated to one or more dues, part or full, and
   an amount larger than what is owed is kept as an advance credit. Collections are never edited or deleted; a
   correction is a reversing entry with a reason. A likely duplicate (same School, amount, date, mode and reference)
   is warned about, not refused.
6. **Balance and ageing.** The balance of each School and due is computed from dues, adjustments and collections;
   nobody types it. Outstanding amounts are shown in ageing buckets 0 to 30, 31 to 60, 61 to 90 and over 90 days past
   due, by School, by area (Zone) and by Zone Manager.
7. **Overdue follow-up and escalation.** When a due passes its due date with a balance, the system creates a
   follow-up task for the School's Zone Manager (through the shared task interface of spec 025) and notifies them
   (spec 010). If it stays unpaid for a set number of days (a setting), it escalates to the Director. One task and
   one escalation per due; both close when the due is paid or cancelled.
8. **Views.** OPERATIONS → School Billing. Admin and Director: totals of owed, collected and outstanding for a month,
   organization-wide and per area, per Zone Manager and per School; the ageing table; the "not billed" list; a
   School's dues, adjustments and collections. Zone Manager: the same for their own Schools only. CSV export in the
   same scope.
9. **Public interface** for payroll margin and reports (013b, 014): the amount owed per Teacher per month, and each
   School's collected amount and balance, so margin (School fee minus Teacher pay) can be reported to the Director,
   Admin and the Zone Manager for their own Schools (D14) and a month where the School has not paid is flagged.
10. New permission module `SCHOOL_BILLING` (View, Create, Edit, Approve, Export) and the menu item; every due,
    adjustment, collection, reversal, cancellation and status change is audited (spec 003). Scope applies to every
    list, total and export (Constitution Principle III).

Out of scope: sending, emailing, printing or sharing anything with the School, invoice documents and external invoice
numbering, GST and tax lines (D10), billing per term or per year (012b is deferred), payment gateways and bank
reconciliation, other currencies, mobile screens, and the Teacher's own pay and payslips (013).

## Decisions already made (carry them into the spec)

- **Internal ledger (owner correction, 2026-10-26)**: dues are not sent to Schools; they record what each School owes
  HLS for its Teachers.
- **D6**: amount from active assignments with adjustments; not from captured attendance.
- **D10**: monthly per Teacher only, no GST.
- **D14**: margin is wanted; Admin, Director and the Zone Manager (own Schools) see it, so amounts per Teacher are
  exposed to 013b and 014.
- **D5**: approvals of MoUs, transfers and incentives are by the Director or the Zone Manager; this spec has no MoU
  approval (that is 031).
- The amount is derived from the contract positions and Teacher assignments of 012 through the public
  `SchoolContracts` and `TeacherPlacementSource` interfaces; billing does not read their tables.
- Spec 012b (fee terms) is deferred; payment due days and escalation days are settings of this spec.
- Money is stored exactly (two decimals), shown in rupees with Indian grouping, dates DD/MM/YYYY; dues, adjustments
  and collections are append-only, corrected by new entries.
- After a Zone Manager change, only the current Zone Manager sees a School's ledger, past months included.
- Overdue work uses the task platform (025, contract C2, `TaskCreation.createFollowUp`, one open task per source key;
  `complete` and `cancel` close it when the due is paid or cancelled). Task holders are Zone Managers; a School
  with no Zone Manager sends the task to no one, so the Director and Admin are notified instead.
- Transfers and replacements (spec 027) move placements through `PlacementMoves`; billing reads the resulting
  assignment history and never the transfer tables, so a transfer mid-month splits the amount between Schools.

## Points for `/speckit-clarify`

- When is the due raised (start of month, in advance, or after month end) and does one action raise every School's?
- Due date basis, and the first values for payment due days and escalation days.
- Who may add an adjustment or cancel a due: Admin or Director only, or also the Zone Manager?
- Must a collection be allocated at once, or can it wait as an advance credit?
- From when does ageing count, and should an overdue School block anything or be information only?
- A Teacher transferred between two Schools in a month: each School owes its part (confirm); a Teacher who exits
  mid-month counts up to the exit date.
- What the Zone Manager sees about Schools outside their area (nothing, per the scope rule).

## Review findings from the first draft to resolve in this spec

From `/speckit-analyze` on the combined draft: add audit-entry tests for every change; make "payment for a Teacher"
precise (collections are per School; the Teacher gets a line in the due); performance targets need a seeded-volume
test; a review of the money logic and the migration before the PR; keep the module-boundary rule (billing reads
contracts and assignments through their public interfaces only).
