# Data Model: School Monthly Working Days

No foreign keys cross modules: `school_id`, `created_by`, `updated_by` and `actor_id` are plain UUIDs checked through
`school.api` and the JWT.

## school_working_days

One row per School and calendar month.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| school_id | UUID NOT NULL | must exist in `school` at create time (`SchoolDirectory.school`); inactive Schools allowed |
| year | INTEGER NOT NULL | 4-digit year (INTEGER, not SMALLINT, so the schema validation of Hibernate accepts it as an int) |
| month | INTEGER NOT NULL | CHECK 1..12 |
| working_days | NUMERIC(3,1) NOT NULL | CHECK 0..31 and a multiple of 0.5; the upper bound per month is checked in code |
| version | BIGINT NOT NULL DEFAULT 0 | JPA `@Version` |
| created_by | UUID NOT NULL | |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_by | UUID NOT NULL | "last changed by" in the grid |
| updated_at | TIMESTAMPTZ NOT NULL | "last changed" in the grid |

Constraints and indexes:

- `UNIQUE (school_id, year, month)` (FR-002)
- `INDEX (year, month)` for the grid and the read interface
- `CHECK (working_days * 2 = floor(working_days * 2))` (half steps)

Validation (`WorkingDaysRules`, pure):

- `0 <= working_days <= YearMonth.of(year, month).lengthOfMonth()`, in steps of 0.5 (FR-003)
- on create (and each copied row): `today.year - 5 <= year <= today.year + 5` (FR-029)
- School must exist (FR-004)

Lifecycle: created -> edited (any number of times) -> deleted (hard delete, Admin by default). After delete the month
falls back to the calendar-derived count (FR-009). A School that has rows cannot be removed: Schools are only
deactivated in the school master data, never deleted (FR-005), so nothing extra is needed beyond keeping rows on
deactivation.

## school_working_days_history

Append-only. Written in the same transaction as each change. No update or delete path exists in code.

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| school_id | UUID NOT NULL | |
| year | INTEGER NOT NULL | |
| month | INTEGER NOT NULL | the row may no longer exist, so history is keyed by School, year and month |
| action | VARCHAR(10) NOT NULL | CHECK IN ('CREATE','EDIT','DELETE','COPY') |
| old_value | NUMERIC(3,1) | null on CREATE and COPY |
| new_value | NUMERIC(3,1) | null on DELETE |
| actor_id | UUID NOT NULL | |
| actor_roles | VARCHAR(100) NOT NULL | comma-separated role names at the time |
| occurred_at | TIMESTAMPTZ NOT NULL | |

Index: `(school_id, year, month, occurred_at DESC)` for a row's history; `(school_id, year, action)` for the Deleted
rows view.

## Working Days Answer (not stored)

Returned by `SchoolWorkingDays`: `schoolId`, `YearMonth`, `workingDays` (decimal, half steps), `source`
(`STORED` or `CALENDAR_DERIVED`). See [contracts/school-working-days-read-api.md](./contracts/school-working-days-read-api.md).

## Grid row (not stored)

Built per request: stored row + School name and Zone (from `SchoolDirectory`) + days in the month + the
calendar-derived count for reference + last changed by (name resolved through the identity user directory the other
master-data screens use) and when.

## Audit

Each create, edit, delete and copied row also publishes `EntityChanged` with entity type `SCHOOL_WORKING_DAYS`, entity
id = the row id, field `workingDays` (or `created` / `deleted` with the figure as detail), old and new value.
