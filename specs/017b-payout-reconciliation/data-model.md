# Data Model: Substitute Payout Reconciliation

Migration `V160__create_substitute_payouts.sql` (number to re-check at implementation). No foreign keys across modules (ids only). Timestamps `timestamptz`; money `numeric(12,2)`.

## substitute_payout

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| requirement_id | uuid NOT NULL | 017a requirement |
| substitute_teacher_id | uuid NOT NULL | |
| school_id | uuid NOT NULL | |
| zone_id | uuid NOT NULL | the School's Zone at last refresh (scope filter) |
| absent_teacher_id | uuid NOT NULL | internal only, never returned to the substitute |
| credit_date | date NOT NULL | |
| day_value | numeric(3,2) NOT NULL | 0.50 or 1.00 |
| credit_live | boolean NOT NULL | false once 017a ended the credit |
| amount | numeric(12,2) null | priced amount (null when not priced) |
| mismatch_reason | varchar(200) null | pricing or payroll reason |
| status | varchar(40) NOT NULL | see below |
| source_ref | varchar(100) NOT NULL | `SUBSTITUTE:<requirementId>:<substituteId>` |
| run_id | uuid null | run that applied it |
| run_month | date null | first day of that month |
| paid_amount | numeric(12,2) null | amount frozen by the applied run |
| correction_ref | varchar(100) null | `SUBSTITUTE-CORRECTION:<id>` |
| version | bigint NOT NULL | optimistic lock |
| created_at / updated_at | timestamptz | |

Unique `(requirement_id, substitute_teacher_id)`. Indexes `(status)`, `(substitute_teacher_id, credit_date)`, `(zone_id, credit_date)`, `(run_month)`.

Status values: BEFORE_START, WAITING_LOCK, CANNOT_PRICE, WAITING_RUN, IN_DRAFT_RUN, IN_RUN_AWAITING_APPROVAL, PAID, VOIDED_BEFORE_PAYMENT, VOIDED_RUN_AWAITING, VOIDED_AFTER_PAYMENT, CORRECTION_RAISED, CORRECTED.

## State derivation (intake status = `SubstituteCreditIntake.status`)

| Credit live | Month | Priced | Intake | Status |
| --- | --- | --- | --- | --- |
| yes | before start month | - | NONE | BEFORE_START |
| yes | not ended or locked | - | NONE | WAITING_LOCK |
| yes | locked | no | NONE | CANNOT_PRICE |
| yes | locked | yes | WAITING / WITHDRAWN-then-offered | WAITING_RUN |
| yes | locked | yes | HELD_BY_DRAFT | IN_DRAFT_RUN |
| yes | locked | yes | HELD_AWAITING_APPROVAL | IN_RUN_AWAITING_APPROVAL |
| yes | locked | yes | APPLIED | PAID |
| no | any | - | NONE / WITHDRAWN | VOIDED_BEFORE_PAYMENT |
| no | any | - | HELD_AWAITING_APPROVAL | VOIDED_RUN_AWAITING |
| no | any | - | APPLIED | VOIDED_AFTER_PAYMENT |
| (any) | | | correction offered | CORRECTION_RAISED then CORRECTED when applied |

Mismatch view = CANNOT_PRICE, VOIDED_RUN_AWAITING, VOIDED_AFTER_PAYMENT, CORRECTION_RAISED, and PAID or in-run rows whose `paid_amount` or `amount` differs from the current pricing, and WAITING_RUN rows past a run that included the substitute without taking them.

## substitute_payout_event (append-only, trigger forbids update and delete)

id, payout_id, at, from_status, to_status, amount, actor_user_id null (null = system), cause (EVENT_ASSIGNED, EVENT_ENDED, JOB, RUN_APPLIED, CORRECTION_RAISED, REPRICED), note varchar(200).

## Setting

`substitution.payout-start-month` (date, first of month). Records dated before it are BEFORE_START.
