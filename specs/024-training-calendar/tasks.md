---

description: "Task list for Training Calendar (Weekend Refreshers and Monthly Sessions)"
---

# Tasks: Training Calendar

**Input**: Design documents from `/specs/024-training-calendar/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/training-api.md, contracts/planned-activity-feed.md,
quickstart.md, and specs 005, 008, 016, 032, 035 (all on `main`).

**Decisions accepted by the user on 2026-10-24** (spec.md Clarifications): OPERATIONS items for Admin, Director and Manager and
MY ATTENDANCE items for Teacher (no new sections); one C1 entry per session with the assigned Managers as attendees; Admin and
Director seeded on `TRAINING_ATTENDANCE`; on a clash with a school mark the training row is saved and nothing is written to 008.

**DECIDED 2026-10-24**: a Manager's attended training day counts as worked in Manager pay (spec 032; whole 1.0, half 0.5, only
when there is no check-in). It is plan.md Open Item 1. Task **T080** is the 032 amendment and is now open to build. No other
task depends on it, and no task other than T080 may add a payroll or pay effect for training.

**Tests**: first-class tasks (Constitution Principle IX), written before the code they test: pattern date rules, flag
derivation, repeat counting, the 12-month generation run (SC-001), 100-person assign and attendance passes (SC-002, SC-008),
hand-counted session counts (SC-003), the 008 mark after record and correction (SC-004), the two-Manager and two-Teacher scope
matrix (SC-006), the 035 feed (SC-007), audit (SC-009), ArchUnit, frontend grants.

**Organization**: grouped by user story (spec.md US1-US7). Order: Setup, Foundational, US1 (sessions and registration, MVP), US2
(patterns), US3 (assignment), US4 (attendance), US5 (exceptions), US6 (history), US7 (C1 feed, T-REPLACE, upcoming training,
reminders), the 032 amendment, Polish with the manual walkthrough last.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US7; absent for Setup/Foundational/032 amendment/Polish

## Path Conventions

`B` = `backend/src/main/java/com/hls`, `T` = `backend/src/test/java/com/hls`, `TC` = `B/training/calendar`,
`FE` = `frontend/src/features/training`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); Docker must be running for
integration tests. Migration in `backend/src/main/resources/db/migration`.

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites and STOP and report if any is missing (do not stub): `attendance.api.TrainingAttendance`, `AttendanceReadApi` (`marksOf`, `isLocked`, `teachersAwayOn`), `WorkingCalendar`; `teacher.api.TeacherDirectory`, `TeacherScopeQueries`, `TeacherPlacementSource.teacherIdsAtSchoolsOn`; `organization.api.ManagerQueries`; `school.api.SchoolDirectory`, `ChangeRecorder`; `activitycalendar.internal.TrainingSource` placeholder; `PermissionModule`, `NavigationCatalog`, `AuditVisibility`, `SettingKey`; and the newest Flyway number on main (V90 on 2026-10-24; spec 031 plans V100; this spec plans V110, re-check open branches and renumber at merge). Record any name that differs in research.md section 1
- [X] T002 Create the package skeleton `TC/internal`, `TC/web` (with `package-info.java`) and extend the `training` module rules test `T/training/TrainingModuleRulesTest.java` (ArchUnit): nothing outside `training` reads `training.calendar.internal`; `attendance` does not depend on `training`; `training.calendar` uses no other module's `internal`
- [X] T003 [P] Create `FE/trainingApi.ts` with types and calls for the endpoints in contracts/training-api.md (dates ISO, months `yyyy-MM`) (sessions and registrations done with US1; later stories add their calls)

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: migration, permissions, navigation, audit, settings, notification types, scope and ports. No story can start before this.

### Migration and model

- [X] T004 Write `V110__create_training_calendar.sql` per data-model.md: `training_pattern`, `training_pattern_skip`, `training_session` (unique `(pattern_id, pattern_month)`), `training_session_history` and `training_attendance_history` (insert-only triggers), `training_registration`, `training_assignment`, `training_attendance_entry`, `training_exception`, `training_reminder_sent`, with the checks, unique constraints and indexes listed there
- [X] T005 [P] Migration test `T/training/calendar/TrainingMigrationTest.java`: constraints (one assignment, registration, entry and exception per person per session; one session per pattern and month; time and rule checks) and that UPDATE and DELETE of the two history tables are refused
- [X] T006 Create the JPA entities and repositories in `TC/internal/` for every table of T004 (`TrainingSession`, `TrainingPattern`, `PatternSkip`, `SessionHistory`, `Registration`, `Assignment`, `AttendanceEntry`, `AttendanceHistory`, `TrainingException`, `ReminderSent`), mirroring the columns; optimistic `version` where the data model lists it. Depends on T004

### Permissions, navigation, audit, settings, notifications

- [X] T007 [P] Add `TRAINING(VIEW, CREATE, EDIT, DELETE, EXPORT)`, `TRAINING_ATTENDANCE(VIEW, EDIT)` and `TRAINING_REGISTRATION(VIEW, CREATE, EDIT)` to `B/identity/permissions/PermissionModule.java`; in `PermissionEligibility`: `TRAINING` for Admin, Director (all) and Manager (View only), `TRAINING_ATTENDANCE` for Admin, Director, Manager, `TRAINING_REGISTRATION` for Teacher only; all three added to `BUSINESS_MODULES` (System none); seeds in `PermissionMatrixService` (Admin and Director full on the first two; Manager `TRAINING` View and `TRAINING_ATTENDANCE` View, Edit; Teacher `TRAINING_REGISTRATION` View, Create, Edit); data-scope labels in `AccessModelService` (ORG_WIDE, ASSIGNED, OWN)
- [X] T008 [P] Add navigation items to `B/identity/accessmodel/NavigationCatalog.java`: OPERATIONS "Training Calendar" `/operations/training` and "Training Exceptions" `/operations/training/exceptions` (Admin, Director); OPERATIONS "My Training" `/operations/my-training` (Manager); MY ATTENDANCE "Upcoming Training" `/my-attendance/training` and "My Training History" `/my-attendance/training/history` (Teacher). The section order does not change
- [X] T009 Extend `PermissionEligibilityTest` and the access-model and `NavigationSectionOrderTest` tests under `T/identity/` for the three modules (Manager cannot be granted a `TRAINING` write; Teacher and System refused `TRAINING` and `TRAINING_ATTENDANCE`; only Teacher holds `TRAINING_REGISTRATION`; right menu per role; section order unchanged). Depends on T007, T008
- [X] T010 [P] Add entity types `TRAINING_SESSION`, `TRAINING_PATTERN`, `TRAINING_ASSIGNMENT`, `TRAINING_REGISTRATION`, `TRAINING_ATTENDANCE`, `TRAINING_EXCEPTION` to `B/audit/support/AuditVisibility.java` against `TRAINING`, with a test in the audit module tests
- [X] T011 [P] Add the six keys of research.md section 8 to `B/settings/internal/SettingKey.java` (group SYSTEM, INTEGER, range and default), the port `B/training/api/TrainingSettings.java` and its implementation in `B/settings/internal/` (the `RuntimeSettings` pattern); fix any settings test that counts the catalog and run `SettingKeyTest`
- [X] T012 [P] Add `TRAINING_ASSIGNED`, `TRAINING_ASSIGNMENT_REMOVED`, `TRAINING_CHANGED`, `TRAINING_REMINDER`, `TRAINING_REGISTERED`, `TRAINING_EXCEPTION`, `TRAINING_CANNOT_ATTEND` to `B/notification/api/NotificationType.java`; create the events in `B/training/api/` (contracts section 7), `B/notification/internal/TrainingEventListener.java` (follows `TransferEventListener`) and the texts in `MessageFactory`; unit test of recipients and texts
- [X] T013 Implement `TC/internal/TrainingScope.java` (caller roles to org-wide, or a Manager's Teachers from `TeacherScopeQueries` plus the Manager's own id; Teacher's own id) and its unit test `T/training/calendar/TrainingScopeTest.java` (two Managers, two Teachers; out of scope gives 404 semantics). Depends on T006
- [X] T014 Create `TC/web/TrainingExceptionAdvice.java` (400, 403, 404, 409 in the existing error shape) and the shared request/response records used by the controllers

**Checkpoint**: tables, grants, menus, settings, notification types and scope exist; nothing user-visible yet.

---

## Phase 3: User Story 1 - Schedule Weekend Refresher Sessions and Teachers Register (Priority: P1) MVP

**Goal**: Admin or Director schedules and publishes a session; Teachers register within the seat limit; the roster is right.

**Independent Test**: quickstart section 1.

### Tests for US1 (write first)

- [X] T015 [P] [US1] Unit test `T/training/calendar/SessionRulesTest.java`: status transitions (Draft, Published, Held, Cancelled, Skipped), what may be edited or moved in each, only Draft deletable, `ends_at > starts_at`, https-only venue link, text length caps
- [X] T016 [P] [US1] Integration test `T/training/calendar/RegistrationTest.java` (seat limit 3): three register, the fourth is refused "no seats left", one cancels and the fourth registers; duplicate registration is idempotent; cancel after start refused; IN_TRAINING, EXITED and ON_LEAVE Teachers refused; a concurrent test of 10 parallel registrations for 3 seats ends with exactly 3 (Principle VIII)
- [X] T017 [P] [US1] API/role test `T/training/calendar/SessionApiTest.java`: Admin and Director create, edit, publish, move (reason required, registrations kept, earlier date in history), cancel (reason, people notified) and delete a draft; Manager and Teacher get 403 on writes; stale `version` gives 409; every write produces a Change History entry (SC-009)
- [X] T018 [P] [US1] Scope test `T/training/calendar/SessionScopeTest.java` (SC-006): a Teacher sees only open and own sessions; a Manager sees only sessions that include their Teachers or themselves; another Manager's session id answers 404

### Implementation for US1

- [X] T019 [US1] Implement `TC/internal/SessionService.java` (create, edit, publish, move with history rows per changed field, cancel, delete draft; `hand_edited`; notifications through events; `ChangeRecorder` calls) and `SessionQueries.java` (list with filters, detail with counts, `actions`, holiday notice through `WorkingCalendar`, derived "attendance not recorded"). Depends on T006, T012, T013
- [X] T020 [US1] Implement `TC/internal/RegistrationService.java` (pessimistic lock on the session row, seat check, unique row flip, Teacher ACTIVE check, start-time cut-off, Admin or Director on behalf) and the roster read. Depends on T019
- [X] T021 [US1] Implement `TC/web/SessionController.java` (sessions and registrations endpoints of contracts/training-api.md; `TRAINING` and `TRAINING_REGISTRATION` grants re-checked on the server). Depends on T019, T020
- [X] T022 [US1] Frontend: `FE/TrainingCalendarPage.tsx` (month grid and list, filters, holiday notice, status chips), `FE/SessionDialog.tsx`, `FE/SessionDetail.tsx` (roster, registration controls, history of earlier dates, `actions`-driven buttons), `FE/UpcomingTrainingPage.tsx` (Teacher register and cancel), routes in `frontend/src/App.tsx` guarded by `TRAINING` and `TRAINING_REGISTRATION`. Depends on T003, T021
- [X] T023 [P] [US1] Frontend test `FE/__tests__/SessionsAndRegistration.test.tsx`: Admin dialog validation, Teacher register and cancel, full session message, menu labels per role

**Checkpoint**: US1 works and is the MVP.

---

## Phase 4: User Story 2 - The Standing Monthly Training Calendar (Priority: P1)

**Goal**: a standing pattern creates each month's session with no further action.

**Independent Test**: quickstart section 2.

### Tests for US2 (write first)

- [X] T024 [P] [US2] Unit test `T/training/calendar/PatternRulesTest.java`: nth weekday (first to fourth, last), fixed day with month-end clamp (31st in a 30-day month, February, leap year), year boundary, preview notes
- [X] T025 [P] [US2] Integration test `T/training/calendar/GenerationTest.java` (SC-001): 12-month simulated run with the job executed daily and twice per day creates one session per month, none missed, none duplicated; the horizon keeps the next three months present; a start month in the past does not backfill; skipped month becomes a SKIPPED row with the reason
- [X] T026 [P] [US2] Integration test `T/training/calendar/PatternEditTest.java`: editing one generated session sets `hand_edited` and leaves the pattern and other months alone; editing the pattern from month M rewrites only untouched, not-held sessions of M and later and keeps their assignments and registrations; stop from month M removes untouched later sessions and keeps held and hand-edited ones; a duplicate rule and time is refused; a pattern on a holiday still creates the session and the detail carries the notice

### Implementation for US2

- [X] T027 [US2] Implement `TC/internal/PatternRules.java` (date maths and preview) and `PatternService.java` (create, preview, edit from month, skip and unskip, stop; `ChangeRecorder`). Depends on T006, T019
- [X] T028 [US2] Implement `TC/internal/GenerationService.java` (idempotent materialisation up to the horizon from `TrainingSettings`, default group snapshot applied through `GroupResolver` from T034 once it exists, otherwise skipped) and `GenerationJob.java` (daily 03:10 in the business zone, `hls.training.generation-job.enabled` switch for tests). Depends on T027
- [X] T029 [US2] Implement `TC/web/PatternController.java` (patterns, preview, skips, stop). Depends on T027
- [X] T030 [US2] Frontend: `FE/PatternDialog.tsx` (rule picker, live preview with clamp and holiday notes, default group, from-month edit, skip and stop dialogs) and a Patterns tab on `TrainingCalendarPage`. Depends on T029
- [X] T031 [P] [US2] Frontend test `FE/__tests__/Patterns.test.tsx`: preview rendered, edit-from-month confirmation, skip with reason required

**Checkpoint**: US1 and US2 give the full calendar.

---

## Phase 5: User Story 3 - Assign Teachers and Managers (Priority: P1)

**Goal**: assignments create exactly one obligation per person, individually or by group.

**Independent Test**: quickstart section 3.

### Tests for US3 (write first)

- [X] T032 [P] [US3] Unit test `T/training/calendar/GroupResolverTest.java`: individual, School, Manager, Zone, all Teachers, all Managers, all; IN_TRAINING and EXITED refused with reasons; ON_LEAVE only by individual pick; group is a snapshot (later moves do not change it)
- [X] T033 [P] [US3] Integration test `T/training/calendar/AssignmentTest.java` (SC-002): 100 people assigned in under a minute with one obligation and one notification each; assigning twice (individually and through a group) keeps one row; removal before HELD notifies and flips the row; removal after HELD refused; assignment to a weekend session makes only those people Required; a "cannot attend" note notifies Admin and Director and changes nothing; the exit clean-up removes the obligation of an exited Teacher on a not-held session (T038)

### Implementation for US3

- [X] T034 [US3] Implement `TC/internal/GroupResolver.java` (through `TeacherPlacementSource`, `SchoolDirectory`, `ManagerQueries`, `TeacherDirectory`) and `AssignmentService.java` (preview, assign, remove, cannot-attend, "group not fully assigned" count, events). Depends on T006, T012
- [X] T035 [US3] Extend `SessionController` with the assignment endpoints and connect `GenerationService` to `GroupResolver`. Depends on T034
- [X] T036 [US3] Frontend: `FE/AssignDialog.tsx` (pick people or group, preview counts and refusals, confirm), assignments list on `SessionDetail` with remove, "N people in the group are not assigned" with "Add the group again", and the "I cannot attend" action on the person's own list. Depends on T035
- [X] T037 [P] [US3] Frontend test `FE/__tests__/Assignment.test.tsx`
- [X] T038 [US3] Implement `TC/internal/ReconcileJob.java` (daily 03:30: remove obligations and registrations of EXITED Teachers and inactive Managers on not-held sessions with a note and an Admin and Director notice; retry `CLASH` syncs up to 31 days old once US4 exists; switch for tests) and its test `T/training/calendar/ReconcileJobTest.java`. Depends on T034

**Checkpoint**: sessions have obligations.

---

## Phase 6: User Story 4 - Record Who Attended (Priority: P1)

**Goal**: expected versus attended, with Teacher days written to 008.

**Independent Test**: quickstart section 4.

### Tests for US4 (write first)

- [X] T039 [P] [US4] Unit test `T/training/calendar/AttendanceBridgeTest.java` (mock ports): Present whole or half calls `markTrainingDay` with 1.00 or 0.50; correction away from Present calls `clearTrainingDay`; a locked month refuses the row (no write call); a different existing mark gives `CLASH` and no write call; a training mark already present is updated; `markTrainingDay` is never called inside a failing pre-check
- [X] T040 [P] [US4] Integration test `T/training/calendar/AttendanceRecordTest.java` (SC-003, SC-004, SC-008): a prepared session (6 assigned, 2 registered, one on leave, one walk-in, one excused, one absent) gives exactly the hand-counted expected, attended, absent, excused, walk-in and no-show counts; the Teachers' days appear in 008 as training days with the right value, also after a correction, with history in both places; a clash row saves with a clash note and 008 is unchanged; the sync endpoint writes the mark after the school mark is removed; a locked month refuses only that row; 100 rows save in under 3 seconds in one pass; the first save sets HELD; future-dated session refused
- [X] T041 [P] [US4] Scope test `T/training/calendar/AttendanceScopeTest.java` (SC-006): a Manager records only for Teachers placed under them; for another Manager's Teacher, for a Manager (including themselves) the row is refused `NOT_IN_SCOPE`; Admin and Director record for anyone; Teacher 403; internal remarks absent from any Teacher response
- [X] T042 [P] [US4] Test `T/training/calendar/OnLeaveTest.java`: a Teacher with approved leave on the date and a Manager with leave (T044) show `onLeave` and are never put on the exceptions list

### Implementation for US4

- [X] T043 [US4] Implement `TC/internal/AttendanceBridge.java` (pre-checks with `AttendanceReadApi`, then `TrainingAttendance`; returns SAVED, SAVED_CLASH or REFUSED with the 008 message) and `AttendanceRecordService.java` (bulk save in one transaction with per-row results, walk-ins, entry and history rows, HELD on first save, on-leave detection, sync retry, counts, `ChangeRecorder`). Depends on T006, T013, T034
- [X] T044 [P] [US4] Add `managersOnLeaveOn(Collection<UUID> managerIds, LocalDate date)` to `B/attendance/api/ManagerAttendanceReadApi.java` with its implementation over 032's day rows in `B/attendance/manager/internal/ManagerAttendanceReadApiImpl.java` and a test `T/attendance/manager/ManagersOnLeaveOnTest.java`; the existing 032 tests pass unchanged
- [X] T045 [US4] Implement `TC/web/AttendanceController.java` (`TRAINING_ATTENDANCE` View and Edit) with the roster, bulk save, walk-in and sync endpoints. Depends on T043
- [X] T046 [US4] Frontend: `FE/AttendanceGrid.tsx` (rows with outcome, whole or half day, reason, certificate note, internal remark for staff only, on-leave and clash badges, per-row result after save, walk-in dialog, the 008 lock message shown on a refused row). Depends on T045
- [X] T047 [P] [US4] Frontend test `FE/__tests__/AttendanceGrid.test.tsx` (Manager sees only own Teachers; results display; reason required for Absent and Excused)

**Checkpoint**: expected versus attended works end to end.

---

## Phase 7: User Story 5 - Non-Compliance Exceptions (Priority: P2)

**Independent Test**: quickstart section 5, first two steps.

### Tests for US5 (write first)

- [ ] T048 [P] [US5] Unit test `T/training/calendar/RepeatRuleTest.java`: Open and Acknowledged exceptions count; Excused, Made up and Withdrawn do not; window of 6 months and threshold 2 from settings; boundary dates
- [ ] T049 [P] [US5] Integration test `T/training/calendar/ExceptionTest.java`: Absent for an assigned person raises one Open exception; a registered no-show raises none; on-leave raises none; correcting the absence withdraws it and correcting back reopens it; close as Excused (reason), Made up (a later Present session required) and Acknowledged (note); closer and time kept; a Manager sees only own Teachers' exceptions and cannot close; a Teacher gets 403; filters by month, Zone, Manager, role and status; export for Admin and Director only; a new exception notifies Admin and Director

### Implementation for US5

- [ ] T050 [US5] Implement `TC/internal/ExceptionService.java` (raise and withdraw from `AttendanceRecordService`, close, list with repeat marker, CSV export) and `TC/web/ExceptionController.java`. Depends on T043
- [ ] T051 [US5] Frontend: `FE/ExceptionsPage.tsx` (filters, repeat mark, close dialog) and the My Training exceptions panel for the Manager. Depends on T050
- [ ] T052 [P] [US5] Frontend test `FE/__tests__/Exceptions.test.tsx`

---

## Phase 8: User Story 6 - Training Completion History (Priority: P2)

**Independent Test**: quickstart section 5, third step.

### Tests for US6 (write first)

- [ ] T053 [P] [US6] Integration test `T/training/calendar/HistoryTest.java`: a Teacher with an induction, three monthly sessions (two attended, one absent and excused) and one weekend session shows each session once newest first, the induction entry read-only, the yearly attended versus expected figure by the rule in research.md section 10; Teacher own history hides internal remarks and exceptions but shows the certificate note; Manager reaches own Teachers and self, another Manager's Teacher gives 404; empty history returns an empty state not an error; CSV export Admin and Director only

### Implementation for US6

- [ ] T054 [US6] Implement `TC/internal/HistoryQueries.java` (union of entries, assignments and registrations plus the 016 induction rows, yearly figure) and `TC/web/HistoryController.java` (`/people/...`, `/me/history`, exports). Depends on T043
- [ ] T055 [US6] Frontend: `FE/HistoryPage.tsx` (person history with year picker, used by Admin and Director from the calendar, by the Manager as My Training, by the Teacher as My Training History) and `FE/MyTrainingPage.tsx`. Depends on T054
- [ ] T056 [P] [US6] Frontend test `FE/__tests__/History.test.tsx`

---

## Phase 9: User Story 7 - Planned-Activity Feed, 035 Source, Upcoming Training and Reminders (Priority: P2)

**Independent Test**: quickstart section 6.

### Tests for US7 (write first)

- [ ] T057 [P] [US7] Contract test `T/training/calendar/TrainingActivitiesFeedTest.java`: one entry per published session; owner is the creator and attendees the assigned Managers only (no Teachers); statuses PLANNED, HELD, CANCELLED; Draft and Skipped never returned; `completedOn` is the session date for HELD; `originalDate` after one and after two moves; a user filter matches owner or attendee; no topic, trainer, roster or remark exposed; `plannedBetween` returns published sessions only; a fixed number of queries for 200 sessions
- [ ] T058 [P] [US7] Test `T/activitycalendar/TrainingSourceTest.java` (SC-007): the real `TrainingSource` reports `available() == true`, maps entries to `CalendarEntry` kind `TRAINING` with sub-kind, and a Manager sees a session they are an attendee of on its date with the right status and not another Manager's; the Admin owner never appears in `byManager`
- [ ] T059 [P] [US7] Test `T/training/calendar/ReminderJobTest.java`: reminder at the configured offsets once per person and offset (second run sends nothing); a moved session clears keys and sends a change notice; a cancelled session sends a change notice and no reminder; setting 0 disables the second reminder
- [ ] T060 [P] [US7] Test `T/training/calendar/UpcomingTrainingTest.java`: a Teacher sees Required and Open (free seat) sessions in date order; a Manager sees only Required; full or started open sessions are not offered

### Implementation for US7

- [ ] T061 [US7] Create `B/training/api/TrainingActivities.java` (records and two methods of contracts/planned-activity-feed.md section 1) and `TC/internal/TrainingActivitiesImpl.java` over `training_session` and `training_session_history`. Depends on T006, T019, T034
- [ ] T062 [US7] **T-REPLACE**: replace the placeholder `B/activitycalendar/internal/TrainingSource.java` (today `available() == false` and no entries) by a real source over `TrainingActivities.activitiesBetween` (contract section 2), add the `TRAINING` case to `ActivityCalendarService.linkFor` (module `TRAINING`, route `/operations/training`), remove the "Training is not included yet" path for TRAINING, and update `SourceAdaptersTest`, `SourceResilienceTest`, `CalendarMonthIntegrationTest` and the 035 frontend tests that expect "not included yet". Depends on T061
- [ ] T063 [US7] Create `B/attendance/api/ManagerTrainingDays.java` (contract section 3), its implementation `TC/internal/ManagerTrainingDaysImpl.java`, and add training sessions to `B/attendance/manager/internal/PlannedActivities.java` `forDay` through the port (kind `TRAINING`); extend the 032 Field day link tests so a Manager sees an assigned session as a choice and every existing link test passes unchanged. Reading `attendedDays` into Manager pay is NOT part of this task (T080). Depends on T061
- [ ] T064 [US7] Implement `TC/internal/ReminderJob.java` (daily 07:30 in the business zone, `training_reminder_sent` keys, settings offsets, events) and `TC/web/MyTrainingController.java` (`/me/upcoming`). Depends on T034, T012
- [ ] T065 [US7] Frontend: Required and Open labels, reminders-free list in `UpcomingTrainingPage` and the Manager "My Training" upcoming list; extend the 035 calendar link test for Training. Depends on T064
- [ ] T066 [P] [US7] Optional separate task **T-015** (do only if the user asks): offer training sessions as a link for a Training Stay claim in `B/expenses/internal/LinkResolver.java` and `LinkKinds` through a small `training.api.TrainingSessions` read; otherwise leave undone and say so in the walkthrough

---

## Phase 10: Spec 032 amendment (decided: attended training day counts as worked)

- [ ] T080 **Decided (user, 2026-10-24): yes, it counts as worked.** Spec 032 amendment: a Manager's attended training day (`ManagerTrainingDays.attendedDays`, whole 1.0, half 0.5, only when there is no check-in) counts as worked in `ManagerMonth` and therefore in Manager payroll. Write the 032 change, its tests (including a payroll figure for a Manager with a training day and no check-in) and the "Amended by 024" note

---

## Phase 11: Polish & Cross-Cutting Concerns

- [ ] T081 [P] ArchUnit and Modulith verification: run `ApplicationModulesTest`, the new training rules and `ActivityCalendarModuleRulesTest` (nothing reads `training.calendar.internal`; `attendance` still does not depend on `training`)
- [ ] T082 [P] Security and privacy sweep test `T/training/calendar/TrainingPrivacyTest.java`: no response to a Teacher contains internal remarks, exceptions or other people's rows; no response anywhere has a pay, stipend or payroll field (FR-030); text is escaped; a non-https venue is refused
- [ ] T083 [P] Demo data `TC/internal/TrainingCalendarDevSeeder.java` (inert unless `hls.seed.demo-data=true`, idempotent): one pattern with three months, a weekend session with registrations, a held session with outcomes and one exception, matching quickstart.md users
- [ ] T084 [P] Postman: add the training requests to the collection under `postman/` and run the folder against local
- [ ] T085 [P] Documentation: roadmap row 024 and the C1 entry in `docs/spec-roadmap.md`; constitution 2.5.11 PATCH with the Training row in the Default role access matrix and the navigation items in `.specify/memory/constitution.md`
- [ ] T086 [P] "Amended by 024" notes in the specs of 035 (TrainingSource real, Training under "No Zone"), 032 (`managersOnLeaveOn`, `ManagerTrainingDays`, Field day link), 008 (training day on a non-working day counts in working days; "training days available" can now use expected days) and 015 (optional T-015)
- [ ] T087 Run the full backend and frontend test suites once (all green; record any failure not caused by this work) and the quickstart timing checks for SC-002 and SC-008
- [ ] T088 Manual walkthrough LAST: follow quickstart.md sections 1 to 6 in the browser as Admin, Director, Manager A, Manager B and Teachers; confirm the screen-reader and keyboard use of the attendance grid; record results in this file. T080 (the 032 Manager-pay amendment) is built and checked separately

---

## Dependencies & Execution Order

- Setup first. Foundational blocks everything: migration T004, then entities T006; T007 to T012 are independent of each other.
- US1 (MVP) needs Phase 2. US2 needs US1's `SessionService` (T019); its group snapshot step waits for `GroupResolver` (T034). US3 needs US1. US4 needs US3 (roster and `GroupResolver`) and T044 for Manager on-leave. US5 and US6 need US4. US7 needs US1 and US3; T062 and T063 need T061.
- T080 (the 032 Manager-pay amendment, decided) is independent and nothing depends on it.
- Polish last; T088 last of all.

```text
Setup -> Foundational -> US1 -> US2
                           \-> US3 -> US4 -> US5
                                         \-> US6
                           \-> US7 (needs US3)
                 all -> Polish -> walkthrough
                 T080 (032 amendment, independent)
```

## Parallel Opportunities

- After T006: T007 to T012 in parallel.
- Within a story: all test tasks marked [P] in parallel (different files); frontend tasks beside the backend once `trainingApi.ts` and the response contract are fixed.
- US2 and US3 can proceed in parallel after US1's T019.
- T081 to T086 in Polish run in parallel.

## Implementation Strategy

1. MVP: Phases 1, 2 and 3 (sessions and registration). Stop and validate with quickstart section 1.
2. Add US2 and US3 (the standing calendar and obligations), then US4 (expected versus attended), the core of the module.
3. Add US5, US6, then US7 (feed, T-REPLACE, reminders), then Polish and the walkthrough.
4. After T062 run the 035 tests and after T063 run the 032 tests unchanged to prove nothing else changed.
