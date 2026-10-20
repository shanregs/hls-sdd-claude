# Data Model: Expenses

Migration `V54__create_expenses.sql`. All ids are UUID. Money is `NUMERIC(14,2)`. Times are `TIMESTAMPTZ`; the business date
(expense date) is a `DATE` in Asia/Kolkata. Every table has `created_at`/`created_by` and, where edited, `updated_at` and
`version` (JPA `@Version`).

## expense_claim

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| claimant_id | uuid NOT NULL | user id; the claimant is the raiser |
| client_request_id | uuid NOT NULL | retry key; unique with claimant (FR-009) |
| expense_date | date NOT NULL | not in the future, at most 60 days old at submission |
| amount | numeric(14,2) NOT NULL | `> 0`; two decimals |
| category | text NOT NULL | `RENT_UTILITIES`, `TRAVEL_FUEL`, `TRAINING_STAY`, `MARKETING`, `OFFICE_SUPPLIES`, `OTHER` |
| zone_id | uuid NULL | null = Head office; only an active Zone for new claims |
| note | varchar(1000) | |
| paid_by | text NOT NULL | `CLAIMANT` or `COMPANY` |
| reason | varchar(500) NULL | required for `OTHER` |
| no_bill_reason | varchar(500) NULL | the only exception to a bill |
| link_kind | text NULL | marketing activity kinds, drive, task, or `INDUCTION_BATCH` |
| link_ref | uuid NULL | id of the activity, task or batch at submission |
| link_date | date NULL | |
| link_label | varchar(200) NULL | place or name at submission (FR-011) |
| status | text NOT NULL | `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `PAID`, `PAID_BY_COMPANY` |
| paid_how | text NULL | `PAYROLL` or `OUTSIDE` when `PAID` |
| submitted_at | timestamptz NULL | latest submission, drives age |
| decided_by | uuid NULL | latest approve or reject |
| decided_at | timestamptz NULL | |
| decision_reason | varchar(500) NULL | rejection reason |
| paid_on | date NULL | outside payment date, or the payroll month's first day |
| paid_mode | text NULL | `CASH`, `BANK_TRANSFER`, `UPI`, `OTHER` |
| paid_reference | varchar(100) NULL | |
| paid_by_user | uuid NULL | who recorded it paid |
| payroll_run_id | uuid NULL | set when settled with payroll |
| payroll_month | date NULL | first day of the month |
| version | bigint | |

Constraints and indexes:

- `UNIQUE (claimant_id, client_request_id)`.
- Checks: `amount > 0`; `status` and `category` in their lists; `paid_how IS NOT NULL` only when `status = 'PAID'`;
  `(link_kind IS NULL) = (link_ref IS NULL)`.
- Indexes: `(claimant_id, expense_date DESC)`, `(status, submitted_at)`, `(zone_id, expense_date)`, `(category, expense_date)`.
- **Trigger** `expense_claim_paid_immutable`: once `status IN ('PAID','PAID_BY_COMPANY')`, refuses a change to claimant,
  date, amount, category, zone, paid_by, link columns and any status change. This is the database half of SC-009.

### Status transitions

```text
DRAFT --submit--> SUBMITTED --approve--> APPROVED --(paid_by COMPANY)--> PAID_BY_COMPANY
                      |  ^                  |  ^
                withdraw| |resubmit    reverse|  | (reversal returns to SUBMITTED)
                      v  |                  v  |
                    DRAFT  REJECTED <--reject-- SUBMITTED
APPROVED --record paid outside--> PAID (OUTSIDE)
APPROVED --payroll run approved--> PAID (PAYROLL)
```

- Approval of a `COMPANY` claim goes straight to `PAID_BY_COMPANY` in the same transaction.
- `REJECTED --correct, resubmit--> SUBMITTED` keeps the same claim; the earlier decision stays in `expense_claim_event`.
- Reversal only from `APPROVED` and only while the payroll offer is `WAITING`/`HELD_BY_DRAFT` or absent.

## expense_bill

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| claim_id | uuid NOT NULL FK | |
| file_id | uuid NOT NULL | the `files` module's id (no foreign key across modules) |
| name | varchar(255) | original name at upload |
| content_type | text | server-decided |
| size_bytes | bigint | |
| added_by / added_at | uuid / timestamptz | |
| removed_by / removed_at | uuid NULL / timestamptz NULL | |
| removal_reason | varchar(500) NULL | required when removed |

At most 5 rows per claim with `removed_at IS NULL` (service rule; a partial unique approach cannot count to 5).

## expense_claim_event (append-only)

`id`, `claim_id`, `at`, `actor_id`, `event` (`CREATED`, `EDITED`, `SUBMITTED`, `WITHDRAWN`, `RESUBMITTED`, `APPROVED`,
`REJECTED`, `REVERSED`, `BILL_ADDED`, `BILL_REMOVED`, `DUPLICATE_CONFIRMED`, `OFFERED_TO_PAYROLL`, `PAID_WITH_PAYROLL`,
`PAID_OUTSIDE`, `PAID_BY_COMPANY`), `reason` (nullable), `detail` (small JSON: before and after, or matching claim ids).
Rows are never updated or deleted (trigger refuses).

## expense_area_budget and expense_budget_change

`expense_area_budget`: `id`, `zone_id` (null = Head office), `month` (first day), `amount numeric(14,2) >= 0`, `version`,
`updated_at/by`. Unique on (`coalesce(zone_id, '00000000-...'::uuid)`, `month`).
`expense_budget_change` (append-only): `id`, `budget_id`, `at`, `actor_id`, `old_amount` (nullable), `new_amount`.

## Not stored

- Budget actual, pending, remaining, ageing and cost measures are computed on read from `expense_claim`.
- Payroll state of a claim comes from the payroll adjustment (`ReimbursementIntake.status`); the claim stores only the
  settled run and month once the event arrives.
- No dashboard preferences or settings: the 60-day window, 5 bills and 10 MB are fixed in version 1.

## Reads of other modules (through `api` only)

| Need | Interface |
| --- | --- |
| Zones (active, names) | `school.api.SchoolDirectory` |
| Planned activities for links | `recruitment.api.DriveActivities`, `MarketingActivities`, `tasks.api.TaskActivities` |
| Induction batch name and dates; completers of a batch | `training.api.InductionBatches` (+ `completedTeachers`) |
| Schools won in a period | `recruitment.api.WonSchools` (new) |
| Bills | `files.api.FileStore` |
| Payroll offer, withdraw, status; applied event | `payroll.api.ReimbursementIntake`, `PayrollAdjustmentApplied` (new) |
| Audit | `school.api.ChangeRecorder` |
