---

description: "Task list for Teacher Salary History"
---

# Tasks: Teacher Salary History

**Input**: Design documents from `/specs/009-teacher-salary-history/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/teacher-salary-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice writes unit + integration tests alongside every service.

**Organization**: Tasks are grouped by user story (spec.md priorities: US1 = P1, US2 = P1, US3 = P1, US4 = P2). This is a small, additive extension of the already-implemented `teacher` module (specs/005-teacher) — no Setup phase is needed (no new package, no new module test).

**Correction note**: specs/005-teacher has not been merged to `main` — this task list corrects its not-yet-final salary design in place (removing `TeacherProfile.hlsOfferedSalary`, changing `UpdateTeacherProfileRequest`'s shape) rather than building a permanent workaround around already-shipped code (research.md §2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)

## Path Conventions

`backend/src/main/java/com/hls/teacher/`, `backend/src/test/java/com/hls/teacher/`, `frontend/src/pages/TeacherProfilesPage/`.

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: The `TeacherSalaryHistory` table/entity/repository/DTOs, and removing salary from `TeacherProfile`, that every user story builds on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T001 Create migration `backend/src/main/resources/db/migration/V7__create_teacher_salary_history_table.sql`: table `teacher_salary_history` with columns `id UUID PRIMARY KEY`, `teacher_id UUID NOT NULL`, `amount NUMERIC(12,2) NOT NULL`, `effective_from DATE NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL` (data-model.md — no delete/update column, never edited per FR-004); add index `ix_teacher_salary_history_teacher_id_effective_from ON teacher_salary_history (teacher_id, effective_from DESC, created_at DESC)` (research.md §5's tie-break ordering); in the same migration, `ALTER TABLE teacher_profile DROP COLUMN hls_offered_salary` (research.md §2 — a new migration, never an edit to `V6`, per this codebase's Flyway discipline)
- [X] T002 [P] Create `backend/src/main/java/com/hls/teacher/internal/TeacherSalaryHistory.java`: JPA entity, fields `id, teacherId, amount, effectiveFrom (LocalDate), createdAt, createdBy` (data-model.md), no setters
- [X] T003 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/TeacherSalaryHistoryView.java`: `record TeacherSalaryHistoryView(UUID id, UUID teacherId, BigDecimal amount, LocalDate effectiveFrom)`
- [X] T004 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/SalaryAsOfAnswer.java`: `record SalaryAsOfAnswer(State state, BigDecimal amount)`, `State{RECORDED,NOT_YET_RECORDED}`, static factories `recorded(amount)`/`notYetRecorded()` — mirrors `school.api.dto.SchoolZoneAnswer`'s pattern (FR-007)
- [X] T005 Create `backend/src/main/java/com/hls/teacher/internal/TeacherSalaryHistoryRepository.java`: `extends Repository<TeacherSalaryHistory, UUID>` (bare, not JpaRepository — FR-004 never-edited/deleted), exposing `save`, and `List<TeacherSalaryHistory> findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc(UUID teacherId)` (research.md §5's ordering) — no delete/update-by-id method
- [X] T006 Create `backend/src/main/java/com/hls/teacher/api/TeacherSalaryQueries.java`: interface with `SalaryAsOfAnswer currentSalary(UUID teacherId)` (FR-005) and `SalaryAsOfAnswer salaryAsOf(UUID teacherId, LocalDate date)` (FR-006/007)
- [X] T007 Create `backend/src/main/java/com/hls/teacher/api/TeacherSalaryCommands.java`: interface with `TeacherSalaryHistoryView recordSalaryChange(UUID teacherId, BigDecimal amount, LocalDate effectiveFrom, UUID actingUserId)` (FR-003 — `effectiveFrom` nullable, defaults to today), Admin-only (enforced by controller)
- [X] T008 Edit `backend/src/main/java/com/hls/teacher/internal/TeacherProfile.java`: remove the `hlsOfferedSalary` field, its constructor parameter, its getter, and the `updateSalary(BigDecimal)` mutator entirely (data-model.md — salary no longer lives on this entity)
- [X] T009 Edit `backend/src/main/java/com/hls/teacher/api/dto/UpdateTeacherProfileRequest.java`: remove the `hlsOfferedSalary` field — record becomes `(String name, String phone, String email)` only (research.md §3)

**Checkpoint**: `TeacherSalaryHistory` persists and is queryable; `TeacherProfile` no longer stores salary. User story implementation can now begin.

---

## Phase 2: User Story 1 - Admin Sets a Teacher's Initial Salary at Onboarding (Priority: P1) 🎯 MVP

**Goal**: FR-002 — the salary entered at profile creation becomes the first salary-history entry, effective from the creation date, immediately retrievable as both "current" and "as of the creation date."

**Independent Test**: Create a teacher profile with an initial salary; confirm it's retrievable both as current salary and as-of the creation date.

### Implementation for User Story 1

- [X] T010 [US1] Create `backend/src/main/java/com/hls/teacher/internal/TeacherService.java` implementations of `TeacherSalaryQueries`/`TeacherSalaryCommands` (same class as `TeacherQueries`/`TeacherCommands` — mirrors `ZoneService`/`PlaceService`'s single-class reasoning): `currentSalary`/`salaryAsOf` query `TeacherSalaryHistoryRepository.findByTeacherIdOrderByEffectiveFromDescCreatedAtDesc`, filter to `effectiveFrom <= (today | requested date)`, return the first match or `notYetRecorded()`
- [X] T011 [US1] Extend `TeacherService.create(...)`: after persisting the `TeacherProfile`, also persist a `TeacherSalaryHistory` row (`amount = request.hlsOfferedSalary()`, `effectiveFrom = clock.instant()` truncated to a `LocalDate`) in the same transaction (FR-002)
- [X] T012 [P] [US1] Add `TeacherServiceTest` cases: `create_alsoRecordsFirstSalaryHistoryEntry_effectiveFromCreationDate`, `currentSalary_returnsTheInitialAmount`, `salaryAsOf_theCreationDate_returnsTheInitialAmount`
- [X] T013 [P] [US1] Add `TeacherIntegrationTest` case: `createTeacher_thenSalaryImmediatelyRetrievable_asCurrentAndAsOfCreationDate` (SC-001) — real HTTP, `GET /teachers/{id}/salary` with and without `asOf`

**Checkpoint**: A newly created profile's salary is durably recorded and queryable — User Story 1 is independently functional (MVP).

---

## Phase 3: User Story 2 - Admin Records a Salary Increment (Priority: P1)

**Goal**: FR-003/FR-004 — Admin records a new salary with an effective date; the prior amount stays retrievable as of its own date, never lost or overwritten.

**Independent Test**: Record a second salary with a later effective date; confirm current salary is the new amount and the original is still retrievable as of a date before the increment.

### Implementation for User Story 2

- [X] T014 [US2] Implement `TeacherService.recordSalaryChange(...)`: defaults `effectiveFrom` to `clock.instant()`'s date when null, persists the new `TeacherSalaryHistory` row, and records an `audit.api.AuditWriter` entry (`action=UPDATED`, before/after amount) — continuing specs/005 FR-005's "every change to a teacher's ... salary" audit requirement, now alongside the structured table (research.md §1)
- [X] T015 [US2] Extend `backend/src/main/java/com/hls/teacher/internal/TeacherController.java`: add `POST /teachers/{teacherId}/salary` (Admin-only via the existing `requireAdmin` helper, `@RequestBody RecordSalaryChangeRequest(BigDecimal amount, LocalDate effectiveFrom)`, 404 if no profile), returning `TeacherSalaryHistoryView`
- [X] T016 [P] [US2] Add `TeacherServiceTest` cases: `recordSalaryChange_becomesTheCurrentSalary`, `recordSalaryChange_priorAmountStaysRetrievableAsOfItsOwnDate` (AC2/AC3), `recordSalaryChange_withNoEffectiveFrom_defaultsToToday`, `recordSalaryChange_backdatedCorrection_sortsIntoItsCorrectPlace` (Edge Case), `recordSalaryChange_sameDayDuplicate_mostRecentlyRecordedWins` (Edge Case, research.md §5), `recordSalaryChange_recordsAuditEntry`
- [X] T017 [P] [US2] Add `TeacherIntegrationTest` cases: `recordSalaryChange_asNonAdmin_isDenied`, `recordSalaryChange_thenAsOfQueries_returnCorrectAmountsBeforeAndAfter` (quickstart.md Scenario 2, real HTTP)

**Checkpoint**: Increments are recorded and correctly queryable by date — User Stories 1-2 are both independently functional.

---

## Phase 4: User Story 3 - Anyone Entitled Sees the Current Salary by Default (Priority: P1)

**Goal**: FR-005 — viewing a teacher's profile (specs/005-teacher's existing `GET /teachers/{id}`/`GET /teachers/me`) shows the current salary, no extra step, exactly as before this feature from the caller's point of view.

**Independent Test**: View a profile after a salary change; confirm the salary shown is the latest one, via the same call as always.

### Implementation for User Story 3

- [X] T018 [US3] Update `TeacherService`'s profile-to-view mapping (used by `findById` and, transitively, `GET /teachers/me`): compose `TeacherProfileView.hlsOfferedSalary` from `currentSalary(teacherId)` instead of a stored field (data-model.md)
- [X] T019 [P] [US3] Add `TeacherServiceTest` case: `findById_showsCurrentSalary_afterAnIncrement` (confirms the composed view reflects the latest history row, not a stale cached value)
- [X] T020 [P] [US3] Add `TeacherIntegrationTest` case: `viewingProfile_afterIncrement_showsNewSalary_withNoExtraStep` (SC-003) — `GET /teachers/{id}` and `GET /teachers/me` both reflect the incremented amount

**Checkpoint**: The default viewing path is unaffected from the caller's perspective while now being correctly sourced from history — User Stories 1-3 are all independently functional.

---

## Phase 5: User Story 4 - Look Up the Salary in Effect on a Past Date (Priority: P2)

**Goal**: FR-006/FR-007 — anyone who can view a teacher's profile can additionally ask for the salary as of a specific past date, with a clear "not yet recorded" for a date before the first entry.

**Independent Test**: Record two salaries with different effective dates; query as-of a date before, between, and after them; confirm each answer is correct, including the "not yet recorded" case.

### Implementation for User Story 4

- [X] T021 [US4] Extend `TeacherController`: `GET /teachers/{teacherId}/salary?asOf=<date>` — reuses the existing `canView(jwt, teacherId)` helper (same viewing rule as the profile itself, FR-008); calls `salaryAsOf(...)` when `asOf` is present, `currentSalary(...)` otherwise; 404 if no profile
- [X] T022 [P] [US4] Add `TeacherServiceTest` cases: `salaryAsOf_forADateBeforeTheFirstEntry_returnsNotYetRecorded` (FR-007), `salaryAsOf_forAFutureDate_returnsTheCurrentSalary` (Edge Case)
- [X] T023 [P] [US4] Add `TeacherIntegrationTest` cases: `salaryAsOf_beforeFirstRecordedEntry_returnsNotYetRecorded` (SC-004, quickstart.md Scenario 4), `salaryAsOf_respectsTheSameViewingRulesAsTheProfile` (a Manager not assigned to the teacher is denied, same as `GET /teachers/{id}`)

**Checkpoint**: All four user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Update specs/005-teacher's own tests/frontend for the changed `UpdateTeacherProfileRequest` shape, add the frontend surface for this feature, and validate end to end.

- [X] T024 [P] Update existing `TeacherServiceTest`/`TeacherIntegrationTest` cases (from specs/005-teacher) that exercised `updateProfile(...)`/`PATCH /teachers/{id}` with a salary field — remove the salary assertion from those cases (salary is no longer part of that call, T009)
- [X] T025 Extend `frontend/src/pages/TeacherProfilesPage/teacherClient.ts`: remove `hlsOfferedSalary` from the `UpdateTeacherProfileRequest` type; add `recordSalaryChange(accessToken, teacherId, amount, effectiveFrom?)` and `getSalary(accessToken, teacherId, asOf?)`
- [X] T026 Extend `frontend/src/pages/TeacherProfilesPage/TeacherProfilesPage.tsx`: remove the salary input from the existing "Update Profile" form; add a "Record Salary Change" form (`data-testid="record-salary-form"`, amount + optional effective-from date) and a "Salary As Of" lookup form (`data-testid="salary-lookup-form"`, teacherId + optional date, showing the `RECORDED`/`NOT_YET_RECORDED` result)
- [X] T027 [P] Update `frontend/src/pages/TeacherProfilesPage/TeacherProfilesPage.test.tsx`: adjust the existing "updates a teacher's salary" case (which used the old PATCH-based flow) to instead exercise the new record-salary-change form; add cases for the as-of lookup (found and not-yet-recorded)
- [X] T028 [P] Run quickstart.md's four scenarios manually (or confirm via the automated equivalents in T013/T017/T020/T023)
- [X] T029 Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s Teacher Master Data row (module 6) to note the salary-history extension (specs/009), and add a "Resolved" Section 7 entry documenting the `hlsOfferedSalary` → `TeacherSalaryHistory` correction
- [X] T030 Run the full backend (`mvn test`) and frontend (`npx vitest run`, `npx eslint .`, `npx tsc -b`) suites to confirm no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS all user stories
- **User Story 1 (Phase 2)**: Depends on Phase 1 only
- **User Story 2 (Phase 3)**: Depends on Phase 1; extends the same `TeacherService`/`TeacherController` files US1 touches, so in practice follows US1
- **User Story 3 (Phase 4)**: Depends on Phase 1 and on US1's `currentSalary` existing (T010) — no dependency on US2
- **User Story 4 (Phase 5)**: Depends on Phase 1 and on US1's `salaryAsOf`/`currentSalary` (T010) and the `canView` helper US3 doesn't itself add (already existed in specs/005) — no dependency on US2 or US3
- **Polish (Phase 6)**: Depends on all four user stories being complete

### Parallel Opportunities

- T002/T003/T004 (Foundational DTOs/entity) — different files
- T012/T013 (US1 tests), T016/T017 (US2 tests), T019/T020 (US3 tests), T022/T023 (US4 tests) — each pair touches different test files
- T024 (fixing specs/005's old tests) can happen any time after T009, in parallel with US1-4's new-file work

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Foundational
2. Complete Phase 2: User Story 1 (initial salary recorded and queryable)
3. **STOP and VALIDATE**: a new profile's salary is immediately retrievable as current and as-of (SC-001)

### Incremental Delivery

1. Foundational → `TeacherSalaryHistory` ready, `TeacherProfile` corrected
2. User Story 1 → initial salary recorded (MVP)
3. User Story 2 → increments recorded, history preserved — this is the actual point of the feature
4. User Story 3 → default profile view stays simple, now correctly sourced
5. User Story 4 → the "as of a date" query a future Payroll module will need
6. Polish → fix specs/005's now-outdated tests, frontend surface, quickstart validation, tracker update

## Notes

- No task edits `V6__create_teacher_tables.sql` directly — the column drop happens in the new `V7` migration (T001), per Flyway discipline.
- `TeacherSalaryQueries`/`TeacherSalaryCommands`/`TeacherQueries`/`TeacherCommands` are all implemented by the same `TeacherService` class, not four separate services.
- Commit after each task or logical group, consistent with this repo's established practice.
