# Implementation Plan: Teacher Transfer and Replacement

**Branch**: `027-teacher-transfer-replacement` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

## Summary

A new `transfer` module (package `com.hls.transfer`, api / internal / web like the other modules) that owns movement
requests: raise (transfer or replacement), check, approve or reject, cancel, a daily "took effect" notifier, and the read
models for a Teacher's and a School's movement history. It never touches placement tables. Placement changes go through one
new narrow interface owned by `schoolbilling` (`PlacementMoves`), which reuses the position and overlap rules of the existing
`AssignmentService` and applies a whole move (one or two Teachers) in the caller's transaction. Two other modules get one
small interface each: `attendance` (check for non-leave marks and re-tag future leave days) and `substitution` (relocate or
cancel open requirements). A new permission module `TEACHER_TRANSFERS` (View, Create, Edit, Approve) is seeded for Admin,
Director and Manager with one OPERATIONS item "Transfers & Replacements". A React feature `features/transfers` provides the
list, detail, the two request forms and the history panels; the Teacher list gains Transferred and Replaced filters through a
port owned by `teacher`. See [research.md](research.md).

Read from the code, not the planning documents (research.md section 1): placements are `contract_assignment` rows in
`schoolbilling`, not the roadmap's `assignment`; the existing `assign` already schedules a future move, but there is no way to
end a placement without starting another and no way to move two Teachers as one step; attendance marks carry their School and
leave days approved ahead of time are real marks that would keep the old School; open substitution requirements are unique per
(Teacher, date), so "cancel and raise again" is done as a relocation of the same row.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers in tests), React + Vite + Vitest.
- Migration: `V56__create_teacher_movement.sql`. Current max on origin/main is V53; 026 takes V60; V54 to V59 are left for
  015, 028, 035 and this spec. Pick the next free number at implementation if another branch merged first.
- Constraints: append-only `movement_request_history` (trigger refuses UPDATE and DELETE), no cross-module foreign keys,
  audit through `ChangeRecorder`, business date through `BusinessDate`, optimistic `version` on the request, 404 for anything
  out of scope, all-or-nothing approval in one transaction.
- Scale: under 100 users, dozens of requests a month; indexes on status, from_school, to_school, teacher and effective date
  are enough.
- No scheduler dependency for correctness: "Applied" is derived from the date. One small daily job sends the "took effect"
  notice and closes requests that went stale.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Every raise, change of date, approve, reject, cancel, apply and stale-close goes to `movement_request_history` (append-only, DB trigger) and to Change History through `ChangeRecorder` with before and after values. Placement rows are only added or end-dated; a cancelled scheduled move is marked CANCELLED, never deleted (FR-009). |
| II Five roles, RBAC | New module `TEACHER_TRANSFERS` with View, Create, Edit (cancel) and Approve (approve, reject). Seeded Admin: View, Create, Edit; Director and Manager: all four. `PermissionEligibility` makes Approve ineligible for Admin (D5) and everything ineligible for Teacher and System. |
| III Data scope | Director and Admin org-wide. A Manager reads a request when the leaving or the receiving School is in their scope (`ScopeQueries`) and raises or decides only for the leaving School. Out of scope answers 404. A Teacher has no endpoint. The same filter drives lists, history panels and the Teacher filter port. |
| IV Role-based UI | One OPERATIONS item from the navigation catalog; buttons follow the grants and a server-sent `actions` list on each request (approve, reject, cancel, change date), so a requester never sees Approve on their own request. |
| V Formula-driven | Not touched: no pay, receivable or margin is computed (FR-018). The move only keeps dated, non-overlapping, per-position placement rows that 013a, 013b and 022 already read. |
| VI Training and substitution | A Teacher in training cannot be moved or named as replacement. Open substitution requirements are relocated or cancelled through `substitution.api`, never by touching its tables. |
| VII Modular monolith | `transfer` reads `teacher.api`, `schoolbilling.api` (new `PlacementMoves`), `school.api`, `organization.api`, `attendance.api` (new `PlacementAttendance`), `substitution.api` (new `PlacementSubstitution`) and `identity`. `teacher` reaches movement data only through its own port `TeacherMovementSource`, implemented by `transfer` (the same inversion as `TeacherPlacementSource`). Only `notification` listens to `transfer.api` events. ArchUnit and Modulith verification enforce it. |
| VIII Concurrency | Request path only, optimistic version (second decider gets 409). Placement writes rely on the existing exclusion constraints and the existing "try again" translation of lock conflicts. The daily job is sequential and small. |
| IX Testability | Unit tests for the checks and the status derivation; API tests per endpoint, role and scope boundary; integration tests for the day-by-day invariants (SC-001, SC-002) over today, future, month-end and year-boundary dates; ArchUnit; frontend tests for grants. |
| X Security | Every endpoint re-checks grant and scope on the server; self-decision and Admin decision refused server-side; free text capped at 1000 characters and escaped on output; no identifiers leak across scope. |

Constitution change: PATCH bump (the next patch after whichever of 026 and 027 merges first), a Transfers & Replacements row in
the default access matrix and in the OPERATIONS navigation. No principle changes. Gate result: pass before research and after
design, no violations.

## Project Structure

```text
specs/027-teacher-transfer-replacement/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/transfer/
  api/       TransferRequested, TransferDecided, TransferApplied (events), package-info
  internal/  MovementRequest, MovementRequestHistory, repositories, MovementService (raise, change date, approve, reject,
             cancel), MovementChecks (shared by raise, approve and the dry run), MovementQueries (list, detail, Teacher and
             School history, status derivation), MovementScope, MovementEffectJob (daily: notify applied, close stale),
             TeacherMovementSourceImpl, TransferDevSeeder
  web/       MovementController, MovementHistoryController, TransferExceptionAdvice
backend/src/main/java/com/hls/schoolbilling/api/PlacementMoves.java (+ MoveSpec, MoveCheck, AppliedMove)
backend/src/main/java/com/hls/schoolbilling/internal/PlacementMovesImpl.java (reuses AssignmentService rules)
backend/src/main/java/com/hls/teacher/api/TeacherMovementSource.java (+ list filter in TeacherService and TeacherController)
backend/src/main/java/com/hls/attendance/api/PlacementAttendance.java (+ implementation in attendance/internal)
backend/src/main/java/com/hls/substitution/api/PlacementSubstitution.java (+ implementation in substitution/internal)
backend/src/main/java/com/hls/notification/internal/TransferEventListener.java (+ MessageFactory texts, three types)
backend/src/main/java/com/hls/identity/ (PermissionModule, PermissionEligibility, NavigationCatalog, AccessModelService)
backend/src/main/resources/db/migration/V56__create_teacher_movement.sql
frontend/src/features/transfers/ (TransfersPage, RequestDetail, TransferForm, ReplacementForm, MovementHistory,
  transfersApi, __tests__); history panel and list filters added in features/teachers
postman/ (collection additions)
```

**Structure Decision**: one new backend module and one frontend feature; mobile is out of scope (spec). The three small
interface additions in existing modules are the only changes to their APIs; existing behaviour (direct placement, exit,
attendance, leave, payroll) is unchanged (SC-007).

## Design Decisions

See [research.md](research.md). Highlights: placement writes stay in `schoolbilling` behind `PlacementMoves` (check and apply
share one code path); approval writes the placement rows at once, even for a future date (rows are dated, so a future start is
a scheduled move), so later marks and leave pick up the right School with no scheduler; the request keeps the ids of the rows it
created, so "Applied" is derived from the date and a direct change that cancels those rows is detectable; existing future leave
marks are re-tagged and open substitution requirements relocated at approval in the same transaction; the outgoing Teacher of a
replacement becomes available by ending the row the day before, which needs no new status.

## Phases

1. Migration, entities, permission module, navigation, scope rules, `PlacementMoves` check and apply (core of US1).
2. Transfer: raise, dry-run check, approve, reject, cancel, change date, notifications (US1).
3. Replacement: candidates, same-position apply, outgoing destination, all or nothing (US2).
4. Attendance and substitution interfaces; day-by-day invariants; locked months (US3).
5. Movement history, Teacher list filters, list and detail reads, daily effect job (US4, US5).
6. Frontend, Postman, demo data, roadmap and constitution rows.

## Open Items (need a decision, not guessed)

1. **Outgoing Teacher of a replacement with no destination, and approved leave after the date.** Plan default: those leave
   days stay with the old School (the Teacher is unplaced, so nothing is paid or billed for them either way). Confirm, or say
   they should be removed.
2. **A direct placement made after approval.** Plan default: the direct route stays unrestricted (spec Assumption). If it
   cancels or replaces a scheduled move created by an Approved request, the request is closed as Cancelled with "Teacher no
   longer placed there". The alternative is to refuse a direct change for a Teacher who has an Approved future request; that
   changes the existing placement action (SC-007), so it is not done without a decision.
3. **Link to spec 026 referrals.** 026 will expose `PerformanceReferrals.reportDecision`. This plan does not call it (the spec
   says no link yet); a later task can add the call from approve once 026 is built.
4. **Migration number** V56 is a placeholder inside the gap 026 left; re-check at implementation against merged branches.
