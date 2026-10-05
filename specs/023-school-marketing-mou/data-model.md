# Data Model: School Marketing and MoU Pipeline (spec 023)

`V23__create_marketing_tables.sql` creates the tables below and extends the notification type check. No foreign keys
cross a module boundary (ids are plain UUIDs); foreign keys inside this sub-package and inside `files` are allowed.
Money is `NUMERIC(12,2)`.

## marketing_prospect

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| name | varchar(200) not null | |
| board | varchar(60) | |
| address | varchar(300) | |
| zone_id | UUID not null | Zone in the `school` module |
| name_key | varchar(260) not null | normalized `name|board|zone` for the duplicate rule |
| contact_person, designation | varchar(160) | |
| phone | varchar(20), email varchar(200) | never logged in plain text |
| expected_teachers | int null | 1 to 500 |
| owner_user_id | UUID not null | |
| stage | varchar(14) not null | PROSPECT, CONTACTED, VISIT, FOLLOW_UP, INTERESTED, NEGOTIATION, FINAL_STAGE, ON_HOLD, LOST |
| stage_before_hold | varchar(14) null | the stage to return to |
| lost_reason | varchar(300) | required when LOST |
| won_at, won_by | timestamptz, UUID null | set when the Final Stage review approves |
| place_id | UUID null | chosen when the School is created |
| school_id | UUID null | the School of spec 005, once created or linked |
| version | bigint | optimistic lock |
| created_by, created_at | | |

Unique `(name_key)`; indexes on `(zone_id, stage)` and `(owner_user_id)`. A stage of ON_HOLD or LOST keeps the
previous active stage in `stage_before_hold` so reopening is exact. **MoU and Active are not stored** (derived from
spec 012).

## prospect_stage_history, prospect_owner_history (insert-only)

`(id, prospect_id, from_stage, to_stage, reason, changed_by, changed_at)` and `(id, prospect_id, from_owner,
to_owner, changed_by, changed_at)`. Review decisions (approved, rejected with reason) are stage-history rows with a
`kind` column (STAGE, REVIEW_APPROVED, REVIEW_REJECTED).

## marketing_activity

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| prospect_id | UUID null FK | null for an account visit to a School that has an MoU |
| school_id | UUID null | the School of such a visit |
| type | varchar(16) | VISIT, CALL, PROPOSAL_MEETING, FOLLOW_UP |
| status | varchar(10) | PLANNED, COMPLETED, CANCELLED (missed is derived) |
| activity_date | date not null | |
| notes | varchar(1000) | |
| outcome | varchar(1000) null | required when COMPLETED (people met, discussion, next action) |
| follow_up_on | date null | |
| cancel_reason | varchar(300) | required when CANCELLED |
| created_by, created_at, version | | |

Check: exactly one of `prospect_id`, `school_id` is set. Indexes on `(activity_date)` and `(prospect_id)`.

`activity_attendee (activity_id, user_id)` PK both. `activity_date_history (id, activity_id, old_date, new_date,
changed_by, changed_at)` insert-only (reschedules). `activity_attachment (id, activity_id, file_id, added_by,
added_at)`; at most 10 per activity (service rule).

## proposal_revision, proposal_position (insert-only)

`proposal_revision (id, prospect_id, revision int, teacher_count int 1..500, start_month date, salary_mode
SAME_FOR_ALL|PER_TEACHER, rate numeric(12,2) null, notes varchar(500), created_by, created_at)`, unique
`(prospect_id, revision)`; checks as in spec 012 (`SAME_FOR_ALL` needs `rate > 0`, `PER_TEACHER` needs none).
`proposal_position (id, revision_id, number, title, salary numeric(12,2) > 0)`, unique `(revision_id, number)`. A
trigger rejects update and delete on both tables.

## marketing_setting, overdue_notice

`marketing_setting (key varchar(60) PK, int_value int, version, updated_by, updated_at)` with the row
`mou_overdue_days = 14` (check 1 to 90). `overdue_notice (prospect_id, limit_days, noticed_at)` PK
`(prospect_id, limit_days)`.

## files module

The `files` module has no migration of its own yet, so this migration creates its table; ownership stays with `files`.


`stored_file (id UUID PK, owner_type varchar(30), owner_id UUID, original_name varchar(255), content_type varchar(100),
size_bytes bigint, sha256 char(64), storage_path varchar(300), added_by UUID, added_at timestamptz, removed_at
timestamptz null, removed_by UUID null, removal_reason varchar(300) null)`; index on `(owner_type, owner_id)`. Rows are
never updated except to record a removal.

## notification

`ALTER TABLE notification` drops and re-creates the `type` check to add `MOU_NOT_RECORDED` (and keeps every existing
value).

## Derived values (not stored)

- **Effective stage** of a won prospect: MOU when its School has a live contract; ACTIVE when that contract has a filled
  position.
- **Missed visit**: PLANNED with a date before today. **Follow-up overdue**: an outcome with `follow_up_on` before today
  and no later activity on the prospect.
- **Monthly total** of a proposal: `rate x teacher_count` or the sum of the position amounts.
- **Overdue MoU**: won, no live contract, `won_at` older than `mou_overdue_days`.
- **Win rate**: won divided by (won plus lost). **Demand**: sum of vacant positions on won Schools with a live
  contract. **Supply**: `SupplySource.readyToDeployCount()`.

## Audit entries (via `ChangeRecorder`)

`PROSPECT` (create, owner, stage, hold, loss, reopen, review, win), `MARKETING_ACTIVITY` (plan, complete, reschedule,
cancel), `ACTIVITY_ATTACHMENT` (add, remove with reason), `PROPOSAL`, `PROSPECT_SCHOOL` (the School created or linked),
`MARKETING_SETTING`.
