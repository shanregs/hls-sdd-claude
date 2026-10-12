# Implementation Plan: Substitution Workflow

**Branch**: `017a-substitution` | **Date**: 2026-10-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017a-substitution/spec.md`

## Summary

Adds the `substitution` bounded context (Constitution Principle VII) and amendment A2 to `attendance`.

- **008 A2 (attendance)**: two public events, `TeacherAbsenceRecorded` and `TeacherAbsenceWithdrawn`, published inside the
  mark transaction when a day enters or leaves the leave category; a `loss_of_pay` flag on each mark, set at write time (true
  for an absence with no leave request and for a day made by a Loss-of-Pay leave request); a public read interface
  `attendance.api.LossOfPayDays`; and an optional Principal confirmation on a mark (`PUT/DELETE
  /api/v1/attendance/teachers/{id}/marks/{date}/principal-confirmation`), audited, refused in a locked month.
- **substitution module**: `substitution_requirement` (one per Teacher and date) and an append-only
  `substitution_history`. A listener on the attendance events raises, updates, reopens or cancels the requirement. A
  service assigns, reassigns, releases, records Manager cover and records School informed, each with a version check, an
  audit entry and a history row. `AvailabilityService` decides who is free. `substitution.api.SubstituteCredits` is the
  public read interface of live credits for 017b.
- **notification**: four new types, listeners in `notification.internal` for the substitution events (publisher stays
  ignorant of notification, as with leave).
- **Access**: permission keys `SUBSTITUTION` (VIEW, EDIT) and `MY_SUBSTITUTIONS` (VIEW) seeded by the matrix service; menu
  items through `NavigationCatalog`; scope by School for Managers, by Teacher for the Teacher.
- **Screens**: OPERATIONS → Substitutions (list, detail with candidates and actions) and MY ATTENDANCE → My
  Substitutions.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1-based); TypeScript 5.7 with React 19. Unchanged.

**Primary Dependencies**: Spring Web, Spring Data JPA, Spring Security, Spring Modulith, Flyway, ArchUnit. Frontend: React
Router, MUI. No new dependency.

**Storage**: PostgreSQL, one migration `V48` (a gap of about 20 above the current `V26`, so work built in parallel does not
collide): adds columns to `attendance_mark`, creates `substitution_requirement` and `substitution_history`, widens the
notification type check. Details in [data-model.md](./data-model.md).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (`IntegrationTestBase`). Pure rule tests for availability. API tests
per role on every endpoint (Admin, Director, in-scope Manager, out-of-scope Manager, Teacher, System). Event tests on the
attendance side (absence, leave, half day, correction, clear, repeat). Credit-interface tests. Audit-entry assertions.
ArchUnit/Modulith rules (`SubstitutionModuleRulesTest`). Existing attendance, leave and notification suites stay green.
Frontend: Vitest + Testing Library with a fixture per role.

**Target Platform**: Browser; single Spring Boot deployable.

**Project Type**: Web application (frontend + backend).

**Performance Goals**: candidates for a requirement load in under 1 s (one query for active Teachers, one for placements,
one for marks that date, one for substitutions that date, one calendar lookup); the credit interface has a bulk form with a
fixed query count.

**Constraints**: no dependency on spec 025 (tasks) or on 013a; no cross-module foreign keys; mark writes must not fail
because of a substitution error that is not a data error.

## Constitution Check

| Principle | How this plan meets it |
| --- | --- |
| I Auditable | Every requirement change and every Principal confirmation goes through `ChangeRecorder`; status history is append-only (trigger). |
| II RBAC | Keys `SUBSTITUTION`, `MY_SUBSTITUTIONS` in the matrix, eligibility-restricted by role; server-side `PermissionGuard` on every endpoint. |
| III Data scope | School scope for a Zone Manager from `SchoolScopeProvider`; Teacher own; System excluded (business module). Out of scope reads as not found. |
| IV Navigation | Menu items from `NavigationCatalog` by grant, not hard-coded. |
| V/VII Modular monolith | `substitution` uses only `api` packages of attendance, teacher, school, organization; nobody uses `substitution.internal`; notification listens to substitution events. |
| VIII Concurrency | Optimistic version on the requirement; unique `(teacher_id, duty_date)` makes duplicate events idempotent; assignment re-reads the substitute's day total under the requirement lock. |
| IX Testing | Tests listed in tasks.md before the code of each story. |

## Project Structure

```text
specs/017a-substitution/            spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/ checklists/
backend/src/main/java/com/hls/
  attendance/api/        TeacherAbsenceRecorded, TeacherAbsenceWithdrawn, LossOfPayDays, LossOfPayDay
  attendance/internal/   mark flag + principal confirmation + event publishing; LossOfPayDaysImpl; PrincipalConfirmationService
  attendance/web/        PrincipalConfirmationController
  substitution/api/      SubstituteCredits, SubstituteCredit, SubstitutionNeeded, SubstitutionAssigned, SubstitutionEnded, SchoolInformed
  substitution/internal/ entities, repositories, AbsenceListener, SubstitutionService, AvailabilityService, SubstitutionScope, SubstituteCreditsImpl
  substitution/web/      SubstitutionController, MySubstitutionController
  notification/internal/ SubstitutionEventListener, MessageFactory additions
  identity/...           PermissionModule, PermissionEligibility, PermissionMatrixService, AccessModelService, NavigationCatalog (one entry each)
backend/src/main/resources/db/migration/V48__create_substitution.sql
frontend/src/features/substitution/  SubstitutionsPage, SubstitutionDetailPage, MySubstitutionsPage, substitutionApi.ts, tests
```

## Complexity Tracking

| Choice | Why | Simpler alternative rejected |
| --- | --- | --- |
| Synchronous event listeners | same transaction: no requirement without a committed mark, no phantom requirement | `@ApplicationModuleListener` (after commit) leaves a window with a mark and no requirement and needs the event registry to retry |
| Credit derived from the requirement, no credit table | one source of truth; voiding is a status change | a second table needs keeping in step |
| Shared-file edits are one entry each | other specs are built in parallel | refactors of `Map.of`, nav lists would conflict |
