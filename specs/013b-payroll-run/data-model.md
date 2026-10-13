# Data Model: Payroll Run

Migration `V53__create_payroll_run.sql` (highest on main is `V52`). All ids are UUIDs, all timestamps are `timestamptz`,
all money is `numeric(14,2)` unless stated, and dates are business-time-zone dates (Asia/Kolkata). Tables belong to the
`payroll` module; nothing outside it reads them.

## payroll_run

One row per run.

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| month | date | first day of the month; must be before the first day of the current business month |
| kind | text | `PRIMARY` or `SUPPLEMENTARY` |
| sequence | int | 1 for the primary run; 1, 2, ... for supplementary runs of the month |
| status | text | `DRAFT`, `AWAITING_APPROVAL`, `APPROVED`, `CANCELLED` |
| compute_state | text | `QUEUED`, `RUNNING`, `DONE`, `FAILED` (not a lifecycle state) |
| progress_done, progress_total | int | for the progress bar |
| heartbeat_at | timestamptz | set while computing; stale after 10 minutes means "compute again" |
| policy | jsonb | the pay policy version used: id, effective on, rounding, half-day fraction |
| total_earned, total_deduction, total_adjustments, total_net | numeric(16,2) | sums over payable lines |
| payable_count, cannot_pay_count | int | |
| recompute_summary | jsonb | last diff: added, removed, changed (old and new net), moved |
| started_by, started_at | uuid, timestamptz | |
| submitted_by, submitted_at | uuid, timestamptz | the last submit |
| approved_by, approved_at | uuid, timestamptz | |
| cancelled_by, cancelled_at, cancel_reason | | draft only |
| version | bigint | optimistic lock |

Constraints and indexes:
- Partial unique index on `(month)` where `kind = 'PRIMARY' and status <> 'CANCELLED'` (one primary run per month,
  also under two simultaneous starts).
- Partial unique index on `(month)` where `kind = 'SUPPLEMENTARY' and status in ('DRAFT','AWAITING_APPROVAL')` (one open
  supplementary run).
- Unique `(month, kind, sequence)`.
- Check: `approved_by is distinct from submitted_by` (a second guard behind the service rule).
- Check: status and the who/when columns agree (approved implies approved_by and approved_at).

State rules: `DRAFT -> AWAITING_APPROVAL -> APPROVED`; `AWAITING_APPROVAL -> DRAFT` (return); `DRAFT -> CANCELLED`. Nothing
leaves `APPROVED` or `CANCELLED`.

## payroll_run_line

One row per person in a run (payable or not).

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| run_id | uuid FK | |
| person_kind | text | `TEACHER`, `MANAGER`, `STAFF` |
| person_id | uuid | teacher id, manager id or user id |
| person_name | text | snapshot at compute time |
| person_label | text | designation name, or role for staff, snapshot |
| state | text | `PAYABLE` or `CANNOT_PAY` |
| reason | text | required when `CANNOT_PAY`, null otherwise |
| payable_days | int | count of payable working dates |
| working_days | numeric(6,2) | the divisor shown (the most-used School's, for display; the working holds each date's) |
| unpaid_days | numeric(6,2) | sum of day values |
| salary | numeric(14,2) | the monthly salary at the last payable date, for display |
| earned | numeric(14,2) | rounded once |
| deduction | numeric(14,2) | rounded once |
| adjustments | numeric(14,2) | sum of the line's adjustment lines |
| net | numeric(14,2) | earned - deduction + adjustments |
| working | jsonb | the frozen working (below) |
| fingerprint | text | hash of the inputs, used by recompute |
| warning | text | for example "calendar changed after the lock" |
| computed_at | timestamptz | |

Constraints and indexes: unique `(run_id, person_kind, person_id)`; check `net = earned - deduction + adjustments`; check
`state = 'CANNOT_PAY'` implies the amounts are null (never zero for missing); index on `(run_id, state)`; index on
`(person_kind, person_id)` for the person history.

### The `working` JSON (what the review screen and the payslip show)

```text
{
  "policy": { "id", "effectiveOn", "rounding", "halfDayFraction" },
  "salaryRows": [ { "from", "to", "monthly", "source" } ],
  "payableDates": [ "yyyy-MM-dd", ... ],
  "days": [ { "date", "salary", "divisor", "amount" } ],            // earned pay, one per payable date
  "unpaid": [ { "date", "dayValue", "salary", "divisor", "amount", "reason" } ],
  "earned":    { "unrounded", "rounded" },
  "deduction": { "unrounded", "rounded" },
  "manager":   { "workingDaysAtLock", "unpaidAtLock" } | null
}
```

Amounts are decimal strings at ten decimals before rounding. An approved line's screen, payslip and CSV row are built
from this JSON and the columns above and nothing else.

## payroll_run_line_adjustment

The frozen copy of an adjustment inside a line.

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| line_id | uuid FK | |
| adjustment_id | uuid FK | the waiting adjustment it came from |
| kind, amount, reason | | copied at compute time |

## payroll_adjustment

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| person_kind, person_id, person_name | | |
| kind | text | `CORRECTION` now; `INCENTIVE`, `REIMBURSEMENT`, `SUBSTITUTE_CREDIT` are valid values no code path creates |
| amount | numeric(14,2) | signed, non-zero, at most two decimals |
| reason | text | required, up to 500 characters |
| status | text | `WAITING`, `APPLIED`, `WITHDRAWN` |
| attached_run_id | uuid | the open draft that currently holds a copy, if any |
| applied_run_id, applied_line_id | uuid | set at approval |
| created_by, created_at | | |
| withdrawn_by, withdrawn_at, withdraw_reason | | |
| source_ref | text | free text for a later spec's own reference (null now) |

Constraints: amount non-zero; `status = 'APPLIED'` implies applied run and line set. An `APPLIED` row cannot be updated
or deleted (trigger, same pattern as 013a's append-only tables).

## payroll_run_event

Run history (start, recompute, submit, return, approve, cancel): `id`, `run_id`, `at`, `actor`, `kind`, `reason`,
`detail jsonb`. Append-only.

## payroll_payslip

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| line_id | uuid FK unique | one payslip per paid line |
| run_id | uuid FK | |
| file_id | uuid | the stored PDF in `FileStore` |
| issued_at | timestamptz | the approval time |

## payroll_payment_entry

Append-only; the current state of a line is its newest entry.

| Column | Type | Notes |
| ------ | ---- | ----- |
| id | uuid PK | |
| line_id | uuid FK | an approved run's payable line only |
| paid | boolean | |
| paid_on | date | required when paid, not in the future |
| mode | text | `BANK_TRANSFER`, `CASH`, `UPI`, `CHEQUE`, `OTHER` |
| reference | text | plain text up to 100 characters |
| entered_by, entered_at | | |

## payroll_paid_month

Written at approval, one row per person paid. This table is what makes "paid once per month" a fact.

| Column | Type | Notes |
| ------ | ---- | ----- |
| month | date | |
| person_kind, person_id | | |
| run_id, line_id | uuid | |
| | | primary key `(month, person_kind, person_id)` |

## Triggers

- `payroll_run_line` and `payroll_run_line_adjustment`: `BEFORE UPDATE OR DELETE` raise an error when the line's run is
  `APPROVED`.
- `payroll_run`: `BEFORE UPDATE` raises when the old status is `APPROVED` (nothing on an approved run changes, not even its status); `BEFORE DELETE` raises for any run that is not a `CANCELLED` or `DRAFT`
  run (a run is never deleted by the application; the trigger covers a mistake).
- `payroll_adjustment`, `payroll_payment_entry`, `payroll_run_event`, `payroll_payslip`: `BEFORE DELETE` always raises;
  `BEFORE UPDATE` is allowed only on a `WAITING` adjustment's status and attachment columns.

## What reads what

- Compute reads: the pay rules and `PersonDirectory` (payroll-internal), `AttendanceReadApi.isLocked`,
  `ManagerAttendanceReadApi.month`.
- An approved run, its payslips, its CSV and its history read only the tables above.
- Audit entries use entity types `PAYROLL_RUN`, `PAYROLL_ADJUSTMENT`, `PAYROLL_PAYMENT`, `PAYROLL_EXPORT`, each mapped to
  `PAYROLL.VIEW` in `AuditVisibility`.
