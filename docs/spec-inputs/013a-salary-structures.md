# 013a Salary Structures and Pay Policy: `/speckit-specify` input

2026-10-05. Roadmap row 013a (first half of the old 013). Depends on 005 and **005a** (designations on Managers and
Teachers) and 012 (the first assignment is a Teacher's reporting date). Feeds **013b** (the payroll run). Decisions
D2, D12 and D13 in `docs/spec-roadmap.md` fix the rules below.

## Feature description (paste as the argument to `/speckit-specify`)

013a-salary-structures: The salary structure and pay policy that the monthly payroll (spec 013b) will use. Module
`payroll`.

1. **Salary structure by designation.** For each designation (spec 005a) an Admin or Director records a **fixed
   monthly salary** with an effective date. Structures are **versioned**: a change adds a new row with a later effective
   date and never overwrites an earlier one, so any past month can be recomputed. There are no pay components (no
   basic, allowances or deductions list) in version 1 (D12).
2. **Whose salary comes from where.** A Manager's salary is the structure of their designation on the date. A
   Teacher's salary is the salary offered on their record (spec 005 salary history); the Teacher's designation structure
   is only the default shown when the offer is made. (Clarify point below.)
3. **Loss of pay (LOP).** For each unpaid day, the deduction is the monthly salary divided by the number of working
   days in that month (D12). Unpaid days are: an absence with no approved leave, and a day of Loss-of-Pay leave. A half
   day counts as half. The policy is stored as a setting so 013b reads one rule.
4. **Pro-rata rules.** A Teacher is paid only for the days they are placed in a School (D2, D13): salary starts on the
   first School assignment, training is unpaid, and a transfer or exit splits or ends the pay by days. A Manager is paid
   from the joining date (spec 005a) and until an exit date. Pro-rata is by working days over the month's working days.
5. **Pay calendar.** The payroll month is the calendar month; the working days of a month come from the holiday
   calendar and weekly offs of spec 008 (a Teacher's from their School's calendar, a Manager's from the default
   calendar). There is no separate academic-year pay window (D13).
6. **Screens.** OPERATIONS → Salary Structures (Admin and Director: View, Create, Edit): the list of designations with
   the structure in effect, the history of each, and the pay policy settings. A Zone Manager, Teacher and System have no
   access. Every change is audited with the prior and new value.
7. **Public interface** for payroll: the monthly salary of a designation on a date, and the LOP and pro-rata rules.

Out of scope: the payroll run, payslips, payment status (013b), incentives (030), reimbursements (015), tax and
statutory deductions (not in the Director deck), bonuses and arrears.

## Points for `/speckit-clarify`

- For Teachers, is the salary always the offered amount on their record, or the structure of their designation with an
  optional override? Who sets the offered amount, and in which spec (016's offer)?
- The first designations and amounts for Teachers and for Managers.
- Rounding: round the net salary to the nearest rupee, or to the nearest 50, and where does the difference go? (The
  requirements doc tracked it as its own line.)
- Does a mid-month change of structure apply from its effective date by days, or from the next month?
- Are statutory deductions (PF, ESI, TDS) needed at all, now or later?
- Can an effective date be in the past (a back-dated raise), and does it create an arrears line in 013b?
- Which unpaid days apply to a Manager (unapproved absence, LOP leave, a missed check-in)?
