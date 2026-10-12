# Data Model: Salary Structures and Pay Policy (spec 013a)

Module `payroll`. Four tables (amended 2026-10-11): `designation_salary`, `pay_policy` and `person_salary` are append-only; `staff_employment` holds a staff member's dates and is audited. No foreign keys cross modules: `designation_id` and the user ids are
plain UUIDs validated through the owning module's public interface.

## designation_salary

A **suggested default** monthly salary for one designation (spec 005a) from an effective date. Never used as anyone's pay; it is only offered when a person's own salary is entered (see `person_salary`).

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| designation_id | UUID NOT NULL | must exist (checked through the 005a interface); a retired designation is allowed |
| amount | NUMERIC(12,2) NOT NULL | `> 0`, at most 9,999,999.99, two decimals |
| effective_on | DATE NOT NULL | not before the first day of the current business month when recorded (FR-004) |
| note | VARCHAR(500) | optional, plain text |
| recorded_by | UUID NOT NULL | the actor from the JWT |
| recorded_at | TIMESTAMPTZ NOT NULL | server time; breaks ties on the same `effective_on` |

- Index `(designation_id, effective_on DESC, recorded_at DESC)`: the latest row on or before a date is one lookup.
- Trigger `BEFORE UPDATE OR DELETE` raises an exception (append-only, SC-002).
- The date check is a service rule, not a table constraint, because it depends on today. The trigger and the index
  guarantee the history; the rule guarantees no arrears.

## pay_policy

One dated version of the pay rules.

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| effective_on | DATE NOT NULL | same earliest-date rule as a salary; the seeded version 1 starts `2000-01-01` |
| lop_divisor | VARCHAR(20) NOT NULL | `WORKING_DAYS` |
| half_day_fraction | NUMERIC(4,3) NOT NULL | `0 < value < 1`, default 0.500 |
| rounding | VARCHAR(20) NOT NULL | `NEAREST_RUPEE`, `UP`, `DOWN` (applied once, to the month total) |
| pay_month | VARCHAR(20) NOT NULL | `CALENDAR_MONTH` |
| note | VARCHAR(500) | optional |
| recorded_by | UUID | null for the seeded version |
| recorded_at | TIMESTAMPTZ NOT NULL | |

- Index `(effective_on DESC, recorded_at DESC)`; same trigger as above.
- Check constraints repeat the ranges, so a direct insert cannot store an invalid policy.
- Seed (in the migration): version 1 with `WORKING_DAYS`, 0.500, `NEAREST_RUPEE`, `CALENDAR_MONTH`, `recorded_by` null.

## Derived (never stored)

| Name | Definition |
| --- | --- |
| Salary in effect | Teacher: own history (spec 005) on the date; Manager and staff: `person_salary` on the date; otherwise `NoSalary(reason)`. A designation's default is never used |
| Value of a day | salary in effect on the day / working days of its month (unrounded); `NoWorkingDays` when 0 |
| Payable days | working dates of the month inside the person's employment and placement spans |
| Unpaid days | payable working dates with unapproved absence or Loss-of-Pay leave (half day = the fraction) |
| Missing-salary flag | count of Managers or Teachers holding a designation who have no salary of their own today; and, on the People list, each person with none |

## State and lifecycle

No state machine. A row is created and never changes. A wrong entry is fixed by a newer row with the same or a later
date. The audit entity types are `DESIGNATION_SALARY` (entity id = designation id, field `amount`, prior = the amount
in effect on that date before, new = the recorded amount), `PERSON_SALARY` (entity id = the person's id, field `amount`, same prior/new rule), `STAFF_EMPLOYMENT` (entity id = user id, fields `joiningDate`, `exitDate`) and `PAY_POLICY` (entity id = policy id, one entry per
changed field against the version in effect before it).

## Reads by other modules

Only through `payroll.api.PayRules`; no other module reads these tables (ArchUnit enforces).

## person_salary (amendment 2026-10-11)

A person's own fixed monthly salary from an effective date. Managers and staff only: a Teacher's history stays in
`teacher_salary_history` (spec 005) and is read through `TeacherDirectory`.

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| person_kind | VARCHAR(10) NOT NULL | `MANAGER` or `STAFF` (check constraint) |
| person_id | UUID NOT NULL | a Manager id for `MANAGER`, a user id for `STAFF`; no foreign key across modules, existence checked through the owning module |
| amount | NUMERIC(12,2) NOT NULL | `> 0`, at most 9,999,999.99 |
| effective_on | DATE NOT NULL | not before the first day of the current business month when recorded |
| note | VARCHAR(500) | optional, plain text |
| recorded_by | UUID NOT NULL | the actor from the JWT |
| recorded_at | TIMESTAMPTZ NOT NULL | breaks ties on the same `effective_on` |

- Index `(person_kind, person_id, effective_on DESC, recorded_at DESC)`; the same `BEFORE UPDATE OR DELETE` trigger.
- Staff are users holding the Admin or Director role (a System account has no salary and is not a payroll person); one person with several roles has one history.

## staff_employment (amendment 2026-10-11)

| Column | Type | Rules |
| --- | --- | --- |
| user_id | UUID PK | the staff member's user account |
| joining_date | DATE | optional; open at the start when null |
| exit_date | DATE | optional, not before `joining_date`; open at the end when null |
| updated_by | UUID NOT NULL | the actor |
| updated_at | TIMESTAMPTZ NOT NULL | |

Not append-only (these are dates, not pay); every change writes a `STAFF_EMPLOYMENT` audit entry with the prior and new value.
