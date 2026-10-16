# Implementation Plan: School Working Days — Enter a Whole Year on One Screen

**Branch**: `038-working-days-year-entry` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/038-working-days-year-entry/spec.md`

## Summary

A second way to maintain the figures of spec 037: one screen, one School and one year, twelve month fields, one Save.
It adds **no table, no migration, no permission key and no menu item**; it adds two endpoints and one page to what 037
built.

- **Read**: `GET /api/v1/school-working-days/year?schoolId=&year=` returns the twelve months with the stored figure
  (id, version, last changed) or none, the days in the month, and the calendar-derived hint. Fixed number of queries.
- **Save**: `PUT /api/v1/school-working-days/year` takes only the months the user changed, each with the version it was
  loaded with (none for a month with no figure). Each month is its own small transaction, so valid months are stored
  and failed months are reported (FR-009). The answer is always `200` with a status per month.
- **Reuse, not copy**: the save calls the same create and edit code paths as the grid (`WorkingDaysService`: rules,
  version check, history row, central audit event). So FR-004, FR-022 and FR-023 hold by construction. The only new
  rule is the clarified one: a stored figure that already equals the typed figure is **unchanged**, not a conflict.
- **Permissions**: `SCHOOL_WORKING_DAYS` View to open; per month, Create for a month with no figure and Edit for a month
  with one (FR-018, FR-020). `PermissionGuard` gains a non-throwing `isGranted` so one request can be allowed for some
  months and refused for others.
- **Screen**: `/master-data/school-working-days/year` (a child of the 037 route, so the existing route guard and menu
  grants apply with no access-model change). Twelve month rows in a single column that becomes two columns on wide
  screens; no table, so nothing can scroll the page sideways at 375 px. An "Enter a year" button on the grid carries the
  School and year across.

Clarified rule (spec session 2026-10-16): a month already holding the typed figure is "unchanged" and counts as
success, which also makes a retry after a dropped connection safe.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, ArchUnit; React Router, MUI.
No new dependency. (`@mui/x-data-grid` is not used by this screen.)

**Storage**: none new. Reads and writes `school_working_days` and `school_working_days_history` (V52, spec 037).
**No migration.**

**Testing**: JUnit 5 + Testcontainers (`IntegrationTestBase`); the pure rule tests (`WorkingDaysRules`) are reused. New:
per-role tests on both endpoints; a partial-save test (valid, out of range, stale, deleted, already exists); the
retry/idempotency test (same Save twice); two-user concurrency; a statement-count test for the read; the ArchUnit rule
file is unchanged. Frontend Vitest with a fixture per role, a partial-save fixture, helper tests, and axe in both themes
(extend `a11y.test.tsx`).

**Target Platform**: Browser (web only); the mobile apps do not show it.

**Project Type**: Web application (frontend + backend), single Spring Boot deployable.

**Performance Goals**: screen load under 2 s and a Save of all twelve months under 3 s (SC-009). Read: one query for the
School's rows in the year, one `SchoolDirectory` read, one `WorkingCalendar.workingDaysBySchool` call for the year, one
user-name lookup. Save: at most twelve short transactions.

**Constraints**: the backend enforces permission and scope per request and per month and fails closed; the actor comes
from the JWT; "today" for the year window is `WorkingCalendar.businessToday()`; a refused or unchanged month writes no
audit and no history row; a repeated Save never duplicates a row or an audit entry.

**Scale/Scope**: 2 endpoints, 1 page with 2 small dialogs (leave confirmation, helper value), no new table.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS. Every created or updated month goes through the 037 write path: one
  `EntityChanged` event and one history row (with the actor's roles) in the same transaction. Unchanged and refused
  months write nothing. No new audit shape.
- **Principle II (Five fixed roles, configurable permissions)** - PASS. No new role and no new key; the existing
  `SCHOOL_WORKING_DAYS` View, Create and Edit decide what the screen offers.
- **Principle III (Data scope)** - PASS. `WorkingDaysScope.requireSchool` runs once per request exactly as in the grid;
  the School picker uses the existing `/schools` options, already limited to the caller's scope.
- **Principle IV (Role-based experience)** - PASS. Unavailable actions are hidden (a month the user may not change is
  read-only, no Save without Create or Edit); a deep link without View shows the not-authorized page through the
  existing route guard; loading, error and empty states; inline validation per month; DD/MM/YYYY and month names;
  WCAG 2.2 AA (labelled fields, focus to the first failed month, status announced); no horizontal page scroll at 375 px.
- **Principle V (Payroll formula-driven)** - PASS. The inputs to pay are the same stored figures; nothing is typed per
  payslip, and this screen changes no payroll figure by itself (FR-016).
- **Principle VII (Modular monolith)** - PASS. All new code is in `attendance.workingdays` (internal + web). One small
  addition outside it: `identity.permissions.PermissionGuard.isGranted` (a public method on a class other modules
  already call; additive). No new cross-module read of tables.
- **Principle VIII (Concurrency)** - request model. Each month is saved independently with the existing version check;
  there is no batch or background work.
- **Principle IX (Reliability, testability)** - PASS, tests first as listed. The figure drives pay once consumers adopt
  it, so the retry/idempotency and conflict tests are mandatory.
- **Principle X (Security)** - PASS. Permission checked in the controller (View, and Create or Edit) and per month in the
  service; scope checked; body validated (month 1 to 12, no duplicate months, at most 12 entries, figure range).
- **Principles VI, XI** - not applicable.

No constitution amendment: no new module, key, menu item or matrix row. No violations; Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. One item to settle at
`/speckit-analyze`: leaving the screen by the left menu cannot be intercepted with the app's router (research.md
section 9).

## Project Structure

### Documentation (this feature)

```text
specs/038-working-days-year-entry/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── school-working-days-year-api.md   # HTTP, for the screen
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/permissions/PermissionGuard.java        # + isGranted(roles, module, action): boolean (additive)
└── attendance/workingdays/
    ├── internal/
    │   ├── WorkingDaysService.java                  # + saveMonth(...): an outcome per month (created, updated,
    │   │                                            #   unchanged, refused), reusing insert(...), the version check
    │   │                                            #   and record(...)
    │   ├── YearEntryService.java                    # NEW: loadYear(...) and saveYear(...): scope, request checks,
    │   │                                            #   per-month permission, one transaction per month
    │   └── WorkingDaysViewFactory.java              # + the 12-month view record (or a small sibling record)
    └── web/SchoolWorkingDaysController.java         # + GET /year (View), PUT /year (View + Create or Edit)

backend/src/test/java/com/hls/attendance/workingdays/
└── YearEntryApiTest.java                            # NEW: per role, partial save, retry, concurrency, statements

frontend/src/features/schoolWorkingDays/
├── YearEntryPage.tsx                                # NEW: pickers, 12 month fields, helpers, Save, results
├── MonthField.tsx                                   # NEW: one month (label, field, stored figure, hint, error)
├── yearEntryState.ts                                # NEW: pure state: loaded, typed, dirty, results, helpers
├── schoolWorkingDaysApi.ts                          # + getYear, saveYear
├── SchoolWorkingDaysPage.tsx                        # + "Enter a year" button carrying School and year
├── YearEntryPage.test.tsx, yearEntryState.test.ts   # NEW
└── workingDaysRules.ts                              # reused (figureProblem, formatFigure)
frontend/src/App.tsx                                 # + route /master-data/school-working-days/year
frontend/src/a11y/a11y.test.tsx                      # + the new page in both themes

docs/spec-roadmap.md                                 # + row 038
```

**Structure Decision**: web application; the feature extends the existing `attendance.workingdays` sub-package and the
existing `schoolWorkingDays` frontend folder. A new controller was rejected: the two endpoints share the base path,
permission module and exception advice of the grid. A new table or "year" aggregate was rejected: the figures stay one
row per School and month (FR-004).

## Complexity Tracking

Not needed; no constitution violations.
