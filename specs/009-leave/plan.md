# Implementation Plan: Leave Management

**Branch**: `009-leave` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-leave/spec.md`

## Summary

Adds the `leave` bounded context (Constitution Principle VII) on top of spec 008 attendance:

- **Leave requests** (type, first and last date, half-day start/end, reason) with a status machine
  `PENDING → APPROVED | REJECTED | CANCELLED` and `APPROVED → CANCELLED` (supervisor revoke, or the
  Teacher's own cancel before the first day). Overlap of a Teacher's live requests is prevented by
  the database as well as by the service.
- **Teacher screens**: LEAVE → Apply Leave (with a live working-day count) and My Leave History
  (cancel).
- **Supervisor screen**: OPERATIONS → Leave Management (Manager in scope, Admin, Director): list with
  status/Teacher/School/month filters and a Pending count, approve, reject (reason), revoke.
- **Attendance feed**: approval, revoke and Teacher-cancel call a new narrow `attendance.api`
  contract (`LeaveAttendance`) that computes the working days, validates (locked months,
  supervisor-set days), and writes or removes the L marks atomically in the caller's transaction.
  Marks carry the request id so history and revoke can find them.
- **Audit**: every request transition and every leave-made mark change publishes to Change History.
- **Seeds**: two permission modules, navigation, a `leave_type` table, and demo requests.

`leave` depends on `attendance`, `teacher` only through their public `api` packages
(`leave -> attendance -> teacher -> organization -> school`). `identity` gets two permission
modules, seeds, eligibility, navigation and data-scope entries; `audit` gets visibility mappings. No
new dependency of `audit` or `attendance` on `leave`.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19 - unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA (`@Version`), Spring Security, Spring Modulith,
Flyway, ArchUnit; frontend React Router, MUI, `react-hook-form`. No new dependency.

**Storage**: PostgreSQL via Flyway `V17__create_leave_tables.sql`: `leave_type` (seeded),
`leave_request` (with a `btree_gist` exclusion constraint on live requests per Teacher), and
`attendance_mark.leave_request_id` / `attendance_mark_history.leave_request_id` (nullable). No
cross-module foreign keys except the attendance column, which is a plain id (no FK) per the
house rule.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`); per-role/per-action
authorization tests on every endpoint; two-Manager scope-boundary tests on list, detail and every
decision; a table-driven unit suite for working-day and half-day counting; approval-feed tests
(replace Teacher mark, refuse supervisor-set day, refuse locked month, future days, revoke leaving
hand-edited days); a concurrency test (two approvers, approver vs Teacher cancel, two overlapping
submissions); ArchUnit rules for `leave`. Frontend: Vitest + Testing Library with role fixtures and
axe in both themes.

**Target Platform**: Browser (desktop, tablet, phone); single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: A Leave Management page (25 rows) in under 1 s with 5,000 requests; approval
of a 90-day request in under 2 s. Lists are paged and filtered in SQL; scope comes as an id set.

**Constraints**: Permission **and** data scope enforced server-side using only
`TeacherScopeQueries` (never its own scoping); out-of-scope requests are 404. Business dates use the
Asia/Kolkata calendar date through the attendance business calendar. Approval, revoke and
Teacher-cancel are all-or-nothing in one transaction and serialize on the same Teacher-month
advisory locks as attendance writes (taken in month order to avoid deadlocks).

**Scale/Scope**: Low thousands of Teachers, a handful of requests per Teacher per year; one module,
2 tables, ~10 endpoints, three screens.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Operational Truth Must Be Auditable)** - PASS. Apply, cancel, approve, reject and
  revoke publish `EntityChanged` (actor, time, before, after, reason); each leave-made mark is also
  written to the append-only mark history with the request id.
- **Principle II (Five Fixed Roles, Configurable Permissions)** - PASS. No new role; modules
  `LEAVE_MANAGEMENT` and `MY_LEAVE` seeded to the spec's table, runtime-editable, with eligibility
  keeping System out.
- **Principle III (Data Scope)** - PASS. Scope only from `TeacherScopeQueries`; Teacher self-service
  is by the signed-in user's own Teacher record; System sees nothing.
- **Principle IV (Role-Based Experience)** - PASS. Server-driven navigation (new LEAVE section,
  OPERATIONS item), consistent loading/empty/error states, DD/MM/YYYY dates, icon + tooltip row
  actions, keyboard- and screen-reader-usable forms and grids.
- **Principle VII (Modular Monolith)** - PASS. `leave` reads only `attendance.api`,
  `teacher.api`; attendance exposes a new narrow named interface instead of leave reaching into its
  tables; ArchUnit rules extended.
- **Principle IX (Reliability, Testability)** - PASS, requires the tests listed above.
- **Principle X (Security, Identity, Observability)** - PASS. Backend is the control; reasons and
  notes are length-limited and rendered as text; no secrets; structured logs without PII.
- **Principles V, VI, VIII, XI** - not applicable now (V: payroll later reads L marks via
  `AttendanceReadApi`; VIII: no batch processing).

No violations requiring justification. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-leave/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── leave-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/
├── identity/
│   ├── permissions/PermissionModule.java        # + LEAVE_MANAGEMENT, MY_LEAVE
│   ├── permissions/PermissionEligibility.java   # LEAVE_MANAGEMENT: not System/Teacher; MY_LEAVE: Teacher only
│   ├── permissions/PermissionMatrixService.java # + seed defaults per spec.md table
│   └── accessmodel/{NavigationCatalog,AccessModelService}.java  # + LEAVE section, OPERATIONS item, scopes
├── audit/support/AuditVisibility.java           # + LEAVE_REQUEST -> LEAVE_MANAGEMENT VIEW
├── attendance/
│   ├── api/LeaveAttendance.java                 # new named-interface contract (+ records)
│   ├── internal/LeaveAttendanceImpl.java        # working days, check, apply, remove
│   ├── internal/AttendanceMark.java             # + leaveRequestId; MarkHistoryEntry + leaveRequestId
│   └── internal/MarkService.java                # setMark clears leaveRequestId; setLeaveMark bypass of the future/window rule
└── leave/
    ├── api/                                     # (reserved; nothing public needed yet)
    ├── internal/                                # LeaveRequest, LeaveType, repositories, LeaveRequestService,
    │                                            #   LeaveDecisionService, LeaveScope, LeaveAudit, LeaveDevSeeder
    └── web/                                     # MyLeaveController, LeaveManagementController, LeaveExceptionAdvice

backend/src/main/resources/db/migration/V17__create_leave_tables.sql
backend/src/test/java/com/hls/leave/             # counting, apply rules, decisions, feed, scope, concurrency, authorization

frontend/src/features/leave/
├── ApplyLeavePage.tsx, MyLeaveHistoryPage.tsx            # Teacher
├── LeaveManagementPage.tsx, LeaveDecisionDialog.tsx      # Manager / Admin / Director
├── LeaveStatusChip.tsx, leaveApi.ts, leaveDates.ts
frontend/src/App.tsx                                      # + guarded routes /leave/apply, /leave/history, /operations/leave
```

**Structure Decision**: Web application (unchanged layout). One new backend module with the
`api` / `internal` / `web` split, one migration, additive changes to `identity`, `audit` and
`attendance`, and one new frontend feature folder.

### Delivery slices (for tasks.md, each independently demoable)

1. **Foundation**: permissions, eligibility, navigation, audit visibility, migration, module
   skeleton, leave types.
2. **Apply and history (US1, US2)**: working-day counting, validation, overlap constraint, own
   list, cancel Pending.
3. **Decide (US3)**: scoped list, approve/reject with locking and concurrency, Pending count.
4. **Attendance feed (US4)**: `LeaveAttendance`, leave marks, revoke, Teacher cancel of future
   approved leave.
5. **Audit, seeds and polish (US5)**: audit mappings, demo data, specs/docs/Postman, a11y.

## Complexity Tracking

No violations. Table intentionally omitted.

## Amendment A3: Loss-of-Pay leave type

One data migration, `V25__add_loss_of_pay_leave_type.sql` (V17 is not edited), and one public interface,
`leave.api.LeaveTypes` (implemented by `leave.internal.LeaveTypesImpl` over `LeaveRequestRepository` and
`LeaveTypeCatalog`). No endpoint, screen, permission or attendance change. Tests: `LeaveLossOfPayTest`.
