# Implementation Plan: Daily Attendance Capture & Monthly Rollup

**Branch**: `011-attendance` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-attendance/spec.md`

## Summary

Replaces the "STAFF ATTENDANCE REGISTER" spreadsheet with a new `attendance` bounded-context module: Teachers mark their own daily attendance from the mobile app (optionally with geo-tag/photo/check-in-code evidence), or a Manager marks it on their behalf for a Teacher currently accountable to them (User Stories 1-2); every mark carries a fractional value (half-day support) and a `schoolId` tag, and is fully attributable via the `audit` module (FR-006), with no lower bound on how far back a still-unlocked date can be corrected (FR-023). A live-computed, per-teacher monthly rollup (training days total vs. attended, days worked, days leave, overall working days, weighted attendance total — User Story 3) is derived from marks **and** a shared, Admin-configured Non-Working Calendar at read time, never cached — a calendar date counts as non-working for every Teacher automatically, unless that Teacher has an explicit mark of their own for it (FR-022, research.md §10). Once a Director locks a teacher-month (standing in for a future `payroll` module's run, which doesn't exist yet — research.md §2), further direct edits are rejected; a Director-only reopen/correction workflow (User Story 4) is the only path back to an editable state, fully recorded — lock and reopen share the same Director-only authority boundary, per Constitution Principle II (research.md §2 amendment). A Director/Manager/Admin can also view a month's attendance across their whole team as a single Teachers-×-days grid (User Story 5), the same shape as the register it replaces — and a Manager (their own portfolio) or Admin (any Teacher, newly unscoped — FR-024) can edit a cell directly from that grid (FR-025), through the exact same write path as US1/US2 (research.md §9), so there's no second place attendance data can be changed. Four real forward-reference/missing-capability gaps surfaced during planning — no "current School assignment" data exists yet, no training calendar exists yet, no "list of Teachers a Manager/Director can see" query existed before this feature needed one, and no shared non-working-day calendar existed anywhere in the system — are resolved with documented, narrower stand-ins or a small new owned entity (research.md §1/§3/§8/§10), the same pattern this codebase already used for Organization's interim Teacher→Manager assignment.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml` (Spring Boot 4.1.1, Spring Modulith, Spring Data JPA, Spring Security/OAuth2 Resource Server). **No new dependency additions.**

**Storage**: PostgreSQL — five new tables (`attendance_status_code`, `attendance_mark`, `attendance_teacher_month_lock`, `attendance_reopen_record`, `attendance_non_working_date`) — migration `V9__create_attendance_tables.sql` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher Salary History=V7, Zone-Manager Assignment=V8). All five tables ship in this one migration since none of them have been created on disk yet (research.md §10 — no need for a separate `V10`).

**Testing**: JUnit 5 + Spring Boot Test — new `AttendanceServiceTest` (unit: marking, editing, rollup arithmetic, lock/reopen transitions) and `AttendanceIntegrationTest` (Testcontainers, real HTTP + real JWT, reusing the established viewing-permission-reuse pattern from `teacher`/specs/009).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — two new frontend pages: `MyAttendancePage` (Teacher self-marking + own rollup) and `AttendancePage` (Manager/Director: mark on behalf, view a Teacher's marks/rollup, lock/reopen, manage status codes).

**Performance Goals**: No specific throughput target given this project's scale (Additional Constraints: under 100 users, ~30%/yr growth). Rollup computation is a single indexed range query (`teacherId` + `markDate` between month bounds) aggregated in memory — not a scan-heavy operation even at full scale.

**Constraints**: FR-012 (locked teacher-month rejects direct edits) enforced at the service layer by checking `AttendanceTeacherMonthLock` status before any create/update, inside the same transaction as the write (fail closed). FR-010 (surface unmarked days, never silently default them) satisfied by computing `unmarkedDays` as its own rollup field rather than folding gaps into any status bucket (data-model.md). FR-023 (no backdating limit other than the lock) enforced by simply having no lower-bound check on `markDate` at all — only the upper bound (future dates beyond the current teacher-month) and the lock check apply. FR-009/FR-010's calendar-aware rollup requires iterating every calendar day in the month (not only days with a mark) to resolve each day's effective category (research.md §10) — still a single indexed range query plus a small calendar-table lookup, not a scan.

**Scale/Scope**: One new module (`com.hls.attendance`), 5 new tables, 13 new REST endpoints (list/create status codes, self-mark, mark-on-behalf/Admin/grid-edit — one shared endpoint, get rollup, list marks, lock, reopen, attendance grid, list/add/deactivate non-working dates), one small additive extension to `teacher.api` (`findAll()`), 2 new frontend pages. The mark-on-behalf endpoint now serves three callers (Manager-scoped, Admin-unscoped, and the grid's inline edit) rather than one.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Every mark create/edit and every lock/reopen/re-lock calls `audit.api.AuditWriter.record(...)` with actor, timestamp, and before/after (FR-006, FR-013) — the same in-transaction pattern `teacher` already established. |
| II. Role-Scoped Access and Manager Ownership | Yes | Manager-marks-on-behalf (FR-002) and viewing (FR-015) both reuse `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` unchanged — no new scoping logic invented, and no new dependency on `organization.api` beyond §8's portfolio listing (research.md §7). Admin's new unscoped on-behalf marking (FR-024) mirrors Admin's existing unscoped write authority over Teacher master data (specs/005) — not a new kind of access, the same role doing the same kind of thing to a new entity. Lock (FR-011) and reopen (FR-014) authority are both restricted to Director, consistent with Principle II reserving payroll-approval-adjacent authority away from Admin at both ends of the cycle (research.md §2 amendment, `/speckit-analyze` finding C1/I1 — originally scoped lock to Director-or-Admin, narrowed on review); Director deliberately gets no new marking/editing authority from FR-024/FR-025 (research.md §9). |
| III. Payroll, School Receivables, and Margin Must Be Formula-Driven | Yes | Attendance doesn't compute payroll itself, but FR-017 makes the monthly rollup the authoritative, formula-derived (not spreadsheet-re-entered) input a future `payroll` module reads — directly serving this principle's intent ahead of `payroll` existing. The rollup formula itself is now calendar-aware (FR-009/FR-022, research.md §10), removing the spreadsheet's manual per-teacher holiday adjustment entirely. |
| IV. Training and Substitution Are Part of the Same Operating Model | Yes | Training-day is a first-class status category (not a footnote), weighted the same as Present by default (research.md §6) — attendance treats training as equal-footing operational data, per this principle, even though the full training calendar arrives in a later module. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | New `com.hls.attendance` package (api/internal), depends on `identity.api`, `audit.api`, `teacher.api` (`TeacherQueries`, extended with `findAll()`), and — since User Story 5's grid needs to enumerate Teachers, not just check one at a time — `organization.api.AccountabilityQueries.portfolioForManager(...)` directly (research.md §8); never reaches into `organization.internal`/`school.internal`. The Non-Working Calendar (FR-022) introduces no new cross-module dependency at all — it's genuinely new data nothing else owns, so `attendance` owns it outright (research.md §10). `ArchitectureTest` gains an `attendance`-internals guard and joins the no-cross-module-dependency list; `AttendanceModuleTest` verifies isolated boot with `ALL_DEPENDENCIES` (research.md §7/§8). |
| VI. Concurrency Uses Structured, Virtual-Thread Batch Processing | Partially | Locking a teacher-month (FR-011) is triggered on demand by a Director, per this principle — but at this feature's scope (one teacher-month per lock call) there's no batch fan-out needing virtual threads yet; that applies once a real `payroll` module locks many teacher-months in one run (research.md §2) — not introduced here. |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Every FR has a corresponding unit + integration test planned (quickstart.md); rollup arithmetic (the calculation-heavy part) gets dedicated unit test coverage before the lock/reopen workflow is layered on top. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth and `@AuthenticationPrincipal Jwt` pattern every other controller in this codebase already uses — no new auth mechanism. |
| IX. Recruitment and Marketing Are Tracked to Outcome | No | Not applicable. |
| Additional Constraints | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 / Docker Compose unchanged. Offline mobile capture (FR-016) and export (FR-018) are named explicitly as constraints this feature must satisfy. |

**Gate result**: PASS. Principle VI is a partial/forward-looking fit (documented above), not a violation — no Complexity Tracking entry needed since nothing here contradicts the principle, it simply doesn't yet reach the batch-fan-out scale the principle anticipates.

**Post-design re-check (after Phase 1)**: No new principle concerns surfaced by data-model.md or contracts/attendance-api.yaml. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/011-attendance/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── attendance-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/hls/attendance/
│   │   │   ├── api/
│   │   │   │   ├── AttendanceMarkCommands.java        # NEW: markAttendance(...)
│   │   │   │   ├── AttendanceMarkQueries.java          # NEW: marksForMonth, markForDate
│   │   │   │   ├── AttendanceRollupQueries.java         # NEW: rollupForMonth
│   │   │   │   ├── AttendanceLockCommands.java           # NEW: lockMonth, reopenMonth
│   │   │   │   ├── AttendanceLockQueries.java             # NEW: lockStatus
│   │   │   │   ├── AttendanceStatusCodeCommands.java       # NEW: createStatusCode
│   │   │   │   ├── AttendanceStatusCodeQueries.java         # NEW: listActiveCodes
│   │   │   │   ├── AttendanceGridQueries.java                # NEW: gridForManager, gridForAllTeachers (US5)
│   │   │   │   ├── AttendanceNonWorkingCalendarQueries.java   # NEW: datesForMonth (FR-022)
│   │   │   │   ├── AttendanceNonWorkingCalendarCommands.java   # NEW: addNonWorkingDate, deactivateNonWorkingDate
│   │   │   │   ├── AttendanceMonthLockedException.java      # NEW
│   │   │   │   ├── UnknownAttendanceStatusCodeException.java # NEW
│   │   │   │   └── dto/
│   │   │   │       ├── AttendanceMarkView.java                 # NEW
│   │   │   │       ├── MarkAttendanceRequest.java               # NEW
│   │   │   │       ├── EvidenceInput.java                        # NEW
│   │   │   │       ├── AttendanceStatusCodeView.java               # NEW
│   │   │   │       ├── CreateStatusCodeRequest.java                 # NEW
│   │   │   │       ├── MonthlyAttendanceRollupView.java              # NEW
│   │   │   │       ├── LockStatusView.java                            # NEW
│   │   │   │       ├── ReopenEntry.java                                # NEW
│   │   │   │       ├── AttendanceGridView.java                          # NEW (US5)
│   │   │   │       ├── AttendanceGridRow.java                            # NEW (US5)
│   │   │   │       ├── GridCell.java                                      # NEW (US5) — + editable flag (FR-025)
│   │   │   │       ├── MarkedByRole.java                                   # NEW — TEACHER, MANAGER, ADMIN (FR-024)
│   │   │   │       ├── NonWorkingDateView.java                              # NEW (FR-022)
│   │   │   │       ├── AddNonWorkingDateRequest.java                         # NEW (FR-022)
│   │   │   │       └── package-info.java                                # NEW
│   │   │   └── internal/
│   │   │       ├── AttendanceStatusCode.java                              # NEW JPA entity
│   │   │       ├── AttendanceStatusCodeRepository.java                     # NEW
│   │   │       ├── AttendanceMark.java                                      # NEW JPA entity
│   │   │       ├── AttendanceMarkRepository.java                            # NEW — no delete method
│   │   │       ├── AttendanceTeacherMonthLock.java                           # NEW JPA entity
│   │   │       ├── AttendanceTeacherMonthLockRepository.java                  # NEW
│   │   │       ├── AttendanceReopenRecord.java                                 # NEW JPA entity
│   │   │       ├── AttendanceReopenRecordRepository.java                        # NEW
│   │   │       ├── AttendanceNonWorkingDate.java                                 # NEW JPA entity (FR-022)
│   │   │       ├── AttendanceNonWorkingDateRepository.java                        # NEW — deactivate, never delete
│   │   │       ├── AttendanceService.java                                        # NEW — implements all api interfaces
│   │   │       └── AttendanceController.java                                      # NEW — REST endpoints per contracts/
│   │   └── resources/db/migration/
│   │       └── V9__create_attendance_tables.sql   # V9 — see Storage above; includes attendance_non_working_date
│   └── test/java/com/hls/
│       ├── ArchitectureTest.java                          # EXTENDED — + attendance-internals guard, + cross-module list entry
│       └── attendance/
│           ├── AttendanceModuleTest.java                    # NEW — @ApplicationModuleTest(ALL_DEPENDENCIES)
│           ├── AttendanceServiceTest.java                     # NEW
│           └── AttendanceIntegrationTest.java                  # NEW

frontend/src/pages/
├── MyAttendancePage/
│   ├── MyAttendancePage.tsx        # NEW — Teacher self-marking form + own monthly rollup
│   ├── MyAttendancePage.test.tsx     # NEW
│   └── attendanceClient.ts             # NEW — markMyAttendance, getMyRollup, listStatusCodes
└── AttendancePage/
    ├── AttendancePage.tsx           # NEW — Manager: mark-on-behalf + a Teacher's rollup/marks;
    │                                  # Director-only: + lock/reopen (FR-011/FR-014, both Director-only);
    │                                  # Admin/Director: + status-code management (FR-005);
    │                                  # + non-working-calendar management (Admin-only, FR-022);
    │                                  # + AttendanceGrid component (US5), Director/Manager/Admin
    ├── AttendanceGrid.tsx              # NEW (US5) — datagrid: Teacher rows × day columns, status per
    │                                     # cell; Manager/Admin can inline-edit an `editable: true` cell
    │                                     # (FR-025), calling the same markOnBehalf client function
    ├── AttendancePage.test.tsx        # NEW
    └── attendanceAdminClient.ts         # NEW — markOnBehalf (Manager-scoped or Admin-unscoped, FR-024;
                                            # also the grid's inline-edit call), getRollup, listMarks,
                                            # lock, reopen, createStatusCode, getAttendanceGrid (US5),
                                            # listNonWorkingDates, addNonWorkingDate, deactivateNonWorkingDate
```

**Structure Decision**: New bounded-context module `com.hls.attendance`, following the exact `api`/`internal` split every prior module (`teacher`, `school`, `organization`, `audit`) already established — no changes to any existing module's source beyond `ArchitectureTest` (a required, additive extension every new module makes) and one small, additive extension to `teacher.api.TeacherQueries` (`findAll()`, research.md §8). Two new frontend pages rather than extending an existing one, since neither `TeacherProfilesPage` nor `MyProfilePage` currently has any attendance-shaped UI to extend; the grid (US5) is its own component within `AttendancePage` rather than a third page, since it's just another view over the same Director/Manager attendance surface. The Non-Working Calendar (FR-022) is a fifth table inside `attendance` itself, not a new module or an extension of any existing one — it's genuinely new data with no other rightful owner (research.md §10), unlike the School-assignment and Teacher-listing gaps §1/§8 resolved by borrowing from where that data already (or will eventually) live.

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
