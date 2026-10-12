# Implementation Plan: Manager Attendance and Field Days

**Branch**: `032-manager-attendance` | **Date**: 2026-10-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-manager-attendance/spec.md`

## Summary

Daily attendance for Managers, and the figures payroll needs to deduct only their unpaid days.

- **Record**: a Manager checks in (day type Office or Field, a required device location, an optional photo) and later
  checks out. One record per Manager per date holds the check-in, check-out, purpose, flags (late, added later,
  corrected, no check-out) and, when leave is approved, the leave on that date.
- **Leave**: a Manager requests leave of a spec 009 leave type; the Director or an Admin decides it. Approved leave
  writes its days onto the Manager's records. This flow lives in the **`leave` module**, because `attendance` must not
  depend on `leave` (existing rule), and it reaches attendance through a small interface, like Teacher leave does.
- **Figures**: a pure calculator turns a Manager's records, the default working calendar and their employment dates into
  working days, days worked, leave days, unpaid days and field days. A locked month stores its figures.
- **Alerts**: a scheduled job notices a working day with no check-in and no leave after the cut-off and publishes one
  event per Manager per day; the notification module tells the Manager, every Director and every Admin.
- **Corrections, forgotten check-ins, lock**: an Admin or Director corrects a day with a reason (history kept); a
  Manager asks for a forgotten past check-in (any day in an unlocked month); an Admin locks a month once it has ended and
  every request touching it is decided.
- **Payroll**: `ManagerAttendanceReadApi` (public) answers the month figures; spec 013a's `PayRules` uses it for a
  Manager's unpaid days and total loss of pay, and returns an explicit "none" for an Admin's or Director's.
- **Screens**: a Manager checks in and sees their month and leave; Admin and Director see the grid of all Managers,
  missed check-ins, who is on leave today, corrections, leave decisions and the lock.

Clarified rules (spec session 2026-10-11): attendance for Managers only (Admin and Director paid in full, System has no
salary); a missing check-out is a flag, never a deduction; a forgotten check-in may be requested for any day of an
unlocked month; half-day leave allows one check-in and counts half leave plus half worked; no joining date means no
check-in, no leave and "no joining date" in the figures; a lock is refused while requests for the month are pending.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit, Spring
scheduling (already enabled). Frontend: React Router, MUI, the browser geolocation API. No new dependency.

**Storage**: PostgreSQL through migrations (`V49` for attendance, `V50` for leave; `V48` is the highest on main after specs
011, 017a and 025). Check-in photos use the shared `files` module (jpg or png, at most 10 MB). Details in
[data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). The pure `ManagerMonthCalculator` is
tested first with hand-computed tables; per-role tests on every endpoint; the month lock paths; the alert job once per
day; ArchUnit rules (`ManagerAttendanceModuleRulesTest`); frontend Vitest with a fixture per role and axe in both themes.

**Target Platform**: Browser (web screens here); Android check-in is spec 033 and uses the same API.

**Project Type**: Web application (frontend + backend), single Spring Boot deployable.

**Performance Goals**: the grid of 100 Managers for a month loads in under 1 s in a fixed number of queries (one for the
month's records, one for leave, the calendar and employment read once); a check-in answers in under 500 ms.

**Constraints**: the backend refuses every request outside the caller's permission and fails closed; the actor comes from
the JWT; the time comes from the server clock in the business time zone (Asia/Kolkata), never the device; a location is
required; the alert job must be idempotent and safe if two instances run; amounts do not appear here (no money).

**Scale/Scope**: about 10 to 40 Managers; about 15 endpoints; 5 screens for a Manager and Admin/Director combined; 6 new
tables.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Auditable)** - PASS. Every check-in, check-out, completion, correction, request, decision, revoke,
  lock, reopen and setting change writes an audit entry in the same transaction; a day keeps its original and every
  correction in an append-only history.
- **Principle II (Five fixed roles, configurable permissions)** - PASS. No new role. Three permission modules
  (`MANAGER_ATTENDANCE`, `MANAGER_LEAVE`, `MY_MANAGER_ATTENDANCE`) seeded and runtime-editable; Teacher and System are
  ineligible. The spec's role table listed Export; no requirement needs it, so the module has no Export action (spec
  table updated).
- **Principle III (Data scope)** - PASS. A Manager reaches only their own records (Own); Admin and Director are
  org-wide. The Zone filter in the grid is a view filter, not a scope.
- **Principle IV (Role-based experience)** - PASS. Server-driven menu items; hidden actions; loading, empty and error
  states; DD/MM/YYYY and 12-hour or 24-hour time as the app does today; WCAG 2.2 AA; phone width for the Manager's own
  screens (they check in from a phone browser).
- **Principle V (Payroll formula-driven)** - PASS. Unpaid days are derived by one calculator from records and the
  calendar; nothing is typed per month. `PayRules` reads them.
- **Principle VII (Modular monolith)** - PASS with additive interfaces: `attendance.api` gains
  `ManagerAttendanceReadApi`, `ManagerLeaveAttendance` and `PendingManagerRequests` (an SPI the lock consults) plus
  events; `leave` implements the SPI and writes through `ManagerLeaveAttendance`; `payroll` reads
  `ManagerAttendanceReadApi`. `attendance` still does not depend on `leave`; `attendance` uses `organization.api` and
  `files.api`. ArchUnit verifies.
- **Principle VIII (Concurrency)** - the alert job runs on the existing scheduler; it is idempotent through a unique key
  per Manager and date, so two runs or two instances cannot notify twice. No shared mutable state.
- **Principle IX (Reliability, testability)** - PASS, tests first as above. This work changes pay (unpaid days), so the
  rules in research.md section 3 get the same financial review as 013a before 013b.
- **Principle X (Security)** - PASS. A location is personal data: it is shown only to the Manager and to Admin and
  Director, never in a list another role can open or in any export; latitude and longitude are range-checked; notes and
  reasons are length-limited plain text; the photo goes through the file store's content check.
- **Principles VI, XI** - not applicable.

No violations requiring justification. Complexity Tracking is not needed.

**Post-design re-check (after data-model.md and contracts)**: unchanged, all PASS. The one open design risk is the
cross-module lock check (research.md section 6), which is handled by an SPI rather than a dependency.

## Project Structure

### Documentation (this feature)

```text
specs/032-manager-attendance/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── manager-attendance-api.md        # HTTP, for the screens (and spec 033)
│   └── manager-attendance-read-api.md   # Java interfaces, for payroll, leave and notifications
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java       # + MANAGER_ATTENDANCE, MANAGER_LEAVE, MY_MANAGER_ATTENDANCE
│   ├── permissions/PermissionEligibility.java  # Manager-only / Admin-Director-only; Teacher and System excluded
│   ├── permissions/PermissionMatrixService.java    # seeds
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + items and scopes
├── attendance/
│   ├── api/                                    # ManagerAttendanceReadApi, ManagerLeaveAttendance, PendingManagerRequests,
│   │                                           #   ManagerMonth, ManagerCheckInMissed, ManagerDayCorrected,
│   │                                           #   ManagerRegularizationRequested, ManagerRegularizationDecided
│   └── manager/
│       ├── internal/                           # ManagerDay (+ repository), ManagerDayHistory, Regularization,
│       │                                       #   ManagerMonthState (+ events), ManagerAttendanceSettings,
│       │                                       #   MissedAlert, CheckInService, CorrectionService,
│       │                                       #   RegularizationService, ManagerMonthLockService,
│       │                                       #   ManagerMonthCalculator (pure), ManagerGridService,
│       │                                       #   ManagerAttendanceReadApiImpl, ManagerLeaveAttendanceImpl,
│       │                                       #   MissedCheckInJob, ManagerAttendanceDevSeeder
│       └── web/                                # MyManagerAttendanceController, ManagerAttendanceController,
│                                               #   ManagerAttendanceSettingsController
├── leave/
│   ├── api/                                    # ManagerLeaveRequested, ManagerLeaveDecided, ManagerLeaveCancelled
│   └── manager/
│       ├── internal/                           # ManagerLeaveRequest (+ repository), ManagerLeaveService,
│       │                                       #   ManagerLeavePendingRequests (implements the SPI)
│       └── web/                                # MyManagerLeaveController, ManagerLeaveDecisionController
├── notification/internal/ManagerAttendanceEventListener.java   # + types in notification/api/NotificationType.java
├── payroll/internal/PayRulesImpl.java          # Manager unpaid days from the read API; Admin/Director explicit "none"
└── organization/api/ManagerQueries.java        # + managerByUserId already exists; joining/exit dates via employment()

backend/src/main/resources/db/migration/
├── V49__create_manager_attendance.sql
└── V50__create_manager_leave.sql

backend/src/test/java/com/hls/attendance/manager/   # calculator, check-in, field day, correction, regularization, lock,
                                                    #   alert job, read API, authorization, audit, module rules
backend/src/test/java/com/hls/leave/manager/        # request, decision, revoke, scope, half day, lock interplay
backend/src/test/java/com/hls/payroll/              # PayRules with Manager and staff unpaid days

frontend/src/features/managerAttendance/
├── managerAttendanceApi.ts
├── ManagerCheckInPage.tsx          # check-in and check-out with the browser location, today's status
├── MyManagerMonthPage.tsx          # the Manager's own month
├── MyManagerLeavePage.tsx          # request and track leave, ask for a forgotten check-in
├── ManagerAttendanceGridPage.tsx   # Admin/Director grid, missed, on leave today, settings, lock
├── ManagerDayDialog.tsx            # a day's detail and a correction
├── ManagerLeaveDecisionsPage.tsx   # Admin/Director: pending leave and forgotten check-ins
└── __tests__/
frontend/src/App.tsx                # + guarded routes

docs/postman/ -> postman/HLS API/032 Manager Attendance/   # new folder
docs/spec-roadmap.md                # 032 row status; the 013a note
.specify/memory/constitution.md     # Manager Attendance rows in the Default role access matrix (patch note)
```

**Structure Decision**: Web application (unchanged layout). Manager attendance is a sub-package of `attendance`; Manager
leave is a sub-package of `leave` (so the dependency direction `leave -> attendance` is kept); the lock consults leave
through an SPI that `leave` implements.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, navigation, migrations, settings, the pure `ManagerMonthCalculator`.
2. **Check-in and check-out (US1)**: record, location, photo, flags, own month view.
3. **Field days (US2)** and **the Manager's month and the grid (US5)**: purpose, grid, missed list, corrections,
   forgotten check-ins.
4. **Manager leave (US3)**: requests and decisions in `leave`, days written to attendance.
5. **Read API and payroll (US7)**: the figures, the lock (US6), `PayRules` for Managers and staff.
6. **Missed check-in alert (US4)**: the job, the event, the notifications.
7. **Polish**: demo data, Postman, accessibility, roadmap, constitution rows, the financial review.

## Complexity Tracking

No violations. Table intentionally omitted.
