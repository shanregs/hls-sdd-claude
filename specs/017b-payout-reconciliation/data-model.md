# Data Model: Substitute Teachers, Day-Rate Pay and Reconciliation

Migrations V160 and V161 (numbers to re-check at implementation). No foreign keys across modules (ids only). Timestamps `timestamptz`; money `numeric(12,2)`.

## teacher (module `teacher`, existing): new column

| Column | Type | Notes |
| --- | --- | --- |
| engagement | varchar(10) NOT NULL DEFAULT 'REGULAR' | check in ('REGULAR','SUBSTITUTE'); setting SUBSTITUTE clears `user_id`; audit on change |

## designation_day_rate (module `payroll`, 013a amendment), append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| seq | bigint generated always as identity | tie-break for the same effective date |
| designation_id | uuid NOT NULL | plain id (005a) |
| amount | numeric(10,2) NOT NULL CHECK > 0 | rupees per full day |
| effective_on | date NOT NULL | |
| recorded_by | uuid NOT NULL | |
| recorded_at | timestamptz NOT NULL | |

Update and delete forbidden by trigger. Rate on a date = row with `effective_on <= date` with greatest `(effective_on, seq)`. Index `(designation_id, effective_on, seq)`.

## substitute_day (module `substitution`)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| requirement_id | uuid NOT NULL | 017a requirement |
| substitute_teacher_id | uuid NOT NULL | |
| school_id | uuid NOT NULL | |
| zone_id | uuid NOT NULL | the School's Zone at last refresh (scope filter) |
| absent_teacher_id | uuid NOT NULL | internal only |
| day_date | date NOT NULL | |
| day_value | numeric(3,2) NOT NULL | 0.50 or 1.00 |
| live | boolean NOT NULL | false once 017a ended the allocation |
| cancel_reason | varchar(100) null | |
| attendance | varchar(12) NOT NULL DEFAULT 'NOT_MARKED' | NOT_MARKED, PRESENT, ABSENT |
| marked_by | uuid null | user id |
| marked_at | timestamptz null | |
| source | varchar(12) NOT NULL | ALLOCATION or MIGRATED |
| needs_review | boolean NOT NULL DEFAULT false | migrated days |
| status | varchar(40) NOT NULL | derived (below), stored for filtering |
| mismatch_reason | varchar(200) null | |
| rate | numeric(10,2) null | rate used at last pricing |
| amount | numeric(12,2) null | day_value times rate |
| run_id | uuid null | run that holds or applied it |
| run_month | date null | first day of that month |
| paid_amount | numeric(12,2) null | frozen amount in the approved run |
| correction_ref | varchar(100) null | `SUBSTITUTE-CORRECTION:<id>` |
| version | bigint NOT NULL | optimistic lock |
| created_at / updated_at | timestamptz | |

Unique `(requirement_id, substitute_teacher_id)`. Indexes `(status)`, `(substitute_teacher_id, day_date)`, `(zone_id, day_date)`, `(run_month)`. Check: `attendance <> 'NOT_MARKED'` requires `marked_by` and `marked_at`.

## substitute_month_lock

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| substitute_teacher_id | uuid NOT NULL | |
| month | date NOT NULL | first of month |
| locked | boolean NOT NULL | current state |
| locked_by / locked_at | uuid / timestamptz | |
| reopened_by / reopened_at / reopen_reason | uuid / timestamptz / varchar(500) | reason required on reopen |

Unique `(substitute_teacher_id, month)`. Lock allowed only after the month ended and with no live day NOT_MARKED.

## substitute_day_event (append-only; trigger forbids update and delete)

id, day_id, at, field (ATTENDANCE, LIVE, STATUS, RUN, LOCK, REOPEN, MIGRATED, CORRECTION), from_value, to_value, actor_user_id null (null = system), cause (MARK, EVENT_ASSIGNED, EVENT_ENDED, LOCK, JOB, RUN_APPROVED, MIGRATION, CORRECTION), note varchar(200).

## Status derivation

| live | attendance | month locked | Pricing | Run holds it | Status |
| --- | --- | --- | --- | --- | --- |
| no | any | | | none or draft | CANCELLED (CANCELLED_BEFORE_PAYMENT if it was in a draft run) |
| no | any | | | awaiting approval | CHANGED_RUN_AWAITING |
| no | any | | | approved | CORRECTION_NEEDED |
| yes | NOT_MARKED | | | | ALLOCATED |
| yes | ABSENT | | | | DID_NOT_ATTEND |
| yes | PRESENT | no | | | ATTENDED |
| yes | PRESENT | yes | no rate / not Active Substitute | | CANNOT_PAY |
| yes | PRESENT | yes | ok | none, month run open or none | LOCKED_WAITING_RUN |
| yes | PRESENT | yes | ok | none, month run approved | BACKPAY_WAITING |
| yes | PRESENT | yes | ok | draft | IN_DRAFT_RUN |
| yes | PRESENT | yes | ok | awaiting approval | IN_RUN_AWAITING_APPROVAL |
| yes | PRESENT | yes | ok | approved | PAID |
| yes | ABSENT or changed after PAID | | | approved | CORRECTION_NEEDED |
| | | | | correction offered | CORRECTION_RAISED, then CORRECTED when applied |

Mismatch view = CANNOT_PAY, CHANGED_RUN_AWAITING, CORRECTION_NEEDED, CORRECTION_RAISED, BACKPAY_WAITING, and PAID rows whose `paid_amount` differs from current pricing.

## Run line (module `payroll`, 013b amendment)

A line for a Substitute stores earned = sum of rated days, deduction 0, and its frozen working lists each day (day id, School, date, day value, rate, amount). No new table if the existing working structure fits (research R11).
