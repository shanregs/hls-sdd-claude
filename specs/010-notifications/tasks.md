---

description: "Task list for feature implementation"
---

# Tasks: Notifications

**Input**: Design documents from `/specs/010-notifications/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/notifications-api.md,
quickstart.md, and **specs 001-005, 008 and 009 implemented** (this feature adds a permission module and
navigation to 002's matrix/catalog, listens to events from leave (009) and attendance (008), and reads
Managers and Teachers through `organization.api` and `teacher.api`).

**Tests**: included as first-class tasks - Constitution Principle IX requires per-role authorization tests on
every endpoint and own-scope tests, and the spec's invariants (created in the producer's transaction, none on
failure, right recipients, merging, retention) are only trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US5). The store, API and bell (US3) come
before the events so that every event task can be proven end to end.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{notification,leave,attendance,identity}/...`, tests under
`backend/src/test/java/com/hls/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/notifications/...`.

---

## Phase 1: Setup

- [x] T001 Create the `notification` module skeleton: `backend/src/main/java/com/hls/notification/{api,internal,web}/` with `package-info.java` (the `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/notification/NotificationModuleRulesTest.java` (ArchUnit: nothing outside `notification` depends on `notification.internal` or `.web`; `leave` and `attendance` do not depend on `notification` at all; `notification` reads `teacher`, `organization`, `school`, `leave`, `attendance` only through their `api` packages)
- [x] T002 Create `frontend/src/features/notifications/` with `notificationsApi.ts` (types and calls for list, unread count, read, read all, delete, clear read per contracts/notifications-api.md)

---

## Phase 2: Foundational (blocking prerequisites)

- [x] T003 Write `backend/src/main/resources/db/migration/V18__create_notification_table.sql` per data-model.md: table `notification` (id uuid PK, recipient_user_id uuid not null, type varchar(30) not null with a check in (LEAVE_DECIDED, LEAVE_REQUESTED, LEAVE_CANCELLED, ATTENDANCE_CHANGED, ATTENDANCE_MONTH_LOCKED, ATTENDANCE_MONTH_REOPENED), title varchar(80) not null, message varchar(300) not null, link varchar(200) with `CHECK (link IS NULL OR link LIKE '/%')`, channel varchar(10) not null default 'IN_APP' with check in ('IN_APP'), group_key varchar(120), detail varchar(600), created_at timestamptz not null, updated_at timestamptz not null, read_at timestamptz); indexes `(recipient_user_id, created_at DESC)`, partial `(recipient_user_id) WHERE read_at IS NULL`, partial `(recipient_user_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL`, and `(created_at)`
- [x] T004 [P] Add permission module `NOTIFICATIONS(VIEW, DELETE)` to `identity/permissions/PermissionModule.java`; add it to `PermissionEligibility.BUSINESS_MODULES` so System is excluded; seed defaults in `PermissionMatrixService.seedDefaults` (VIEW and DELETE granted to Admin, Director, Manager, Teacher), idempotent
- [x] T005 [P] Add navigation `ACCOUNT -> Notifications` (`/account/notifications`, NOTIFICATIONS VIEW, all four roles) to `identity/accessmodel/NavigationCatalog.java` after Sessions (order 93), and the data-scope entry `OWN` in `AccessModelService.java`
- [x] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model tests under `backend/src/test/java/com/hls/identity/` for the module (System excluded) and the ACCOUNT item per role
- [x] T007 `Notification` JPA entity, `NotificationType` enum and `NotificationChannel` enum (IN_APP only) in `notification/api` (enums) and `notification/internal` (entity, `NotificationRepository` with own-scope queries `findByIdAndRecipientUserId`, the paged list with an unread filter, the unread count, mark-all-read, delete-read, the merge lookup `findMergeable(recipient, groupKey, since)` and `deleteOlderThan(instant)`); quote the lengths from data-model.md (title 80, message 300, link 200, detail 600)
- [x] T008 `NotificationService` in `notification/internal`: `create(recipientUserId, type, title, message, link)` (truncating over-long text with an ellipsis, link must start with `/`), `createOrMerge(...)` for attendance changes with the group key and the distinct date set in `detail`, and the own-scope operations (list, count, read, read all, delete, clear read); plus `MessageFactory` building every title/message/link of contracts/notifications-api.md with DD/MM/YYYY dates and reasons shortened to 200 characters
- [x] T009 [P] Unit tests `backend/src/test/java/com/hls/notification/MessageFactoryTest.java` (every text in the contract, date format, long reason truncation, link shape) and `NotificationServiceTest.java` (create, truncation, merge inside and outside the 10-minute window, no merge into a read row, no merge across actors, teachers or months, distinct-day count)

**Checkpoint**: migration applies, permission and navigation seeded, store and service proven.

---

## Phase 3: User Story 3 - The Bell and the Notifications List (P1) 🎯 MVP

**Goal**: a user sees their own notifications, unread count, and can read and delete them.

**Independent Test**: with three notifications (two unread) the bell shows 2; mark all read hides it; delete one; clear read empties the list; another user's id is 404; System has nothing.

- [x] T010 [US3] `NotificationController` (`/api/v1/me/notifications`) in `notification/web/` with the six endpoints of contracts/notifications-api.md, recipient always from the JWT, `PermissionGuard` VIEW for list/count/read/read-all and DELETE for delete/clear-read, page size capped at 100, the list response carrying the caller's `unread` count; add `com.hls.notification` to `MasterDataExceptionAdvice` base packages
- [x] T011 [P] [US3] Tests `NotificationApiTest.java` (extends `IntegrationTestBase`; notifications inserted with `NotificationService`): list newest first and paged, unread filter, count, read is idempotent, read all, delete, clear read, two users never see or change each other's (foreign id is 404 for read and delete), per-role authorization (Admin, Director, Manager, Teacher allowed; System 403 on every endpoint), a role without DELETE can read but not delete
- [x] T012 [P] [US3] Frontend `NotificationBell.tsx` (bell icon with an unread badge hidden at zero and "99+" above 99, polls the count every 30 s while `document.visibilityState === "visible"` and on focus, announces the count with an `aria-live` label, links to `/account/notifications`; renders only when the access model contains the Notifications item) and mount it in `frontend/src/app/AppShell.tsx` next to the theme toggle; `NotificationBell.test.tsx` with fake timers (poll, hide at zero, 99+, hidden without the permission, paused when the tab is hidden)
- [x] T013 [P] [US3] Frontend `NotificationsPage.tsx` (list newest first, unread highlighted with a visible marker and not by colour alone, open = mark read then navigate to the link, "Mark all as read", delete icon via `RowActionButton` + `ConfirmDialog`, "Clear read", unread-only toggle, paging with "Load more", loading/empty/error states, delete controls only with the DELETE action) and the guarded route `/account/notifications` in `frontend/src/App.tsx`; `NotificationsPage.test.tsx`; add the page to `frontend/src/a11y/a11y.test.tsx` in both themes (mock `/api/v1/me/notifications` and `/unread-count`)

**Checkpoint**: the bell and the list work with stored notifications.

---

## Phase 4: User Story 1 - A Teacher Hears About Their Leave Decision (P1)

**Goal**: approve, reject and revoke each notify the Teacher, atomically with the decision.

**Independent Test**: reject with a reason and approve another as a Manager; the Teacher has two unread notifications with the right text; a refused approval creates none.

- [x] T014 [US1] Define the events in `leave/api/` (records `LeaveDecided(requestId, teacherId, decision, firstDate, lastDate, reason)` with an enum `Decision` APPROVED/REJECTED/REVOKED, `LeaveRequested`, `LeaveCancelled` per contracts/notifications-api.md) and publish them from `LeaveDecisionService.approve/reject/revoke` and `LeaveRequestService.submit/cancelOwn` using `ApplicationEventPublisher` inside the existing transactions, after the status change is saved and only on success (the Teacher cancelling publishes `LeaveCancelled` with `wasApproved`)
- [x] T015 [US1] `notification/internal/RecipientResolver.java`: `teacherUser(teacherId)` through `TeacherDirectory.teacherInfo(...).userId()` (null means nothing to notify), `leaveSupervisors(schoolId)` through `ManagerQueries.managerOfSchool` (the Manager's user when the Manager record is active), otherwise every active user holding the Admin or Director role (`RoleAssignmentRepository.findByRole` plus `AppUser.isActive`); and `notification/internal/LeaveEventListener.java` with `@EventListener` methods for `LeaveDecided` (notify the Teacher) that create the rows through `NotificationService`
- [x] T016 [P] [US1] Tests `LeaveDecisionNotificationTest.java` (extends `LeaveTestBase`): approve, reject (reason in the message), revoke each give the Teacher exactly one unread notification with the right title, message and link `/leave/history`; a refused approval (supervisor-set day, locked month) creates none and leaves no notification after the rollback; a Teacher cancelling their own request notifies the Teacher of nothing; a Teacher with no linked user creates nothing and the decision still succeeds

**Checkpoint**: Teachers hear about leave decisions.

---

## Phase 5: User Story 2 - Supervisors Hear About New and Withdrawn Requests (P1)

**Goal**: submit and Teacher-cancel notify the Teacher's Manager, or Admin and Director when there is none.

**Independent Test**: Tara submits; Manoj is notified and Asha is not; for a School without a Manager the Admin and Director are notified.

- [x] T017 [US2] Extend `LeaveEventListener` with `LeaveRequested` and `LeaveCancelled` handlers using `RecipientResolver.leaveSupervisors(schoolId)`; the message names the Teacher and the dates, link `/operations/leave`
- [x] T018 [P] [US2] Tests `LeaveRequestNotificationTest.java`: a Teacher with a Manager notifies that Manager and no other Manager nor Admin/Director; a School with no Manager notifies every active Admin and Director (an inactive one is skipped); cancelling a Pending and an Approved request notifies the same recipients; a rejected or failed submit (overlap) creates none; a Manager's later reassignment does not change an already stored notification; two Managers in different Schools are not mixed up

**Checkpoint**: supervisors hear about requests.

---

## Phase 6: User Story 4 - A Teacher Hears About Attendance Changes (P2)

**Goal**: supervisor changes, month lock and reopen notify the Teacher, with merging.

**Independent Test**: Manoj marks three days for Tara in a row: one unread notification with a count; locking the month notifies once.

- [x] T019 [US4] Define the events in `attendance/api/` (`AttendanceMarkChanged(teacherId, date, actorUserId, actorName)`, `AttendanceMonthLocked(teacherId, month, actorUserId)`, `AttendanceMonthReopened(teacherId, month, actorUserId, reason)`) and publish them: `MarkService.setMark` and `clearMark` publish `AttendanceMarkChanged` only when the kind is SUPERVISOR (never for `setLeaveMark`/`clearLeaveMark`, never for self marks); `MonthLockService.lockMonth` and `relock` publish `AttendanceMonthLocked` once per affected Teacher, `reopen` publishes `AttendanceMonthReopened`
- [x] T020 [US4] `notification/internal/AttendanceEventListener.java`: `AttendanceMarkChanged` creates or merges (group key `attendance:{actor}:{teacher}:{yyyy-MM}`, 10-minute window on `updated_at`, unread only) a notification for the Teacher's user, linking to `/my-attendance?month=YYYY-MM`; the lock and reopen handlers create one notification each
- [x] T021 [P] [US4] Tests `AttendanceNotificationTest.java` (extends `AttendanceTestBase`): a Manager marking a day notifies the Teacher; the Teacher's own mark and leave-made marks create none; three marks within 10 minutes by the same Manager give one unread notification with 3 days, and a mark after the Teacher read it starts a new one; another supervisor's mark is a separate notification; a mark more than 10 minutes later (clock moved in the row) is separate; lock month notifies each Teacher once and reopen once; a mark refused for a locked month creates none

**Checkpoint**: attendance changes reach Teachers.

---

## Phase 7: User Story 5 - Housekeeping and Future Channels (P3)

**Goal**: 90-day retention and an open channel concept; demo data.

**Independent Test**: a notification dated 91 days ago disappears after the clean-up; a new one carries the in-app channel.

- [x] T022 [US5] `notification/internal/RetentionJob.java` with a public `purge()` (deletes rows with `created_at` older than 90 days, logs the count only) and `@Scheduled(cron = "0 30 2 * * *")`; add `@EnableScheduling` to `backend/src/main/java/com/hls/HlsApplication.java` and a property `hls.notification.retention.enabled` (default true, false in the test profile) so the job does not fire during the suite
- [x] T023 [P] [US5] Tests `NotificationRetentionTest.java`: rows at 89, 90 and 91 days (boundary: older than 90 days goes), merged rows keep `created_at` for retention, newer ones untouched, every row has channel IN_APP, and the database rejects another channel and a link that does not start with `/`
- [x] T024 [P] [US5] `notification/internal/NotificationDevSeeder.java` (demo flag only, runs after `LeaveDevSeeder`, idempotent per user: skipped when the user already has a notification): Tara a read "leave approved", an unread "leave rejected" with the seeded reason, an unread "attendance updated"; Manoj two unread "new leave request" notices for Meena and Karthik; Asha one for Lakshmi (no Manager); test `NotificationDevSeederTest.java` for counts, unread flags and idempotency, and extend `DevSeedTest` if it lists seeders

---

## Phase 8: Polish & Cross-Cutting

- [ ] T025 [P] Update `docs/running-locally.md` (a Notifications section: the bell, the list, what triggers a notification, demo data) and add Postman requests under `postman/HLS API/` folder `010 Notifications` (list, unread count, read, read all, delete, clear read) with collection variable `notificationId`; update `postman/README.md`
- [ ] T026 [P] Update `docs/spec-roadmap.md` row 010 to Implemented with the test counts, and `specs/002-access-model-app-shell/tasks.md` with the `NOTIFICATIONS` module and ACCOUNT item (same way specs 008 and 009 did)
- [ ] T027 Run the backend tests for the module, the touched identity/leave/attendance tests and the frontend Vitest suite including `src/a11y`, then execute `quickstart.md` scenarios 1-6 against the running local app (Playwright-core script allowed for the UI checks) and record the results in `specs/010-notifications/quickstart-results.md`
- [ ] T028 Re-read spec.md FR-001..FR-012 and SC-001..SC-007 against what was built and note any gap in `quickstart-results.md`; open the PR with `gh pr create`

---

## Dependencies & Execution Order

- Phase 1 then Phase 2 (blocking). Within Phase 2: T003 first; T004-T006 parallel; T007 before T008; T009 after T008.
- US3 (Phase 3) needs Phase 2 and gives the surface for every later story. US1 needs T008 (service) and the leave code. US2 needs US1's resolver. US4 needs T008 and the attendance code. US5 can run any time after Phase 2.
- Parallel examples: T004/T005/T006 together; T011/T012/T013 together once T010 exists; T016, T018 and T021 are independent of each other once their listeners exist.

## Implementation Strategy

- **MVP**: Phase 1, Phase 2, US3 (the bell and the list) then US1 (leave decisions) - a Teacher sees the outcome of a leave request in the bell, the gap spec 009 left open; then US2, US4, US5.
- Land each phase as its own commit with tests green; run the frontend a11y cases for each new screen as it is added.
- Specs, docs and Postman are updated in Phase 8, but any task that changes a contract updates `contracts/notifications-api.md` in the same commit.
