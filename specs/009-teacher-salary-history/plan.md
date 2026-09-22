# Implementation Plan: Teacher Salary History

**Branch**: `feature/005-teacher-master-data` (continues the not-yet-merged Teacher module work) | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-teacher-salary-history/spec.md`

## Summary

Replaces `specs/005-teacher`'s plain, freely-overwritable `hlsOfferedSalary` field with a small, structured, append-only `TeacherSalaryHistory` table inside the same `teacher` module: the current salary stays the default, no-extra-step answer on a Teacher Profile view (User Story 3), but "what was the salary on date X" becomes a real, directly-answerable query (User Story 4) — the actual capability this feature exists for, anticipating a future Payroll module's need to recompute a past month's pay using the salary that actually applied then. Recording a salary change becomes its own Admin-only operation, separate from the general contact-detail update, since it carries an effective date the latter doesn't need.

## Technical Context

**Language/Version**: Java 25 (unchanged)

**Primary Dependencies**: All already present in `backend/pom.xml`. **No new dependency additions.**

**Storage**: PostgreSQL — one new table, `teacher_salary_history`, plus dropping `teacher_profile.hls_offered_salary` (research.md §2) — migration `V7__create_teacher_salary_history_table.sql` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6). A new migration, not an edit to `V6`, per this codebase's Flyway discipline (never modify an already-numbered migration after the fact — established across every prior module).

**Testing**: JUnit 5 + Spring Boot Test — extends `TeacherServiceTest` (new cases for salary recording/current/as-of, and updated cases wherever the old `hlsOfferedSalary`-on-profile behavior was previously asserted) and `TeacherIntegrationTest` (new cases for the two new endpoints, real HTTP + real JWT, same viewing-permission reuse as profile viewing). No new module test / ArchUnit rule needed (research.md §6).

**Target Platform**: Single EC2/VM in `ap-south-1`, Docker Compose — unchanged

**Project Type**: Web application (Option 2) — extends the existing `TeacherProfilesPage` with a salary-history section (record + as-of lookup) rather than a new page; `MyProfilePage` needs no change (it already shows "current salary," which continues to work unchanged from the caller's point of view).

**Performance Goals**: No specific throughput target given this project's scale. "Current salary" and "as of" are both single indexed queries (`teacherId` + `effectiveFrom` ordering), not a scan.

**Constraints**: FR-004 (never edited/deleted) enforced by a narrow repository interface with no update-by-id or delete method, the now-repeatedly-proven pattern. FR-007 (clear "not yet recorded," never an error or a misleading zero) is satisfied by the same `State`-enum "answer" pattern `SchoolZoneAnswer` already established (data-model.md).

**Scale/Scope**: One new entity/table, 2 new REST endpoints (record a salary change, get salary current-or-as-of), one dropped column, one changed request shape (`UpdateTeacherProfileRequest` loses `hlsOfferedSalary`), one frontend extension (no new page).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|---|---|---|
| I. Operational Truth Must Be Auditable | Yes | Every salary change still writes a real `audit.api.AuditWriter` entry (specs/005 FR-005, unchanged) *and* is now additionally queryable as structured, date-indexed history — strictly more auditable than before, not less. |
| II. Role-Scoped Access and Manager Ownership | Yes | Viewing salary reuses the exact same `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` checks already in `TeacherController` (FR-008) — no new scoping logic. Recording is Admin-only (FR-009), matching specs/005 FR-010's existing write restriction. |
| III–IV, VI, IX | No | Not applicable. |
| V. Architecture Is a Modular Monolith With Enforced Boundaries | Yes | Pure extension of the existing `teacher` module — no new package, no new dependency direction, no new `ArchitectureTest` rule needed (research.md §6). |
| VII. Reliability, Testability, and Incremental Delivery | Yes | Unit and integration tests for every FR, written before/alongside implementation. |
| VIII. Security, Identity, and Observability | Yes | Reuses the exact same JWT bearer auth every other `teacher` endpoint already uses. |
| Additional Constraints | Yes | Java 25 / Spring Boot 4.1.1 / PostgreSQL / single EC2 / Docker Compose unchanged. |

**Gate result**: PASS, no open exceptions.

**Post-design re-check (after Phase 1)**: No new principle concerns. Gate result unchanged: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/009-teacher-salary-history/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   └── teacher-salary-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                             # NO CHANGES
├── src/
│   ├── main/
│   │   ├── java/com/hls/teacher/
│   │   │   ├── api/
│   │   │   │   ├── TeacherQueries.java                  # existing, unchanged
│   │   │   │   ├── TeacherCommands.java                 # existing — updateProfile's request shape loses salary
│   │   │   │   ├── TeacherSalaryQueries.java             # NEW: currentSalary/salaryAsOf (FR-005/006/007)
│   │   │   │   ├── TeacherSalaryCommands.java             # NEW: recordSalaryChange (FR-003)
│   │   │   │   └── dto/
│   │   │   │       ├── TeacherProfileView.java             # existing, unchanged SHAPE (derivation changes)
│   │   │   │       ├── UpdateTeacherProfileRequest.java     # CHANGED: drops hlsOfferedSalary
│   │   │   │       ├── TeacherSalaryHistoryView.java          # NEW: id, teacherId, amount, effectiveFrom
│   │   │   │       └── SalaryAsOfAnswer.java                   # NEW: State{RECORDED,NOT_YET_RECORDED} + amount
│   │   │   └── internal/
│   │   │       ├── TeacherProfile.java                          # CHANGED: drops hlsOfferedSalary field/mutator
│   │   │       ├── TeacherService.java                           # CHANGED: findById composes current salary in;
│   │   │       │                                                  # updateProfile no longer touches salary
│   │   │       ├── TeacherSalaryHistory.java                      # NEW JPA entity
│   │   │       ├── TeacherSalaryHistoryRepository.java              # NEW — extends bare Repository<>, no
│   │   │       │                                                     # delete/update method (FR-004)
│   │   │       └── TeacherController.java                            # EXTENDED: + salary endpoints
│   │   └── resources/db/migration/
│   │       └── V7__create_teacher_salary_history_table.sql   # V7 — see Storage above
│   └── test/java/com/hls/teacher/
│       ├── TeacherServiceTest.java                      # EXTENDED — new + updated cases
│       └── TeacherIntegrationTest.java                    # EXTENDED — new cases

frontend/src/pages/TeacherProfilesPage/
├── TeacherProfilesPage.tsx     # EXTENDED — record-salary-change form, salary-as-of lookup;
│                                # update form drops the salary input (moved to its own form)
├── TeacherProfilesPage.test.tsx  # EXTENDED — new cases
└── teacherClient.ts                # EXTENDED — recordSalaryChange/getSalary calls;
                                     # UpdateTeacherProfileRequest type drops hlsOfferedSalary
```

**Structure Decision**: Pure extension of the existing `teacher` module and `TeacherProfilesPage` — no new bounded-context package, no new frontend page, no `ArchitectureTest`/`TeacherModuleTest` changes needed. `MyProfilePage` and its client call are untouched (the `/teachers/me` response shape is unchanged).

## Complexity Tracking

*(none — no Constitution Check violations to justify)*
