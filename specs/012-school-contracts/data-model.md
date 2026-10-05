# Data Model: School Contracts (MoU) (spec 012)

Flyway `V20__create_school_contract_tables.sql` creates the four tables and copies the placements;
`V21__drop_teacher_placement.sql` drops the old table. No foreign keys cross a module boundary (ids are plain
UUIDs), as in specs 008 to 010; foreign keys inside `schoolbilling` are allowed. Money is `NUMERIC(12,2)`.
Receivable, payment and settings tables belong to spec 022.

## contract (the MoU)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID | School in the `school` module |
| state | VARCHAR(12) | `RATE_PENDING` (shown as "MoU pending"), `ACTIVE`, `CANCELLED` |
| salary_mode | VARCHAR(12) null | `SAME_FOR_ALL` or `PER_TEACHER`; null while `RATE_PENDING` |
| teacher_count | INT null | N, 1 to 500; null while `RATE_PENDING` |
| rate | NUMERIC(12,2) null | the one monthly salary when `SAME_FOR_ALL` |
| signed_on | DATE null | date the MoU was signed; null while `RATE_PENDING` |
| cycle | VARCHAR(10) | `MONTHLY` only |
| starts_on | DATE | |
| ends_on | DATE null | inclusive |
| version | BIGINT | optimistic lock |
| created_by, created_at | | |

Checks: `SAME_FOR_ALL` requires `rate > 0`; `PER_TEACHER` requires `rate` null (the amounts are on the positions);
`teacher_count between 1 and 500`; `RATE_PENDING` has `salary_mode`, `teacher_count`, `rate` and `signed_on` all null; any other
state has `salary_mode`, `teacher_count` and `signed_on` set; `signed_on` is checked against today in the service.
`ends_on` null or `>= starts_on`. Exclusion constraint: no two non-`CANCELLED` contracts of one School overlap in
dates.

Lifecycle: `RATE_PENDING` becomes `ACTIVE` when the MoU is recorded on it in place (once; the positions and
signatories are inserted then). A change of salary, count or signatory is a new contract that ends the current one
the day before it starts. A contract with any mapped Teacher is never deleted; a mistaken contract with none is
`CANCELLED`. Signing details are written once and never updated. The responsible Manager is not a column: it is
the School's Zone Manager, read from `organization` when needed.

## contract_position

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| contract_id | UUID FK to `contract` | |
| number | INT | 1 to N, unique per contract |
| title | VARCHAR(80) null | optional label such as "Maths PGT" |
| salary | NUMERIC(12,2) | `> 0`; for `SAME_FOR_ALL` every position carries the contract's `rate` |

Exactly `teacher_count` rows per non-pending contract, created with it. Never added, removed or re-priced
afterwards. Vacant or filled is derived from assignments.

## contract_signatory

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| contract_id | UUID FK to `contract` | |
| party | VARCHAR(6) | `SCHOOL` or `HLS` |
| name | VARCHAR(120) | not blank; for HLS copied from the user's name at signing |
| designation | VARCHAR(120) | not blank, for example "Principal", "Zone Manager", "Director" |
| user_id | UUID null | set for HLS signatories |

At least one `SCHOOL` row and at least one `HLS` row. Each `HLS` row is either the School's Zone Manager (designation
"Zone Manager") or an active Director (designation "Director"); both may sign, and more than one Director is allowed
(checked in the service). Insert-only.

## contract_assignment (replaces `teacher_placement`)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | same ids as the migrated placements |
| teacher_id | UUID | |
| school_id | UUID | the School, for fast lookups |
| position_id | UUID null FK to `contract_position` | null only for carried-over assignments not yet mapped |
| starts_on, ends_on | DATE | `ends_on` inclusive, null while open |
| status | VARCHAR(12) | `ACTIVE`, `CANCELLED`, `CORRECTED` (as `PlacementStatus`) |
| created_by, created_at | | |

Same checks as the old table: `ends_on >= starts_on`, and a gist exclusion so one Teacher has no overlapping
`ACTIVE` rows. A second gist exclusion keeps one position from being filled by two Teachers on overlapping dates
(`position_id WITH =`, the date range `WITH &&`, `WHERE status = 'ACTIVE' AND position_id IS NOT NULL`), which also
guarantees a contract never has more Teachers mapped at once than positions. Indexes on `school_id`, `teacher_id`
and `position_id`. The service checks that the position belongs to the School's contract covering the dates.
Re-mapping to a new contract ends the Teacher's rows the day before and inserts new ones, so history is kept and the
Teacher has no gap.

## Derived values (not stored)

- **Position state** on a date: filled (an `ACTIVE` assignment covers it) or vacant.
- **Contract status** for the list: `ACTIVE`, `ENDS_SOON` (active with an end date within 30 days), `MOU_PENDING`,
  `NONE` (a School with no contract), `ENDED` (end date passed).
- **Filled and vacant counts** per contract from the assignments in effect today.

## Migration (V20)

For each School with any `teacher_placement` row: insert one `RATE_PENDING` contract (`starts_on` = the School's
earliest placement `starts_on`, `ends_on` null, `cycle` = `MONTHLY`, state `RATE_PENDING`); copy every placement
(all statuses, same id, dates and status) into `contract_assignment` with `position_id` null.

## Audit entries (via `ChangeRecorder`)

`CONTRACT` (create with its positions and signatories, record the MoU on a pending contract, end, cancel);
`TEACHER_PLACEMENT` (map, move, cancel pending, exit), unchanged name and Teacher as entity id so existing history
stays readable; `CONTRACT_REMAP` (one entry per Teacher re-mapped to a new contract).
