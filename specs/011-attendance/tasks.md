---

description: "Task list for Daily Attendance Capture & Monthly Rollup"
---

# Tasks: Daily Attendance Capture & Monthly Rollup

**Input**: Design documents from `/specs/011-attendance/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/attendance-api.yaml, quickstart.md

**Tests**: Included — this codebase's established practice (specs/002/003/004/005/007/008/009) writes unit + integration tests alongside every service.

**Organization**: Tasks are grouped by user story (spec.md priorities: US1 = P1, US2 = P1, US3 = P1, US4 = P2, US5 = P2).

**Path Conventions**: Web app — `backend/src/main/java/com/hls/attendance/`, `backend/src/test/java/com/hls/attendance/`, `frontend/src/pages/MyAttendancePage/`, `frontend/src/pages/AttendancePage/` (including `AttendanceGrid.tsx`, US5). This is a brand-new backend module (like specs/005-teacher, unlike specs/008/009 which extended an existing one), so it needs a Setup phase; T076 is the sole exception, a small additive edit to the already-existing `teacher` module (research.md §8).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5)

---

## Phase 1: Setup (New Module Scaffolding)

**Purpose**: Establish the `com.hls.attendance` package and its Spring Modulith named-interface boundary, before any entity or logic exists.

- [X] T001 Create `backend/src/main/java/com/hls/attendance/api/package-info.java`: `@org.springframework.modulith.NamedInterface("api")` on `package com.hls.attendance.api;`, mirroring `teacher/api/package-info.java`
- [X] T002 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/package-info.java`: same `@NamedInterface("api")` name on `package com.hls.attendance.api.dto;` (a subpackage needs its own `package-info.java` carrying the identical name, per specs/005 research.md)
- [X] T003 [P] Create `backend/src/main/java/com/hls/attendance/internal/package-info.java`: plain doc comment, no annotation, `package com.hls.attendance.internal;`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The five tables/entities/repositories and public interfaces every user story builds on (data-model.md), including the shared Non-Working Calendar (FR-022) that both US3's rollup and US5's grid depend on.

**⚠️ CRITICAL**: No user story task can begin until this phase is complete.

- [X] T004 Create migration `backend/src/main/resources/db/migration/V9__create_attendance_tables.sql` (next available version — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher Salary History=V7, Zone-Manager Assignment=V8, per plan.md). All five tables ship in this one migration since none exist on disk yet:
  - `attendance_status_code`: `code VARCHAR(20) PRIMARY KEY`, `label VARCHAR(100) NOT NULL`, `category VARCHAR(20) NOT NULL`, `weight NUMERIC(3,2) NOT NULL DEFAULT 1.00`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL`; seed rows `('PRESENT','Present','WORKED',1.00,TRUE,...)`, `('LEAVE','Leave','LEAVE',0.00,TRUE,...)`, `('TRAINING','Training Day','TRAINING',1.00,TRUE,...)`, `('NON_WORKING','Non-Working Day','NON_WORKING',0.00,TRUE,...)` (data-model.md)
  - `attendance_mark`: `id UUID PRIMARY KEY`, `teacher_id UUID NOT NULL`, `mark_date DATE NOT NULL`, `school_id UUID NOT NULL`, `status_code VARCHAR(20) NOT NULL REFERENCES attendance_status_code(code)`, `fractional_value NUMERIC(3,2) NOT NULL DEFAULT 1.00 CHECK (fractional_value >= 0 AND fractional_value <= 1)`, `evidence_geo_lat NUMERIC(9,6)`, `evidence_geo_lng NUMERIC(9,6)`, `evidence_photo_url VARCHAR(500)`, `evidence_checkin_code VARCHAR(50)`, `marked_by UUID NOT NULL`, `marked_by_role VARCHAR(20) NOT NULL`, `marked_at TIMESTAMPTZ NOT NULL`; `UNIQUE (teacher_id, mark_date)`; index `ix_attendance_mark_teacher_id_mark_date ON attendance_mark (teacher_id, mark_date)` for month-range queries
  - `attendance_teacher_month_lock`: `id UUID PRIMARY KEY`, `teacher_id UUID NOT NULL`, `period VARCHAR(7) NOT NULL`, `status VARCHAR(20) NOT NULL`, `locked_at TIMESTAMPTZ NOT NULL`, `locked_by UUID NOT NULL`; `UNIQUE (teacher_id, period)`
  - `attendance_reopen_record`: `id UUID PRIMARY KEY`, `lock_id UUID NOT NULL REFERENCES attendance_teacher_month_lock(id)`, `reason VARCHAR(500) NOT NULL`, `reopened_at TIMESTAMPTZ NOT NULL`, `reopened_by UUID NOT NULL`, `relocked_at TIMESTAMPTZ`, `relocked_by UUID`
  - `attendance_non_working_date`: `id UUID PRIMARY KEY`, `date DATE NOT NULL UNIQUE`, `label VARCHAR(200) NOT NULL`, `active BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMPTZ NOT NULL`, `created_by UUID NOT NULL` (FR-022, data-model.md); index `ix_attendance_non_working_date_date ON attendance_non_working_date (date)` for month-range lookups
- [X] T005 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/AttendanceCategory.java`: enum `WORKED, LEAVE, TRAINING, NON_WORKING` (data-model.md)
- [X] T006 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/MarkedByRole.java`: enum `TEACHER, MANAGER, ADMIN` (FR-002/FR-006/FR-024 — `ADMIN` added for unscoped on-behalf marking)
- [X] T007 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/LockStatus.java`: enum `UNLOCKED, LOCKED, REOPENED` (data-model.md)
- [X] T008 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/EvidenceInput.java`: `record EvidenceInput(BigDecimal geoLat, BigDecimal geoLng, String photoUrl, String checkinCode)`, every field nullable (FR-003)
- [X] T009 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/AttendanceStatusCodeView.java`: `record AttendanceStatusCodeView(String code, String label, AttendanceCategory category, BigDecimal weight, boolean active)`
- [X] T010 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/CreateStatusCodeRequest.java`: `record CreateStatusCodeRequest(String code, String label, AttendanceCategory category, BigDecimal weight)`, `code`/`label`/`category`/`weight` `@NotNull`/`@NotBlank` (FR-005)
- [X] T011 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/AttendanceMarkView.java`: `record AttendanceMarkView(UUID id, UUID teacherId, LocalDate markDate, UUID schoolId, String statusCode, BigDecimal fractionalValue, EvidenceInput evidence, UUID markedBy, MarkedByRole markedByRole, Instant markedAt)`
- [X] T012 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/MarkAttendanceRequest.java`: `record MarkAttendanceRequest(LocalDate markDate, UUID schoolId, String statusCode, BigDecimal fractionalValue, EvidenceInput evidence)`, `markDate`/`schoolId`/`statusCode` `@NotNull` (FR-001), `fractionalValue` nullable — defaults to `1.00` when absent (data-model.md)
- [X] T013 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/MonthlyAttendanceRollupView.java`: `record MonthlyAttendanceRollupView(UUID teacherId, String period, int trainingDaysTotal, BigDecimal trainingDaysAttended, BigDecimal daysWorked, BigDecimal daysLeave, int overallWorkingDays, int unmarkedDays, BigDecimal weightedAttendanceTotal, LockStatus lockStatus)` (data-model.md's formulas)
- [X] T014 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/ReopenEntry.java`: `record ReopenEntry(String reason, Instant reopenedAt, UUID reopenedBy, Instant relockedAt, UUID relockedBy)`, `relockedAt`/`relockedBy` nullable
- [X] T015 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/LockStatusView.java`: `record LockStatusView(UUID teacherId, String period, LockStatus status, Instant lockedAt, UUID lockedBy, List<ReopenEntry> reopenHistory)`, `lockedAt`/`lockedBy` nullable when `status = UNLOCKED`
- [X] T016 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/NonWorkingDateView.java`: `record NonWorkingDateView(UUID id, LocalDate date, String label, boolean active)` (FR-022, data-model.md)
- [X] T017 [P] Create `backend/src/main/java/com/hls/attendance/api/dto/AddNonWorkingDateRequest.java`: `record AddNonWorkingDateRequest(LocalDate date, String label)`, both `@NotNull`/`@NotBlank` (FR-022)
- [X] T018 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceStatusCode.java`: JPA entity, fields `code (PK), label, category, weight, active, createdAt, createdBy` (data-model.md), mutator `deactivate()`/`updateLabelAndWeight(...)`
- [X] T019 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceMark.java`: JPA entity, fields `id, teacherId, markDate, schoolId, statusCode, fractionalValue, evidenceGeoLat, evidenceGeoLng, evidencePhotoUrl, evidenceCheckinCode, markedBy, markedByRole, markedAt` (data-model.md), mutator `update(schoolId, statusCode, fractionalValue, evidence, markedBy, markedByRole, markedAt)` for in-place edits (US1 AC4)
- [X] T020 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceTeacherMonthLock.java`: JPA entity, fields `id, teacherId, period, status, lockedAt, lockedBy` (data-model.md), mutators `lock(lockedAt, lockedBy)`/`reopen()`
- [X] T021 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceReopenRecord.java`: JPA entity, fields `id, lockId, reason, reopenedAt, reopenedBy, relockedAt, relockedBy` (data-model.md), mutator `relock(relockedAt, relockedBy)`
- [X] T022 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceNonWorkingDate.java`: JPA entity, fields `id, date, label, active, createdAt, createdBy` (data-model.md), mutator `deactivate()` — `date`/`label` immutable after creation
- [X] T023 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceStatusCodeRepository.java`: `extends Repository<AttendanceStatusCode, String>`, `save`, `findById`, `findByActiveTrue()`, `List<AttendanceStatusCode> findAll()` — no delete (codes are deactivated, never removed)
- [X] T024 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceMarkRepository.java`: `extends Repository<AttendanceMark, UUID>`, `save`, `findById`, `Optional<AttendanceMark> findByTeacherIdAndMarkDate(UUID teacherId, LocalDate markDate)`, `List<AttendanceMark> findByTeacherIdAndMarkDateBetween(UUID teacherId, LocalDate start, LocalDate end)` — no delete method (data-model.md, corrections happen by editing the row)
- [X] T025 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceTeacherMonthLockRepository.java`: `extends Repository<AttendanceTeacherMonthLock, UUID>`, `save`, `Optional<AttendanceTeacherMonthLock> findByTeacherIdAndPeriod(UUID teacherId, String period)`
- [X] T026 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceReopenRecordRepository.java`: `extends Repository<AttendanceReopenRecord, UUID>`, `save`, `List<AttendanceReopenRecord> findByLockIdOrderByReopenedAtAsc(UUID lockId)`, `Optional<AttendanceReopenRecord> findByLockIdAndRelockedAtIsNull(UUID lockId)` (finds the currently-open reopen cycle, if any)
- [X] T027 [P] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceNonWorkingDateRepository.java`: `extends Repository<AttendanceNonWorkingDate, UUID>`, `save`, `findById`, `List<AttendanceNonWorkingDate> findByActiveTrueAndDateBetween(LocalDate start, LocalDate end)` (used by both US3's rollup and US5's grid), `List<AttendanceNonWorkingDate> findByActiveTrue()` — no delete (deactivate only, data-model.md)
- [X] T028 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceStatusCodeQueries.java`: interface, `List<AttendanceStatusCodeView> listActiveCodes()` (FR-005)
- [X] T029 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceStatusCodeCommands.java`: interface, `AttendanceStatusCodeView createStatusCode(CreateStatusCodeRequest request, UUID actingUserId)` (FR-005, Admin-or-Director — enforced by controller)
- [X] T030 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceMarkQueries.java`: interface, `Optional<AttendanceMarkView> markForDate(UUID teacherId, LocalDate date)`, `List<AttendanceMarkView> marksForMonth(UUID teacherId, String period)` (FR-010)
- [X] T031 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceMarkCommands.java`: interface, `AttendanceMarkView markAttendance(UUID teacherId, MarkAttendanceRequest request, UUID actingUserId, MarkedByRole actingRole)` (FR-001/FR-002/FR-004/FR-006/FR-023/FR-024)
- [X] T032 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceRollupQueries.java`: interface, `MonthlyAttendanceRollupView rollupForMonth(UUID teacherId, String period)` (FR-007/FR-008/FR-009/FR-010)
- [X] T033 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceLockQueries.java`: interface, `LockStatusView lockStatus(UUID teacherId, String period)`
- [X] T034 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceLockCommands.java`: interface, `LockStatusView lockMonth(UUID teacherId, String period, UUID actingUserId)` (FR-011), `LockStatusView reopenMonth(UUID teacherId, String period, String reason, UUID actingUserId)` (FR-013/FR-014)
- [X] T035 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceNonWorkingCalendarQueries.java`: interface, `List<NonWorkingDateView> datesForMonth(String period)` (FR-022)
- [X] T036 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceNonWorkingCalendarCommands.java`: interface, `NonWorkingDateView addNonWorkingDate(AddNonWorkingDateRequest request, UUID actingUserId)` (FR-022, Admin-only), `NonWorkingDateView deactivateNonWorkingDate(UUID id, UUID actingUserId)`
- [X] T037 [P] Create `backend/src/main/java/com/hls/attendance/api/AttendanceMonthLockedException.java`: unchecked exception, carries `teacherId`/`period`, clear message "Attendance for {period} is locked" (FR-012)
- [X] T038 [P] Create `backend/src/main/java/com/hls/attendance/api/UnknownAttendanceStatusCodeException.java`: unchecked exception, carries the unknown `code`
- [X] T039 Add `attendanceInternalsAreOnlyAccessedFromWithinAttendance` rule to `backend/src/test/java/com/hls/ArchitectureTest.java` (mirroring `teacherInternalsAreOnlyAccessedFromWithinTeacher`), and add `"com.hls.attendance.."` to the existing cross-module dependency list check
- [X] T040 Create `backend/src/test/java/com/hls/attendance/AttendanceModuleTest.java`: `@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)` — `attendance` has real bean dependencies on `identity.api`, `audit.api`, `teacher.api`, and (US5) `organization.api` from day one, so `ALL_DEPENDENCIES` is used from the start rather than discovering the need during implementation (specs/005 research.md §6's lesson, applied up front)

**Checkpoint**: All five tables persist and are queryable; the module boundary is enforced. User story implementation can now begin.

---

## Phase 3: User Story 1 - Teacher Marks Their Own Daily Attendance (Priority: P1) 🎯 MVP

**Goal**: FR-001/FR-003/FR-004/FR-005/FR-006/FR-023 — a Teacher marks their own attendance (status, optional half-day fractional value, optional geo-tag/photo/check-in-code evidence), tagged to a school assignment, fully attributed; re-marking the same day edits in place; no lower bound on how far back a still-unlocked date can be corrected.

**Independent Test**: `POST /api/v1/attendance/me/marks` as a Teacher; confirm the mark is saved, attributed to the Teacher, and immediately retrievable; re-post for the same date and confirm it edits the existing mark rather than duplicating it.

### Implementation for User Story 1

- [X] T041 [US1] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceService.java` implementing `AttendanceStatusCodeQueries`: `listActiveCodes()` reads `AttendanceStatusCodeRepository.findByActiveTrue()`
- [X] T042 [US1] Implement `AttendanceMarkCommands.markAttendance(...)` in `AttendanceService`: validates `statusCode` exists and is active (else `UnknownAttendanceStatusCodeException`), validates `teacherId` exists via `teacher.api.TeacherQueries.exists(teacherId)` (else 404 upstream), rejects `markDate` beyond the current teacher-month (FR-023 — no lower bound otherwise), upserts by `AttendanceMarkRepository.findByTeacherIdAndMarkDate(teacherId, markDate)` — creates a new `AttendanceMark` if absent, else calls its `update(...)` mutator in place (US1 AC4, never a duplicate row); sets `markedBy = actingUserId`, `markedByRole = actingRole`, `markedAt = clock.instant()`; saves; calls `audit.api.AuditWriter.record(...)` with `sourceModule="attendance"`, `entityType="AttendanceMark"`, before/after description including status, fractional value, and school id (FR-006)
- [X] T043 [US1] Implement `AttendanceMarkQueries.markForDate(...)` in `AttendanceService`: `AttendanceMarkRepository.findByTeacherIdAndMarkDate(...)`, mapped to `AttendanceMarkView`
- [X] T044 [US1] Create `backend/src/main/java/com/hls/attendance/internal/AttendanceController.java`: `GET /api/v1/attendance/status-codes` (any authenticated caller); `POST /api/v1/attendance/me/marks` — reads `teacherId` from the JWT's `teacherId` claim (mirrors `TeacherController.getMyProfile`'s pattern), calls `markAttendance(teacherId, request, userId(jwt), MarkedByRole.TEACHER)`; `@ExceptionHandler` mapping `UnknownAttendanceStatusCodeException` → 400
- [X] T045 [P] [US1] Add `AttendanceServiceTest` cases: `markAttendance_createsNewMark_withGivenStatusAndFractionalValue`, `markAttendance_withHalfDayValue_0_5_savesFractionalValue`, `markAttendance_withEvidence_savesGeoTagPhotoAndCheckinCode`, `markAttendance_withNoEvidence_savesSuccessfully`, `markAttendance_sameTeacherAndDateAgain_updatesExistingRow_notADuplicate` (AC4), `markAttendance_recordsAuditEntry_withActorRoleAndTimestamp` (FR-006), `markAttendance_unknownStatusCode_throwsUnknownAttendanceStatusCodeException`, `markAttendance_farInThePast_stillAccepted_whenTeacherMonthUnlocked` (FR-023), `markAttendance_dateBeyondCurrentTeacherMonth_rejected` (FR-023)
- [X] T046 [P] [US1] Add `AttendanceIntegrationTest` cases: `selfMark_savesAndIsImmediatelyRetrievable` (SC-001, AC1), `selfMark_halfDay_reflectedInSavedMark` (AC2), `selfMark_withOptionalEvidence_allFieldsPersisted` (AC3), `selfMark_sameDayTwice_editsInPlace` (AC4), `selfMark_editedMark_priorValueRetrievableViaAuditReader` (FR-006 — confirms the pre-edit value is actually readable back through `audit.api.AuditReader`, not just that an entry was written; `/speckit-analyze` finding Co1)
- [X] T047 [US1] Create `frontend/src/pages/MyAttendancePage/attendanceClient.ts`: `listStatusCodes(accessToken)`, `markMyAttendance(accessToken, markDate, schoolId, statusCode, fractionalValue?, evidence?)`
- [X] T048 [US1] Create `frontend/src/pages/MyAttendancePage/MyAttendancePage.tsx`: date field (defaults to today), status-code dropdown populated from `listStatusCodes`, fractional-value input (default `1.0`, accepts `0.5`), optional evidence inputs (geo lat/lng, photo URL, check-in code — `data-testid="mark-attendance-form"`), submit calls `markMyAttendance`, shows the saved mark's confirmation
- [X] T049 [P] [US1] Create `frontend/src/pages/MyAttendancePage/MyAttendancePage.test.tsx`: cases for marking Present, marking a half-day, marking with/without evidence, and re-marking the same day updating the displayed value

**Checkpoint**: A Teacher can self-mark attendance, with or without evidence, half-day or full-day, any past date within the still-unlocked month, and re-marking the same day edits in place — User Story 1 is independently functional (MVP).

---

## Phase 4: User Story 2 - Manager (or Admin) Marks Attendance on a Teacher's Behalf (Priority: P1)

**Goal**: FR-002/FR-024 — a Manager marks attendance for a Teacher currently accountable to them, denied otherwise, attributed to the Manager; an Admin marks attendance for any Teacher, unscoped, attributed to the Admin.

**Independent Test**: `POST /api/v1/attendance/teachers/{teacherId}/marks` as the Teacher's accountable Manager succeeds and is attributed to the Manager; the same call from an unrelated Manager is denied; the same call as Admin succeeds for any Teacher.

### Implementation for User Story 2

- [X] T050 [US2] Extend `AttendanceController`: `POST /api/v1/attendance/teachers/{teacherId}/marks` — ADMIN role calls `markAttendance(teacherId, request, callerId, MarkedByRole.ADMIN)` unscoped (FR-024, no accountability check); MANAGER role checks `identity.api.ManagerScopeQueries.isAllowedForTeacher(callerId, teacherId)` first (else `AccessDeniedException` → 403, mirroring `TeacherController`'s pattern), then calls `markAttendance(teacherId, request, callerId, MarkedByRole.MANAGER)` — both reuse the same T042 service method, no new service logic, only role-branching in the controller
- [X] T051 [P] [US2] Add `AttendanceServiceTest` cases: `markAttendance_asManager_attributesMarkedByAndRoleToTheManager` (AC1), `markAttendance_managerOverwritesTeacherSelfMark_newValueAttributedToManager_priorRetrievableViaAudit` (AC3), `markAttendance_asAdmin_attributesMarkedByAndRoleToAdmin` (FR-024)
- [X] T052 [P] [US2] Add `AttendanceIntegrationTest` cases: `managerMarksOnBehalf_forAccountableTeacher_succeeds` (AC1), `managerMarksOnBehalf_forNonAccountableTeacher_isDenied403` (AC2), `managerOverwritesTeacherMark_attributionUpdatesToManager` (AC3), `adminMarksOnBehalf_forAnyTeacher_succeeds_unscoped` (FR-024)
- [X] T053 [US2] Create `frontend/src/pages/AttendancePage/attendanceAdminClient.ts`: `markOnBehalf(accessToken, teacherId, markDate, schoolId, statusCode, fractionalValue?, evidence?)`
- [X] T054 [US2] Create `frontend/src/pages/AttendancePage/AttendancePage.tsx`: Manager view — a Teacher picker (their own portfolio) plus the same marking fields as `MyAttendancePage` (`data-testid="mark-on-behalf-form"`), submits via `markOnBehalf`
- [X] T055 [P] [US2] Create `frontend/src/pages/AttendancePage/AttendancePage.test.tsx`: cases for marking on behalf of an accountable Teacher, and a denied attempt for a non-accountable Teacher

**Checkpoint**: User Stories 1 and 2 both work independently — self-marking and Manager/Admin-on-behalf marking are both functional.

---

## Phase 5: User Story 3 - Automatic Monthly Rollup Per Teacher (Priority: P1)

**Goal**: FR-007/FR-008/FR-009/FR-010/FR-015/FR-022 — a live-computed monthly rollup (training total vs. attended, days worked, days leave, overall working days, weighted attendance total, unmarked days, lock status), calendar-aware (a shared non-working date counts automatically, unless an explicit mark overrides it), correctly scoped to Director/accountable-Manager/self.

**Independent Test**: Mark a full month with a known mix of statuses for a Teacher, including a shared calendar non-working date; `GET` the rollup and confirm all figures match the mix exactly; confirm an unrelated Manager is denied.

### Implementation for User Story 3

- [X] T056 [US3] Implement `AttendanceRollupQueries.rollupForMonth(...)` in `AttendanceService`: loads all marks for the Teacher/period via `AttendanceMarkRepository.findByTeacherIdAndMarkDateBetween(...)`, all active non-working dates in the period via `AttendanceNonWorkingDateRepository.findByActiveTrueAndDateBetween(...)` (T027), and all `AttendanceStatusCode`s once to resolve each mark's `category`/`weight`; for every calendar day `d` in `period`, resolves one **effective category** — `d`'s mark category if a mark exists, else `NON_WORKING` if `d` is on the active calendar, else `UNMARKED` (data-model.md, research.md §10) — then computes: `overallWorkingDays` = count of days whose effective category ≠ `NON_WORKING` (FR-009); `daysWorked` = Σ `fractionalValue` over marks with category `WORKED`; `daysLeave` = Σ `fractionalValue` over marks with category `LEAVE`; `trainingDaysTotal` = count of marks with category `TRAINING`, `trainingDaysAttended` = Σ their `fractionalValue`; `unmarkedDays` = count of days whose effective category is `UNMARKED` (FR-010 — a calendar-covered day is never counted here); `weightedAttendanceTotal` = Σ (`fractionalValue` × `weight`) over marks whose category ≠ `NON_WORKING` (research.md §6); `lockStatus` from `AttendanceTeacherMonthLockRepository.findByTeacherIdAndPeriod(...)` (`UNLOCKED` if absent)
- [X] T057 [US3] Implement `AttendanceMarkQueries.marksForMonth(...)` in `AttendanceService`: `AttendanceMarkRepository.findByTeacherIdAndMarkDateBetween(...)`, mapped to `AttendanceMarkView` list
- [X] T058 [US3] Extend `AttendanceController`: `GET /api/v1/attendance/teachers/{teacherId}/months/{period}` (rollup) and `GET /api/v1/attendance/teachers/{teacherId}/months/{period}/marks` (mark list) — both reuse a `canView(jwt, teacherId)` helper (ADMIN/DIRECTOR unscoped, MANAGER via `ManagerScopeQueries.isAllowedForTeacher`, TEACHER via `TeacherScopeQueries.isAllowed`, else 403 — mirrors `TeacherController.canView`, FR-015)
- [X] T059 [P] [US3] Add `AttendanceServiceTest` cases exercising data-model.md's formulas verbatim: `rollupForMonth_daysWorked_sumsFractionalValueOverWorkedCategoryMarks`, `rollupForMonth_daysLeave_sumsFractionalValueOverLeaveCategoryMarks`, `rollupForMonth_trainingDaysTotal_isCountOfTrainingCategoryMarks_attendedIsSumOfTheirFractionalValues`, `rollupForMonth_nonWorkingDaysExcludedFromOverallWorkingDaysAndAllDenominators` (FR-009), `rollupForMonth_unmarkedDaysSurfacedSeparately_neverDefaultedToAStatus` (FR-010), `rollupForMonth_weightedAttendanceTotal_sumsFractionalValueTimesCodeWeight_excludingNonWorking` (research.md §6), `rollupForMonth_reflectsAnAddedOrEditedOrRemovedMarkImmediately` (FR-008), `rollupForMonth_calendarNonWorkingDate_excludedAutomatically_noMarkNeeded` (FR-022), `rollupForMonth_explicitMarkOnCalendarNonWorkingDate_takesPrecedence` (FR-009's override rule, Edge Cases)
- [X] T060 [P] [US3] Add `AttendanceIntegrationTest` cases: `rollup_forAKnownMonthMix_returnsCorrectFiveFigures` (AC1, quickstart.md Scenario 3), `rollup_unrelatedManager_isDenied403` (AC4), `rollup_teacherViewsOwn_succeeds_anotherTeachersRollup_isDenied` (FR-015), `rollup_withCalendarNonWorkingDate_excludesItWithoutAnyMark` (FR-022, quickstart.md Scenario 6), `marksForMonth_evidenceFieldsRideAlongWithMarkVisibility_noNarrowerRuleThanTheMarkItself` (FR-015's evidence-scope sentence — same `canView` gate covers evidence, no separate check exists; `/speckit-analyze` finding Co2)
- [X] T061 [US3] Extend `frontend/src/pages/MyAttendancePage/attendanceClient.ts`: `getMyRollup(accessToken, period)`
- [X] T062 [US3] Extend `MyAttendancePage.tsx`: display the current month's rollup (all figures + lock status, `data-testid="my-rollup"`) below the marking form
- [X] T063 [US3] Extend `frontend/src/pages/AttendancePage/attendanceAdminClient.ts`: `getRollup(accessToken, teacherId, period)`, `listMarks(accessToken, teacherId, period)`
- [X] T064 [US3] Extend `AttendancePage.tsx`: show the selected Teacher's monthly rollup and full mark list (`data-testid="teacher-rollup"`)
- [X] T065 [P] [US3] Extend `MyAttendancePage.test.tsx` and `AttendancePage.test.tsx` with rollup-display cases

**Checkpoint**: User Stories 1-3 all work independently — marking (self and on-behalf) and the calendar-aware live rollup are functional.

---

## Phase 6: User Story 4 - Monthly Lock and Explicit Re-open/Correction (Priority: P2)

**Goal**: FR-011/FR-012/FR-013/FR-014 — locking a teacher-month rejects further direct edits; an explicit, Director-only reopen-with-reason workflow is the only path back to editable, fully recorded.

**Independent Test**: Lock a teacher-month; confirm a direct mark attempt is rejected with a clear message; reopen it with a reason; confirm the correction succeeds; re-lock; confirm the full sequence (original marks, reopen, correction, re-lock) is retrievable.

### Implementation for User Story 4

- [X] T066 [US4] Implement `AttendanceLockCommands.lockMonth(...)` in `AttendanceService`: finds-or-creates the `AttendanceTeacherMonthLock` row for `(teacherId, period)`, sets `status = LOCKED`, `lockedAt = clock.instant()`, `lockedBy = actingUserId`; if an open `AttendanceReopenRecord` exists for this lock (`findByLockIdAndRelockedAtIsNull`), sets its `relockedAt`/`relockedBy` (the re-lock-after-correction case, FR-013 AC4); calls `audit.api.AuditWriter.record(...)` (`action=UPDATED` for a first lock, `action=CORRECTED` for a re-lock)
- [X] T067 [US4] Implement `AttendanceLockCommands.reopenMonth(...)` in `AttendanceService`: requires the current `AttendanceTeacherMonthLock.status = LOCKED` (else throws — mapped to 409, "teacher-month is not currently locked"), sets `status = REOPENED`, appends a new `AttendanceReopenRecord` (`reason`, `reopenedAt = clock.instant()`, `reopenedBy = actingUserId`); calls `audit.api.AuditWriter.record(...)` (`action=CORRECTED`) with the stated reason in the audit summary
- [X] T068 [US4] Implement `AttendanceLockQueries.lockStatus(...)` in `AttendanceService`: builds `LockStatusView` from the `AttendanceTeacherMonthLock` row (or `UNLOCKED` with null `lockedAt`/`lockedBy` if none exists) plus its ordered `AttendanceReopenRecordRepository.findByLockIdOrderByReopenedAtAsc(...)` mapped to `ReopenEntry`
- [X] T069 [US4] Wire `AttendanceService.markAttendance(...)` (T042) to check `AttendanceTeacherMonthLockRepository.findByTeacherIdAndPeriod(teacherId, periodOf(markDate))`: throws `AttendanceMonthLockedException` when `status = LOCKED`, proceeds normally when absent or `REOPENED` (FR-012)
- [X] T070 [US4] Extend `AttendanceController`: `POST /api/v1/attendance/teachers/{teacherId}/months/{period}/lock` and `POST .../reopen` (`@RequestBody {reason}` for reopen) both gated by the same new `requireDirector` helper — Director-only for both, standing in for a future `payroll` run (research.md §2) and matching Constitution Principle II's payroll-approval-adjacent authority boundary (FR-011/FR-014, `/speckit-analyze` finding C1/I1); `@ExceptionHandler` mapping `AttendanceMonthLockedException` → 409 with a clear "attendance for {period} is locked" message (FR-012 AC2), and the reopen's "not currently locked" error → 409
- [X] T071 [P] [US4] Add `AttendanceServiceTest` cases: `lockMonth_thenMarkAttendance_isRejected_withAttendanceMonthLockedException` (AC1/AC2), `reopenMonth_whenNotLocked_isRejected`, `reopenMonth_makesTeacherMonthEditableAgain_appendsReopenRecord` (AC3), `lockMonth_afterReopen_setsRelockedAtAndBy_fullSequenceRetrievableViaLockStatus` (AC4)
- [X] T072 [P] [US4] Add `AttendanceIntegrationTest` cases: `lockedMonth_directMarkAttempt_rejectedWith409ClearMessage` (AC2, quickstart.md Scenario 4), `reopenWorkflow_endToEnd_lockThenReopenThenCorrectThenRelock` (AC3/AC4), `reopenMonth_asAdminOrManager_isDenied403_directorOnly` (FR-014), `lockMonth_asAdminOrManager_isDenied403_directorOnly` (FR-011)
- [X] T073 [US4] Extend `frontend/src/pages/AttendancePage/attendanceAdminClient.ts`: `lockMonth(accessToken, teacherId, period)`, `reopenMonth(accessToken, teacherId, period, reason)`, `getLockStatus(accessToken, teacherId, period)`
- [X] T074 [US4] Extend `AttendancePage.tsx`: Director-only "Lock Month" action and "Reopen" form (`data-testid="reopen-form"`, reason required) — both gated the same way, per T070; displays lock status and full reopen history
- [X] T075 [P] [US4] Extend `AttendancePage.test.tsx` with lock/reopen cases, including role-gating (non-Director cannot see/submit either the lock action or the reopen form)

**Checkpoint**: All four foundational user stories are independently functional.

---

## Phase 7: User Story 5 - Grid View of a Month's Attendance Across Teachers, Editable in Place (Priority: P2)

**Goal**: FR-019/FR-020/FR-021/FR-024/FR-025 — a single Teachers-×-days grid for a selected month, scoped the same as the rollup (Director unscoped or Manager-filtered, Manager limited to their own portfolio), each cell showing that day's status (explicit mark, calendar non-working, or unmarked — research.md §10) or a clear unmarked indicator, with a Manager (own portfolio) or Admin (any Teacher) able to edit a cell in place through the same write path as US1/US2.

**Independent Test**: Mark a mix of attendance for several Teachers across a month, including a calendar non-working date; request the grid; confirm every row/cell matches, that scoping is enforced, and that editing a cell (as Manager/Admin) persists and is rejected when locked.

### Implementation for User Story 5

- [X] T076 [US5] Extend `backend/src/main/java/com/hls/teacher/api/TeacherQueries.java` (existing module, additive change — research.md §8): add `List<TeacherProfileView> findAll()`; implement in `TeacherService` via the already-existing `TeacherProfileRepository.findAll()`, mapped to `TeacherProfileView` the same way `findById` already does
- [X] T077 [P] [US5] Create `backend/src/main/java/com/hls/attendance/api/dto/GridCell.java`: `record GridCell(String statusCode, AttendanceCategory category, BigDecimal fractionalValue, UUID schoolId, boolean editable)` — three states (data-model.md): explicit mark (all four set), calendar non-working with no mark (`category=NON_WORKING`, others null), truly unmarked (all four null); `editable` is `false` when the cell's teacher-month is `LOCKED` or the caller has no write permission for that Teacher, `true` otherwise (FR-025); `schoolId` lets a grid-originated edit resubmit the same school assignment (implementation-time addition)
- [X] T078 [P] [US5] Create `backend/src/main/java/com/hls/attendance/api/dto/AttendanceGridRow.java`: `record AttendanceGridRow(UUID teacherId, String teacherName, Map<LocalDate, GridCell> cells)`
- [X] T079 [P] [US5] Create `backend/src/main/java/com/hls/attendance/api/dto/AttendanceGridView.java`: `record AttendanceGridView(String period, List<LocalDate> days, List<AttendanceGridRow> rows)` — `days` is every calendar day in `period`, so column count always matches the month's actual length (Edge Case)
- [X] T080 [US5] Create `backend/src/main/java/com/hls/attendance/api/AttendanceGridQueries.java`: interface, `AttendanceGridView gridForManager(UUID managerId, String period, boolean callerCanEdit)` (FR-020/FR-021), `AttendanceGridView gridForAllTeachers(String period, boolean callerCanEdit)` (FR-020) — `callerCanEdit` is `true` only when the caller is the Manager themself viewing their own portfolio, or an Admin (FR-024/FR-025); `false` for a Director (viewing, even Manager-filtered — Director gains no new write authority here, research.md §9)
- [X] T081 [US5] Implement `AttendanceGridQueries` in `AttendanceService`: `gridForManager` resolves Teacher ids via `organization.api.AccountabilityQueries.portfolioForManager(managerId)` (filtering `PortfolioItem`s to `ItemType.TEACHER`, research.md §8); `gridForAllTeachers` resolves every Teacher via `teacher.api.TeacherQueries.findAll()` (T076); for each resolved Teacher, loads that Teacher's marks for `period` via `AttendanceMarkRepository.findByTeacherIdAndMarkDateBetween(...)` (T024, already used by T056/T057), the period's active non-working dates via `AttendanceNonWorkingDateRepository.findByActiveTrueAndDateBetween(...)` (T027, already used by T056), and that Teacher-month's lock status via `AttendanceTeacherMonthLockRepository.findByTeacherIdAndPeriod(...)`; builds `cells` keyed by date using the same three-state resolution as T056's rollup (explicit mark → calendar non-working → unmarked), each cell's `editable = callerCanEdit && lockStatus != LOCKED` (research.md §9/§10)
- [X] T082 [US5] Extend `AttendanceController`: `GET /api/v1/attendance/grid?period=&managerId=` — ADMIN with no `managerId` calls `gridForAllTeachers(period, callerCanEdit=true)`; ADMIN with `managerId` calls `gridForManager(managerId, period, callerCanEdit=true)`; DIRECTOR calls either with `callerCanEdit=false`; MANAGER calls `gridForManager(callerId, period, callerCanEdit=true)` and is denied (403) if a `managerId` other than their own id is passed; TEACHER role is denied (403) — this endpoint has no self-view shape (per contracts/attendance-api.yaml)
- [X] T083 [P] [US5] Add `AttendanceServiceTest` cases: `gridForAllTeachers_includesEveryTeacher_cellsMatchTheirIndividualMarks`, `gridForManager_includesOnlyThatManagersCurrentPortfolio`, `grid_dayWithNoMark_cellIsNull_neverDefaultedToAStatus` (AC3), `grid_columnCount_matchesActualDaysInSelectedMonth` (Edge Case — 28/29/30/31), `grid_calendarNonWorkingDate_cellShowsNonWorkingCategory_noStatusCode` (FR-022, research.md §10), `grid_asAdmin_cellsAreEditableWhenUnlocked` (AC5), `grid_lockedTeacherMonth_cellsAreNotEditable_regardlessOfCallerCanEdit` (AC6), `grid_asDirector_cellsAreNeverEditable_evenWhenUnlocked` (research.md §9)
- [X] T084 [P] [US5] Add `AttendanceIntegrationTest` cases: `grid_asDirector_noFilter_returnsEveryTeacher` (AC1), `grid_asDirector_filteredByManager_returnsOnlyThatPortfolio` (AC4), `grid_asManager_returnsOnlyOwnPortfolio_neverAnotherManagersTeachers` (AC2), `grid_asManager_passingAnotherManagersId_isDenied403`, `grid_asTeacher_isDenied403`, `gridCellEdit_asManagerOrAdmin_persistsThroughMarkOnBehalfEndpoint_andGridReflectsItImmediately` (AC5), `gridCellEdit_lockedMonth_rejected409_cellShowsNotEditable` (AC6)
- [X] T085 [US5] Extend `frontend/src/pages/AttendancePage/attendanceAdminClient.ts`: `getAttendanceGrid(accessToken, period, managerId?)` (response includes each cell's `editable`)
- [X] T086 [US5] Create `frontend/src/pages/AttendancePage/AttendanceGrid.tsx`: datagrid component (`data-testid="attendance-grid"`) — one row per Teacher (`teacherName`), one column per day in `days`, each cell showing `cells[date].statusCode` (a distinct visual for `category="NON_WORKING"` with no `statusCode`, and another for truly unmarked when null); a month selector and, for Director/Admin, a Manager-filter dropdown; a cell with `editable: true` opens an inline status/fractional-value editor on click (`data-testid="grid-cell-editor"`) that calls `attendanceAdminClient.markOnBehalf(...)` (T053) and refreshes that cell on success; a cell with `editable: false` renders visibly disabled/read-only (no click handler), with a tooltip noting the teacher-month is locked when that's the reason
- [X] T087 [US5] Extend `AttendancePage.tsx`: render `<AttendanceGrid>` for Director/Manager/Admin roles, hidden for Teacher (this endpoint has no Teacher-scoped shape)
- [X] T088 [P] [US5] Create `frontend/src/pages/AttendancePage/AttendanceGrid.test.tsx`: cases for row/column rendering, the calendar-non-working cell's distinct display, the unmarked-cell indicator, the Director's Manager filter, editing an `editable: true` cell (as Manager and as Admin) and seeing it persist, and confirming an `editable: false` cell offers no edit interaction

**Checkpoint**: All five user stories are independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Admin/Director status-code configuration (FR-005), the Non-Working Calendar's admin-facing management (FR-022), export (FR-018), and end-to-end validation.

- [X] T089 [P] Implement `AttendanceStatusCodeCommands.createStatusCode(...)` in `AttendanceService`: persists a new `AttendanceStatusCode` (`active = true`), calls `audit.api.AuditWriter.record(...)`; extend `AttendanceController` with `POST /api/v1/attendance/status-codes` (Admin **or** Director, new `requireAdminOrDirector` helper — FR-005)
- [X] T090 [P] Add `AttendanceServiceTest`/`AttendanceIntegrationTest` cases: `createStatusCode_asAdmin_succeeds_andAppearsInListActiveCodes`, `createStatusCode_asDirector_succeeds` (FR-005), `createStatusCode_asManagerOrTeacher_isDenied403`
- [X] T091 Implement `AttendanceNonWorkingCalendarQueries.datesForMonth(...)` and `AttendanceNonWorkingCalendarCommands.addNonWorkingDate(...)`/`deactivateNonWorkingDate(...)` in `AttendanceService` (using `AttendanceNonWorkingDateRepository`, T027); extend `AttendanceController` with `GET /api/v1/attendance/non-working-dates?period=` (any authenticated caller), `POST /api/v1/attendance/non-working-dates` (Admin-only, new `requireAdmin` helper, FR-022), `POST /api/v1/attendance/non-working-dates/{id}/deactivate` (Admin-only); every add/deactivate calls `audit.api.AuditWriter.record(...)`
- [X] T092 [P] Add `AttendanceServiceTest` cases: `addNonWorkingDate_asAdmin_succeeds_appearsInDatesForMonth`, `addNonWorkingDate_asDirectorOrManager_isDenied403` (FR-022 is Admin-only, unlike FR-005), `deactivateNonWorkingDate_stopsItApplyingGoingForward_neverDeleted`
- [X] T093 [P] Add `AttendanceIntegrationTest` cases: `nonWorkingDateEndToEnd_addThenRollupAndGridExcludeItAutomatically` (FR-022, quickstart.md Scenario 6), `nonWorkingDate_explicitMarkOnSameDate_overridesCalendar_forThatTeacherOnly` (Edge Cases)
- [X] T094 Extend `frontend/src/pages/AttendancePage/attendanceAdminClient.ts` (`listNonWorkingDates`, `addNonWorkingDate`, `deactivateNonWorkingDate`) and `AttendancePage.tsx` (Admin-only non-working-calendar management section: list, add-date form, deactivate action — `data-testid="non-working-calendar"`)
- [X] T095 [P] Extend `AttendancePage.test.tsx` with non-working-calendar management cases (add, deactivate, non-Admin denied)
- [X] T096 Implement CSV export: `GET /api/v1/attendance/teachers/{teacherId}/months/{period}/export` (same `canView` scoping as the rollup, FR-015), returning the month's marks and rollup figures as `text/csv` (FR-018, Constitution's export requirement)
- [X] T097 [P] Add `AttendanceIntegrationTest` case: `exportMonth_returnsCsvWithMarksAndRollupFigures_sameScopingAsRollup`
- [X] T098 [P] Run quickstart.md's six scenarios manually (or confirm via the automated equivalents in T046/T052/T060/T072/T084/T093)
- [X] T099 Update `docs/HLS SDD Implementation Plan & Deliverables Tracker.md` row 9 ("Attendance module") status once this feature is fully implemented and tested
- [X] T100 Run the full backend (`mvn test`) and frontend (`npx vitest run`, `npx eslint .`, `npx tsc -b`) suites to confirm no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Phase 2 only
- **User Story 2 (Phase 4)**: Depends on Phase 2 and directly extends US1's `markAttendance` (T042) and `AttendanceController` (T044) — in practice follows US1, though it adds no new service logic
- **User Story 3 (Phase 5)**: Depends on Phase 2 only (rollup reads marks and the non-working calendar however they got there) — independently implementable in parallel with US1/US2, though most realistic to verify after marks exist
- **User Story 4 (Phase 6)**: Depends on Phase 2 and on US1's `markAttendance` (T042, extended by T069 to add the lock check) — independent of US2 and US3
- **User Story 5 (Phase 7)**: Depends on Phase 2 and reads marks/calendar however US1-4 produced them (no code dependency on US1-4's tasks); T076's `teacher.api` extension is its own small prerequisite within this phase
- **Polish (Phase 8)**: Depends on all five user stories being complete

### Parallel Opportunities

- T002/T003 (Setup) — different files
- T005-T038 (Foundational DTOs/entities/repositories/interfaces) — almost all different files, marked `[P]`
- T045/T046 (US1 tests), T051/T052 (US2 tests), T059/T060 (US3 tests), T071/T072 (US4 tests), T083/T084 (US5 tests) — each pair touches different test files
- US3 (rollup) and US5 (grid) can both be developed in parallel with US2 (manager marking) once Foundational is done, since none depends on another's code
- T077/T078/T079 (US5 DTOs) — different files
- T089/T090, T091/T092/T093 (Polish, calendar admin) — independent additions, different files

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (Teacher self-marking)
4. **STOP and VALIDATE**: a Teacher can mark and see their own attendance immediately, half-day and evidence both work, any past date within the unlocked month is accepted, re-marking a day edits in place (SC-001)

### Incremental Delivery

1. Setup + Foundational → all five tables ready (including the Non-Working Calendar), module boundary enforced
2. User Story 1 → Teacher self-marking (MVP)
3. User Story 2 → Manager/Admin marks on behalf — closes the gap self-marking alone leaves
4. User Story 3 → the calendar-aware monthly rollup — the actual spreadsheet-replacement deliverable
5. User Story 4 → lock + reopen/correction — the financial-integrity guarantee
6. User Story 5 → the Teachers-×-days grid, editable in place — the at-a-glance, multi-teacher view the paper/spreadsheet register was actually read (and corrected) as, day to day
7. Polish → status-code admin config, non-working-calendar management, CSV export, quickstart validation, tracker update

## Notes

- No task creates a mobile (React Native) app — this codebase currently has no mobile client repository; `MyAttendancePage`/`AttendancePage` are web pages, the same stand-in specs/005-teacher's `MyProfilePage` already established for teacher self-service ahead of a dedicated mobile app existing.
- T076 is the one task in this file that edits an existing module (`teacher`), not `attendance` — a small, additive, non-breaking interface extension (research.md §8), the same kind of change specs/009 already made to `teacher.api` from outside its original spec.
- FR-016 (offline mobile capture with temporary local storage) is **not implemented by this task list** — it requires a mobile app shell that doesn't exist yet in this repository. This is a known, documented gap, not an oversight; it should be picked up once a mobile client project exists.
- Every FR through FR-025 now has at least one implementing task — no outstanding gap remains between spec.md and this task list.
- Commit after each task or logical group, consistent with this repo's established practice.
