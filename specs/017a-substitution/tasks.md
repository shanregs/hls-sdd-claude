---

description: "Task list for feature implementation"
---

# Tasks: Substitution Workflow

**Input**: Design documents from `/specs/017a-substitution/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/. Specs 005, 008, 009 (with A3), 010 and 012 are
merged. 008 amendment A2 is built here (Phase 2).

**Tests**: first-class tasks (Constitution Principle IX): per-role tests on every endpoint, event tests, audit tests,
ArchUnit rules. Run one Maven test class at a time.

**Format**: `[ID] [P?] [Story] Description`. Backend `backend/src/main/java/com/hls/...`; tests `backend/src/test/java/com/hls/...`.

## Phase 1: Setup

- [X] T001 Verify the building blocks listed in research.md section 1 exist; record the Flyway number used (V48) in plan.md
- [X] T002 Create `substitution/{api,internal,web}` with `package-info.java` (`api` a `@NamedInterface`) and `SubstitutionModuleRulesTest` (ArchUnit: nobody uses `substitution.internal/web`; substitution uses only `api` packages of attendance, teacher, school, organization; attendance, leave, teacher, organization do not depend on substitution)

## Phase 2: Foundational - 008 amendment A2 and shared wiring

- [X] T003 `V48__create_substitution.sql`: mark columns, both substitution tables, append-only trigger, notification check, backfill of `loss_of_pay`
- [X] T004 [P] Tests first: `AbsenceEventTest` (absent, leave, half day, repeat write, correction to Present, clear, revoke, Present/Holiday publish nothing), `LossOfPayFlagTest` (absence true, Casual leave false, Loss-of-Pay leave true), `LossOfPayDaysTest` (month, range, bulk query count)
- [X] T005 Attendance: `loss_of_pay` on `AttendanceMark`, set in `MarkService` for supervisor/self marks (category LEAVE and no leave request) and `setLeaveMark` (flag passed by caller); `LeaveAttendance.apply` overload with the flag and the leave module passes the Loss-of-Pay type result; `MarkView` shows `lossOfPay`
- [X] T006 Attendance: events `TeacherAbsenceRecorded` and `TeacherAbsenceWithdrawn`, published from `MarkService` on set, leave mark, clear and leave clear; `LossOfPayDays` interface and implementation
- [X] T007 [P] Tests first: `PrincipalConfirmationTest` (record, change, clear, locked month refused, scope, audit, Teacher refused, mark status change clears it)
- [X] T008 Attendance: Principal confirmation columns, `PrincipalConfirmationService`, `PrincipalConfirmationController`, audit
- [X] T009 Identity wiring: `PermissionModule.SUBSTITUTION/MY_SUBSTITUTIONS`, eligibility, matrix seed, access-model data scope, two `NavigationCatalog` items, audit visibility entity type; extend `PermissionEligibilityTest`
- [X] T010 Notification: types `SUBSTITUTE_NEEDED/ASSIGNED/ENDED/SCHOOL_INFORMED`, message texts, `SubstitutionEventListener`

## Phase 3: User Story 1 - Absence raises a requirement and the Manager is told (P1) 🎯 MVP

- [X] T011 [P] [US1] Tests: `RequirementRaisingTest` (one per Teacher-date, no duplicates, half day, leave days, cancel on withdraw, reopen, alert to Manager / to Admin and Director when no Manager, no second alert)
- [X] T012 [US1] Entities `SubstitutionRequirement`, `SubstitutionHistory`, repositories, `RequirementStatus`, audit helper
- [X] T013 [US1] `AbsenceListener` (raise, update, cancel) and publishing `SubstitutionNeeded`/`SubstitutionEnded`

## Phase 4: User Story 2 - Candidates and assignment (P1)

- [X] T014 [P] [US2] Tests: `AvailabilityServiceTest` (unplaced, placed on a working day, placed in a closed School, leave, already covering, half days), `AssignSubstituteTest` (assign, reassign, refuse unavailable, stale version, credit appears, absent Teacher none)
- [X] T015 [US2] `AvailabilityService`, `SubstitutionService.assign/release`, `SubstituteCredits` + implementation
- [X] T016 [US2] `SubstitutionController` list, detail, candidates, assign, release; `SubstitutionScope`

## Phase 5: User Story 3 - School informed, Manager covers (P1)

- [X] T017 [P] [US3] Tests: `InformAndCoverTest` (inform needs an outcome, cleared on change, cover has no credit, assign after cover), `SubstitutionAuthorizationTest` (every endpoint per role, scope as not found), `SubstitutionAuditTest`
- [X] T018 [US3] `SubstitutionService.cover/inform`, endpoints

## Phase 6: User Story 4 and 5 - My Substitutions and credits (P2)

- [X] T019 [P] [US4] Tests: `MySubstitutionsTest` (own only, absent name hidden, other id not found); `SubstituteCreditsTest` (month, range bulk, voided absent)
- [X] T020 [US4] `MySubstitutionController`

## Phase 7: Frontend

- [X] T021 [P] Vitest tests per role for list, detail (candidates, assign, cover, inform) and My Substitutions
- [X] T022 `substitutionApi.ts`, `SubstitutionsPage`, `SubstitutionDetailPage`, `MySubstitutionsPage`, routes in `App.tsx`

## Phase 8: Polish

- [X] T023 Dev seed is not needed (no demo data); Postman folder "Substitution"; roadmap rows 017 and 008 (A2); constitution access matrix row
- [X] T024 Full backend test run and frontend test, lint and build run; record in quickstart-results.md
- [ ] T025 Manual browser walkthrough and accessibility pass (not done in this run)
- [ ] T026 Postman folder "Substitution" (not done; endpoints are in contracts/substitution-api.md)

## Requirement coverage

| FR | Tasks |
| --- | --- |
| FR-001, FR-002 | T005, T006, T004 |
| FR-003 | T007, T008 |
| FR-004, FR-005 | T011, T013 |
| FR-006, FR-011 | T010, T011, T017 |
| FR-007, FR-008, FR-009 | T014, T015, T016 |
| FR-010 | T017, T018 |
| FR-012, FR-013 | T016, T017, T019, T021, T022 |
| FR-014 | T012, T017 |
| FR-015 | T015, T019 |
| FR-016 | no task dependency; checked by the module rules test T002 |
