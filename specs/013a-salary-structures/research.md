# Research: Salary Structures and Pay Policy (spec 013a)

Every open point from the spec is settled here. No `NEEDS CLARIFICATION` remains.

## 1. Prerequisites that do not exist yet

- **Decision**: three things this spec reads are not in the code today, and the plan treats each as a named
  prerequisite instead of inventing a stand-in.
  1. **Spec 005a (designations)**: no `Designation` type, no designation on a Manager or Teacher, no Manager
     joining or exit date. `payroll` reads them through the interfaces 005a specifies (`organization.api.ManagerQueries`
     and `teacher.api.TeacherDirectory`) and stores a plain `designation_id` with no foreign key across modules.
     **Open point for 005a**: 013a needs the designation **on a date**; 005a's own input asks whether a designation
     can change over time and leaves it open. This plan assumes 005a exposes `designationOn(person, date)` (current
     only if history is not kept; then a mid-month promotion is valued at the current designation, a limit to state
     in 005a's clarify). It does not block 013a's own tables and screens.
  2. **Amendment A3 to spec 009 (Loss-of-Pay leave type)**: not started (roadmap row 009). The unpaid-day
     classifier recognizes it by a code the leave module exposes; until A3 lands it simply finds none.
  3. **A working-calendar interface in `attendance`**: `CalendarService` and the weekly-off rules are `internal`;
     `attendance.api` has rollups, marks and leave helpers but nothing that answers "which dates of this range are
     working days for this School". New here as `WorkingCalendar` (amendment A5 to spec 008, additive).
- **Rationale**: stand-ins (copying the weekly-off rules, reading designation tables) would break Principle VII and
  produce two truths about working days.
- **Alternatives**: wait for all three before planning (stalls the order the roadmap fixes); implement 013a with the
  unpaid-day rules inside `attendance` (couples attendance to a pay rule).

## 2. Where "who is a Manager, a Teacher, and since when" comes from

- **Decision**: `PayRules` takes a `PersonRef(kind, id)` with kind `TEACHER` or `MANAGER`. For a Teacher it reads
  `teacher.api.TeacherDirectory` (placements, exit) and `teacher.api` salary lookup; for a Manager it reads the
  005a Manager interface (designation, joining date, exit date).
- **Rationale**: the spec's rules differ by kind (own recorded salary versus designation salary; School calendar
  versus default calendar), so the kind is part of the question.
- **Alternatives**: separate methods per kind (duplicates the result types); one id with a lookup of kind (an
  extra query and ambiguity if ids ever collide).

## 3. A Teacher's recorded salary

- **Decision**: add `TeacherDirectory.salaryOn(teacherId, date)` returning an optional amount, implemented over the
  existing `SalaryService.asOf` rows. It is a read for server code and is **not** an HTTP endpoint, so spec 005's
  rule that salary is only reachable through `TEACHER_SALARY` endpoints still holds.
- **Rationale**: `payroll` may not read `teacher_salary_history`. Today the only reader is `SalaryService`, which
  checks the caller's permission and scope, which a payroll batch run cannot supply; the new method is the
  unchecked server-side form, documented as such.
- **Alternatives**: call `SalaryService.asOf` with a synthetic Admin (impersonation, rejected); copy the history
  into `payroll` (two truths).

## 4. The rules, stated as formulas

Let `W(m, cal)` be the working days of month `m` on calendar `cal` (weekly offs and non-working dates removed).

- **Salary in effect** `S(person, d)`: Manager: the latest `designation_salary` row of the person's designation on
  `d` with `effective_on <= d`, ties by latest `recorded_at`; Teacher: the same rule over the Teacher's own history.
  No row, no designation, or a Teacher without a record salary gives `NoSalary(reason)`; never `0`.
- **Value of a day** `V(person, d) = S(person, d) / W(month(d), cal(person, d))`, unrounded (clarified). A half day
  is `V * halfDayFraction` (policy, default 0.5). `W = 0` gives `NoWorkingDays` and no division.
- **Month total deducted** `D = round(sum over unpaid days u of V(person, u) * dayValue(u))`, where `dayValue` is 1 or the
  half-day fraction, rounded once with the policy rule (default nearest rupee, `HALF_UP`) and **only here**.
  Valuing each unpaid day at the salary of that day (clarified) means a raise on the 16th changes the value of days
  after it and not before.
- **Payable days** `P(person, m)`: the set of working dates `d` in `m` where the person is payable, as a count and as
  the list of dates:
  - Teacher: `d` lies inside an ACTIVE placement span (`TeacherDirectory.placementsOverlapping`), and `d` is a working
    day on **that placement's School calendar**; a date belongs to at most one span, so a transfer day is counted once
    (the span that covers it; spans in the data never overlap); dates before the first assignment (training), after
    the exit date, or in a gap between Schools are not payable.
  - Manager: `d` from the joining date to the exit date (inclusive, either bound open), on the default calendar.
- **Pro-rata share** is `P` over `W(m, cal)`, reported as two numbers (`payable`, `workingInMonth`) so 013b decides
  how to use them. For a Teacher placed in two Schools the denominator is stated per span.
- **Unpaid days** `U(person, m)`: a working, payable date with an absence that has no approved leave, or a Loss-of-Pay
  leave day; a half day counts as the half-day fraction; a holiday or weekly off is never unpaid (it is not a working
  day). A Manager's missed check-in is not unpaid. Source: spec 008 amendment A2's per-day
  loss-of-pay flag where it exists; the status code **category** of the attendance mark (not the code letter) is the
  fallback, so a code added later with the right category is picked up.
- **Rationale**: the open points in the roadmap (D2, D12, D13) fix the formulas; the table above fixes the edge cases
  the spec lists (mid-month change, transfer, holiday).
- **Alternatives**: a per-month salary (rejected by the clarified mid-month rule); rounding each day (rejected in
  clarify); a calendar-day divisor (rejected by D12).

## 5. Manager unpaid days

- **Decision**: `U` for a Manager returns `Unavailable("Manager attendance is not recorded yet")` until spec 032
  (Manager attendance and leave) provides marks. 013b must show the reason; it must not treat it as zero unpaid days.
- **Rationale**: spec 013a defines the rule (FR-012), but no Manager attendance exists to apply it to. Answering
  "no unpaid days" would silently pay in full; the spec's own principle (SC-007) forbids that.
- **Alternatives**: define a stub marker table (premature, 032 owns the shape); return zero (silent wrong pay).

## 6. Append-only, and how it is enforced

- **Decision**: no `UPDATE` or `DELETE` path in code, plus a `BEFORE UPDATE OR DELETE` trigger on both tables that raises
  an exception, the same device `V20` uses. Corrections are new rows; "the latest recorded on the same date wins".
- **Rationale**: SC-002 requires refusal "directly in the data". The trigger is the only way to cover that.
- **Alternatives**: revoke privileges (the app role owns the tables, so it cannot be revoked without a second role);
  trust the code alone (fails SC-002).

## 7. Date rules and the business clock

- **Decision**: "the first day of the current month" uses the business date (Asia/Kolkata) from the shared
  `BusinessCalendar`, which today sits in `attendance.internal`. Move the one method needed behind
  `WorkingCalendar.businessToday()` rather than duplicating the zone logic.
- **Rationale**: between 00:00 and 05:30 IST the UTC date is still yesterday; spec 008 solved this once.
- **Alternatives**: `LocalDate.now(clock)` (wrong around month ends in the early hours).

## 8. Pay policy shape

- **Decision**: one row per version with typed columns: `lop_divisor` (`WORKING_DAYS` only today), `half_day_fraction`
  `NUMERIC(4,3)` with `0 < f < 1`, `rounding` (`NEAREST_RUPEE`, `UP`, `DOWN`), `pay_month` (`CALENDAR_MONTH` only today),
  `effective_on`, note, who and when. Enumerations with one member are kept so a later change is a new value, not a
  schema change.
- **Rationale**: the spec asks for these four rules versioned together; payroll reads one object. Typed columns are
  validated by the database as well as the code.
- **Alternatives**: a key-value settings table (loses range checks); storing the policy in `settings` (that module is
  owned by the System role, who must not see payroll rules).

## 9. Missing-salary flag

- **Decision**: `MissingSalaryReport` counts, per designation with no salary in effect today, the Managers holding it
  (`ManagerQueries`) and the Teachers holding it (`TeacherDirectory`), in two bulk queries. For a Teacher designation
  the flag counts Teachers with **no recorded salary of their own**, because the designation's amount is only a
  default (FR-005), and the screen says so.
- **Rationale**: the useful gap for payroll is "a person who would get no salary", which differs by kind.
- **Alternatives**: flag on designation salary alone (would flag Teacher designations that cause no payroll gap).

## 10. Amounts and presentation

- **Decision**: amounts are `NUMERIC(12,2)`, serialized as strings with two decimals, shown with ₹ and Indian grouping.
  Maximum `9,999,999.99`. The note is at most 500 characters, plain text.
- **Rationale**: house convention (`V20`); string money avoids float errors in the JSON.
