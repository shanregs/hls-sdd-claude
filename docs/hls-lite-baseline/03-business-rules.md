# Business rules and worked examples

Money is `numeric(12,2)` rupees. Dates are business dates in IST. Each component is rounded half-up to 2 decimals.
Items tagged (A-nn) depend on an assumption in `02-decisions-and-assumptions.md`.

## 1. Statuses

| Person kind | Statuses | Notes |
|-------------|----------|-------|
| Teacher | Trainee, Active, Exited | Trainee has no salary and no pay. Active with no placement is unpaid. Exited keeps history. Never deleted. |
| Manager, Admin, Director, System Engineer, Content Creator | Active, Exited | |
| Substitute | Active, Exited | No login, no salary, no placement. Belongs to a Zone. |

A Teacher moves Trainee to Active when induction is signed off. She is paid only from her first placement date.

## 2. Calendar

- Weekly offs are org-wide, with an optional per-School override. Holidays are a single list.
- A **working day** for a School is a day that is neither a weekly off nor a holiday.
- Attendance is marked only on working days. Weekly offs and holidays are never deducted.
- Calendar days of a month is the real count (28 to 31).

## 3. Teacher attendance

Codes: Present, Absent, Leave, Half day, Training day. Holiday is system-generated.
- Teacher marks own day for today and the previous 3 days. Manager of the School corrects. Admin and Director anywhere.
- **Training day** counts as Present for pay. If an invited Active Teacher is not attended in the session list, the day is Absent (LOP).
- Same-day leave is auto-approved and flagged.
- Month lock: Admin locks a month. It is refused while any placed working day is unmarked (A-05). Reopen needs a reason and is audited.
- Unmarked-day resolution: Manager or Admin sets each remaining day, or bulk-sets Present or Absent.

## 4. Leave

- Types: Casual, Sick, Personal, Other, Loss-of-Pay. No balances. Every leave day is deducted (D-07).
- Only working days inside the request are leave days; a holiday or weekly off in the range is not counted.
- Teacher leave: Manager decides (reject needs a reason). Approval writes Leave marks.
- Manager leave: Director decides.
- Half-day leave deducts 0.5 day.

## 5. Pay formula (A-01, A-02)

For one person and one month:

```
D            = calendar days in the month
dayValue     = salaryInEffect / D
paidDays     = daysInEmployment(month)            -- Teacher: placed days; others: employed days
unpaidDays   = Absent + Leave + 0.5 x Half day     -- inside paidDays only
net          = round( dayValue x (paidDays - unpaidDays) , 2 )
lines        = net + adjustments (+ or -)
```

- Salary in effect on a day comes from dated salary history. If salary changes mid-month, compute each segment with its own value over its own days.
- Teacher paidDays count only days placed in a School (D-04, D-05). A transfer mid-month does not change the salary or divisor, because there is no School-specific divisor.
- Manager: paidDays = days employed. Unpaid days = Leave (Director decided), Absent, resolved no-shows.
- Staff (Admin, Director, SE, Content Creator): unpaidDays = 0 unless a manual LOP adjustment line exists (A-03).
- Trainee: no line.
- Exited: paidDays ends on the exit date (inclusive).

### Examples (tests)

| # | Case | Result |
|---|------|--------|
| P1 | Salary 24,000. June (30 days). Placed all month. 2 Absent days. | dayValue 800. Net 22,400. |
| P2 | Salary 24,000. July (31 days). Placed all month, no absence. | 24,000.00. |
| P3 | Salary 24,000. June. Placed from 11 June. | paidDays 20. Net 16,000. |
| P4 | Salary 24,000. June. Placed all month. 1 Half day. | 24,000 - 400 = 23,600. |
| P5 | Salary 31,000. July (31 days). Placed all month. 1 Absent training day. | dayValue 1,000. Net 30,000. |
| P6 | Salary 30,000. Transfer on 16 June from School A to B. No absences. | 30,000 (no School-specific divisor). |
| P7 | Active Teacher with no placement. | No pay line. |
| P8 | Salary 24,000. February 2027 (28 days). 1 Absent. | dayValue 857.142857... Net 24,000 - 857.14 = 23,142.86. |
| P9 | Leave across a Sunday and a holiday inside the range. | Only the working days in the range are deducted. |
| P10 | Salary changes 24,000 to 27,000 effective 16 June. Placed all month, no absence. | 24,000 x 15/30 + 27,000 x 15/30 = 25,500.00. |

## 6. Payroll run

1. Admin locks Teacher and Manager attendance for the ended month.
2. Admin starts a run. Per-person lines: earned pay, LOP, adjustments, net. People that cannot be computed are listed with a reason and do not stop the run.
3. Admin recomputes while the run is Draft.
4. Admin **finalises**. The run is locked. The Director is notified (D-18).
5. Director **acknowledges** (optional comment). Acknowledgement does not gate anything.
6. Payment status is entered manually per line (paid date, mode, reference). CSV for the accountant. Payslip is a printable page.
7. Mistakes after finalise become adjustment lines in the next month's run. No reopen.
- Admin's own pay is a line in the same run.
- Adjustment kinds: Incentive, Reimbursement, Substitute pay, Correction, Manual LOP, Other. Each has a reason and a creator.
- One run per month.

## 7. Substitution and Substitute pay

- When a placed Teacher is Absent, or has approved Leave, on a School working day, the system creates a **substitution requirement** (School, Teacher, date). The Manager is notified.
- Manager options: assign one of the Zone's Substitutes not booked that day, or mark "Manager covers", or leave open (record "School informed" and a note).
- After the day, any Manager of the Zone, or Admin, marks the Substitute Present or Absent.
- Pay for a Substitute = sum of (rate x 1 day) over days marked Present. **The rate is typed on each substitution** (D-19). It may differ every time.
- A day marked after the run is finalised is paid by an adjustment line in the next run.
- If leave is revoked or the Teacher attendance changes to Present, the requirement reopens or is cancelled; a cancelled requirement with a marked-Present Substitute day is flagged to Admin.
- Substitution never changes the School invoice (A-04).

## 8. Contracts, billing, collections

- Contract: Teacher count, start and end dates, monthly fee (same for all positions, or per position), signatories, signed PDF. One active contract per School.
- MoU approval: pending then approved. Approved by Director or the School's Manager. Document is optional with a warning.
- A new MoU ends the old one and re-maps Teachers with no gap.
- **Invoice** per School per month (Admin raises). Line per placement segment:

```
lineAmount = round( positionFee x placedDaysInMonth / calendarDaysInMonth , 2 )
```

  A full-month placement is the full fee. Invoice is immutable once raised. Due date = invoice date + the due-days setting.
- Corrections are credit/debit adjustments, never edits.
- Payment: amount, date, mode, reference; allocated across invoices (part or full). Reversal is a negative payment.
- Ageing buckets 0-30, 31-60, 61-90, 90+ days overdue, by School, Zone and Manager.
- A School with an active contract and placements but no invoice for a finished month shows in the "not billed" list.
- No GST in this release (D-20).

### Examples (tests)

| # | Case | Result |
|---|------|--------|
| B1 | Fee 30,000. Placed all of June (30 days). | 30,000.00. |
| B2 | Fee 30,000. Placed 11 to 30 June (20 days). | 30,000 x 20/30 = 20,000.00. |
| B3 | Fee 31,000. Placed 10 days of July. | 10,000.00. |
| B4 | Teacher transferred 16 June: School A 15 days, School B 15 days, fee 30,000 each. | A: 15,000. B: 15,000. |
| B5 | Payment 50,000 allocated 30,000 to invoice 1 and 20,000 to invoice 2. | Invoice 1 settled; invoice 2 has the balance. |

## 9. Transfers

- One request: Teacher, from School, to School, effective date (today or later), reason, optional replacement Teacher.
- Raised by Admin, Director or the Manager; approved by Director or the leaving School's Manager.
- Approval ends the old placement the day before and starts the new one, in one transaction.
- No change is allowed to days already marked in attendance.
- Billing and pay read placements by date, so they follow the change.

## 10. Recruitment and induction

- College, then Drive, then Candidate (single entry or CSV). Outcome: selected, waitlisted, rejected.
- Director issues an Offer (salary, terms, deadline). Admin records acceptance.
- Acceptance creates the employee as Teacher/Trainee and adds her to an induction batch. A new offer replaces the old one manually.
- Zone Manager marks induction attendance. Admin or Director sign off the batch; members become Active and unplaced.
- First placement starts pay. A salary history row is dated from that placement. Induction is unpaid (D-02, D-03).

## 11. Training sessions

- A session has date, topic, trainer, venue, and an **audience** (new joiners or ongoing Teachers). Several sessions can run on the same day in different places.
- Invited attendees are listed. The Zone Manager marks each Attended or Not.
- For Active Teachers: Attended writes a Training-day mark (paid). Not attended writes Absent (LOP).
- For Trainees: attendance is recorded for sign-off only; never paid.

## 12. Incentive (D-14, A-11)

- When a MoU is approved a pending Incentive adjustment is created for the earner, once per School (the first signed MoU), with the amount from the setting at that time.
- It is paid in the next payroll run as an adjustment line; the Director acknowledges it with the run.
- Reversal is a negative adjustment.

## 13. Expenses (A-07)

- Claim: date, amount, category (6), Zone or Head office, up to 5 bills, optional reference text.
- Statuses: Submitted, Approved, Rejected (reason), Paid.
- Approver by hierarchy: Teacher claims to the School's Manager; Manager claims to Admin or Director; Admin's claim to the Director. Admin and Director can approve any other claim. No amount limit.
- Reimbursed via a payroll adjustment line or marked paid.
- Report: monthly total by Zone and category.

## 14. Tasks

- Statuses: Assigned, In progress, Completed, Verified. Creator: Admin, Director, or the system (observations, follow-ups).
- Assignee is a Manager. Completion needs a note; evidence files are optional.
- Overdue is a query. Completing late requires a reason.
- Director sees tasks overdue more than N days (setting).

## 15. Observation

- A Manager observes a Teacher at one of the Manager's Schools and enters the record: date, six ratings (1 to 5), comments, optional School feedback note, attachments, optional result.
- Draft is editable; after Submit only notes can be added.
- "Needs improvement" creates a follow-up task. "Unsuitable" opens a transfer request.

## 16. Content

- Content Creator (or Manager) creates an item and uploads version 1, then submits.
- Admin or Director approves or returns with a comment. The creator uploads the next version. All versions are kept.
- Upload cap 100 MB; larger videos are linked.
