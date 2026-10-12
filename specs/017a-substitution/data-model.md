# Data Model: Substitution Workflow (spec 017a)

Migration `V48__create_substitution.sql`. No cross-module foreign keys: teacher, school and user ids are plain UUIDs.

## attendance_mark (additions, 008 A2)

| Column | Type | Rules |
| --- | --- | --- |
| loss_of_pay | BOOLEAN NOT NULL DEFAULT FALSE | set when the mark is written; backfilled in the migration from the status category and the leave type |
| principal_confirmed | BOOLEAN | null = not recorded |
| principal_name | VARCHAR(120) | optional |
| principal_note | VARCHAR(500) | optional |
| principal_recorded_by | UUID | |
| principal_recorded_at | TIMESTAMPTZ | |

## substitution_requirement

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id | UUID NOT NULL | the absent Teacher |
| school_id | UUID NOT NULL | the School of the placement that day |
| duty_date | DATE NOT NULL | UNIQUE with teacher_id |
| day_value | NUMERIC(3,2) NOT NULL | 0.50 or 1.00 |
| kind | VARCHAR(8) | ABSENT or LEAVE |
| status | VARCHAR(16) | OPEN, ASSIGNED, MANAGER_COVER, CANCELLED |
| substitute_teacher_id | UUID | required when ASSIGNED, null otherwise |
| cover_user_id | UUID | when MANAGER_COVER (may be null if the School has no Manager user) |
| note | VARCHAR(500) | last outcome note |
| informed_at, informed_by, informed_contact, informed_method, informed_note | | School informed record; method PHONE, IN_PERSON, EMAIL, MESSAGE, OTHER; cleared when the outcome changes |
| raised_at, updated_at | TIMESTAMPTZ | |
| version | BIGINT | optimistic lock |

Checks: status/substitute consistency (`ASSIGNED` iff substitute set); `informed_*` all set or all null. Indexes
`(school_id, duty_date)`, `(substitute_teacher_id, duty_date) WHERE status='ASSIGNED'`, `(status, duty_date)`.

## substitution_history (append-only, trigger refuses UPDATE and DELETE)

`id, requirement_id (FK), action (RAISED, UPDATED, ASSIGNED, REASSIGNED, RELEASED, COVERED, INFORMED, CANCELLED, REOPENED),
from_status, to_status, substitute_teacher_id, actor_user_id (null for the system), note, at`.

## Status transitions

OPEN → ASSIGNED | MANAGER_COVER | CANCELLED; ASSIGNED → ASSIGNED (other Teacher) | OPEN (release, reason required) |
MANAGER_COVER | CANCELLED; MANAGER_COVER → ASSIGNED | OPEN | CANCELLED; CANCELLED → OPEN (absence again). INFORMED needs
ASSIGNED or MANAGER_COVER.

## notification type check

Replaced by `type ~ '^[A-Z_]+$'` (see research D-6). New values `SUBSTITUTE_NEEDED`, `SUBSTITUTE_ASSIGNED`,
`SUBSTITUTE_ENDED`, `SUBSTITUTE_SCHOOL_INFORMED`.
