# Feature Specification: Salary Structures and Pay Policy

**Feature Branch**: `013a-salary-structures`

**Created**: 2026-10-06

**Status**: Amended 2026-10-11 (every person has their own salary; see Clarifications)

**Input**: User description: "013a-salary-structures: The salary structure and pay policy that the monthly payroll (spec 013b) will use. A fixed monthly salary for each person (Teacher, Manager, Admin, Director and other staff) with an effective date, versioned and never overwritten, because people on the same designation are paid differently; a designation carries only a suggested default amount (spec 005a); loss of pay for each unpaid day is the monthly salary divided by the month's working days; pro-rata rules for joining, transfer and exit (a Teacher is paid only for days placed in a School, training is unpaid); the pay month is the calendar month. Admin and Director keep it under OPERATIONS → Salary Structures. Module `payroll`. Full source: docs/spec-inputs/013a-salary-structures.md."

## Clarifications

### Session 2026-10-11 (amendment: salary belongs to the person, not the designation)

HLS has no fixed pay scale. People on the same designation are paid different amounts, and the amount is agreed when a
person is recruited. This replaces the earlier rule that a Manager is paid their designation's salary (decision D12 of
`docs/spec-roadmap.md` is amended: a fixed monthly salary **per person**, versioned by effective date).

- Q: Whose salary does payroll use? → A: Every person's own. A Teacher's is the salary on their record (spec 005, which spec 016's accepted offer already writes). A Manager's and any other staff member's is recorded in this spec as a dated, append-only history of their own. A designation's amount is never used as anyone's pay.
- Q: What is a designation's amount for, then? → A: Only a suggested default shown when a person's salary is entered, so a recruiter starts from a typical figure. It can be left unset.
- Q: Who counts as "staff" for pay, given Admin and Director have no Manager or Teacher record? → A: Anyone with a login who holds the Admin or Director role; they are identified by their user account. A System account is a technical account with no salary and is not a payroll person (corrected 2026-10-11). Their joining and exit dates are kept in this spec (a Manager's come from spec 005a, a Teacher's from placements).
- Q: May a screen show an individual person's pay? → A: Yes, but only the People list and a person's salary history under OPERATIONS → Salary Structures, to Admin and Director (the `SALARY_STRUCTURES` holders). No other screen, role or profile shows it. This replaces the earlier "no screen shows an individual's pay" rule (old FR-016).
- Q: Where is a Teacher's salary entered? → A: Where it is today, on the Teachers screen (spec 005, `TEACHER_SALARY`). The People list shows a Teacher's salary in effect read-only and links there, so there is one place to change it.
- Q: What does "no salary" mean for a Manager or staff member? → A: Nothing recorded on or before the date: it is reported as such and never as zero (as for a Teacher).

### Session 2026-10-08

- Q: Is rounding to the nearest rupee applied to each day's loss-of-pay value or only to the total deducted for the month? → A: Only the total deduction for the month is rounded; the value of a day and of a half day are kept at full precision.
- Q: When a salary changes mid-month, which salary values a day of loss of pay? → A: The salary in effect on the unpaid day itself, divided by the month's working days.
- Q: If a Teacher placed in a School has no salary on their own record, is "no salary" reported or does the designation's salary apply? → A: "No salary" is reported; the designation's salary is never used as a fallback for a Teacher.
- Q: What does the "Edit" action allow on append-only salary and policy rows? → A: `EDIT` is removed; the key has only `VIEW` and `CREATE`, and a correction is a new row.
- Q: Does a pay policy version have the same earliest-date rule as a salary? → A: Yes; a policy version's effective date cannot be earlier than the first day of the current month.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record a Suggested Salary for a Designation (Priority: P2)

An Admin or Director opens Salary Structures → Designation defaults, sees every designation (spec 005a) with its suggested
monthly salary in effect today, and records a new suggested salary for one of them with an effective date. It is only
the starting figure offered when a person's own salary is entered; nobody is paid it. The earlier salary is not changed: both stay in that
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

1. **Given** a Manager or other staff member with a recorded salary, **When** their salary on a date is asked for,
   **Then** it is their own salary in effect on that date, whatever their designation or the designation's default.
2. **Given** a Teacher, **When** their salary on a date is asked for, **Then** it is the salary on their own record
   (spec 005 salary history) in effect on that date; a designation's amount is never used for anyone's pay.
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
7. **Given** a Manager or other staff member who joined mid-month or exits mid-month, **When** the payable days are asked
   for, **Then** the working days on the default calendar from the joining date, or up to the exit date, are payable.
8. **Given** a person with no salary in effect (nothing recorded for them on or before the date), **When** the salary is
   asked for, **Then** the answer says so explicitly; it is never zero.
9. **Given** a month with no working days, **When** the value of a day of loss of pay is asked for, **Then** the
   answer says the month has no working days; it never divides by zero.
10. **Given** a Manager, **When** the unpaid days of a month are asked for, **Then** the days come from spec 032 attendance (a
    Manager with no joining date gets a stated reason); it never says there are no unpaid days for a month that cannot be known.

---

### User Story 4 - See What Is in Effect and Why It Changed (Priority: P2)

An Admin or Director opens a person to see their full salary history (each salary, its effective date, who recorded it and
when, and any note), a designation to see its suggested-default history, and the history of the pay policy. Every change
appears in the audit log with the prior and new value. People without a salary are flagged, and so are the designations
that have such people, so the gap is found before payroll.

**Why this priority**: History is what makes past months reproducible and a change explainable. It is valuable but
the structures work without the screen.

**Independent Test**: Record three salaries for a designation and open its history to see all three in date order with
who and when; find the matching audit entries; see the flag on a designation that people use but that has no salary.

**Acceptance Scenarios**:

1. **Given** a designation with several salaries, **When** its history is opened, **Then** every row is shown, newest
   first, with the amount, effective date, who recorded it, when, and the note.
2. **Given** any change to a salary or the policy, **When** the audit log is opened, **Then** it shows who, when, and
   the prior and new values.
3. **Given** a designation held by Managers or Teachers who have no salary of their own in effect today, **When** the
   list is opened, **Then** it is flagged with the number of people affected.
4. **Given** any designation, **When** its row is opened, **Then** a note says the amount is only a suggested default
   for entering a person's salary; each person's own salary is what is paid.

---

### User Story 5 - Record Each Person's Own Salary (Priority: P1) 🎯 MVP

When a Manager, Admin, Director or other staff member is recruited, an Admin or Director records that person's own
monthly salary with an effective date, starting from the designation's suggested default if there is one. Two people on
the same designation can have different salaries. A later raise or correction is a new row with its date; earlier rows
stay. A Teacher's salary is entered where it is today (the Teachers screen) and shown here read-only. The People list
shows everyone with their salary in effect and flags the people who have none.

**Why this priority**: Pay differs person to person, so the person's own salary is the only thing payroll can use.
Without it nobody but a Teacher can be paid.

**Independent Test**: Record ₹50,000 for one Manager and ₹65,000 for another on the same designation, and see each
person's own salary in effect, the first Manager's unchanged by the second; record a staff member's salary and joining
date and see them paid from that date.

**Acceptance Scenarios**:

1. **Given** two Managers on one designation, **When** an Admin records ₹50,000 for one and ₹65,000 for the other,
   **Then** each person's salary in effect is their own, and neither affects the other or the designation.
2. **Given** a person with a salary, **When** a new one is recorded with a later effective date, **Then** both rows are
   kept, the earlier unchanged, and the salary on any date is the latest on or before it (the one recorded last on a tie).
3. **Given** a designation with a suggested default, **When** the salary form for a person on it opens, **Then** the
   default is shown as a suggestion and is not saved unless the Admin enters it as that person's salary.
4. **Given** an Admin, a Director or other staff member, **When** their salary and joining date are recorded, **Then**
   payroll can ask for their salary and payable days like anyone else's; their exit date ends their pay.
5. **Given** the People list, **When** it is filtered to people without a salary, **Then** it shows every Manager,
   Teacher and staff member with no salary in effect today.
6. **Given** a Teacher, **When** they appear in the People list, **Then** their salary in effect is shown read-only with a
   link to the Teachers screen, where it is changed; this spec never writes a Teacher's salary.
7. **Given** a Zone Manager, Teacher or System user, **When** they try the People list or a person's salary, **Then**
   it is refused on the server and not shown in any menu.
8. **Given** the form, **When** the amount is zero, negative, has more than two decimals or exceeds ₹9,999,999.99, or the
   date is missing or earlier than the first day of the current month, **Then** it is refused and the problem is named.

---

### Edge Cases

- A salary effective date falls mid-month: the old salary applies to the days before it and the new one from it, each
  counted by working days; payroll receives the salary on each date, not one figure per month.
- Two people record a salary for the same designation at the same moment: both are kept; the later recorded is in effect
  for that date.
- A designation is retired: its suggested-default history stays and it can still be given a new default; it cannot be
  newly chosen for people (spec 005a).
- A designation belongs to Teachers or to Managers, never both (spec 005a), so a suggested default is for one kind of person.
- A person has no designation, or a designation with no default: their own recorded salary is used regardless; the
  missing default only means no suggestion is shown.
- Two people on one designation have different salaries: each is paid their own; changing the designation's default
  changes nobody's pay.
- A person holds more than one role (for example Admin and Director): they are one person with one salary history,
  keyed by their user account.
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

- **FR-001**: The system MUST let an Admin or Director record a fixed monthly salary in rupees for a **person** (a
  Manager or a staff member identified by their user account) with an effective date, a note (optional), and record who
  entered it and when. It MUST also let them record a suggested default amount for a designation in the same way.
- **FR-002**: The system MUST keep every salary row, for a person or a designation; a row MUST NOT be edited or deleted,
  and a change is a new row.
- **FR-003**: The system MUST determine the salary of a person (or the default of a designation) on a date as the latest
  row with an effective date on or before it, and where several rows share that date, the one recorded last.
- **FR-004**: The system MUST refuse a salary that is zero or negative, has more than two decimals, has no effective
  date, or has an effective date earlier than the first day of the current month.
- **FR-005**: The system MUST take every person's salary from their own history: a Teacher's from the Teacher's own
  salary history (spec 005), a Manager's or staff member's from the history recorded in this spec. A designation's amount
  MUST NOT be used as anyone's pay and MUST be offered only as the suggested default when a salary is entered. A person
  with no salary of their own has no salary in effect, even if their designation has a default.
- **FR-006**: The system MUST keep the pay policy as dated versions that are never overwritten, holding: the value of a
  day of loss of pay (the monthly salary divided by the working days of that month), the fraction a half day counts
  (one half), the rounding of amounts (the nearest rupee), and the payroll month (the calendar month).
- **FR-007**: The system MUST refuse a policy value outside its range, MUST refuse a policy version whose effective date
  is missing or earlier than the first day of the current month, and MUST use, for any date, the policy version in
  effect on that date.
- **FR-008**: The system MUST answer, for a person (Teacher, Manager or staff member) and a date, the monthly salary in
  effect, or state that there is none; it MUST NOT answer zero for a missing salary.
- **FR-009**: The system MUST answer, for a person and a month, the value of one day and of a half day of loss of pay,
  computed from the salary in effect and the working days of the month, and MUST state when the month has no working
  days. The value of a day and of a half day MUST be kept at full precision (not rounded); rounding per the pay policy
  applies only to the total deducted for the month. When the salary changes during the month, each unpaid day is
  valued at the salary in effect on that day, divided by the month's working days.
- **FR-010**: The system MUST answer, for a person and a month, the payable working days: for a Teacher, only days placed
  in a School (from the first School assignment, with training unpaid, ending at transfer or exit) counted on that
  School's working calendar; for a Manager or staff member, the days from the joining date to the exit date (if any) on the default
  calendar.
- **FR-011**: The system MUST count each day once when a person changes School within a month, so no day is paid twice or
  lost.
- **FR-012**: The system MUST treat the unpaid days that payroll deducts as: an absence with no approved leave and a day
  of Loss-of-Pay leave; a half day counts as half; a Manager's missed check-in raises an alert but is not unpaid by
  itself. A Manager's unpaid days come from spec 032 Manager attendance; a Manager whose month cannot be worked out (no joining date) is reported as not available, with a
  stated reason, never as none. An Admin or Director has no attendance, so their unpaid days are none by rule.
- **FR-013**: The system MUST show Admin and Director, under OPERATIONS → Salary Structures, every Manager, Teacher and
  staff member with their salary in effect and a flag for those with none, each person's full salary history, each
  designation with its suggested default and a flag where people hold it without a salary of their own, and the pay
  policy with its history.
- **FR-014**: The system MUST audit every salary and policy change with who, when, and the prior and new values.
- **FR-015**: The system MUST hide Salary Structures from a Zone Manager, Teacher and System, and MUST refuse their
  requests for it on the server, not only in the menu.
- **FR-016**: The system MUST keep all salary and policy data out of other roles' screens and out of the Teacher's
  profile. An individual person's pay is shown only in the People list and a person's salary history, only to holders of
  `SALARY_STRUCTURES` (Admin and Director by default); no other screen, role or response carries it.
- **FR-017**: The system MUST make the answers of FR-008 to FR-012 and FR-018 available to the payroll run as one public
  interface, so payroll and reports never read the underlying tables or reimplement the rules.
- **FR-019**: The system MUST identify staff other than Managers and Teachers by their user account (a user holding the
  Admin or Director role; a System account has no salary and is not one), record their joining and exit dates, and treat them as payable only between those
  dates; a user with several roles is one person with one salary history.
- **FR-020**: The system MUST show, when a person's salary is entered, the suggested default of their designation if it
  has one, MUST NOT save it as the person's salary unless the user enters it, and MUST NOT change anyone's pay when a
  designation's default changes.
- **FR-021**: The system MUST NOT write a Teacher's salary; it MUST show a Teacher's salary in effect read-only and
  point to the Teachers screen where it is changed (spec 005).
- **FR-018**: The system MUST answer, for a person and a month, the total loss of pay: each unpaid day valued at the
  salary in effect on that day, summed and rounded once by the pay policy. If any unpaid day has no salary, or its month
  has no working days, the answer MUST say so and give no figure.

### Key Entities *(include if feature involves data)*

- **Person Salary Row**: a monthly salary in rupees for one Manager or staff member, with an effective date, a note, who
  recorded it and when. Append-only. Many rows per person form their history. (A Teacher's rows are spec 005's.)
- **Staff Employment**: a staff member's joining and exit dates, kept per user account; changes are audited.
- **Designation Default Row**: a suggested monthly salary for one designation, with an effective date, a note, who
  recorded it and when. Append-only. Never used as pay.
- **Pay Policy Version**: the loss-of-pay, half-day, rounding and payroll-month rules with an effective date, who
  recorded it and when. Append-only.
- **Designation** (spec 005a): the named job title a structure belongs to; applies to Teachers or Managers; may be
  retired.
- **Salary in Effect** (derived): the monthly salary of a person on a date, from their own history (Teacher: spec 005;
  Manager and staff: this spec), or "none".
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
organization-wide with no per-Zone scope. The People list and a person's salary history are under the same module, so
only Admin and Director can see an individual's pay. Manager, Teacher and System are not eligible; only Admin and Director can hold
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
  module, proved by a test for each endpoint; no screen outside Salary Structures shows any salary, and the People list is reachable only by Admin and Director.
- **SC-005**: An Admin can record a person's salary in under 1 minute and find the salary in effect on any date from
  their history in under 30 seconds.
- **SC-006**: Before payroll, an Admin can see in one screen every Manager, Teacher and staff member who has no salary in
  effect, and every designation that such people hold.
- **SC-008**: Two people on one designation with different recorded salaries always get their own salary from every
  question payroll asks; changing a designation's default or one person's salary changes no one else's answer.
- **SC-007**: A missing salary, a missing designation or a month with no working days never produces a zero or an error
  page: each produces a stated reason that payroll can show.

## Assumptions

- Spec 005a (designations for Managers and Teachers, and a Manager's employee id and joining date) is a prerequisite and
  is built (PR #33). It keeps a **Manager's** designation history, so the designation on a date is exact; it keeps only a
  **Teacher's** current designation, which this spec never uses for pay.
- Rounding is applied once, to the total loss-of-pay deduction of a month, never to the value of a single day.
- Spec 005 (Teacher salary history, Zones, Schools), spec 008 (attendance, holiday calendar and weekly offs), spec 009
  (leave, including the Loss-of-Pay type to be added by amendment A3), spec 012 (the first School assignment is a
  Teacher's reporting date), spec 003 (audit) and spec 016 (the accepted offer's package becomes the Teacher's first
  salary entry at the first assignment) are implemented and merged.
- Decisions D2, D12 (amended 2026-10-11) and D13 of `docs/spec-roadmap.md` fix the rules: a fixed monthly salary **per
  person** with no pay components, loss of pay as salary divided by the month's working days, and pay only for days
  placed in a School.
- Defaults chosen where the input left a point open, settled in the clarifications above: amounts round to the nearest
  rupee, once, on the month's total; a mid-month change applies from its effective date by working days; an effective
  date cannot be earlier than the first day of the current month, so no arrears arise here; a Manager's unpaid days are
  an absence with no approved leave and Loss-of-Pay leave, and a missed check-in alone is not unpaid; the Teacher's own
  salary is always the amount paid.
- A Manager's salary is their own, whatever designation they hold, so a change of designation never changes their pay.
  A Manager's joining and exit dates come from spec 005a; a staff member's are kept here.
- An existing installation has no Manager or staff salaries yet; they are entered through the People list, and payroll
  reports each person without one as having no salary until they are.
- Statutory deductions (PF, ESI, TDS), bonuses and arrears are not in the Director deck and are out of scope here.
- Out of scope: the payroll run, payslips and payment status (013b), incentives (030), reimbursements (015), a
  person's pay shown to anyone but Admin and Director, a recruitment offer for Managers and staff (their salary is
  entered here), and changes to the offer form of spec 016.
- Amounts are Indian Rupees and dates are shown as DD/MM/YYYY (Constitution, Additional Constraints).

> **Amended by spec 040 (2026-10-28):** Every Teacher leave day (any type, full or half) is an unpaid day at the payroll run; the Loss-of-Pay type no longer decides it (`UnpaidDayClassifier`). Manager attendance keeps its own rule.
