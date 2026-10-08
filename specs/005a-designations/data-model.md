# Data Model: Designations and Employment Details (spec 005a)

One Flyway file, `V24__create_designations.sql`. Foreign keys exist only inside a module; designation ids stored by
`organization` and `teacher` are plain ids checked through `designation.api`.

## designation (module `designation`)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| name | varchar(80) not null | trimmed, 1 to 80 characters |
| kind | varchar(8) not null | check in ('TEACHER','MANAGER') |
| retired | boolean not null default false | |
| held_ever | boolean not null default false | set when first assigned to a person; blocks a kind change |
| version | bigint not null | optimistic locking |
| created_by | uuid not null | |
| created_at, updated_at | timestamptz not null | |

Unique index on `(kind, lower(regexp_replace(btrim(name), '\s+', ' ', 'g')))`: same name allowed for different
kinds, refused for the same kind ignoring capitals and extra spaces (FR-002). Rows are never deleted.

## employee_id_claim (module `designation`)

| Column | Type | Notes |
| --- | --- | --- |
| employee_key | varchar(20) PK | the id trimmed and lower-cased |
| person_kind | varchar(8) not null | 'TEACHER' or 'MANAGER' |
| person_id | uuid not null unique | one id per person |
| employee_id | varchar(20) not null | as typed (trimmed) |
| person_name | varchar(200) not null | for the "already used by" message |
| claimed_at | timestamptz not null | |

Primary key = the uniqueness guarantee across Managers and Teachers together, including simultaneous requests.

## manager (module `organization`, existing) - new columns

| Column | Type | Notes |
| --- | --- | --- |
| employee_id | varchar(20) null | |
| joining_date | date null | any date; not after `exit_date` |
| exit_date | date null | check `exit_date >= joining_date` when both set |

## manager_designation (module `organization`)

| Column | Type | Notes |
| --- | --- | --- |
| id | uuid PK | |
| seq | bigint generated always as identity | tie-break for the same effective date |
| manager_id | uuid not null FK manager(id) | |
| designation_id | uuid not null | plain id, no foreign key across modules |
| effective_on | date not null | |
| recorded_by | uuid not null | |
| recorded_at | timestamptz not null | |

Append-only: a `BEFORE UPDATE OR DELETE` trigger raises an exception. Index on `(manager_id, effective_on, seq)`.
Designation on a date = the row with `effective_on <= date` that has the greatest `(effective_on, seq)`.

## teacher (module `teacher`, existing) - new columns

| Column | Type | Notes |
| --- | --- | --- |
| designation_id | uuid null | plain id; current designation only |
| employee_id | varchar(20) null | |

## Validation summary

- Designation name: 1 to 80 characters after trimming; duplicate per kind refused.
- Employee id: `^[A-Za-z0-9-]{1,20}$` after trimming; unique by `lower(trim)` across Managers and Teachers.
- Manager joining date: any; not after the exit date.
- Manager exit date: only while inactive; not before the joining date; not more than 90 days after the business date.
- Manager designation: an active designation of kind MANAGER; never cleared; effective date rules of FR-006.
- Teacher designation: an active designation of kind TEACHER, or unchanged; the spec does not forbid clearing it, so it
  may be cleared (audited) and then shows as missing.

## State

Designation: ACTIVE <-> RETIRED (any number of times). No other state.
