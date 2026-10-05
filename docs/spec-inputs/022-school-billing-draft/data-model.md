# Data Model: School Contracts and Billing (spec 012)

Flyway `V20__create_schoolbilling_tables.sql` creates the tables (contract, contract_position, contract_signatory, contract_assignment and the billing tables) and copies the placements;
`V21__drop_teacher_placement.sql` drops the old table. No foreign keys cross a module boundary (ids are
plain UUIDs), as in specs 008 to 010. Money is `NUMERIC(12,2)`.

## contract (the MoU)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID | School in `school` module |
| state | VARCHAR(12) | `RATE_PENDING` (shown as "MoU pending"), `ACTIVE`, `CANCELLED` |
| salary_mode | VARCHAR(12) null | `SAME_FOR_ALL` or `PER_TEACHER`; null while `RATE_PENDING` |
| teacher_count | INT null | N, at least 1; null while `RATE_PENDING` |
| rate | NUMERIC(12,2) null | the one monthly salary when `SAME_FOR_ALL` |
| signed_on | DATE null | date the MoU was signed; null while `RATE_PENDING` |
| cycle | VARCHAR(10) | `MONTHLY` only |
| starts_on | DATE | |
| ends_on | DATE null | inclusive |
| version | BIGINT | optimistic lock |
| created_by, created_at | | |

Checks: `SAME_FOR_ALL` requires `rate > 0`; `PER_TEACHER` requires `rate` null (the amounts are on the
positions); `teacher_count >= 1`; `signed_on <= created date`; `RATE_PENDING` has `salary_mode`, `teacher_count`,
`rate` and `signed_on` all null; any other state has them set. `ends_on` null or `>= starts_on`. Exclusion
constraint: no two non-`CANCELLED` contracts of one School overlap in dates.

Lifecycle: `RATE_PENDING` to `ACTIVE` by recording the MoU on it in place (only while no receivable exists). A
change of terms or signatories is a new contract that ends the current one the day before it starts. A contract
that ever fed a receivable is never deleted; a mistaken contract is `CANCELLED` only before that. Signing
details are written once with the contract and never updated.

## contract_position

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| contract_id | UUID FK to `contract` | same module |
| number | INT | 1 to N, unique per contract |
| title | VARCHAR(80) null | optional label such as "Maths PGT" to help recruiters tell positions apart |
| salary | NUMERIC(12,2) | `> 0`; for `SAME_FOR_ALL` every position carries the contract's `rate` |

Exactly `teacher_count` rows per non-pending contract, created with it. Positions are never added, removed or
re-priced afterwards; that is a new contract. Vacant or filled is derived from assignments.

## contract_signatory

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| contract_id | UUID FK to `contract` | |
| party | VARCHAR(6) | `SCHOOL` or `HLS` |
| name | VARCHAR(120) | not blank; for HLS copied from the user's name at signing |
| designation | VARCHAR(120) | not blank, for example "Principal", "Zone Manager", "Director" |
| user_id | UUID null | set for HLS signatories (the Zone Manager's and the Director's user) |

At least one `SCHOOL` row, and for `HLS` the School's Zone Manager and one Director (checked in the service).
Rows are insert-only.

## contract_assignment (replaces `teacher_placement`)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | same ids as the migrated placements |
| teacher_id | UUID | |
| school_id | UUID | the School, for fast lookups |
| position_id | UUID null | the contract position the Teacher fills; null only for carried-over placements not yet mapped |
| starts_on, ends_on | DATE | `ends_on` inclusive, null while open |
| status | VARCHAR(12) | `ACTIVE`, `CANCELLED`, `CORRECTED` (as `PlacementStatus`) |
| created_by, created_at | | |

Same checks as the old table: `ends_on >= starts_on`, and a gist exclusion so one Teacher has no overlapping
`ACTIVE` rows. A second gist exclusion keeps one position from being filled by two Teachers on overlapping dates
(`position_id WITH =`, date range `&&`, `WHERE status = 'ACTIVE' AND position_id IS NOT NULL`). Indexes on
`school_id`, `teacher_id` and `position_id`. A mapped assignment lies within its contract's dates and the School
never has more mapped Teachers at once than positions (the second constraint enforces it, since each position
holds at most one). The service checks that the position belongs to the School's contract on those dates.
Moving to a new contract is an end and a start of the Teacher's rows (same School, new position), so history is
kept and the Teacher has no gap.

## billing_month

| Column | Type | Notes |
| --- | --- | --- |
| month | DATE PK | first day of the month |
| status | VARCHAR(8) | `OPEN`, `CLOSED` |
| closed_by, closed_at | | set on close |

Rows are created when a month is first calculated. The first billable month comes from `billing_settings`.

## receivable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID | |
| month | DATE | |
| kind | VARCHAR(10) | `GENERATED` or `ADJUSTMENT` |
| version | INT | 1, 2, ... per School and month for `GENERATED` |
| current | BOOLEAN | exactly one `GENERATED` row per School and month is current |
| amount | NUMERIC(12,2) | signed; adjustments may be negative |
| breakdown | JSONB | per-Teacher lines: contract id, position number, salary, working days at the School in the contract's dates, working days in the month, amount; and the Teachers at the School not yet mapped |
| reason | TEXT null | required for `ADJUSTMENT` |
| generated_by, generated_at | | |

Partial unique index on `(school_id, month) WHERE kind = 'GENERATED' AND current`. A closed month's rows
are never changed; corrections are new `ADJUSTMENT` rows in an open month.

## school_payment (append-only)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID | |
| month | DATE | the billing month it settles |
| kind | VARCHAR(10) | `PAYMENT` or `REVERSAL` |
| paid_on | DATE | |
| mode | VARCHAR(8) | `BANK`, `CASH`, `CHEQUE`, `UPI` |
| receiver_user_id | UUID | defaults to the recorder |
| receiver_name | VARCHAR(120) | kept as text so history survives a rename |
| amount | NUMERIC(12,2) | always positive; the kind gives the sign |
| comment | VARCHAR(500) null | |
| reverses_id | UUID null | the payment a `REVERSAL` undoes |
| reason | VARCHAR(500) null | required for `REVERSAL` |
| recorded_by, created_at | | |

A trigger rejects `UPDATE` and `DELETE`. Unique index on `reverses_id`, so a payment is reversed at most once.
Index on `(school_id, month)`.

## overdue_flag

`(school_id, month)` primary key, `flagged_at`, `cleared_at` null. A row exists once a School-month has been
flagged; `cleared_at` is set when the balance reaches zero. Prevents a second notification.

## billing_settings (one row)

`overdue_days` INT default 7, `expected_payment_day` INT default 5 (1 to 28), `first_billable_month` DATE,
`version`. Edits are audited.

## Derived values (not stored)

- **Balance** for a School up to a month = Σ current receivables (generated and adjustments) up to that month
  − Σ payments + Σ reversals up to that month. Negative means advance.
- **Expected, collected, outstanding** for a month = Σ receivables of the month; Σ net payments for the
  month; the sum of the two for carried-forward balance. Per Manager and per School are grouped from the same
  rows, using the School's *current* Manager from `ManagerQueries` (clarification 4), so totals always add up.

## Audit entries (via `ChangeRecorder`)

`CONTRACT` (create with its positions and signatories, record the MoU on a pending contract, end, cancel), `CONTRACT_ASSIGNMENT` (same entity name family as
`TEACHER_PLACEMENT`, so history stays readable: the migrated placements keep `TEACHER_PLACEMENT` and new
ones use it too, with the Teacher as entity id), `RECEIVABLE` (generate, recalculate, adjust),
`SCHOOL_PAYMENT` (record, reverse), `BILLING_MONTH` (close), `BILLING_SETTINGS`.
