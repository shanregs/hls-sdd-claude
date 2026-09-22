---

description: "Task list for Teacher Master Data"
---

# Tasks: Teacher Master Data

**Input**: Design documents from `/specs/005-teacher/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/teacher-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice (specs/002/003/004/007/008) writes unit + integration tests alongside every service, and plan.md's Testing section explicitly names `TeacherServiceTest`/`TeacherModuleTest`/`TeacherIntegrationTest`/`TokenServiceTest`.

**Organization**: Tasks are grouped by user story (spec.md priorities: US1 = P1, US2 = P2, US3 = P2, US4 = P3).

**Scope note**: Bank details for payout are excluded from this feature (spec.md Assumptions, scope correction 2026-09-22) — no task below creates a bank-detail field, column, or form control.

**Historical note (2026-09-22, later same day)**: T006-T009 below describe `hlsOfferedSalary` as a plain field on `TeacherProfileView`/`CreateTeacherProfileRequest`/`UpdateTeacherProfileRequest`/`TeacherProfile` — accurate for what this task list originally built. `specs/009-teacher-salary-history` then corrected this: `TeacherProfile` no longer stores salary at all (it's derived from a new `TeacherSalaryHistory` table), and `UpdateTeacherProfileRequest` no longer has a salary field. This file is left as a record of what was executed at the time rather than rewritten; see `specs/009-teacher-salary-history/data-model.md` and `tasks.md` for the corrected, current design and `specs/005-teacher/data-model.md` for the updated field list.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)

## Path Conventions

Web app: `backend/src/main/java/com/hls/teacher/`, `backend/src/test/java/com/hls/teacher/`, `frontend/src/pages/TeacherProfilesPage/`, `frontend/src/pages/MyProfilePage/`. This is a brand-new backend module (unlike specs/008, which extended `school`), so — unlike that feature — this one does need a Setup phase.

---

## Phase 1: Setup (New Module Scaffolding)

**Purpose**: Establish the `com.hls.teacher` package and its Spring Modulith named-interface boundary, before any entity or logic exists.

- [X] T001 Create `backend/src/main/java/com/hls/teacher/api/package-info.java`: `@org.springframework.modulith.NamedInterface("api")` on `package com.hls.teacher.api;`, mirroring `school/api/package-info.java`
- [X] T002 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/package-info.java`: same `@NamedInterface("api")` name on `package com.hls.teacher.api.dto;` (research.md's established finding: a subpackage needs its own `package-info.java` carrying the identical name, or `propagate` won't reach it)
- [X] T003 [P] Create `backend/src/main/java/com/hls/teacher/internal/package-info.java`: plain doc comment, no annotation, `package com.hls.teacher.internal;`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `TeacherProfile` table, entity, repository, DTOs, and public interfaces every user story builds on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T004 Create migration `backend/src/main/resources/db/migration/V6__create_teacher_tables.sql`: table `teacher_profile` with columns `id UUID PRIMARY KEY`, `name VARCHAR(255) NOT NULL`, `phone VARCHAR(32) NOT NULL`, `email VARCHAR(255)` (nullable — data-model.md), `hls_offered_salary NUMERIC(12,2) NOT NULL`, `status VARCHAR(20) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL` — no bank-detail columns of any kind (spec.md Assumptions). Next available version is `V6`: Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5 (plan.md)
- [X] T005 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/TeacherStatus.java`: enum `IN_TRAINING, ACTIVE, ON_LEAVE, EXITED` (FR-003)
- [X] T006 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/TeacherProfileView.java`: `record TeacherProfileView(UUID id, String name, String phone, String email, BigDecimal hlsOfferedSalary, TeacherStatus status, Instant createdAt)` per data-model.md — no bank fields
- [X] T007 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/CreateTeacherProfileRequest.java`: `record CreateTeacherProfileRequest(String name, String phone, String email, BigDecimal hlsOfferedSalary, TeacherStatus status)`, with `name`/`phone`/`hlsOfferedSalary`/`status` bean-validated `@NotNull`/`@NotBlank` (FR-001, spec.md AC2 — a missing required detail must be rejected, not silently accepted)
- [X] T008 [P] Create `backend/src/main/java/com/hls/teacher/api/dto/UpdateTeacherProfileRequest.java`: `record UpdateTeacherProfileRequest(String name, String phone, String email, BigDecimal hlsOfferedSalary)` — every field nullable/optional; only fields present are changed (FR-002, data-model.md)
- [X] T009 Create `backend/src/main/java/com/hls/teacher/internal/TeacherProfile.java`: JPA entity with fields `id, name, phone, email, hlsOfferedSalary, status, createdAt, createdBy` (data-model.md) — no bank fields; mutator methods `updateContact(String phone, String email)`, `updateSalary(BigDecimal salary)`, `changeStatus(TeacherStatus newStatus)` (every transition allowed — data-model.md's state-transition diagram names no illegal-transition rule)
- [X] T010 Create `backend/src/main/java/com/hls/teacher/internal/TeacherProfileRepository.java`: `extends Repository<TeacherProfile, UUID>` (bare, not JpaRepository — FR-004 never-deleted, mirroring `ZoneRepository`/`PlaceRepository`), exposing only `save`, `findById`, `findAll` — no delete method (research.md §4)
- [X] T011 Create `backend/src/main/java/com/hls/teacher/api/TeacherQueries.java`: interface with `Optional<TeacherProfileView> findById(UUID teacherId)` and `boolean exists(UUID teacherId)` (FR-011; the `exists` method is the forward hook research.md §9 flags for Organization's still-unaddressed FR-012, not wired up by this plan)
- [X] T012 Create `backend/src/main/java/com/hls/teacher/api/TeacherCommands.java`: interface with `TeacherProfileView create(CreateTeacherProfileRequest request, UUID actingUserId)` (FR-001), `TeacherProfileView updateProfile(UUID teacherId, UpdateTeacherProfileRequest request, UUID actingUserId)` (FR-002), `TeacherProfileView changeStatus(UUID teacherId, TeacherStatus newStatus, UUID actingUserId)` (FR-003)
- [X] T013 Add `teacherInternalsAreOnlyAccessedFromWithinTeacher` rule to `backend/src/test/java/com/hls/ArchitectureTest.java`, mirroring `schoolInternalsAreOnlyAccessedFromWithinSchool`
- [X] T014 Create `backend/src/test/java/com/hls/teacher/TeacherModuleTest.java`: `@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)` — `teacher` has real bean dependencies on `identity.api` and `audit.api` from day one, unlike `school`'s `STANDALONE` start; `DIRECT_DEPENDENCIES` (research.md §6's original plan) turned out insufficient because `identity`'s own `ManagerScopeGuard` has a *transitive* dependency on `organization.api` — discovered during implementation and corrected (research.md §6, updated)

**Checkpoint**: `TeacherProfile` persists and is queryable; the module boundary is enforced. User story implementation can now begin.

---

## Phase 3: User Story 1 - Admin Onboards a New Teacher's Profile (Priority: P1) 🎯 MVP

**Goal**: FR-001 — Admin creates a profile (name, contact, HLS-offered salary, initial status), immediately retrievable with exactly those details (SC-001); a missing required detail is rejected with a clear error (AC2).

**Independent Test**: Admin calls `POST /api/v1/teachers`, then retrieves the same profile and confirms every field matches.

### Implementation for User Story 1

- [X] T015 [US1] Create `backend/src/main/java/com/hls/teacher/internal/TeacherService.java` implementing `TeacherQueries`/`TeacherCommands`: `create(...)` persists a new `TeacherProfile` via `TeacherProfileRepository.save`, then calls `audit.api.AuditWriter.record(...)` in the same transaction — `sourceModule="teacher"`, `entityType="TeacherProfile"`, `entityId=<new id>`, `action=CREATED`, `actorUserId=actingUserId` (FR-001/FR-005, research.md §5 — `teacher` becomes Audit's first real caller)
- [X] T016 [US1] Implement `TeacherService.findById(...)` mapping `TeacherProfile` → `TeacherProfileView`, and `exists(...)` (FR-011)
- [X] T017 [US1] Create `backend/src/main/java/com/hls/teacher/internal/TeacherController.java`: `@RestController @RequestMapping("/api/v1/teachers")`, reads `@AuthenticationPrincipal Jwt` directly (established pattern); a private `requireAdmin(Jwt jwt)` helper — **Admin-only, not Director+Admin** (FR-010 is narrower than every other write-endpoint precedent in this codebase so far, which combine Director+Admin); `POST /teachers` with `@Valid @RequestBody CreateTeacherProfileRequest`, calling `requireAdmin` then `teacherCommands.create(request, userId(jwt))`, returning the `TeacherProfileView`; `GET /teachers/{teacherId}` returning 404 via `ResponseEntity` when `findById` is empty (US1 only needs this much of it — Director/Manager/Teacher-specific scoping is added in US3/US4)
- [X] T018 [US1] Wire bean validation: `@Valid` on the controller's `CreateTeacherProfileRequest` parameter plus a `@ExceptionHandler(MethodArgumentNotValidException.class)` returning 400 with the offending field name(s) (AC2 — Admin is told what's missing, not just refused)
- [X] T019 [P] [US1] Create `backend/src/test/java/com/hls/teacher/TeacherServiceTest.java` (new file) with cases: `create_persistsAndReturnsProfileWithExactDetails`, `create_recordsAuditCreatedEntry` (mocked `AuditWriter`, verify the recorded `entityType`/`action`/`actorUserId`), `findById_forUnknownId_returnsEmpty`
- [X] T020 [P] [US1] Create `backend/src/test/java/com/hls/teacher/TeacherIntegrationTest.java` (new file, Testcontainers Postgres + real JWT auth, mirroring `SchoolIntegrationTest`'s structure) with cases: `createTeacher_asAdmin_thenImmediatelyRetrievable_withExactDetails` (SC-001), `createTeacher_asNonAdmin_isDenied` (FR-010, expect 403), `createTeacher_missingRequiredField_returns400WithFieldNamed` (AC2)
- [X] T021 [US1] Create `frontend/src/pages/TeacherProfilesPage/teacherClient.ts`: `createTeacherProfile(accessToken, request)` and `getTeacherProfile(accessToken, teacherId)`, following `zoneClient.ts`'s `request<T>` helper pattern
- [X] T022 [US1] Create `frontend/src/pages/TeacherProfilesPage/TeacherProfilesPage.tsx`: an Admin-facing "Create Profile" form (name/phone/email/salary/status inputs, `data-testid="create-teacher-form"`), showing the created profile's id on success and the validation error on failure
- [X] T023 [P] [US1] Create `frontend/src/pages/TeacherProfilesPage/TeacherProfilesPage.test.tsx` (new file) with case: creates a profile (User Story 1)

**Checkpoint**: A profile can be created by Admin and is durably stored and retrievable — User Story 1 is independently functional. This is the MVP slice.

---

## Phase 4: User Story 2 - Admin Maintains a Profile With Full History (Priority: P2)

**Goal**: FR-002/003/005 — Admin updates contact/salary or changes status; every change is retrievable as history via the real Audit module, never a silent overwrite (SC-002).

**Independent Test**: Admin updates a field or status on an existing profile, then retrieves both the current value and its full change history via Audit's real `GET /api/v1/audit/TeacherProfile/{id}/history`.

### Implementation for User Story 2

- [X] T024 [US2] Implement `TeacherService.updateProfile(...)`: applies only the non-null fields from `UpdateTeacherProfileRequest` to the loaded `TeacherProfile` (via `updateContact`/`updateSalary`), then calls `audit.api.AuditWriter.record(...)` with `action=UPDATED`, `beforeValue`/`afterValue` capturing what changed (FR-002/FR-005)
- [X] T025 [US2] Implement `TeacherService.changeStatus(...)`: calls `TeacherProfile.changeStatus(newStatus)` (any transition allowed, including re-activating a previously `EXITED` teacher — data-model.md), then records an `audit.api.AuditWriter` entry with `action=UPDATED`, before/after status (FR-003/FR-005)
- [X] T026 [US2] Extend `TeacherController`: `PATCH /teachers/{teacherId}` (Admin-only via `requireAdmin`, `@RequestBody UpdateTeacherProfileRequest`, calls `updateProfile`, 404 if no profile) and `POST /teachers/{teacherId}/status` (Admin-only, `@RequestBody` with a required `status` field, calls `changeStatus`, 404 if no profile)
- [X] T027 [P] [US2] Add `TeacherServiceTest` cases: `updateProfile_changesOnlyTheProvidedFields_leavesOthersUntouched`, `updateProfile_toTheExactCurrentValue_isUnaffectedEitherWay` (Edge Case — no behavior depends on whether this produces a new history entry), `changeStatus_recordsBeforeAndAfterStatus`, `changeStatus_reactivationAfterExited_isAllowed` (data-model.md — every transition allowed)
- [X] T028 [P] [US2] Add `TeacherIntegrationTest` cases: `updateSalary_thenChangeStatus_bothRetrievableAsHistory_viaRealAuditEndpoint` (quickstart.md Scenario 2 — `GET /api/v1/audit/TeacherProfile/{teacherId}/history` returns CREATED, then the salary UPDATE, then the status UPDATE, each with prior value preserved), `updateProfile_asNonAdmin_isDenied`, `changeStatus_asNonAdmin_isDenied`
- [X] T029 [US2] Extend `TeacherProfilesPage.tsx`: an "Update Profile" form (salary/phone/email, `data-testid="update-teacher-form"`) and a "Change Status" control (`data-testid="change-status-form"`), both calling new `teacherClient.ts` functions `updateTeacherProfile(...)`/`changeTeacherStatus(...)`
- [X] T030 [P] [US2] Add `TeacherProfilesPage.test.tsx` cases: updates a teacher's salary (User Story 2), changes a teacher's status

**Checkpoint**: Profiles can be updated and status-changed with full history — User Stories 1 and 2 are both independently functional.

---

## Phase 5: User Story 3 - Director and the Assigned Manager View a Profile (Priority: P2)

**Goal**: FR-006/007 — a Director can view any profile; a Manager can view only a profile for a teacher currently assigned to them, tracking reassignment in real time (SC-003).

**Independent Test**: A Director views any profile (always succeeds); a Manager views a profile for a currently-assigned teacher (succeeds) and a non-assigned one (denied); reassigning the teacher flips which Manager can view it.

### Implementation for User Story 3

- [X] T031 [US3] Extend `TeacherController`'s `GET /teachers/{teacherId}` from T017: Admin and Director always allowed; Manager allowed only if `identity.api.ManagerScopeQueries.isAllowedForTeacher(callerId, teacherId)` returns true (403 otherwise, FR-007); every other caller denied for now (the Teacher-self case is added in US4, not here) — no new scoping logic invented, `teacher` never depends on `organization.api` directly (research.md §1)
- [X] T032 [P] [US3] Add a `TeacherServiceTest` case confirming `findById` behavior backing 404s is unaffected by this story (no new business logic lives in `TeacherService` for this story — the scoping check is entirely in the controller, per plan.md's Project Structure)
- [X] T033 [P] [US3] Add `TeacherIntegrationTest` cases: `director_viewsAnyProfile_regardlessOfCurrentAssignment` (AC1), `managerCurrentlyAssignedToTeacher_canViewProfile` (AC2 — seed the assignment via Organization's real `POST /api/v1/organization/teacher-assignments`, per quickstart.md Scenario 3), `managerNotAssignedToTeacher_isDenied` (AC3, and Edge Case: a Manager with zero teachers assigned is denied identically), `teacherReassignedFromManagerAToManagerB_accessFollowsTheReassignment` (AC4 — Manager B can now view it, Manager A no longer can)
- [X] T034 [US3] Extend `TeacherProfilesPage.tsx`: a "View Profile" lookup form (`data-testid="view-teacher-form"`, by teacher id) rendering the profile on success or the resulting 403/404 message — the same page Admin/Director/Manager all use, the backend deciding what each role can actually do (matches `AssignmentsPage`/`ZonesPage` precedent — frontend does not itself gate by role)
- [X] T035 [P] [US3] Add `TeacherProfilesPage.test.tsx` cases: a Director views any profile; a Manager is denied a non-assigned teacher's profile (shows the error)

**Checkpoint**: Director/Manager viewing works end-to-end, tracking Organization's live assignment — User Stories 1-3 are all independently functional.

---

## Phase 6: User Story 4 - A Teacher Views Their Own Profile, Read-Only (Priority: P3)

**Goal**: FR-008/009 — a Teacher retrieves their own profile via a convenience `/me` endpoint, with no edit path anywhere and no access to another teacher's profile (SC-004).

**Independent Test**: A logged-in Teacher calls `GET /teachers/me` and sees their own profile; the same Teacher's token is denied on `GET /teachers/{anotherTeacherId}`; no PATCH/status endpoint is ever reachable by a Teacher token.

### Implementation for User Story 4

- [X] T036 [US4] Small, additive touch to `backend/src/main/java/com/hls/identity/internal/TokenService.java` (research.md §3): `buildAccessToken(...)` gains a `UUID linkedTeacherId` parameter and adds a `"teacherId"` claim to the built token when non-null — same claim-embedding pattern already used for `"roles"`
- [X] T037 [US4] Update `TokenService.issueForNewSession(...)` and `TokenService.refresh(...)` signatures to accept `UUID linkedTeacherId` and pass it through to `buildAccessToken(...)`
- [X] T038 [US4] Update all four call sites in `backend/src/main/java/com/hls/identity/internal/AuthenticationService.java` (password login, `verifyMfa`, `verifyOtp`, `refresh`) to pass `user.getLinkedTeacherId()` through to the updated `TokenService` methods — additive, no existing test's expectations change (the claim is simply absent, not malformed, for a caller with no linked teacher id)
- [X] T039 [P] [US4] Create `backend/src/test/java/com/hls/identity/TokenServiceTest.java` (new file) with cases: `buildAccessToken_whenLinkedTeacherIdSet_includesTeacherIdClaim`, `buildAccessToken_whenLinkedTeacherIdNull_omitsTeacherIdClaim`
- [X] T040 [US4] Extend `TeacherController`'s `GET /teachers/{teacherId}` (from T031) with a Teacher-role branch: `identity.api.TeacherScopeQueries.isAllowed(callerId, teacherId)` — own id allowed, else 403 (FR-009); add `GET /teachers/me` reading `jwt.getClaim("teacherId")` (research.md §3), returning 403 if the claim is absent (not a Teacher, or not yet linked) and 404 if the linked id has no profile
- [X] T041 [P] [US4] Add `TeacherIntegrationTest` cases: `teacherViewsOwnProfile_viaMeEndpoint_succeeds` (seed a `User` with `linkedTeacherId` set, log in for real, prove the new `"teacherId"` claim round-trips end to end), `teacherAttemptsToViewAnotherTeachersProfile_viaTeacherIdEndpoint_isDenied` (FR-009), `callerWithNoLinkedTeacherId_meEndpoint_returns403`
- [X] T042 [US4] Create `frontend/src/pages/MyProfilePage/MyProfilePage.tsx` (Teacher-facing, read-only): calls `GET /teachers/me` on load, renders name/contact/salary/status, and offers **no** edit control anywhere in the markup (AC2) — plus `frontend/src/pages/MyProfilePage/MyProfilePage.test.tsx` with cases: renders the caller's own profile; confirms no edit affordance is present (e.g. no `<button>`/`<input>` that would change a field)
- [X] T043 [US4] Add a `getMyTeacherProfile(accessToken)` function to `teacherClient.ts` (or a small dedicated client for `MyProfilePage`, consistent with the rest of this codebase's one-client-per-page pattern); wire `TeacherProfilesPage` and `MyProfilePage` into `frontend/src/App.tsx`'s `AuthenticatedView` union and nav (unconditionally shown to every authenticated user, same as every existing nav entry — the backend enforces who can actually do what, frontend does not itself gate by role, per `AssignmentsPage`/`ZonesPage` precedent)

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T044 [P] Run quickstart.md's four scenarios manually (or confirm via the automated equivalents in T020/T028/T033/T041) — note quickstart.md's Scenario 1 payload no longer includes bank fields (already updated for the scope correction)
- [X] T045 Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md`'s Teacher Master Data row (module 6) to Done, noting the full spec/plan/tasks/implement cycle and that bank details were excluded per the 2026-09-22 scope correction
- [X] T046 Run the full backend (`mvn test`) and frontend (`npx vitest run`, `npx eslint .`, `npx tsc -b`) suites to confirm no regressions in Identity's existing test expectations (T036-T038 touch shared, already-shipped code)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational only
- **User Story 2 (Phase 4)**: Depends on Foundational; extends the same `TeacherController`/`TeacherService`/`TeacherProfilesPage` files US1 created, so in practice follows US1 even though there's no *data* dependency between them
- **User Story 3 (Phase 5)**: Depends on Foundational and on US1's `GET /teachers/{teacherId}` endpoint existing (T017) to extend — no dependency on US2
- **User Story 4 (Phase 6)**: Depends on Foundational and on US3's `GET /teachers/{teacherId}` endpoint (T031) to extend with the Teacher-role branch; T036-T039 (the `TokenService`/`AuthenticationService` touch) have no dependency on any other story and could be built first if staffed separately
- **Polish (Phase 7)**: Depends on all four user stories being complete

### Within Each User Story

- DTOs/entity (Foundational) before service methods
- Service methods before the controller endpoints that call them
- Controller endpoints before their integration tests
- Frontend client functions before the page code that calls them

### Parallel Opportunities

- T002/T003 (Setup) — different files
- T005-T008 (Foundational DTOs) — different files, no dependency on each other
- T019/T020 (US1 tests), T027/T028 (US2 tests), T032/T033 (US3 tests), T039/T041 (US4 tests) — each pair touches different test files
- T036-T039 (the Identity `TokenService` touch) can be built in parallel with T031-T035 (US3) by a different contributor, since neither depends on the other — both are prerequisites only for US4's T040

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (Admin creates a profile)
4. **STOP and VALIDATE**: a profile can be created and is immediately retrievable with exact details (SC-001)

### Incremental Delivery

1. Setup + Foundational → module scaffolding and `TeacherProfile` ready
2. User Story 1 → profiles can be created (MVP)
3. User Story 2 → profiles can be maintained with full history — this is where Audit's real value shows up
4. User Story 3 → Director/Manager visibility, tracking Organization's live assignment
5. User Story 4 → Teacher self-service, plus the one small Identity touch this feature needs
6. Polish → frontend nav wiring, quickstart validation, tracker update

## Notes

- No bank-detail field, column, DTO property, contract schema property, or frontend form control anywhere in this task list (scope correction, 2026-09-22).
- `FR-010`'s Admin-only write restriction is narrower than every other write-endpoint precedent in this codebase so far (which combine Director+Admin) — T017 calls this out explicitly so it isn't accidentally copy-pasted as `requireDirectorOrAdmin`.
- Commit after each task or logical group, consistent with this repo's established practice.
