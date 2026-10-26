# Data Model: School Billing and Receivables (migration V120)

All tables belong to module `schoolbilling`. No foreign keys to other modules (school, teacher, user ids are plain uuids). Money is `numeric(14,2)`. Ledger tables refuse UPDATE and DELETE through a trigger, except the write-once cancellation on `monthly_due`.

## monthly_due

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| reference | varchar(30) unique | internal, `MD-YYYYMM-<seq>` per month |
| school_id | uuid | |
| month | date | first day of the month |
| raised_on | date | business date |
| due_on | date | raised_on + payment due days (setting at raise time) |
| total | numeric(14,2) | sum of line amounts, fixed at raise |
| raised_by | uuid | |
| cancelled_at / cancelled_by / cancel_reason | timestamptz / uuid / varchar(300) | write once |
| created_at | timestamptz | |

Indexes: partial unique (school_id, month) where cancelled_at is null; (due_on) for the job and ageing; (school_id).
Derived, not stored: status, balance, overdue.

## due_line

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| due_id | uuid | |
| teacher_id | uuid | |
| position_id / position_number | uuid / int | |
| contract_id | uuid | |
| salary | numeric(14,2) | position salary on the line's start |
| span_from / span_to | date | clipped to month and contract |
| worked_days / month_days | numeric(5,1) | working days in the span / in the month (037) |
| full_month | boolean | true when no proration applied |
| amount | numeric(14,2) | salary, or salary x worked_days / month_days HALF_UP |

## due_adjustment

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| due_id | uuid | |
| amount | numeric(14,2) | > 0 |
| reason | varchar(300) | required |
| reverses_id | uuid | set on a reversing entry (then amount repeats the original) |
| created_by, created_at | | |

A reversed adjustment counts as zero. Check: sum of active adjustments + allocations cannot exceed the due total (service enforced and re-checked under the School lock).

## collection

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| school_id | uuid | |
| amount | numeric(14,2) | > 0 |
| received_on | date | not in the future |
| mode | varchar(10) | BANK, CASH, CHEQUE, UPI |
| reference | varchar(80) | optional for CASH |
| receiver | varchar(120) | |
| comment | varchar(500) | |
| reverses_id | uuid | reversing entry |
| reason | varchar(300) | required on a reversal |
| created_by, created_at | | |

Index: (school_id, received_on); (school_id, amount, received_on, mode, reference) for the duplicate warning.

## collection_allocation

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| collection_id | uuid | |
| due_id | uuid | |
| amount | numeric(14,2) | positive to allocate, negative to undo (insert-only) |
| created_by, created_at | | |

Derived: due balance = total - active adjustments - sum(allocation amounts); advance credit of a School = collections not reversed - allocations of them.

## overdue_follow_up

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid pk | |
| due_id | uuid unique | one row per due |
| task_id | uuid | null when the School had no Zone Manager |
| created_on | date | |
| closed_on | date | set once when the due is paid or cancelled |

This is the only mutable ledger-side row (closed_on written once).

## billing_setting

`key` (pk), `value` int, `updated_by`, `updated_at`. Keys `billing.payment_due_days` (default 15, 1 to 120) and `billing.escalation_days` (default 7, 2 to 120); a missing row means the default. Every change goes to Change History.

## due_balance (view)

Per due: total, active adjustments, net allocations and balance, so every screen and the public interface read the same figures.

## Permission and navigation

Module `SCHOOL_BILLING`: View, Create, Edit, Approve, Export. Mapping as built: View = see the ledger; Create = record and allocate collections (Zone Manager own Schools); Edit = raise dues, adjustments, reversals, settings; Approve = cancel a due; Export = CSV. Raise, adjust, cancel are additionally limited to Admin and Director by eligibility rules. Menu: OPERATIONS → School Billing.

## State (derived)

OPEN (no allocations or adjustments) → PART_PAID (balance > 0 and something applied) → PAID (balance = 0); CANCELLED is the only stored state. Reversals move it back. Overdue = not cancelled, balance > 0, due_on < today.
