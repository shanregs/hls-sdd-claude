# Phase 1 Data Model: Master Data

Three modules, three Flyway migrations (`V11`, `V12`, `V13`). No foreign key crosses a module
boundary: cross-module ids (`zone_id`, `school_id`, `user_id`, `manager_id`) are plain UUID columns
validated through the owning module's public API at write time (the same posture `audit` uses).
All tables use UUID primary keys and `TIMESTAMPTZ`/`DATE` columns; "mutable" entities carry a
`version` column for optimistic locking (research.md section 11).

## `school` module (`V11__create_school_tables.sql`)

### Zone (`zone`) - mutable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| name | VARCHAR(120) NOT NULL | unique case-insensitively (`unique index on lower(name)`) |
| version | BIGINT NOT NULL | optimistic lock |
| created_at, updated_at | TIMESTAMPTZ | |

Deleted only if no Place, School or Manager depends on it (`ZoneChangeGuard` covers Managers;
Places/Schools are checked in `school`).

### Place (`place`) - mutable, deletable only when unused

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| zone_id | UUID NOT NULL FK -> zone | same module, real FK |
| name | VARCHAR(160) NOT NULL | not unique |
| pin_code | CHAR(6) NOT NULL | six digits (`CHECK pin_code ~ '^[0-9]{6}$'`), not unique |
| created_at | TIMESTAMPTZ | |

Indexes: `(zone_id)`, `(pin_code)`, `(lower(name))`. Rule: a Place with any School in it can be
neither deleted nor moved to another Zone (FR-005a).

### School (`school`) - mutable, never deleted

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| name | VARCHAR(200) NOT NULL | |
| place_id | UUID NOT NULL FK -> place | the School's Zone is the Place's Zone (research.md section 4) |
| address | TEXT NOT NULL | Manager-editable |
| contact_person | VARCHAR(120) | Manager-editable |
| contact_phone | VARCHAR(20) | Manager-editable |
| billing_contact | VARCHAR(200) | Admin/Director only |
| active | BOOLEAN NOT NULL DEFAULT true | |
| version | BIGINT NOT NULL | |
| created_at, updated_at | TIMESTAMPTZ | |

Indexes: `(place_id)`, `(active)`, `(lower(name))`.

**Invariants**: the School's current Manager (if any) must cover the School's Zone
(`SchoolChangeGuard`); deactivation is refused while Teachers are placed or scheduled in it
(`SchoolDeactivationGuard`).

## `organization` module (`V12__create_organization_tables.sql`)

### Manager (`manager`) - mutable

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| user_id | UUID NOT NULL UNIQUE | account with the Manager role (validated via `identity`) |
| active | BOOLEAN NOT NULL DEFAULT true | mirrors the user's usability; Admin-managed |
| version | BIGINT NOT NULL | |
| created_at | TIMESTAMPTZ | |

A Manager's display name and phone are read from `identity` at query time, never copied.

### Zone-Manager Assignment (`zone_manager_assignment`) - append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| zone_id | UUID NOT NULL | validated via `school.api` |
| manager_id | UUID NOT NULL FK -> manager | |
| starts_on | DATE NOT NULL | |
| ends_on | DATE | NULL = current |

Partial unique index: one current row per `(zone_id, manager_id)` where `ends_on IS NULL`. A Zone
may have several current rows; a Manager may appear in several Zones. Ending a row is refused while
the Manager still has current School assignments in that Zone.

### School-Manager Assignment (`school_manager_assignment`) - append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| school_id | UUID NOT NULL | validated via `school.api` |
| manager_id | UUID NOT NULL FK -> manager | |
| starts_on | DATE NOT NULL | |
| ends_on | DATE | NULL = current |

Partial unique index: at most one current row per `school_id`. **Invariant**: the Manager must hold
a current Zone-Manager row for the School's Zone (Zone read via `school.api.SchoolQueries`).

## `teacher` module (`V13__create_teacher_tables.sql`)

### Teacher (`teacher`) - mutable, never deleted

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| name | VARCHAR(160) NOT NULL | |
| phone | VARCHAR(20) | contact |
| email | VARCHAR(200) | contact |
| address | TEXT | contact |
| status | VARCHAR(20) NOT NULL | `IN_TRAINING`, `ACTIVE`, `ON_LEAVE`, `EXITED` |
| status_effective_on | DATE NOT NULL | |
| user_id | UUID UNIQUE | nullable link to the Teacher's account; cleared on exit |
| version | BIGINT NOT NULL | |
| created_at, updated_at | TIMESTAMPTZ | |

**State transitions** (research.md section 7): `IN_TRAINING -> ACTIVE`, `ACTIVE <-> ON_LEAVE`, any
-> `EXITED` (final). Anything else is rejected.

### Teacher Placement (`teacher_placement`) - append-only (status flips only)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id | UUID NOT NULL FK -> teacher | |
| school_id | UUID NOT NULL | validated via `school.api` |
| starts_on | DATE NOT NULL | may be in the future (scheduled) |
| ends_on | DATE | NULL = open-ended |
| status | VARCHAR(12) NOT NULL | `ACTIVE`, `CANCELLED`, `CORRECTED` |
| created_at | TIMESTAMPTZ | |

Exclusion constraint per Teacher: `ACTIVE` rows may not overlap in `daterange(starts_on,
coalesce(ends_on,'infinity'),'[]')`. At most one `ACTIVE` row with `starts_on > current_date`
(a scheduled move). The row in effect on date D = the `ACTIVE` row containing D. This is the
**interim** Teacher-School assignment, to be replaced by the school-billing contract; every screen
labels it as interim.

### Salary History Entry (`teacher_salary_history`) - append-only

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| teacher_id | UUID NOT NULL FK -> teacher | |
| amount | NUMERIC(12,2) NOT NULL | rupees; `CHECK amount >= 0` |
| effective_on | DATE NOT NULL | |
| recorded_by | UUID NOT NULL | actor |
| created_at | TIMESTAMPTZ | |

Index `(teacher_id, effective_on DESC)`. Current salary = latest `effective_on <= today`; "as of D"
= latest `effective_on <= D`, or "no salary recorded" if none. Rows are never updated or deleted.

## `identity` additions (no schema change)

Permission matrix rows are data, seeded by `PermissionMatrixService.seedDefaults()` (idempotent).
New `PermissionModule` constants: `ZONES`, `SCHOOLS`, `MANAGERS`, `TEACHERS`, `TEACHER_SALARY`.

| Module | Admin | Director | Manager | Teacher | System |
| --- | --- | --- | --- | --- | --- |
| ZONES | VIEW, CREATE, EDIT, DELETE | VIEW, CREATE, EDIT | - | - | - |
| SCHOOLS | VIEW, CREATE, EDIT, DELETE | VIEW, CREATE, EDIT | VIEW, EDIT (assigned, field-limited) | - | - |

Deactivating or reactivating a School is an Edit (restricted by role to Admin and Director); `SCHOOLS.DELETE` is
reserved for a future hard delete and is unused in this spec.
| MANAGERS | VIEW, CREATE, EDIT | VIEW, CREATE, EDIT | - | - | - |
| TEACHERS | VIEW, CREATE, EDIT | VIEW, CREATE, EDIT | VIEW, EDIT (assigned) | - | - |
| TEACHER_SALARY | VIEW, CREATE | VIEW, CREATE | - | - | - |

Navigation (section `MASTER DATA`): Zones and Managers (Admin, Director); Schools and Teachers
(Admin, Director, Manager). Teacher "My Profile" reuses `ACCOUNT_PROFILE`.

## `audit` additions (no schema change)

`change_history_entry` already holds `(entity_type, entity_id, field, before_value, after_value)`.
New `entity_type` values: `ZONE`, `PLACE`, `SCHOOL`, `MANAGER`, `ZONE_MANAGER_ASSIGNMENT`,
`SCHOOL_MANAGER_ASSIGNMENT`, `TEACHER`, `TEACHER_PLACEMENT`, `TEACHER_SALARY`. These rows
are business data, but the Audit screens are readable by Admin and System, and System MUST NOT see
teacher or school data (Constitution Principle II) and nobody without salary access may see
salary (FR-019). So audit reads of Change History and the unified Audit Logs filter out an entity
type unless the caller holds the matching `VIEW` grant: `ZONE`/`PLACE` need `ZONES.VIEW`, `SCHOOL`
needs `SCHOOLS.VIEW`, `MANAGER` and both assignment types need `MANAGERS.VIEW`, `TEACHER` and
`TEACHER_PLACEMENT` need `TEACHERS.VIEW`, `TEACHER_SALARY` needs `TEACHER_SALARY.VIEW`
(research.md section 16).

## Derived views (no tables)

- **ScopeView** (`orgWide`, `zoneIds`, `schoolIds`) from the assignment tables (research.md
  section 3).
- **Teacher in scope** = Teachers whose `ACTIVE` placement today is at a School in `schoolIds`
  (org-wide: all Teachers including unplaced ones; Teacher role: their own record only).
