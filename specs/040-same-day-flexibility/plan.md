# Implementation Plan: Same-Day Flexibility Without Prior Approval

**Branch**: `040-same-day-flexibility` | **Date**: 2026-10-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/040-same-day-flexibility/spec.md`

## Summary

Four things that happen on the day, without waiting for anyone, and one pay rule change that goes with them. There is
**no new module, no new permission key and no new menu item**. One migration adds a `same_day` flag to four existing
tables; everything else extends the code that already owns each record.

- **Same-day leave (US1)**: `LeaveRequestService` gains a same-day path. A Teacher applies for today (existing
  `POST /api/v1/me/leave`); a Manager, Admin or Director records it for a Teacher in scope (new
  `POST /api/v1/leave/same-day`). The request is saved **Approved** in the same transaction, the day is marked L through
  the existing `LeaveAttendance.apply`, and the existing `LeaveRequested` / `LeaveDecided` events fire, so the Manager
  notification and the substitution requirement (spec 017a `AbsenceListener`) need no change.
- **A request that also covers later days (FR-005)** is stored as **two linked requests**: today's part Approved and
  marked same-day, the later part Pending under the normal flow. No new status; each part keeps its own state machine.
- **Manager cover (US2)**: `SubstitutionService.cover` already accepts a cover from `OPEN` with no prior step, keeps the
  cover user on the requirement and writes a `COVERED` history row. What is missing is the *record*: a Manager-cover
  list and counts, the "same-day" flag, and the label. No new table; the entry **is** the requirement in
  `MANAGER_COVER` plus its history (an entry "ends" when the status moves on, as spec 017a already does).
- **Same-day tasks and visits (US3)**: `TaskService.logSameDay` creates the Manager's own task directly as
  `IN_PROGRESS` or as final; `markDoneOnTheSpot` does the same for a task planned for today. "Final" means the existing
  `VERIFIED` status reached in one step (history rows `CREATED`, `COMPLETED`, `VERIFIED`, all by the Manager, note
  "same-day, auto-accepted"), so **Awaiting verification, which lists `COMPLETED`, never sees it**. Visits are the
  marketing activities of spec 023: `ActivityService.logSameDay` creates a `COMPLETED` activity for today.
- **Visibility (US4)**: the flag is on every record, in the central audit event detail, in a `sameDay` filter on the
  five lists, and in a per-Manager and per-School count report.
- **Pay (FR-003, amends 013a/013b)**: `UnpaidDayClassifier` is already Teacher-only through `teacherUnpaid`. For
  Teachers it now classes **every** LEAVE-category mark as unpaid, whatever the leave type; the Loss-of-Pay lookup is
  no longer read for them. Managers keep their own rule (spec 032).

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19 (web); React Native (mobile).

**Primary Dependencies**: Spring Web, Data JPA, Security, Modulith, ArchUnit; MUI; the mobile outbox engine of spec 036.
No new dependency.

**Storage**: PostgreSQL. **One migration, `V150__same_day_flags.sql`**: `same_day boolean not null default false` on
`leave_request`, `substitution_requirement` (set when the cover is recorded on the duty date), `task`,
`marketing_activity`; `client_key uuid null` (unique) on `task` and `marketing_activity`; `linked_request_id uuid null` on `leave_request` (the later part of a split); a partial index on
each `same_day` column. No data is rewritten; existing rows read as not same-day.

**Testing**: JUnit 5 + Testcontainers (`IntegrationTestBase`); pure unit tests for `UnpaidDayClassifier` and the split
rule. Frontend Vitest + axe; mobile Jest with the outbox fixtures.

**Target Platform**: web (all roles named in the spec) and the mobile Manager app (US3 only).

**Project Type**: web application plus mobile app, single Spring Boot deployable.

**Performance Goals**: same-day leave saved in under 1 s; the count report for a month under 2 s (a fixed number of
queries, one per module).

**Constraints**: "today" is `BusinessDate.today()` (Asia/Kolkata); guards of spec 009 are not bypassed except as stated in
research.md D4; the actor comes from the JWT; reads and writes enforce scope and permission per request; no write
without an audit event in the same transaction.

**Scale/Scope**: 6 new endpoints, 4 amended list endpoints, 1 report endpoint, 1 migration, about 6 screens touched
(web) and 2 forms (mobile).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I (Auditable)** - PASS. Auto-acceptance is exactly where auditability matters: every same-day record writes the
  actor, the person it is for, the time and "auto-accepted" through the existing audit writers (`LeaveAudit`,
  `SubstitutionAudit`, `TaskRecorder`, `ChangeRecorder`) and keeps the attendance mark history for a replaced Absent.
  No audit row is edited.
- **II (Fixed roles, configurable permissions)** - PASS. No new role or key. Recording uses the existing Approve (leave) or Create (substitution, tasks, visits) action of
  each module; the `sameDay` filter uses View. `PermissionGuard` is called in each controller as today.
- **III (Data scope)** - PASS. Manager-recorded leave and cover go through `LeaveScope` and `SubstitutionScope`; own
  tasks and visits are bound to the JWT user; a record outside scope is answered "not found".
- **IV (Role-based experience)** - PASS. Items and actions appear only when granted; loading, error and empty states;
  DD/MM/YYYY; WCAG 2.2 AA; no horizontal page scroll at 375 px. Menu items are unchanged (the spec adds none).
- **V (Formula-driven pay)** - PASS, and the reason for the care taken in D1: the deduction rule changes in one pure
  class, is covered by hand-computed test cases (SC-003), and the amount still comes from `PayRules`.
- **VI (Training and substitution)** - PASS. A Manager cover becomes a first-class, reportable substitution record.
- **VII (Modular monolith)** - PASS. Each change is inside the module that owns the record. Cross-module: `leave` calls
  `attendance` through `LeaveAttendance` (existing); `reporting` reads a new small public interface per module
  (`SameDayCounts`) and owns no data; nothing reads another module's tables.
- **VIII (Concurrency)** - request model. Two people recording the same Teacher's same-day leave at once are serialised
  by the existing overlap exclusion constraint (D5); no batch work.
- **IX (Reliability, testability)** - PASS. Tests first for: every role and scope boundary per endpoint, the split, the
  payroll classification, concurrency, offline replay, and the unchanged behaviour of non-same-day paths.
- **X (Security)** - PASS. Bodies validated; dates must equal today server-side (the client date is never trusted);
  an offline replay keeps the stored activity date but is re-checked against scope on arrival (D7).
- **XI (Recruitment and marketing)** - PASS. A same-day visit goes through the marketing `ActivityService`, so its
  outcome tracking is unchanged.

No constitution amendment. No violations; Complexity Tracking is not needed. **Post-design re-check**: unchanged, all
PASS. Two items to settle at `/speckit-analyze` are listed in research.md (D4 and D8).

## Amendments to earlier specs

| Spec | Change |
| ---- | ------ |
| 008 attendance | `LeaveAttendance.problems` gains a flag allowing an Absent mark set by a supervisor to be replaced by same-day leave (D4); the replaced value stays in the mark history. |
| 009 leave | A request may be created Approved (same-day); a request spanning today and later days is split into two linked requests (D2); a Manager, Admin or Director may record leave for a Teacher (new endpoint, same-day only). |
| 013a/013b payroll | Every Teacher leave day is unpaid at the payroll run, any type (D1). `lossOfPayRequests` is no longer read for Teachers. |
| 017a substitution | Manager cover list and counts; cover recorded on the duty date carries the same-day flag; Manager covers are labelled apart from Teacher credits. |
| 025 manager tasks | Same-day log and "done on the spot" skip acknowledgement and verification. |
| 026 observation | A same-day OBSERVATION satisfies the open observation due (`completeLinked`). |
| 023/033/034 visits and mobile | Same-day visit log (web and mobile), including the outbox. |

## Project Structure

### Documentation (this feature)

```text
specs/040-same-day-flexibility/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── same-day-api.md  # HTTP, web and mobile
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/V150__same_day_flags.sql         # NEW: the flags and linked_request_id

backend/src/main/java/com/hls/
├── leave/internal/
│   ├── LeaveRequestService.java        # + same-day path in submit(); split when the range runs past today
│   ├── SameDayLeaveService.java        # NEW: record for a Teacher in scope (Manager, Admin, Director)
│   ├── LeaveRequest.java               # + sameDay, linkedRequestId; static approvedSameDay(...)
│   ├── LeaveRequestRepository.java     # + same-day filter, counts by Manager and School
│   └── LeaveViewFactory.java           # + sameDay and "other part" in the view
├── leave/web/LeaveManagementController.java   # + POST /same-day, + sameDay query filter
├── attendance/internal/LeaveAttendanceImpl.java   # + replace-Absent flag in problems(...)/apply(...)
├── payroll/internal/UnpaidDayClassifier.java      # every LEAVE-category mark is unpaid (Teachers)
├── payroll/internal/PayRulesImpl.java             # teacherUnpaid no longer reads lossOfPayRequests
├── substitution/internal/SubstitutionService.java # cover(): same-day flag; + managerCovers(...) list
├── substitution/web/SubstitutionController.java   # + GET /manager-covers, + sameDay filter
├── tasks/internal/TaskService.java                # + logSameDay(...), markDoneOnTheSpot(...)
├── tasks/web/TaskController.java                  # + POST /same-day, POST /{id}/done-on-the-spot, sameDay filter
├── recruitment/marketing/internal/ActivityService.java   # + logSameDay(...) (Completed, today)
├── recruitment/marketing/web/ActivityController.java     # + POST /same-day, sameDay filter
├── activitycalendar/internal/CalendarEntry.java   # + sameDay (read from the sources)
└── reporting/                                     # + SameDayReportController and service (reads the SameDayCounts APIs)
    leave/api, substitution/api, tasks/api, recruitment/api   # + SameDayCounts (one small public interface each)

backend/src/test/java/com/hls/
├── leave/SameDayLeaveApiTest.java, SameDayLeaveSplitTest.java, SameDayLeaveConcurrencyTest.java
├── payroll/TeacherLeaveDeductionTest.java        # pure + integration, hand-computed months
├── substitution/ManagerCoverApiTest.java
├── tasks/SameDayTaskApiTest.java
├── recruitment/marketing/SameDayVisitApiTest.java
└── reporting/SameDayReportApiTest.java

frontend/src/features/
├── leave/ApplyLeavePage.tsx            # today-only request says "takes effect at once"; split notice
├── leave/RecordSameDayLeaveDialog.tsx  # NEW: Manager, Admin, Director record for a Teacher
├── leave/LeaveManagementPage.tsx       # + same-day chip and filter
├── substitution/…                      # + "I covered" on a requirement, Manager cover list, label, filter
├── tasks/LogSameDayDialog.tsx          # NEW; + "Done on the spot" on a planned task; filter
├── marketing/…                         # + "Log a visit for today"; filter
├── activityCalendar/…                  # + same-day chip and filter
└── dashboard or reports/SameDayReport.tsx   # NEW: counts per Manager and per School

mobile/src/
├── tasks/AddTaskForm.tsx, visits/PlanVisitForm.tsx   # + "Log for today" mode
└── outbox/syncSteps.ts                                # + same-day task and visit steps (idempotent, date kept)

docs/spec-roadmap.md                                   # + row 040
```

**Structure Decision**: web application plus mobile; every change lives in the module that owns the record. A single
cross-module "same-day" aggregate or table was rejected: the flag is a fact about each record and the report only needs
counts, so one boolean per table and a small counting interface per module is enough and keeps module boundaries.

## Complexity Tracking

Not needed; no constitution violations.
