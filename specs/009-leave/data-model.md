# Data Model: Leave Management

Migration `V17__create_leave_tables.sql`. Plain ids, no cross-module foreign keys (house rule).

## leave_type (seeded, read-only in this spec)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| code | varchar(20) unique | CASUAL, SICK, PERSONAL, OTHER, LOP (A3) |
| name | varchar(60) | Casual, Sick, Personal, Other, Loss of Pay (A3) |
| sort_order | int | display order |
| active | boolean | default true |

## leave_request

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| teacher_id | uuid not null | plain id (teacher module) |
| school_id | uuid not null | School of placement on the first working day, kept for display and the School filter (plain id) |
| leave_type_id | uuid not null | FK to `leave_type` (same module) |
| first_date | date not null | |
| last_date | date not null | `last_date >= first_date`, at most 90 days span (check constraint) |
| half_day_start | boolean not null default false | applies to the first working day |
| half_day_end | boolean not null default false | applies to the last working day |
| working_days | numeric(5,2) not null | counted at submission (halves count 0.5) |
| reason | varchar(500) not null | |
| status | varchar(12) not null | PENDING, APPROVED, REJECTED, CANCELLED |
| decided_by_user_id | uuid null | approver / rejecter / revoker |
| decided_at | timestamptz null | |
| decision_note | varchar(500) null | approval note, rejection reason or revoke reason |
| cancelled_by_kind | varchar(12) null | TEACHER or SUPERVISOR when status = CANCELLED |
| created_by_user_id | uuid not null | the Teacher's user |
| created_at | timestamptz not null | |
| version | bigint not null | `@Version` |

Constraints and indexes:

- `EXCLUDE USING gist (teacher_id WITH =, daterange(first_date, last_date, '[]') WITH &&)
  WHERE (status IN ('PENDING','APPROVED'))` - one live request per Teacher per date.
- Reject requires a note: `CHECK (status <> 'REJECTED' OR decision_note IS NOT NULL)`.
- Index `(status, first_date)` for the Pending list; `(teacher_id, first_date desc)` for history.

### State machine

```
PENDING  --approve--> APPROVED      (approver in scope; applies L marks atomically)
PENDING  --reject---> REJECTED      (reason required)
PENDING  --cancel---> CANCELLED     (Teacher; or supervisor)
APPROVED --cancel---> CANCELLED     (Teacher while first_date > today; removes marks)
APPROVED --revoke---> CANCELLED     (supervisor in scope, reason required; removes marks)
```

REJECTED and CANCELLED are terminal.

## Changes to attendance tables (same migration)

| Table | Change |
| --- | --- |
| attendance_mark | `leave_request_id uuid null`; index on it |
| attendance_mark_history | `leave_request_id uuid null` |

`MarkService.setMark` sets `leave_request_id = null` on every non-leave write; the leave path sets it.

## Derived values

- **Working days of a request**: plan from attendance (placement + weekly off + non-working dates);
  count 1 per working day, 0.5 for a half-day first or last working day; recomputed (not trusted) at
  approval.
- **Pending count** for the Leave Management header: number of PENDING requests in the caller's scope.

## Seed data (V17, idempotent)

`leave_type` rows Casual, Sick, Personal, Other (V17) and Loss of Pay (V25, amendment A3). Permission matrix rows are seeded by
`PermissionMatrixService.seedDefaults` (idempotent), not by SQL.
