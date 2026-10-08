# Feature Specification: Salary Structures and Pay Policy

**Feature Branch**: `013a-salary-structures`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "013a-salary-structures: The salary structure and pay policy that the monthly payroll (spec 013b) will use. A fixed monthly salary for each designation (spec 005a) with an effective date, versioned and never overwritten; a Manager's salary is the structure of their designation, a Teacher's is the salary on their record; loss of pay for each unpaid day is the monthly salary divided by the month's working days; pro-rata rules for joining, transfer and exit (a Teacher is paid only for days placed in a School, training is unpaid); the pay month is the calendar month. Admin and Director keep it under OPERATIONS → Salary Structures. Module `payroll`. Full source: docs/spec-inputs/013a-salary-structures.md."

## Clarifications

### Session 2026-10-08

- Q: Is rounding to the nearest rupee applied to each day's loss-of-pay value or only to the total deducted for the month? → A: Only the total deduction for the month is rounded; the value of a day and of a half day are kept at full precision.
- Q: When a salary changes mid-month, which salary values a day of loss of pay? → A: The salary in effect on the unpaid day itself, divided by the month's working days.
- Q: If a Teacher placed in a School has no salary on their own record, is "no salary" reported or does the designation's salary apply? → A: "No salary" is reported; the designation's salary is never used as a fallback for a Teacher.
- Q: What does the "Edit" action allow on append-only salary and policy rows? → A: `EDIT` is removed; the key has only `VIEW` and `CREATE`, and a correction is a new row.
- Q: Does a pay policy version have the same earliest-date rule as a salary? → A: Yes; a policy version's effective date cannot be earlier than the first day of the current month.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record the Salary of a Designation (Priority: P1) 🎯 MVP

An Admin or Director opens Salary Structures, sees every designation (spec 005a) with the monthly salary in effect today,
and records a new salary for one of them with an effective date. The earlier salary is not changed: both stay in that
designation's history, and the new one applies from its date. There are no pay components; a salary is one fixed
monthly amount in rupees.

**Why this priority**: Payroll (013b) cannot compute anyone's pay without a salary to start from. This is the smallest
slice that gives it one.

**Independent Test**: Record ₹30,000 for a designation from 01/11/2026, then ₹32,000 from 01/02/2027, and see both in
its history, the first in effect in December 2026 and the second in February 2027, with the first row unchanged.

**Acceptance Scenarios**:

1. **Given** a designation with no salary, **When** an Admin records ₹30,000 effective 01/11/2026, **Then** the list
   shows that salary as in effect from that date, and before that date the designation shows "no salary yet".
2. **Given** a designation with a salary, **When** a new one is recorded with a later effective date, **Then** both
   rows are kept in the history, the earlier unchanged, and the salary in effect on any date is the latest one on or
   before it.
3. **Given** two salaries recorded for the same designation and the same effective date, **When** the salary in effect
   is asked for, **Then** the one recorded last is used, and both stay in the history with who recorded each and when.
4. **Given** the form, **When** the amount is zero, negative, has more than two decimals, or the date is missing or
   earlier than the first day of the current month, **Then** it is refused and the problem is named.
5. **Given** a Zone Manager, Teacher or System user, **When** they open Salary Structures, **Then** they are not
   authorized and the menu item is not shown.
6. **Given** a designation that has been retired (spec 005a), **When** the list is opened, **Then** it is still shown
   with its history so past months can be recomputed, and a new salary can still be recorded for it.

---

### User Story 2 - Set the Pay Policy (Priority: P1)

An Admin or Director sees and maintains the pay policy that payroll reads as one rule: how a day of loss of pay is
valued, how a half day counts, how amounts are rounded, and what the payroll month is. The policy has an effective date
and is versioned like a salary, so a past month is always computed with the policy that applied then.

**Why this priority**: Loss of pay and rounding change what every person is paid. They must be recorded once, in one
place, with a history, before a payroll run exists.

**Independent Test**: Open the policy, see the standard rules, change the rounding from an effective date, and see the
earlier policy still shown for earlier dates.

**Acceptance Scenarios**:

1. **Given** a new installation, **When** the policy is opened, **Then** it shows the standard rules: one day of loss of
   pay is the monthly salary divided by the working days of that month, a half day counts as half a day, amounts are
   rounded to the nearest rupee, and the payroll month is the calendar month.
2. **Given** the policy, **When** a change is recorded with an effective date, **Then** a new version is added, the
   earlier version is unchanged, and each date uses the version in effect on it.
3. **Given** the policy form, **When** a value is outside its allowed range (for example a half-day fraction not
   between 0 and 1), **Then** it is refused.
4. **Given** a Zone Manager, **When** they look for the policy, **Then** they cannot open it.

---

### User Story 3 - Payroll Asks One Place for the Rules (Priority: P1)

The payroll run (013b) asks this spec three things and never works them out itself: what monthly salary a person has on a
date, what one day of loss of pay is worth in a given month, and how many of a month's days a person is payable for.
The answers follow the rules below, so the same inputs always give the same pay.

**Why this priority**: This is the reason the spec exists. If these answers are not fixed here, 013b and any report
will compute pay in different ways.

**Independent Test**: For a fixed set of people and months (a full month, a mid-month joiner, a mid-month transfer, an
exit, a month with a holiday) check the salary on a date, the loss-of-pay value of a day, and the payable days against
hand-computed figures.

**Acceptance Scenarios**:

1. **Given** a Manager with a designation that has a salary, **When** their salary on a date is asked for, **Then** it
   is the salary of their designation in effect on that date.
2. **Given** a Teacher, **When** their salary on a date is asked for, **Then** it is the salary on their own record
   (spec 005 salary history) in effect on that date; the designation's salary is never used for a Teacher's pay.
3. **Given** a month with 26 working days and a salary of ₹26,000, **When** the value of one day of loss of pay is asked
   for, **Then** it is ₹1,000, and ₹500 for a half day.
4. **Given** a Teacher whose first School assignment starts on the 11th, **When** the payable days of that month are
   asked for, **Then** only the working days from the 11th are payable, and the days before it (including training)
   are not.
5. **Given** a Teacher transferred between two Schools in the month, **When** the payable days are asked for, **Then**
   the days at each School are counted on that School's working calendar and added, with no day counted twice and
   none lost between the two.
6. **Given** a Teacher who has exited or is between Schools, **When** the payable days are asked for, **Then** days
   after the exit date or with no current School are not payable.
7. **Given** a Manager who joined mid-month or exits mid-month, **When** the payable days are asked for, **Then** the
   working days on the default calendar from the joining date, or up to the exit date, are payable.
8. **Given** a person with no salary in effect (no designation, no salary yet for it, or none on the Teacher's
   record), **When** the salary is asked for, **Then** the answer says so explicitly; it is never zero.
9. **Given** a month with no working days, **When** the value of a day of loss of pay is asked for, **Then** the
   answer says the month has no working days; it never divides by zero.
10. **Given** a Manager, **When** the unpaid days of a month are asked for before spec 032 is built, **Then** the
    answer says Manager attendance is not recorded yet; it never says there are no unpaid days.

---

### User Story 4 - See What Is in Effect and Why It Changed (Priority: P2)

An Admin or Director opens a designation to see its full history (each salary, its effective date, who recorded it and
when, and any note), and the history of the pay policy. Every change appears in the audit log with the prior and new
value. A designation that has Managers or Teachers without a salary is flagged so the gap is found before payroll.

**Why this priority**: History is what makes past months reproducible and a change explainable. It is valuable but
the structures work without the screen.

**Independent Test**: Record three salaries for a designation and open its history to see all three in date order with
who and when; find the matching audit entries; see the flag on a designation that people use but that has no salary.

**Acceptance Scenarios**:

1. **Given** a designation with several salaries, **When** its history is opened, **Then** every row is shown, newest
   first, with the amount, effective date, who recorded it, when, and the note.
2. **Given** any change to a salary or the policy, **When** the audit log is opened, **Then** it shows who, when, and
   the prior and new values.
3. **Given** a designation that Managers use but that has no salary in effect today, **When** the list is opened,
   **Then** it is flagged with the number of people affected.
4. **Given** a Teacher designation, **When** its row is opened, **Then** a note says the amount is only the default
   offered when a Teacher's salary is set; the Teacher's own recorded salary is what is paid.

---

### Edge Cases

- A salary effective date falls mid-month: the old salary applies to the days before it and the new one from it, each
  counted by working days; payroll receives the salary on each date, not one figure per month.
- Two people record a salary for the same designation at the same moment: both are kept; the later recorded is in effect
  for that date.
- A designation is retired: its salary history stays and is still used for Managers who hold it; it cannot be newly
  chosen for people (spec 005a).
- A designation belongs to Teachers or to Managers, never both (spec 005a), so a salary row is for one kind of person.
- A Teacher has a salary on their record but no designation, or a designation with no salary: the Teacher's own recorded
  salary is used; the missing designation salary only affects the default shown.
- The effective date of a salary is before the person's joining or first assignment: it is accepted (it is a rate, not a
  payment); nothing is paid for days before joining.
- A School's working calendar changes after a month is computed: the answers for a month follow the calendar in force
  when asked, and payroll's own month lock (013b) is what freezes a computed month.
- A salary is wrongly entered: it is not edited or deleted; a new row with the same or a later date supersedes it and
  the wrong one stays in the history.
- A holiday falls on a day a Manager is on loss of pay: the holiday is not a working day, so it is neither paid as a
  loss nor deducted.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let an Admin or Director record a fixed monthly salary in rupees for a designation with an
  effective date, a note (optional), and record who entered it and when.
- **FR-002**: The system MUST keep every salary row for a designation; a row MUST NOT be edited or deleted, and a change
  is a new row.
- **FR-003**: The system MUST determine the salary of a designation on a date as the latest row with an effective date on
  or before it, and where several rows share that date, the one recorded last.
- **FR-004**: The system MUST refuse a salary that is zero or negative, has more than two decimals, has no effective
  date, or has an effective date earlier than the first day of the current month.
- **FR-005**: The system MUST apply a designation's salary to a Manager only; a Teacher's salary MUST come from the
  Teacher's own salary history, and the designation's salary MUST be offered only as the default when a Teacher's salary
  is set. A Teacher with no salary on their own record has no salary in effect, even if their designation has one.
- **FR-006**: The system MUST keep the pay policy as dated versions that are never overwritten, holding: the value of a
  day of loss of pay (the monthly salary divided by the working days of that month), the fraction a half day counts
  (one half), the rounding of amounts (the nearest rupee), and the payroll month (the calendar month).
- **FR-007**: The system MUST refuse a policy value outside its range, MUST refuse a policy version whose effective date
  is missing or earlier than the first day of the current month, and MUST use, for any date, the policy version in
  effect on that date.
- **FR-008**: The system MUST answer, for a person and a date, the monthly salary in effect, or state that there is none;
  it MUST NOT answer zero for a missing salary.
- **FR-009**: The system MUST answer, for a person and a month, the value of one day and of a half day of loss of pay,
  computed from the salary in effect and the working days of the month, and MUST state when the month has no working
  days. The value of a day and of a half day MUST be kept at full precision (not rounded); rounding per the pay policy
  applies only to the total deducted for the month. When the salary changes during the month, each unpaid day is
  valued at the salary in effect on that day, divided by the month's working days.
- **FR-010**: The system MUST answer, for a person and a month, the payable working days: for a Teacher, only days placed
  in a School (from the first School assignment, with training unpaid, ending at transfer or exit) counted on that
  School's working calendar; for a Manager, the days from the joining date to the exit date (if any) on the default
  calendar.
- **FR-011**: The system MUST count each day once when a person changes School within a month, so no day is paid twice or
  lost.
- **FR-012**: The system MUST treat the unpaid days that payroll deducts as: an absence with no approved leave and a day
  of Loss-of-Pay leave; a half day counts as half; a Manager's missed check-in raises an alert but is not unpaid by
  itself. Until Manager attendance exists (spec 032), a Manager's unpaid days are reported as not available, with a
  stated reason; they are never reported as none.
- **FR-013**: The system MUST show Admin and Director, under OPERATIONS → Salary Structures, each designation with the
  salary in effect, a flag where people hold a designation that has no salary, each designation's full history, and the
  pay policy with its history.
- **FR-014**: The system MUST audit every salary and policy change with who, when, and the prior and new values.
- **FR-015**: The system MUST hide Salary Structures from a Zone Manager, Teacher and System, and MUST refuse their
  requests for it on the server, not only in the menu.
- **FR-016**: The system MUST keep all salary and policy data out of other roles' screens and out of the Teacher's
  profile; no screen of this spec shows an individual person's pay.
- **FR-017**: The system MUST make the answers of FR-008 to FR-012 and FR-018 available to the payroll run as one public
  interface, so payroll and reports never read the underlying tables or reimplement the rules.
- **FR-018**: The system MUST answer, for a person and a month, the total loss of pay: each unpaid day valued at the
  salary in effect on that day, summed and rounded once by the pay policy. If any unpaid day has no salary, or its month
  has no working days, the answer MUST say so and give no figure.

### Key Entities *(include if feature involves data)*

- **Salary Structure Row**: a monthly salary in rupees for one designation, with an effective date, a note, who recorded
  it and when. Append-only. Many rows per designation form its history.
- **Pay Policy Version**: the loss-of-pay, half-day, rounding and payroll-month rules with an effective date, who
  recorded it and when. Append-only.
- **Designation** (spec 005a): the named job title a structure belongs to; applies to Teachers or Managers; may be
  retired.
- **Salary in Effect** (derived): the monthly salary of a person on a date, from the designation (Manager) or the
  person's own record (Teacher), or "none".
- **Payable Days** (derived): the working days of a month for which a person is paid, after joining, assignment,
  transfer and exit rules.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Salary Structures | View, Create | Org-wide |
| Director | OPERATIONS → Salary Structures | View, Create | Org-wide |
| Manager (Zone Manager) | none | none | None |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: `SALARY_STRUCTURES` (`VIEW`, `CREATE`; structures and the pay policy; there is no `EDIT` because rows are append-only). Seeded to Admin
and Director only; a Zone Manager is deliberately left out because salary rates are payroll data and these screens are
organization-wide with no per-Zone scope. Manager, Teacher and System are not eligible; only Admin and Director can hold
this module. Only Admin,
Director and System edit the matrix. The constitution's Default role access matrix gets a Salary Structures row when
this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every designation and any date, the salary in effect equals the hand-computed answer from its history,
  including two rows on one date and a change in the middle of a month; checked on a table of at least 10 cases.
- **SC-002**: No salary or policy row is ever changed or removed after it is saved; an attempt through any screen or
  directly in the data is refused, and every addition has an audit entry with who, when and prior and new values.
- **SC-003**: The value of a day of loss of pay and the payable days match hand-computed figures for a full month, a
  mid-month joiner, a mid-month transfer between two Schools with different calendars, an exit, and a month with
  holidays, with no day counted twice or lost.
- **SC-004**: A Zone Manager, Teacher and System user get no menu item and a refusal on every request to this
  module, proved by a test for each endpoint; no screen outside Salary Structures shows a designation's salary rate.
- **SC-005**: An Admin can record a designation's salary in under 1 minute and find the salary in effect on any date from
  its history in under 30 seconds.
- **SC-006**: Before payroll, an Admin can see in one screen every designation that people hold but that has no salary
  in effect.
- **SC-007**: A missing salary, a missing designation or a month with no working days never produces a zero or an error
  page: each produces a stated reason that payroll can show.

## Assumptions

- Spec 005a (designations for Managers and Teachers, and a Manager's employee id and joining date) is a prerequisite: it
  is specified in `docs/spec-inputs/005a-designations.md` but not yet built. This spec cannot be implemented until it
  is, and the order is 005a, then 013a.
- Rounding is applied once, to the total loss-of-pay deduction of a month, never to the value of a single day.
- Spec 005 (Teacher salary history, Zones, Schools), spec 008 (attendance, holiday calendar and weekly offs), spec 009
  (leave, including the Loss-of-Pay type to be added by amendment A3), spec 012 (the first School assignment is a
  Teacher's reporting date), spec 003 (audit) and spec 016 (the accepted offer's package becomes the Teacher's first
  salary entry at the first assignment) are implemented and merged.
- Decisions D2, D12 and D13 of `docs/spec-roadmap.md` fix the rules: a fixed monthly salary per designation with no
  pay components, loss of pay as salary divided by the month's working days, and pay only for days placed in a School.
- Defaults chosen where the input left a point open, settled in the clarifications above: amounts round to the nearest
  rupee, once, on the month's total; a mid-month change applies from its effective date by working days; an effective
  date cannot be earlier than the first day of the current month, so no arrears arise here; a Manager's unpaid days are
  an absence with no approved leave and Loss-of-Pay leave, and a missed check-in alone is not unpaid; the Teacher's own
  salary is always the amount paid.
- A Manager's salary is the salary of the designation they hold on the date. If spec 005a keeps only the current
  designation, a mid-month change of designation is valued at the current one, and this limit is stated in 005a's clarify.
- Statutory deductions (PF, ESI, TDS), bonuses and arrears are not in the Director deck and are out of scope here.
- Out of scope: the payroll run, payslips and payment status (013b), incentives (030), reimbursements (015), any
  individual's pay shown on a screen, and changes to the offer form of spec 016 (this spec only exposes the designation
  default so that a later change can show it).
- Amounts are Indian Rupees and dates are shown as DD/MM/YYYY (Constitution, Additional Constraints).
