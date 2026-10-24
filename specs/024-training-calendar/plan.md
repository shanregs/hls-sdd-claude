# Implementation Plan: Training Calendar (Weekend Refreshers and Monthly Sessions)

**Branch**: `024-training-calendar` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

## Summary

The existing `training` module (built by spec 016 for induction) grows a second area, `com.hls.training.calendar`
(`internal` and `web`), that owns training sessions of two types (Weekend refresher: open to register; Monthly: assigned),
standing monthly patterns that generate each month's session, registrations, assignments (the expected-attendance
obligation), the attendance register, non-compliance exceptions and the per-person training history. Induction stays in
`training.internal` and appears in the history as one read-only entry.

Three design points drive the rest (research.md sections 1 to 4):

1. **Teacher attendance goes to spec 008 through the existing `attendance.api.TrainingAttendance`** (the A5 amendment built
   for induction). Nothing in 008 changes. Because that call throws when the day already holds a school mark or the month is
   locked, and a caught exception would mark the surrounding transaction rollback-only, the training module checks first with
   `AttendanceReadApi` (`marksOf`, `isLocked`) and calls the writer only when the write will succeed. A clash is therefore
   shown and recorded in the register with the 008 write held back (nothing overwritten, nothing blocked); a locked month
   refuses the row with the 008 message.
2. **Manager training attendance stays in the training register** (decided). Spec 032 reads it through a port that
   `attendance.api` owns and `training` implements (`ManagerTrainingDays`), because `training` already depends on
   `attendance` and a module cycle is forbidden. The same port offers a Manager's assigned sessions to the Field day link.
3. **C1 feed for 035 (and 015, 028)**: `training.api.TrainingActivities` with `plannedBetween` and `activitiesBetween`,
   one entry per session (owner = scheduler, attendees = assigned Managers), the same shape as drives. The 035 placeholder
   `TrainingSource` is replaced by a real `TrainingSource` bean over it (task T-REPLACE, see "Tasks that must not be lost").

Sessions come from patterns by materialising rows: on pattern create or edit and in a daily job that keeps the current month
and the next three present (unique `(pattern_id, pattern_month)`, so running twice creates nothing twice). Reminders, the
"attendance not recorded" flag, the exits clean-up and the repeat-absence mark are small daily jobs or derived reads; none is
needed for the correctness of a stored fact.

One migration, `V110__create_training_calendar.sql` (max on main is V90; 031 plans V100). New permission modules `TRAINING`,
`TRAINING_ATTENDANCE` and `TRAINING_REGISTRATION`; seven notification types; six runtime settings; React feature
`features/training` (web only, as decided).

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers), React + Vite + Vitest.
- Migration: `V110__create_training_calendar.sql`. Checked on origin/main at the merge of 2026-10-24: V51 to V53, V60 (026),
  V70 (035), V80 (027), V90 (015) are the newest; spec 031 (branch spec/031-mou-documents) plans V100, so this spec takes V110. Pick the next free
  number again at implementation if another branch merged first. History tables are insert-only (trigger), as in 027.
- Constraints: no cross-module foreign keys (ids only); dates are plain business dates from `BusinessDate`/`WorkingCalendar`
  (Asia/Kolkata, no conversion); audit through `ChangeRecorder`; optimistic `version` on sessions, patterns and entries; seat
  limit enforced under a row lock on the session (Principle VIII); 404 for anything out of scope; the Manager's scope comes
  from `TeacherScopeQueries` and `ManagerQueries`, never re-implemented.
- Scale: under 100 users overall, rosters up to 500 (spec), tens of sessions a year. One pass for 100 rows in under 3 seconds
  (SC-008): the save loads marks, locks and scope in bulk and writes all rows in one transaction.
- No `NEEDS CLARIFICATION` remains. The points that still need the user's decision are in "Open Items".

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Every create, edit, publish, move, cancel, skip, pattern change, assignment, removal, registration, attendance entry, correction and exception closure goes to Change History through `ChangeRecorder` (before and after, reason where asked) and to an insert-only module history (`training_session_history`, `training_attendance_history`). The earlier date, trainer and venue stay visible. The Teacher's training-day mark keeps its own history in 008. |
| II Five roles, RBAC | Three new modules: `TRAINING` (View, Create, Edit, Delete, Export), `TRAINING_ATTENDANCE` (View, Edit) and `TRAINING_REGISTRATION` (View, Create, Edit). Seeded Admin and Director full on `TRAINING` and `TRAINING_ATTENDANCE`; Manager `TRAINING` View and `TRAINING_ATTENDANCE` View and Edit; Teacher `TRAINING_REGISTRATION` View, Create, Edit; System nothing. `PermissionEligibility` makes everything else ineligible (a Manager cannot be granted a write on `TRAINING`; Teacher and System cannot be granted `TRAINING` or `TRAINING_ATTENDANCE`; only Teacher holds `TRAINING_REGISTRATION`). |
| III Data scope | Admin and Director org-wide. A Manager reaches only the Teachers placed under them (`TeacherScopeQueries`) and their own record, for sessions, assignments, attendance, exceptions and history; recording attendance for anyone else, including themselves, is refused. A Teacher reaches only their own records. Anything out of scope answers 404. System has no access. |
| IV Role-based UI | OPERATIONS items per role from `NavigationCatalog`; buttons follow the grants and a server-sent `actions` list on each session (publish, move, cancel, assign, record attendance, register, cancel registration). Teachers never see exceptions, internal remarks or other people's rows. |
| V Formula-driven | Not touched: training is unpaid (D2); no stipend, pay or payroll figure anywhere in the module (FR-030). Attendance reaches payroll only as the existing 008 training-day mark; research.md section 4 records the one rollup side effect on non-working days. |
| VI Training and substitution | This spec is the "recurring monthly training calendar" and "training attendance and history" the principle names. A Teacher in induction cannot be assigned or register; induction stays in 016 and is shown read-only. |
| VII Modular monolith | `training` reads `attendance.api` (`TrainingAttendance`, `AttendanceReadApi`, `WorkingCalendar`), `teacher.api` (`TeacherDirectory`, `TeacherScopeQueries`, `TeacherPlacementSource`), `organization.api`, `school.api`, `designation.api` (`BusinessDate`) and identity (permission guard). Reverse dependencies are inverted so no cycle appears: `attendance.api.ManagerTrainingDays` (implemented by `training`) and `training.api.TrainingSettings` (implemented by `settings`). Only `notification` listens to `training.api` events; `activitycalendar` and `expenses` read `training.api` interfaces. ArchUnit and Modulith verification enforce it. |
| VIII Concurrency | Registration takes a pessimistic lock on the session row so the seat limit cannot be passed by two simultaneous requests; assignment and registration are unique by constraint (a second request is a no-op, not an error); sessions, patterns and entries carry a version (second editor gets 409); generation is idempotent by unique `(pattern_id, pattern_month)`; one roster save is one transaction. |
| IX Testability | Unit tests for pattern date rules (nth weekday, last, fixed day, month-end clamp), flag derivation and repeat counting; API tests per endpoint, role and scope boundary (two Managers, two Teachers: SC-006); integration tests for the 12-month generation run (SC-001), the 100-person assignment and attendance passes (SC-002, SC-008), the 008 mark after record and correction (SC-004), the hand-counted session counts (SC-003) and the 035 feed (SC-007); ArchUnit; frontend tests for grants. |
| X Security | Server re-checks grant and scope on every request; free text (topic, trainer, venue, reasons, remarks) capped and escaped on output; a virtual venue link is stored as text and offered as a link only when it is https; internal remarks and exceptions are never in a Teacher's response; exports only with `TRAINING` Export. |

Constitution change: PATCH to 2.5.11 (the next patch after whichever of the parallel specs merges first), a Training row in the
Default role access matrix and the navigation items. No principle changes. Gate result: pass before research and after
design, no violations.

## Project Structure

```text
specs/024-training-calendar/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/training/api/         TrainingActivities (C1), TrainingSessions (read for 015), TrainingSettings (port),
                                                    events: TrainingAssigned, TrainingAssignmentRemoved, TrainingSessionChanged,
                                                    TrainingReminderDue, TrainingRegistered, TrainingExceptionRaised, TrainingCannotAttend
backend/src/main/java/com/hls/training/calendar/internal/
  TrainingSession, TrainingPattern, PatternSkip, SessionHistory, Registration, Assignment, AttendanceEntry, AttendanceHistory,
  TrainingException (+ repositories), PatternRules (date maths), SessionService, PatternService, GenerationService,
  RegistrationService, AssignmentService, GroupResolver, AttendanceRecordService, AttendanceBridge (008 pre-check and write),
  ExceptionService, HistoryQueries, SessionQueries, TrainingScope, TrainingActivitiesImpl, TrainingSessionsImpl,
  ManagerTrainingDaysImpl, GenerationJob, ReminderJob, ReconcileJob, TrainingCalendarDevSeeder
backend/src/main/java/com/hls/training/calendar/web/ SessionController, PatternController, AttendanceController,
                                                    ExceptionController, HistoryController, MyTrainingController, TrainingExceptionAdvice
backend/src/main/java/com/hls/attendance/api/       ManagerTrainingDays (new port, implemented by training); ManagerAttendanceReadApi
                                                    + managersOnLeaveOn (additive read of 032's day rows, research 6)
backend/src/main/java/com/hls/attendance/manager/internal/PlannedActivities.java  (+ training sessions through the port)
backend/src/main/java/com/hls/activitycalendar/internal/TrainingSource.java       (placeholder replaced; link case for TRAINING)
backend/src/main/java/com/hls/notification/         NotificationType (+7), TrainingEventListener, MessageFactory texts
backend/src/main/java/com/hls/identity/             PermissionModule, PermissionEligibility, PermissionMatrixService (seed),
                                                    NavigationCatalog, AccessModelService (data scope)
backend/src/main/java/com/hls/audit/support/AuditVisibility.java   (entity types to modules)
backend/src/main/java/com/hls/settings/internal/    SettingKey (+6), TrainingSettings implementation
backend/src/main/resources/db/migration/V110__create_training_calendar.sql
frontend/src/features/training/ (TrainingCalendarPage, SessionDialog, SessionDetail, PatternDialog, AssignDialog, AttendanceGrid,
  ExceptionsPage, MyTrainingPage, UpcomingTrainingPage, HistoryPage, trainingApi, __tests__) + routes in App.tsx
postman/ (collection additions)
```

**Structure Decision**: one new sub-package pair inside the existing `training` module and one frontend feature; mobile is out
of scope (spec). Changes outside `training` are additive: one port and one read method in `attendance.api`, one source bean, one link case,
permission, navigation, notification, settings and audit entries.

## Design Decisions

See [research.md](research.md). Highlights:

- Statuses: Draft, Published, Held (set by the first saved attendance), Cancelled, Skipped. Generated sessions are created
  Published. Only a not-held session can be moved, re-assigned or have its trainer changed.
- Patterns are materialised rows. Editing "from month M on" updates only not-held, not-hand-edited sessions of M and later
  and keeps their assignments; stopping removes untouched later sessions and keeps held ones.
- The default group of a pattern is a snapshot taken at generation. A session shows "N people in the group are not assigned"
  so a later joiner is not silently missed; "Add the group again" is idempotent.
- One attendance entry per person per session; one exception per assigned person who was Absent; a corrected absence
  withdraws its exception. Repeat marker = Open or Acknowledged exceptions in the last 6 months (default 2).
- "On leave" is stored on the entry when attendance is saved and keeps the person out of the exceptions.
- C1: one entry per session with the scheduler as owner and the assigned Managers as attendees; held sessions report
  `completedOn` = the session date; a moved session reports `originalDate` from `training_session_history`.

## Phases

1. Migration V110, permission modules, eligibility, seeding, navigation, audit entries, settings, notification types, module
   skeleton and scope (blocks the rest).
2. Sessions, statuses, history, calendar reads (US1 without registration, US2 pattern rules and generation).
3. Registration, assignment and groups (US1 registration, US3).
4. Attendance register, 008 bridge, counts (US4), Manager port for 032.
5. Exceptions, repeat marker, history and exports (US5, US6).
6. C1 feed, `TrainingSource` replacement, Field day link, Upcoming training, reminders and jobs (US7).
7. Frontend (calendar, detail, assign, attendance grid, exceptions, my training, history), Postman, demo data, roadmap and
   constitution row, and the notes in the specs of 035, 032, 015.

## Tasks that must not be lost (carry into tasks.md)

- **T-REPLACE**: replace the 035 placeholder `activitycalendar.internal.TrainingSource` (reports `available() == false` and no
  entries) by a real `TrainingSource` over `training.api.TrainingActivities.activitiesBetween`, `available() == true`, kind
  `TRAINING`, sub-kind the session type; add the `TRAINING` case to `linkFor` (module `TRAINING`, route `/operations/training`);
  extend `SourceAdaptersTest` and the 035 month fixture; the "Training is not included yet" note disappears.
- **T-032**: add training sessions to `attendance.manager.internal.PlannedActivities.forDay` through
  `attendance.api.ManagerTrainingDays`; the 032 read of attended training days waits for Open Item 2.
- **T-015** (optional, separate PR): offer training sessions as a link for a Training Stay claim (`LinkResolver`, `LinkKinds`).
- **T-SPEC-NOTES**: "Amended by 024" notes in specs 035, 032, 015 and the roadmap C1 entry.

## Open Items (need the user's decision; defaults are used until decided)

1. **Navigation sections.** The spec names MY WORK (Manager) and MY TRAINING (Teacher), but the catalog has neither and the
   section order is fixed and tested (spec 002 FR-008a). Default: Admin and Director OPERATIONS items Training Calendar and
   Training Exceptions; Manager OPERATIONS item My Training; Teacher MY ATTENDANCE items Upcoming Training and My Training
   History (next to My Substitutions). Confirm, or approve new sections (a spec 002 change).
2. **Manager pay on a training day.** `ManagerMonth` counts a working day with no check-in and no leave as unpaid. A Manager
   who attends training and does not check in would lose that day unless spec 032 treats an attended training day as worked.
   The spec says 032 "reads it to show the day as a training field day"; the payroll effect is not stated. Default: 024 exposes
   the attended days through `ManagerTrainingDays` and does nothing else; 032 must adopt it by its own amendment before a
   payroll run that includes a Manager training day. Confirm that 032 should count an attended day as worked (whole 1.0, half
   0.5, only when there is no check-in).
3. **FR-026 wording.** "One entry per assigned or registered Manager" cannot be built as written: only Teachers register, and
   035 keys an item by kind, id and date, so one entry per Manager would collide. Default: one entry per session, owner the
   scheduler, attendees the assigned Managers. Confirm and let the spec text be corrected.
4. **Admin and Director on `TRAINING_ATTENDANCE`.** The spec seeds `TRAINING_ATTENDANCE` for the Manager and lists attendance
   under `TRAINING`. Default: attendance endpoints need `TRAINING_ATTENDANCE` (Admin and Director seeded full), so one key
   governs recording for everyone. Confirm.
5. **Clash resolution.** On a clash with a school mark the training row is saved and the 008 write is held back until a
   supervisor resolves the day in 008 (one mark per day; 008's split-day rule). Default as stated; confirm this reading of
   "the half-day rules of spec 008 decide".
