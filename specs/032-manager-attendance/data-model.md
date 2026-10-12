# Data Model: Manager Attendance and Field Days (spec 032)

Tables in `attendance` (V49) and `leave` (V50). No foreign keys cross modules: manager and user ids are plain UUIDs
checked through `organization.api` and `identity`; file ids come from `files.api`.

## manager_day

One Manager's record for one date. Holds the attendance and, when leave is approved, the leave on that date.

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| manager_id | UUID NOT NULL | the Manager (spec 005) |
| day_date | DATE NOT NULL | business date; unique with `manager_id` |
| day_type | VARCHAR(10) | `OFFICE` or `FIELD`; null for a leave-only row |
| purpose | VARCHAR(500) | required for `FIELD` while no activity links exist |
| unlinked | BOOLEAN NOT NULL DEFAULT FALSE | a Field day with a purpose and no planned-activity link |
| check_in_at | TIMESTAMPTZ | server time |
| check_in_lat, check_in_lng | NUMERIC(9,6) | required with a check-in; ranges checked |
| check_in_accuracy_m | NUMERIC(8,2) | optional |
| check_out_at | TIMESTAMPTZ | after `check_in_at` |
| check_out_lat, check_out_lng | NUMERIC(9,6) | required with a check-out |
| check_out_accuracy_m | NUMERIC(8,2) | optional |
| photo_file_id | UUID | optional, from the file store |
| late | BOOLEAN NOT NULL DEFAULT FALSE | check-in after the late time |
| added_later | BOOLEAN NOT NULL DEFAULT FALSE | from an approved forgotten check-in |
| corrected | BOOLEAN NOT NULL DEFAULT FALSE | an Admin or Director changed it |
| leave_request_id | UUID | the approved leave request of the leave module |
| leave_fraction | NUMERIC(2,1) | 1.0 or 0.5 when `leave_request_id` is set |
| leave_unpaid | BOOLEAN NOT NULL DEFAULT FALSE | true for the Loss-of-Pay type, set when leave is applied |
| version | BIGINT NOT NULL | optimistic locking |

- Unique `(manager_id, day_date)`; index `(day_date)` for the grid and the alert job.
- A check-in requires day_type, a location and no existing check-in; a check-out requires a check-in.
- Check constraints repeat the ranges (`leave_fraction IN (0.5, 1.0)`, latitude and longitude).

## manager_day_activity

A planned activity linked to a Field day (contract C1). `day_id` UUID NOT NULL, `kind` VARCHAR(40) NOT NULL (the
`PlannedActivity.kind` of the provider), `activity_id` UUID NOT NULL; primary key `(day_id, kind, activity_id)`. Rows are
replaced together with the day when it is corrected.

## manager_day_history

Append-only. One row per change to a day: the first is the original.

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| day_id | UUID NOT NULL | |
| changed_by | UUID NOT NULL | the actor (the Manager for a check-in or check-out) |
| changed_at | TIMESTAMPTZ NOT NULL | |
| action | VARCHAR(20) NOT NULL | `CHECK_IN`, `CHECK_OUT`, `COMPLETE_CHECK_OUT`, `CORRECT`, `LEAVE_APPLIED`, `LEAVE_REMOVED`, `ADDED_LATER` |
| reason | VARCHAR(500) | required for `COMPLETE_CHECK_OUT`, `CORRECT`, `ADDED_LATER` |
| before_json, after_json | TEXT | the affected fields |

A trigger refuses `UPDATE` and `DELETE`.

## manager_regularization

A Manager's request to add a forgotten check-in.

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| manager_id | UUID NOT NULL | |
| day_date | DATE NOT NULL | a past date of an unlocked month; at most one pending request per Manager and date |
| proposed_time | TIME NOT NULL | not in the future |
| day_type | VARCHAR(10) NOT NULL | `OFFICE` or `FIELD` |
| purpose | VARCHAR(500) | required for `FIELD` |
| reason | VARCHAR(500) NOT NULL | |
| status | VARCHAR(10) NOT NULL | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| requested_at | TIMESTAMPTZ NOT NULL | |
| decided_by | UUID | not the requester |
| decided_at | TIMESTAMPTZ | |
| decision_reason | VARCHAR(500) | required on rejection |

## manager_month

A Manager's month and its lock.

| Column | Type | Rules |
| --- | --- | --- |
| manager_id | UUID NOT NULL | |
| year_month | VARCHAR(7) NOT NULL | `YYYY-MM`; unique with `manager_id` |
| state | VARCHAR(8) NOT NULL | `OPEN` (default when absent) or `LOCKED` |
| frozen_working_days, frozen_days_worked, frozen_leave_paid, frozen_leave_unpaid, frozen_unpaid, frozen_field_days | NUMERIC(4,1) | set at lock, cleared at reopen |
| locked_at, locked_by | TIMESTAMPTZ, UUID | |
| version | BIGINT NOT NULL | |

`manager_month_event` (append-only): `manager_id`, `year_month`, `event` (`LOCKED`, `REOPENED`), `reason` (required for
`REOPENED`), `actor`, `occurred_at`.

## manager_attendance_setting

One row. `missed_cutoff` TIME (default 10:30), `late_after` TIME (default 09:30), `updated_by`, `updated_at`,
`version`. `late_after` must be before `missed_cutoff`.

## manager_missed_alert

`manager_id`, `alert_date`, `created_at`; primary key `(manager_id, alert_date)`. Its only job is to make the alert once
per Manager per day.

## manager_leave_request (leave module, V29)

| Column | Type | Rules |
| --- | --- | --- |
| id | UUID PK | |
| manager_id | UUID NOT NULL | the requester; a Manager with no joining date cannot create one |
| leave_type_id | UUID NOT NULL | a spec 009 leave type, including Loss-of-Pay |
| first_date, last_date | DATE NOT NULL | `first_date <= last_date`; not before the joining date |
| first_half, last_half | BOOLEAN NOT NULL DEFAULT FALSE | half day at either end |
| reason | VARCHAR(500) NOT NULL | |
| status | VARCHAR(10) NOT NULL | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `REVOKED` |
| requested_at | TIMESTAMPTZ NOT NULL | |
| decided_by, decided_at, decision_reason | | decider never the requester (Admin or Director only); a reason on rejection and on revoke |
| version | BIGINT NOT NULL | |

## Derived (never stored, except frozen at lock)

| Name | Definition |
| --- | --- |
| Day status | worked, field, leave, absent, weekly off, holiday, missed, no check-out, pending (see research.md section 3) |
| Manager month figures | working days, days worked, leave days (paid, unpaid), unpaid days, field days, locked |
| Missed check-in | a working day inside employment, past the cut-off, with no check-in and no approved leave, and no later check-in that day |

## State and lifecycle

- **Day**: none, then checked in, then checked out; a correction or an approved forgotten check-in edits it with history;
  leave sets `leave_*`. A day in a locked month does not change.
- **Leave request**: pending, then approved, rejected or cancelled; approved may be revoked. Approving writes the leave
  days; revoking or cancelling an approved request removes them.
- **Regularization**: pending, then approved, rejected or cancelled.
- **Month**: open, then locked (after the month ends and nothing is pending), then reopened with a reason.

Audit entity types: `MANAGER_DAY`, `MANAGER_REGULARIZATION`, `MANAGER_LEAVE`, `MANAGER_MONTH`,
`MANAGER_ATTENDANCE_SETTING`.

## Reads by other modules

Only through `attendance.api.ManagerAttendanceReadApi`; no other module reads these tables (ArchUnit enforces).
