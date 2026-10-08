# Data Model: Salary Structures and Pay Policy (spec 013a)

Module `payroll`. Two tables, both append-only. No foreign keys cross modules: `designation_id` and the user ids are
plain UUIDs validated through the owning module's public interface.

## designation_salary

A fixed monthly salary for one designation (spec 005a) from an effective date.

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
| Salary in effect | Manager: designation row on the date; Teacher: own history on the date; otherwise `NoSalary(reason)` |
| Value of a day | salary in effect on the day / working days of its month (unrounded); `NoWorkingDays` when 0 |
| Payable days | working dates of the month inside the person's employment and placement spans |
| Unpaid days | payable working dates with unapproved absence or Loss-of-Pay leave (half day = the fraction) |
| Missing-salary flag | count of Managers or Teachers holding a designation that leaves them with no salary today |

## State and lifecycle

No state machine. A row is created and never changes. A wrong entry is fixed by a newer row with the same or a later
date. The audit entity types are `DESIGNATION_SALARY` (entity id = designation id, field `amount`, prior = the amount
in effect on that date before, new = the recorded amount) and `PAY_POLICY` (entity id = policy id, one entry per
changed field against the version in effect before it).

## Reads by other modules

Only through `payroll.api.PayRules`; no other module reads these tables (ArchUnit enforces).
