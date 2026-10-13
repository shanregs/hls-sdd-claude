# Research: School Monthly Working Days

No `NEEDS CLARIFICATION` remained in the Technical Context. These are the design decisions the plan rests on.

## 1. Where the code lives

- **Decision**: sub-package `com.hls.attendance.workingdays` (internal + web), public types in `com.hls.attendance.api`.
- **Rationale**: the fallback is the spec 008 calendar (`WorkingCalendar`), which `attendance` owns; `attendance`
  already reads `school.api`. The `attendance.manager` sub-package (spec 032) is the precedent. Consumers (payroll,
  Manager attendance, rollups) already depend on `attendance.api`.
- **Alternatives**: in `school` (cycle: `school` -> `attendance` -> `school`); a new top-level module (needs a
  Principle VII list change for one table).

## 2. Value type and validation

- **Decision**: `NUMERIC(3,1)` column; the application rejects anything that is not a multiple of 0.5, below 0, or above
  `YearMonth.lengthOfMonth()`. A DB check repeats the cheap parts (0 to 31, multiple of 0.5, month 1-12).
- **Rationale**: matches the half-day treatment in specs 008, 009 and 013a; leap years are an application concern, so the
  database cannot be the only guard.
- **Rules table (hand-computed, used as the first unit tests)**:

  | Month | Days | Accepted | Refused |
  |---|---|---|---|
  | Feb 2028 (leap) | 29 | 0, 0.5, 28.5, 29 | 29.5, -0.5 |
  | Feb 2027 | 28 | 28 | 28.5, 29 |
  | Apr 2027 | 30 | 25.5, 30 | 30.5, 25.3 |
  | Jul 2027 | 31 | 31 | 31.5 |

## 3. Year window (FR-029)

- **Decision**: a new row (including a copied row) must have year in `[today.year - 5, today.year + 5]`, with `today`
  from `WorkingCalendar.businessToday()`. Edits and deletes of existing rows are not restricted by the window.
- **Rationale**: simple and testable; the clarification chose it. Existing rows that later fall outside stay visible and
  can be edited or deleted (Assumptions).
- **Alternatives**: fixed floor year; any year 2000-2100 (both rejected in the clarification).

## 4. Grid query: filter, sort, page

- **Decision**: resolve School and Zone filters and the name search against `SchoolDirectory.allSchools()` in memory
  (one call), fetch the matching rows of the selected year with one indexed query, enrich with School name, Zone and the
  calendar-derived count, then sort and page in memory. Responses return `total` for the pager.
- **Rationale**: no cross-module joins are allowed, sorting by School name or Zone needs data from `school`, and a year
  holds at most about 6,000 rows (SC-008), so in-memory work is small. The number of queries does not depend on the
  number of Schools.
- **Alternatives**: duplicating School name and Zone into this table (stale when a School is renamed or moved); a view
  joining `school` tables (breaks module boundaries).

## 5. Calendar-derived count

- **Decision**: for a School and year, call `WorkingCalendar.workingDaysBySchool(schoolIds, jan1, dec31)` once and bucket
  the returned dates by month; the derived count of a month is the size of its bucket. A School without an override
  already gets the default calendar from that call.
- **Rationale**: one call regardless of School count; reuses the exact rules the rollups and grid use, so the reference
  number always agrees with spec 008.
- **Alternatives**: one call per month (12x the work); recomputing weekly offs here (a second copy of the rules).

## 6. Published read interface

- **Decision**: `attendance.api.SchoolWorkingDays` with `forRange(Collection<UUID> schoolIds, YearMonth from,
  YearMonth to)` as the one real query path; `forMonth` (one or many Schools) and `forYear` delegate to it. Every answer
  has `source` = `STORED` or `CALENDAR_DERIVED`. It does not check the screen permission and exposes no person data.
- **Rationale**: FR-011 to FR-014. One implementation keeps every consumer on the same figure at the same moment: one
  stored-rows query (`school_id IN (...) AND (year, month) BETWEEN ...`) plus one `workingDaysBySchool` call.
- **Unknown School id**: treated like the calendar does today (default calendar), labelled `CALENDAR_DERIVED`; callers
  only pass Schools they got from `school.api`.
- **Caching**: none now. Rows are small and the read is one indexed query; revisit when 013b adopts it (docs/caching.md).

## 7. History and the "Deleted rows" view

- **Decision**: the feature keeps its own append-only table `school_working_days_history` (action `CREATE`, `EDIT`,
  `DELETE`, `COPY`; old and new value; actor; roles; time; School, year, month). It is written in the same transaction
  as the change, and each change also publishes `EntityChanged` so the central audit trail (spec 003) stays complete.
- **Rationale**: FR-023 gives View holders (Admin and Director) the history, but the central change-history screen
  needs audit permissions that the Director does not hold by default, and `audit` tables must not be read by other
  modules. Spec 032's `ManagerDayHistory` set the precedent. The deleted-rows view reads this table, keyed by School,
  year and month, not by row id, so it survives deletion.
- **Alternatives**: a read interface in `audit` (a second public API to design and keep in step); reading
  `change_history` directly (breaks module boundaries and permission model).

## 8. Concurrency

- **Decision**: JPA `@Version` on the row. Edit and delete send the version they loaded.
  - Row exists, version differs -> 409 with the current figure and version.
  - Row no longer exists -> 404 with code `ROW_DELETED`; the screen removes the row and says so; nothing is recreated.
  - Create when a row already exists (unique key) -> 409 naming the existing row and offering edit.
- **Rationale**: FR-002, FR-021, SC-009, and the clarified deleted-row rule. The unique constraint is the real guard for
  two simultaneous creates; the service catches the violation and answers with the existing row.

## 9. Permissions and scope

- **Decision**: module `SCHOOL_WORKING_DAYS` (View, Create, Edit, Delete). Eligibility: Admin all four; Director all
  four grantable, seeded without Delete; Manager View only (off by default); Teacher and System none; the module joins
  `BUSINESS_MODULES` so System never sees it. A Manager with View is limited to Schools assigned to them through the
  organization scope query (same as attendance); Admin and Director are organization-wide.
- **Rationale**: spec Role & Permission Impact, FR-018, FR-024 to FR-026. The `Map.of` tables in
  `PermissionEligibility` hold at most ten pairs and `ROLE_RESTRICTED_MODULES` is full, so a new map is added (as 032
  did).

## 10. Year copy

- **Decision**: `POST .../copy` with School, source year, target year. In one transaction, for each month of the target
  year: if a row exists, report `EXISTS` and leave it; if the source month has no row, skip silently; if the figure
  exceeds the target month's days (29 into February 2027), report `EXCEEDS_DAYS`; otherwise create it and write a `COPY`
  history row and an audit event. Target year is checked against the window; same source and target year is refused; a
  source year with no rows is refused. The result lists `created`, `skipped` (with reason).
- **Rationale**: FR-027 and User Story 5, scenario 1.

## 11. Finalised-month notice

- **Decision**: a fixed text on the inline editor (beside Save) and in the delete confirmation. No query to payroll or
  attendance. The text lives in the frontend with the other messages; the API does not carry it.
- **Rationale**: FR-028 (clarification): avoids a dependency on modules that do not yet read this figure.

## 12. Migration number and spec numbering

- `origin/main` ended at V50 when this was planned and at V51 (manager leave) when it was implemented, so this feature uses V52.
- Prerequisites verified before coding (T001, 2026-10-13): `attendance.api.WorkingCalendar` (`businessToday`, `workingDays`,
  `workingDaysBySchool`), `school.api.SchoolDirectory` (`school`, `schools`, `zones`, `allSchools`),
  `school.api.ChangeRecorder` (publishes `audit.api.EntityChanged`), `school.api.SchoolScopeProvider` (implemented by
  `organization`; the same scope rule `SchoolService` uses), `identity.permissions.PermissionGuard`,
  `school.api.CallerContext`, `school.api.StaleVersion`, `identity.user.AppUserRepository` for names. Schools are only
  deactivated, never deleted (no delete endpoint), so rows cannot be orphaned.
- Spec number 037 is reserved in `docs/spec-roadmap.md` (spec Assumptions); the roadmap row is added during
  implementation.
