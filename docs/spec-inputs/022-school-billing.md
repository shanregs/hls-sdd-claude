# 022 School Billing and Receivables: `/speckit-specify` input

2026-10-05, rewritten the same day after the Director deck (slide 12) and decisions D5 to D15 in
`docs/spec-roadmap.md`. Depends on **012 School Contracts (MoU)** (merged in #23) and on 008 (attendance calendar),
003 (audit), 005 (scope), 010 (notifications) and, for follow-up tasks, **025 Manager tasks**. Feeds 013b (payroll
margin inputs), 014 (reports, margin) and 028 (dashboard). The earlier combined draft is kept for reference in
`docs/spec-inputs/022-school-billing-draft/`; the "bill by captured attendance" idea in it is **replaced** (D6).

The business process this spec completes: **MoU contract (012) → Teacher mapping (012) → attendance capture (008) →
month-end billing and collection (this spec) and salary (013)**.

## Feature description (paste as the argument to `/speckit-specify`)

022-school-billing: Invoicing Schools and collecting what they owe, for the Teachers mapped under the MoUs of spec
012. Module `schoolbilling` (it already holds contracts). Version 1 is monthly per Teacher only, with plain invoices
and no GST (decision D10).

1. **The invoice.** For each School and month, an Admin or Director raises one invoice. Each line is one Teacher
   mapped to a position of the School's contract in that month, at the salary of that position. A Teacher assigned
   for the whole month is billed in full; a Teacher who joined, was transferred in or out, or exited during the month
   is billed for the part of the month they were at the School, pro-rated by the Teacher's working days at the School
   over the Teacher's working days in the month (spec 008 calendar). Absences do **not** reduce the invoice (D6); an
   agreed deduction is a credit note. A vacant position adds nothing. Teachers at the School who are not mapped to a
   position, and Schools with no signed MoU, are listed as "not billed" so they cannot be missed. A raised invoice
   never changes; it shows how every amount was worked out.
2. **Invoice lifecycle.** An invoice has a number, an invoice date, a due date (invoice date plus the payment due days,
   a setting), and a status: raised, sent (the user marks it sent to the School, with the date), part paid, paid, or
   cancelled. "Overdue" is derived from the due date and the balance, not stored. A printable invoice view is
   available. A cancelled invoice stays visible with its reason.
3. **Replacements and substitutes keep billing continuous.** The position is billed whoever fills it: a replacement
   Teacher mapped to the same position continues the billing, and a substitute covering an absence does not change the
   invoice (substitute pay is spec 017b).
4. **Credit notes.** A credit note reduces an invoice by an amount, with a reason, for an agreed deduction. It is an
   append-only entry linked to the invoice; a mistake is corrected by a reversing entry, never an edit.
5. **Receipts.** The Zone Manager (own Schools), Admin or Director records a receipt: amount, date, mode (Bank, Cash,
   Cheque, UPI), reference, receiver, comment. A receipt is allocated to one or more invoices, part or full, and a
   receipt larger than what is owed is kept as an advance credit. Receipts are never edited or deleted; a correction is
   a reversing entry with a reason. A likely duplicate (same School, amount, date, mode and reference) is warned about,
   not refused.
6. **Balance and ageing.** The balance of each School and invoice is computed from invoices, credit notes and
   receipts; nobody types it. Outstanding amounts are shown in ageing buckets 0 to 30, 31 to 60, 61 to 90 and over 90
   days past due, by School, by area (Zone) and by Zone Manager.
7. **Overdue follow-up and escalation.** When an invoice passes its due date with a balance, the system creates a
   follow-up task for the School's Zone Manager (through the shared task interface of spec 025) and notifies them
   (spec 010). If it stays unpaid for a set number of days (a setting), it escalates to the Director. One task and
   one escalation per invoice; both close when the invoice is paid.
8. **Views.** OPERATIONS → School Billing. Admin and Director: totals of billed, collected and outstanding for a
   month, organization-wide and per area, per Zone Manager and per School; the ageing table; the "not billed" list;
   a School's invoices, credit notes and receipts. Zone Manager: the same for their own Schools only. CSV export in the
   same scope.
9. **Public interface** for payroll margin and reports (013b, 014): the amount billed per Teacher per month, and each
   School's collected amount and balance, so margin (School fee minus Teacher pay) can be reported to the Director,
   Admin and the Zone Manager for their own Schools (D14) and a month where the School has not paid is flagged.
10. New permission module `SCHOOL_BILLING` (View, Create, Edit, Approve, Export) and the menu item; every invoice,
    credit note, receipt, reversal, cancellation and status change is audited (spec 003). Scope applies to every list,
    total and export (Constitution Principle III).

Out of scope: GST and tax lines (D10), billing per term or per year (012b is deferred), sending the invoice by email
or SMS (the user marks it sent), payment gateways and bank reconciliation, other currencies, mobile screens, and the
Teacher's own pay and payslips (013).

## Decisions already made (carry them into the spec)

- **D6**: invoice from active assignments with credit notes; not from captured attendance.
- **D10**: monthly per Teacher only, no GST.
- **D14**: margin is wanted; Admin, Director and the Zone Manager (own Schools) see it, so billed amounts per Teacher
  are exposed to 013b and 014.
- **D5**: approvals of MoUs, transfers and incentives are by the Director or the Zone Manager; this spec has no MoU
  approval (that is 031).
- The invoice is derived from the contract positions and Teacher assignments of 012 through the public
  `SchoolContracts` and `TeacherPlacementSource` interfaces; billing does not read their tables.
- Money is stored exactly (two decimals), shown in rupees with Indian grouping, dates DD/MM/YYYY; payments, receipts,
  credit notes and invoices are append-only, corrected by new entries.
- After a Zone Manager change, only the current Zone Manager sees a School's billing, past months included.
- Overdue work uses the task platform (025, contract C2); until 025 exists, only the notification is raised.

## Points for `/speckit-clarify`

- When is the invoice raised: at the start of the month for that month (in advance), or after month end? Does one
  action raise every School's invoice, or one School at a time?
- Invoice number format and whether numbers must be gapless.
- Default payment due days, and the escalation days (both settings; give the first values).
- Who approves a credit note: Admin or Director only, or also the Zone Manager?
- A Teacher transferred between two Schools in a month: each School is billed its part (confirm), and what happens to
  a Teacher who exits mid-month?
- Is a printable invoice enough (print or save as PDF from the browser), or must the system generate a PDF file?
- Can an invoice be re-issued after a contract mistake (cancel and raise again), and how are both kept visible?
- Does a receipt have to be allocated at once, or can it wait as an unallocated advance until the Zone Manager chooses?
- Should a School with an overdue invoice block anything (for example new Teacher mapping), or is it information only?
- What should the Zone Manager see about Schools outside their area in the ageing table (nothing, per the scope rule)?

## Review findings from the first draft to resolve in this spec

From `/speckit-analyze` on the combined draft: add audit-entry tests for every change; make "payment for a Teacher"
precise (payments are per School; the Teacher gets a billed line); performance targets need a seeded-volume test; a
review of the money logic and the migration before the PR; keep the module-boundary rule (billing reads contracts and
assignments through their public interfaces only).
