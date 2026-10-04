# Phase 1 Data Model: Attendance

Two migrations: `V14__create_attendance_tables.sql` (all tables, the four built-in status codes and
the default calendar row) and `V15__seed_more_attendance_status_codes.sql` (data only: the `S`, `H`
and `A` codes). The Holiday Calendar screen (User Story 9) and its PDF need **no schema change**: they
read the existing `attendance_non_working_date` and `attendance_calendar_setting` rows. No foreign
key leaves the module: `teacher_id`,
`school_id` and `user_id` are plain ids validated through the owning module's public API at write
time. "Mutable" rows carry a `version` for optimistic locking. History and event tables are
append-only: the application has no update or delete path for them.

## Status Code (`attendance_status_code`) - mutable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| short_code | VARCHAR(8) NOT NULL | unique case-insensitively (`unique index on lower(short_code)`), e.g. `P`, `L`, `T`, `H` |
| name | VARCHAR(60) NOT NULL | |
| category | VARCHAR(12) NOT NULL | `WORKED`, `LEAVE`, `TRAINING`, `NON_WORKING` (`CHECK`) |
| weight | NUMERIC(4,2) NOT NULL | `CHECK (weight >= 0 AND weight <= 1)` |
| active | BOOLEAN NOT NULL DEFAULT true | |
| system | BOOLEAN NOT NULL DEFAULT false | the four built-in defaults (`P`, `L`, `T`, `N`); cannot be deleted or deactivated |
| sort_order | INT NOT NULL | |
| version | BIGINT NOT NULL | |

Seeded by the migration: `P` Present (WORKED, 1.00), `L` Leave (LEAVE, 0.00), `T` Training day
(TRAINING, 1.00), `N` Non-working (NON_WORKING, 0.00), all `system = true`. `V15` adds three
ordinary codes (`system = false`, so Admin can rename, re-weight or deactivate them): `S`
Substitution (WORKED, 1.00), `H` Holiday (NON_WORKING, 0.00) and `A` Absent (LEAVE, 0.00, treated
like `L`); each is inserted only if no code with that short code exists. A code referenced by any
mark is never deleted (only deactivated).

## Attendance Mark (`attendance_mark`) - mutable, one per Teacher per date

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id | UUID NOT NULL | |
| mark_date | DATE NOT NULL | unique with `teacher_id` |
| status_code_id | UUID NOT NULL FK -> attendance_status_code | |
| day_value | NUMERIC(3,2) NOT NULL | `CHECK (day_value IN (0.50, 1.00))` |
| school_id | UUID NOT NULL | the School of the placement in effect that date (snapshot) |
| note | VARCHAR(500) | |
| set_by_user_id | UUID NOT NULL | |
| set_by_kind | VARCHAR(10) NOT NULL | `SELF` or `SUPERVISOR` |
| set_at | TIMESTAMPTZ NOT NULL | |
| version | BIGINT NOT NULL | |

Indexes: unique `(teacher_id, mark_date)`; `(mark_date)`; `(school_id, mark_date)`.

## Mark History (`attendance_mark_history`) - append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id, mark_date | UUID, DATE NOT NULL | |
| action | VARCHAR(10) NOT NULL | `CREATED`, `CORRECTED`, `CLEARED` |
| status_code_id | UUID | null for `CLEARED` |
| day_value | NUMERIC(3,2) | |
| school_id | UUID | |
| note | VARCHAR(500) | |
| set_by_user_id | UUID NOT NULL | |
| set_by_kind | VARCHAR(10) NOT NULL | |
| set_at | TIMESTAMPTZ NOT NULL | |

Index `(teacher_id, mark_date, set_at)`.

## Calendar Setting (`attendance_calendar_setting`) - mutable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID | null = the organization-wide default; unique where not null and a unique partial index for the single null row |
| weekly_off_days | VARCHAR(40) NOT NULL | comma list of `MON,TUE,...,SUN`; may be empty for a six/seven day School |
| version | BIGINT NOT NULL | |

Seeded: the default row with `SUN`. A School override replaces (does not merge with) the default.

## Non-Working Date (`attendance_non_working_date`) - mutable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| on_date | DATE NOT NULL UNIQUE | organization-wide |
| description | VARCHAR(200) NOT NULL | |
| created_by | UUID NOT NULL | |

## Teacher-Month (`attendance_teacher_month`) - exists only once locked

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id | UUID NOT NULL | |
| year_month | CHAR(7) NOT NULL | `YYYY-MM`; unique with `teacher_id` (`CHECK year_month ~ '^[0-9]{4}-[0-9]{2}$'`) |
| state | VARCHAR(8) NOT NULL | `LOCKED` or `OPEN` (OPEN = reopened) |
| working_days, days_worked, days_leave, training_available, training_attended | NUMERIC(6,2) NOT NULL | frozen at lock |
| unmarked | INT NOT NULL | always 0 when frozen (lock requires it) |
| weighted_total | NUMERIC(7,2) NOT NULL | |
| changed_at | TIMESTAMPTZ NOT NULL | |
| version | BIGINT NOT NULL | |

Absence of a row means the Teacher-month is open and never locked.

## Teacher-Month Event (`attendance_teacher_month_event`) - append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id, year_month | UUID, CHAR(7) NOT NULL | |
| event | VARCHAR(10) NOT NULL | `LOCKED`, `REOPENED`, `RELOCKED` |
| reason | VARCHAR(500) | required for `REOPENED` |
| actor_user_id | UUID NOT NULL | |
| occurred_at | TIMESTAMPTZ NOT NULL | |

## Validation rules and state transitions

- A mark needs a placement on the date (via `TeacherDirectory`), a date not after today
  (Asia/Kolkata), a date not after the Teacher's exit, an unlocked Teacher-month, and an active
  status code. Day value is 0.5 or 1.
- Teacher self-marking: date in `[today - 3, today]` and the current mark (if any) has
  `set_by_kind = SELF`. Supervisors (scoped Manager, Admin, Director): any unlocked date inside a
  placement; their writes set `SUPERVISOR`.
- Teacher-month: no row -> `LOCKED` (lock) -> `OPEN` (reopen with reason) -> `LOCKED` (relock). Both
  locks are refused while the Teacher has an unmarked working day, and for a month that has not
  ended.
- Rollup definitions are in research.md section 4; they are computed live for open months and read
  from the frozen columns for locked ones.

## `identity` and `audit` additions (no schema change)

New `PermissionModule` constants `ATTENDANCE`, `TEACHER_ATTENDANCE`, `MY_ATTENDANCE`,
`ATTENDANCE_SETUP` and `HOLIDAY_CALENDAR` (VIEW for every role, EDIT for Admin and Director) seeded
per the spec table. Navigation: MASTER DATA → `Holiday Calendar` (`HOLIDAY_CALENDAR`, all roles) and
`Attendance Setup` (`ATTENDANCE_SETUP`, Admin/Director). New `change_history_entry.entity_type` values
`ATTENDANCE_MARK`, `ATTENDANCE_MONTH`, `ATTENDANCE_CODE`, `ATTENDANCE_CALENDAR`,
`ATTENDANCE_EXPORT`, mapped in `AuditVisibility` to `ATTENDANCE.VIEW` (marks, months, exports) and
`ATTENDANCE_SETUP.VIEW` (codes, calendar).

## Derived views (no tables)

- **Rollup** from `RollupCalculator` (live) or `attendance_teacher_month` (frozen).
- **Grid** rows: Teachers allowed by scope and filters, placed during the month, with one cell per
  actual day of the month (code short letter, the code's `category`, half-day flag, set-by kind,
  locked flag, and not-placed / weekly-off / non-working variants). The `category` lets the screen
  colour leave/absent and holiday-status marks without knowing individual codes.
- **Holiday calendar view** (User Story 9): the year or month is derived on the client from
  `GET /api/v1/attendance/calendar` (`defaultWeeklyOff` plus `nonWorkingDates`); a date in
  `nonWorkingDates` is a holiday even when it is also a weekly off day. School overrides are not
  part of this organization-wide view. The PDF is drawn on the client from the same data; nothing
  is stored.
