---

description: "Task list for Manager Activity Calendar"
---

# Tasks: Manager Activity Calendar

**Input**: Design documents from `/specs/035-manager-activity-calendar/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/calendar-api.md, contracts/source-feed-amendments.md,
quickstart.md, and specs 016, 023, 025 (the three C1 feeds), 032 (Field day reads the same feeds) and 034 implemented and
merged (they are on `main`). Spec 024 is NOT built; training is only a placeholder here.

**Defaults used for the plan's open items (no user answer yet; change here and in plan.md if the user decides otherwise)**:

1. Navigation: the Manager's "My Activity Calendar" goes under the **OPERATIONS** section next to My Tasks (the catalog has no
   MY WORK section). Admin and Director: OPERATIONS "Activity Calendar". Path `/operations/activity-calendar`.
2. History: **no backfill** of `completed_on` and `held_on` for visits completed and drives held before V70; they show as Done
   without a "done late" mark.
3. Drive reschedule rule: a drive date change counts as Rescheduled only when the removed and added dates are **equal in
   number** (paired in date order); adding or removing a date is not a reschedule.

**Tests**: first-class tasks (Constitution Principle IX): per-feed contract tests, a regression test per amended feed that
`plannedBetween` is unchanged, a hand-counted month fixture (SC-002), the two-Manager scope matrix (SC-004), a no-write
ArchUnit and HTTP test (SC-008), a 500-item timing test (SC-005), and frontend tests per role.

**Organization**: grouped by user story (spec.md US1-US5). Order: Setup, Foundational (migration V70, feed amendments in
023/025/016, permission and navigation, source SPI), US1 (Director calendar, MVP), US2 (Manager scope), US3 (Needs attention),
US4 (filters and views), US5 (training placeholder and resilience), Polish (docs, "Amended by 035" notes, Postman, demo data).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/activitycalendar/{api,internal,web}/`, tests under
`backend/src/test/java/com/hls/activitycalendar/`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/activityCalendar/`. Run one Maven test class at a time (`mvn -o test -Dtest=...`); Docker must be
running for integration tests. Below, `B` = `backend/src/main/java/com/hls`, `T` = `backend/src/test/java/com/hls`,
`FE` = `frontend/src/features/activityCalendar`.

---

## Phase 1: Setup

- [x] T001 Verify the prerequisites and STOP and report if any is missing (do not stub): in `B/recruitment/api` confirm `DriveActivities`, `MarketingActivities`, `PlannedActivity`; in `B/tasks/api` confirm `TaskActivities`; `designation.api.BusinessDate`, `organization.api` `ManagerQueries.allManagers` and `ScopeQueries.scopeOf`, `school.api.SchoolDirectory`; the `activity_date_history` table (023) and `task_history` rows of type `DUE_CHANGED` (025); and the highest migration number on main (V53 on 2026-10-21; the plan reserves V70, re-check open pull requests and renumber at merge time). Record any name that differs in `specs/035-manager-activity-calendar/research.md` section 1
- [x] T002 Create the package skeleton `B/activitycalendar/{api,internal,web}/` with `package-info.java` (api is empty: nothing is exposed) and `T/activitycalendar/ActivityCalendarModuleRulesTest.java` (ArchUnit: nothing reads `activitycalendar`; `activitycalendar` uses no other module's `internal`; `activitycalendar.web` declares only `@GetMapping`; `activitycalendar.internal` has no `@Entity`, no repository and no write call)
- [x] T003 [P] Create `FE/activityCalendarApi.ts` with types and calls for the three endpoints in contracts/calendar-api.md (`GET /`, `GET /needs-attention`, `GET /filters`; dates ISO, months `yyyy-MM`)

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the migration, the three additive feed amendments (specs 023, 025, 016), the permission and the source SPI. No
user story can start before the feeds return completed and cancelled items.

### Migration

- [x] T004 Write `backend/src/main/resources/db/migration/V70__amend_planned_activity_sources.sql` per data-model.md: `ALTER TABLE marketing_activity ADD COLUMN completed_on DATE NULL`; `ALTER TABLE campus_drive ADD COLUMN held_on DATE NULL`; new `drive_date_change` (`id UUID PK`, `drive_id UUID NOT NULL` FK to `campus_drive`, `old_date DATE NOT NULL`, `new_date DATE NOT NULL`, `changed_by UUID NOT NULL`, `changed_at TIMESTAMPTZ NOT NULL`), index on `drive_id`, trigger that refuses UPDATE and DELETE. No backfill and no data change (default 2)

### Spec 023 amendment (school-marketing-mou), contracts/source-feed-amendments.md section 1

- [x] T005 [P] Create record `ActivityEntry(String kind, UUID id, UUID ownerUserId, Set<UUID> attendeeUserIds, LocalDate date, LocalDate originalDate, String place, UUID schoolId, UUID zoneId, String linkedType, UUID linkedId, String status, LocalDate completedOn)` in `B/recruitment/api/ActivityEntry.java` (shared with `DriveActivities`); add `List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId)` to `B/recruitment/api/MarketingActivities.java`; leave `plannedBetween` and `PlannedActivity` unchanged
- [x] T006 Add `completedOn` to the entity `B/recruitment/marketing/internal/MarketingActivity.java` and set `completed_on = BusinessDate.today()` inside `ActivityService.complete` in `B/recruitment/marketing/internal/ActivityService.java` (no endpoint, screen or response of 023 changes). Depends on T004
- [x] T007 Implement `MarketingActivitiesImpl.activitiesBetween` in `B/recruitment/marketing/internal/MarketingActivitiesImpl.java`: every visit, call and meeting dated in the range with status PLANNED, COMPLETED and CANCELLED; a null user means everyone's, a given user matches creator or attendee; `ownerUserId` is the creator; same kind strings (`SCHOOL_VISIT`, `SCHOOL_CALL`, `SCHOOL_PROPOSAL_MEETING`, `SCHOOL_FOLLOW_UP`); `originalDate` is the oldest `old_date` in `activity_date_history`, read in ONE bulk query, null when never moved; `place` is the prospect name (School name for an account visit); `zoneId` is the prospect `zone_id`, else the School Zone through `SchoolDirectory`; `schoolId` set for account visits; no notes, outcome or attachments leave the module. Depends on T005, T006
- [x] T008 [P] Contract test `T/recruitment/marketing/MarketingActivitiesFeedTest.java`: planned, completed and cancelled visits returned; `completedOn` set after complete and null for older rows; `originalDate` after one and after two moves (oldest `old_date`); attendees listed; a user filter matches creator or attendee; only the contract fields are returned
- [x] T009 [P] Regression test `T/recruitment/marketing/MarketingPlannedBetweenUnchangedTest.java`: `plannedBetween` of the marketing feed still returns PLANNED visits only, with the same `PlannedActivity` values as before the amendment, for a fixture holding planned, completed and cancelled visits (spec 032 Field day depends on this)

### Spec 016 amendment (campus-recruitment), contracts/source-feed-amendments.md section 3

- [x] T010 [P] Add `List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID userId)` to `B/recruitment/api/DriveActivities.java`; leave `plannedBetween` unchanged
- [x] T011 Add `heldOn` to `B/recruitment/internal/CampusDrive.java` and create the insert-only entity `B/recruitment/internal/DriveDateChange.java` with repository `B/recruitment/internal/DriveDateChangeRepository.java` (mirrors `drive_date_change`). Depends on T004
- [x] T012 In `B/recruitment/internal/DriveService.java`: `markHeld` stores `held_on = BusinessDate.today()`; `update` computes removed and added dates and, only when both sets are non-empty and EQUAL in size (default 3), sorts both and writes one `drive_date_change` row per pair, otherwise writes nothing. Existing Change History entries stay exactly as they are. Depends on T011
- [x] T013 Implement `DriveActivitiesImpl.activitiesBetween` in `B/recruitment/internal/DriveActivitiesImpl.java`: one entry per drive date in the range for every status (PLANNED, HELD, CANCELLED); a null user means every drive; `ownerUserId` is `scheduledBy`, `attendeeUserIds` are the interviewers, a given user matches either; `completedOn` for a HELD drive is `held_on` on the last date and `min(held_on, date)` on earlier dates, null when `held_on` is null; `originalDate` per date follows `drive_date_change` back to the first old date, null when never moved; `zoneId` and `schoolId` null; `place` stays "college, city"; history read in bulk. Depends on T010, T012
- [x] T014 [P] Contract test `T/recruitment/DriveActivitiesFeedTest.java`: all statuses returned; `held_on` stored by mark-held; an equal-size date swap writes paired `drive_date_change` rows and sets `originalDate`; a date added or removed alone writes nothing and gives no `originalDate`; two successive moves resolve to the first old date; the trigger refuses UPDATE and DELETE of `drive_date_change`; interviewers returned as attendees
- [x] T015 [P] Regression test `T/recruitment/DrivePlannedBetweenUnchangedTest.java`: `plannedBetween` of drives returns exactly what it did before (same entries, statuses and owner), and the existing Change History entries for update and mark-held are unchanged

### Spec 025 amendment (manager-tasks), contracts/source-feed-amendments.md section 2

- [x] T016 [P] Add the nested record `ActivityEntry(String kind, UUID id, UUID ownerUserId, LocalDate date, LocalDate originalDate, String place, String linkedType, UUID linkedId, String status, LocalDate completedOn)` and `List<ActivityEntry> activitiesBetween(LocalDate from, LocalDate to, UUID ownerUserId)` to `B/tasks/api/TaskActivities.java`; leave `PlannedActivity` and `plannedBetween` unchanged (tasks must not depend on a recruitment type)
- [x] T017 Implement `TaskActivitiesImpl.activitiesBetween` in `B/tasks/internal/TaskActivitiesImpl.java`: every task due in the range INCLUDING CANCELLED; a null owner means everyone's; `kind` is the `TaskType` name and `status` the `TaskStatus` name (ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, VERIFIED, CANCELLED); `completedOn` is `completedAt` in the business time zone when COMPLETED or VERIFIED, else null (a task sent back to IN_PROGRESS has none); `originalDate` is `before_value` of the earliest `DUE_CHANGED` `task_history` row parsed as an ISO date (unparsable rows ignored), read in one bulk query; `linkedType`/`linkedId` returned; NO title, description, note or evidence. No migration, no behaviour change. Depends on T016
- [x] T018 [P] Contract test `T/tasks/TaskActivitiesFeedTest.java`: cancelled tasks returned; `completedOn` for COMPLETED and VERIFIED and null after being sent back; `originalDate` after one and after two due-date changes; unparsable history ignored; only the contract fields exposed
- [x] T019 [P] Regression test `T/tasks/TaskPlannedBetweenUnchangedTest.java`: `plannedBetween` still excludes cancelled tasks and returns the same `PlannedActivity` values

### Prospect Zones lookup (contracts/source-feed-amendments.md section 4)

- [x] T020 [P] Create `B/recruitment/api/ProspectZones.java` (`Map<UUID, UUID> zonesOf(Collection<UUID> prospectIds)`, bulk, read-only, no scope applied), its implementation `B/recruitment/marketing/internal/ProspectZonesImpl.java`, and a test `T/recruitment/marketing/ProspectZonesTest.java`
- [x] T021 Phase 2 gate: run the existing spec 032 Field day tests and the spec 034 tests unchanged, plus `RecruitmentModuleRulesTest` and the tasks module rules test, to prove the amendments broke nothing. Depends on T007, T013, T017

### Permission, navigation and source SPI

- [x] T022 [P] Add `ACTIVITY_CALENDAR(VIEW)` to `B/identity/permissions/PermissionModule.java`; in `PermissionEligibility` make it eligible for Admin, Director and Manager only (Teacher and System refused); seed View granted for Admin, Director and Manager in `PermissionMatrixService`
- [x] T023 [P] Add navigation to `B/identity/accessmodel/NavigationCatalog.java`: OPERATIONS "Activity Calendar" for Admin and Director and OPERATIONS "My Activity Calendar" for Manager (default 1), path `/operations/activity-calendar`, `ACTIVITY_CALENDAR` VIEW
- [x] T024 Extend `PermissionEligibilityTest` and the navigation and access-model tests under `T/identity/` for `ACTIVITY_CALENDAR` (Teacher and System refused by the matrix; the right menu label per role). Depends on T022, T023
- [x] T025 Create the internal source SPI in `B/activitycalendar/internal/`: `ActivitySource` (`group()` DRIVES|VISITS|TASKS|TRAINING, `available()`, `entries(from, to, userIdOrNull)`), record `CalendarEntry` (`kind, id, date, originalDate?, ownerUserId, attendeeUserIds, place?, zoneId?, schoolId?, linkedType?, linkedId?, nativeStatus, completedOn?`) and the adapters `DriveSource`, `VisitSource`, `TaskSource` over the three `activitiesBetween` methods (task kind becomes `TASK` with the task type kept as `subKind`; visit types VISIT, CALL, PROPOSAL_MEETING and FOLLOW_UP map to School visit, Call, Meeting, Meeting). Depends on T007, T013, T017
- [x] T026 [P] Unit test `T/activitycalendar/SourceAdaptersTest.java` for the three adapters (kind mapping, null-safe fields, attendees, no extra data). Depends on T025

**Checkpoint**: the feeds return completed and cancelled items with `completedOn` and `originalDate`; the old `plannedBetween` behaviour is proven unchanged.

---

## Phase 3: User Story 1 - The Director Sees What Every Manager Planned and Did (Priority: P1) MVP

**Goal**: the Director or Admin opens a month and sees every Manager's drives, visits, calls, meetings and tasks with status, plus the summary counts.

**Independent Test**: with drives, visits and tasks for two Managers in two Zones (done, open in the past, moved, cancelled), the month shows each item once under the right Manager, Zone, day and status, and the summary equals the hand count (quickstart sections 1 and 2).

### Tests for US1 (write first)

- [x] T027 [P] [US1] Unit test `T/activitycalendar/StatusRulesTest.java` for the state rules in data-model.md: cancelled gives CANCELLED; completed, held or verified gives DONE with `doneLate` when `completedOn` is after the date; otherwise a date before business today gives MISSED; an item dated today and open stays PLANNED; a multi-date drive is judged on its last date; invariant planned = done + open + missed with cancelled outside planned
- [x] T028 [P] [US1] Unit test `T/activitycalendar/ZoneResolverTest.java` for FR-014: the item's own Zone (prospect Zone, School Zone, Zone of a linked School or prospect) first, then the owner's current Zone (a Manager may have several: pick deterministically), else "No Zone"
- [x] T029 [P] [US1] Role test `T/activitycalendar/CalendarAccessTest.java`: Admin and Director 200 org-wide; Teacher and System 403 before any lookup; no token 401
- [x] T030 [P] [US1] No-write test `T/activitycalendar/CalendarReadOnlyTest.java` (SC-008): POST, PUT, PATCH and DELETE on `/api/v1/activity-calendar` and its sub-paths return 405, and a row-count snapshot of the source tables is unchanged after calling every GET
- [x] T031 [P] [US1] Performance test `T/activitycalendar/CalendarPerformanceTest.java` (SC-005): 500 activities across 20 Managers load in under 3 seconds, with a query count independent of the item count (three feed calls plus bulk Zone and name lookups)
- [x] T032 [US1] Integration test `T/activitycalendar/CalendarMonthIntegrationTest.java` (SC-002, SC-007): the hand-counted fixture from quickstart section 1 (visit done on the 5th, drive held on the 8th, task open past due, a visit moved 14th to 20th, a cancelled visit, a 3-date drive, a visit with two attendee Managers, a visit with no School, a task completed late) yields exactly the expected statuses and summary totals, no item counted twice; the shared visit is under both Managers in `byManager` but once in totals; the 3-date drive counts once; a change in an owning screen shows on the next load; an empty month returns 200 with zero counts and `items: []`

### Implementation for US1

- [x] T033 [US1] Implement `B/activitycalendar/internal/StatusRules.java` (single place for status derivation, `doneLate`, `daysLate`, `daysMoved`) and the records `CalendarItem`, `CalendarSummary`, `SourceReport` in `B/activitycalendar/internal/` per data-model.md (status values `PLANNED`, `DONE`, `MISSED`, `CANCELLED`; kinds `CAMPUS_DRIVE`, `SCHOOL_VISIT`, `CALL`, `MEETING`, `TASK`, `TRAINING`)
- [x] T034 [P] [US1] Implement `B/activitycalendar/internal/ZoneResolver.java` (bulk: prospect Zones through `ProspectZones`, School Zones through `SchoolDirectory`, owner Zones through `organization.api`) and `B/activitycalendar/internal/PeopleDirectory.java` (names, Manager flag and Zones of owners and attendees in bulk, no per-item query)
- [x] T035 [US1] Implement `B/activitycalendar/internal/CalendarService.java` (`@Transactional(readOnly = true)`, no cache): call each available `ActivitySource` for the month (a range over 31 days gives 400), each in its own try/catch; build `CalendarItem`s with people (owner and every attendee who is a Manager); compute the summary (totals count distinct `(kind, id)` once, with the last date's status for a multi-date drive; `byKind`; `byManager` for Director and Admin only, counting a shared visit for each Manager); `rescheduled` counted once however many moves; the `SourceReport` list and `partial`. Depends on T025, T033, T034
- [x] T036 [US1] Implement `B/activitycalendar/web/ActivityCalendarController.java` (GET only, `ACTIVITY_CALENDAR` View, base path `/api/v1/activity-calendar`): `GET /?month=YYYY-MM` returning the response in contracts/calendar-api.md (month defaults to the current business month, `today` from `BusinessDate`); each item carries `key`, `kind`, `subKind`, `date`, `status`, `doneLate`, `completedOn`, `rescheduled`, `originalDate`, `daysMoved`, `daysLate`, `people`, `zoneId`, `zoneName`, `place` and an owning-screen `link` (`MARKETING`, `RECRUITMENT` or `TASKS`) present ONLY when the caller holds that screen's View; never notes, outcome text, evidence, attachments or incentive data (FR-020). Add `B/activitycalendar/web/ActivityCalendarExceptionAdvice.java` (400 bad month, 403, 404 in the existing error shape). Depends on T035
- [x] T037 [US1] Frontend: `FE/ActivityCalendarPage.tsx` (month header with previous, next, today; loads `GET /`; empty state "Nothing planned" with zero counts, FR-018), `FE/SummaryBar.tsx` (planned, done, done late, missed, cancelled, rescheduled, total and by kind; "partial" marker), `FE/MonthGrid.tsx` (items per day with status chip, Rescheduled mark with the first planned date, struck-through Cancelled, "No place recorded", "No Zone" for Director and Admin only, DD/MM/YYYY), `FE/ItemDetail.tsx` (read-only detail with Manager, kind, date, place, status, reschedule history and the owning-screen link only when `link` is present; no complete, move or cancel control anywhere), and the route `/operations/activity-calendar` in `frontend/src/App.tsx` guarded by `ACTIVITY_CALENDAR`. Depends on T003, T036
- [x] T038 [P] [US1] Frontend test `FE/__tests__/ActivityCalendarPage.test.tsx`: month with statuses and summary for the Director; empty month; detail is read-only and shows no link without a grant; menu label per role (Admin and Director "Activity Calendar")

**Checkpoint**: US1 works for Director and Admin and is the MVP.

---

## Phase 4: User Story 2 - A Manager Sees Their Own Plan Against What Is Done (Priority: P1)

**Goal**: a Manager sees only their own activities (owned, attended, assigned) with their own counts.

**Independent Test**: two Managers in one Zone and one in another; each sees only their own across every filter combination (SC-004).

- [x] T039 [US2] Scope matrix test `T/activitycalendar/CalendarScopeMatrixTest.java` (SC-004): Managers A and B in Zone 1 and C in Zone 2; for every combination of `managerId`, `zoneId`, `kind` and `status`, A never sees B's or C's items; a visit created by B with A as attendee shows for A and for B; a task assigned to A shows only for A; a `managerId` of another user answers 404; a `zoneId` of another Zone returns nothing and never widens; a deactivated Manager's past items stay visible to the Director
- [x] T040 [US2] Implement the Manager scope in `B/activitycalendar/internal/CalendarService.java` and `B/activitycalendar/web/ActivityCalendarController.java`: for a Manager the user comes from the token and is passed to the sources as `userId` (creator, attendee, interviewer or assignee), items are additionally filtered to the caller on the server, `byManager` is omitted, a `managerId` that is not the caller's answers 404, `zoneId` only narrows; Teacher and System get 403 first. Depends on T035, T036
- [x] T041 [US2] Frontend: for a Manager hide the Manager filter, title the page "My Activity Calendar" and show only own counts in `FE/ActivityCalendarPage.tsx` and `FE/SummaryBar.tsx`; test `FE/__tests__/ManagerView.test.tsx` (no Manager picker, own-only summary, no per-Manager table). Depends on T037

---

## Phase 5: User Story 3 - Missed and Rescheduled Items Are Easy to Find (Priority: P1)

**Goal**: a "Needs attention" list of missed and rescheduled items, grouped by Manager, oldest first.

**Independent Test**: three missed items for Manager A, one rescheduled for B, a clean month for C: the list shows A with 3 missed, B with 1 rescheduled and not C.

- [x] T042 [US3] Integration test `T/activitycalendar/NeedsAttentionTest.java`: groups per Manager sorted by name and only for Managers with an entry; entries oldest date first with `daysLate` or `daysMoved`; an item rescheduled twice is one entry showing the original and the current date; a missed task later completed shows Done with "done late" and leaves the list (FR-010), as does a cancelled one; an empty result is reported; a range over 93 days answers 400; a Manager sees only their own group
- [x] T043 [US3] Implement `B/activitycalendar/internal/NeedsAttentionService.java` and `GET /needs-attention?from&to&managerId&zoneId&kind` in the controller (default current month, up to 93 days, reasons `MISSED` and/or `RESCHEDULED`, grouped by Manager, same scope rules and partial handling as the calendar). Depends on T035, T040
- [x] T044 [US3] Frontend `FE/NeedsAttention.tsx` (grouped by Manager with missed and rescheduled counts, days late or moved, oldest first, opens `ItemDetail`, empty message) wired into `FE/ActivityCalendarPage.tsx` as a tab, with test `FE/__tests__/NeedsAttention.test.tsx`. Depends on T037, T043

---

## Phase 6: User Story 4 - Choose the Period, the Person and the Kind (Priority: P2)

**Goal**: month grid and list views, month navigation, and combinable filters kept in the session.

**Independent Test**: 40 items for 6 Managers; apply Manager, Zone, kind and status filters in turn; grid, list and summary agree each time.

- [x] T045 [US4] Test `T/activitycalendar/CalendarFilterTest.java`: `managerId`, `zoneId`, `kind` and `status` combine and the summary follows them ("Task, Missed" leaves only missed tasks in items and summary); unknown `kind` or `status` and a bad month answer 400; two items of one Manager on one day are both returned; Zone follows FR-014; items with no Zone appear only for Director and Admin
- [x] T046 [US4] Implement the filters in `B/activitycalendar/internal/CalendarService.java` and the query parameters in the controller; implement `GET /filters` (Managers for Director and Admin, only self for a Manager; Zones; the six kinds and which are available, `TRAINING available:false`) with test `T/activitycalendar/FiltersEndpointTest.java`. Depends on T040
- [x] T047 [US4] Frontend `FE/ListView.tsx` (same items and filters as the grid), filter bar and month/list toggle in `FE/ActivityCalendarPage.tsx` (Manager filter for Director and Admin only, Zone, kind, status; Monday-first week; "N more" on a busy day opening the day's full list in `FE/MonthGrid.tsx`; month and filters kept in `sessionStorage` with try/catch so they survive a refresh; clearing all restores everything). Depends on T037, T046
- [x] T048 [P] [US4] Frontend test `FE/__tests__/Filters.test.tsx`: filter combinations, "6 more" for a day with 9 items, month and filters survive a re-mount, clear all

---

## Phase 7: User Story 5 - Training Appears When It Exists (Priority: P3)

**Goal**: the calendar works without training, says so, tolerates a failing source, and accepts a future training source with no change.

**Independent Test**: no training source gives a note and normal loading; a stand-in source with one session shows it; a throwing task source leaves drives and visits.

- [x] T049 [US5] Test `T/activitycalendar/SourceResilienceTest.java` (SC-006): with no training bean the response has `TRAINING available:false, read:false` with the note "Training is not included yet" and `partial` stays false; a stand-in `ActivitySource` for TRAINING with one session shows it as kind Training with its assigned Managers; a task source that throws gives `TASKS read:false`, the note "Tasks could not be loaded", `partial: true` and drives and visits unchanged; all sources failing gives 200 with empty items and every group `read:false`
- [x] T050 [US5] Implement the placeholder `B/activitycalendar/internal/TrainingSource.java` (`available() = false`, no entries; spec 024 later replaces it with its own bean) and make `CalendarService` report unavailable and failed sources distinctly and by name. Depends on T035
- [x] T051 [US5] Frontend: show the `sources` notes in `FE/ActivityCalendarPage.tsx` ("Training: not available yet" in the kind filter, a banner naming a failed kind) and mark counts partial in `FE/SummaryBar.tsx`; test `FE/__tests__/Sources.test.tsx`. Depends on T037, T050

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T052 [P] Postman: add the three endpoints for Admin, Director, Manager, Teacher and System, including the 403 and 404 cases, to the collection under `postman/`
- [ ] T053 [P] Demo data: extend the demo seeder with the quickstart month for Managers A, B and C so the calendar is non-empty in a local run (find the existing seeder with `grep -ril demo backend/src/main/java`); no production effect
- [ ] T054 [P] Add "Amended by 035" notes: append a short section to `specs/023-school-marketing-mou/spec.md`, `specs/025-manager-tasks/spec.md` and `specs/016-campus-recruitment/spec.md` stating that `activitiesBetween`, `marketing_activity.completed_on`, `campus_drive.held_on` and `drive_date_change` were added additively by 035, that `plannedBetween` is unchanged, and the name of its regression test (T009, T015, T019)
- [ ] T055 [P] Update `docs/spec-roadmap.md`: the 035 row status, the C1 entry ("second method `activitiesBetween`, amended by 035"), and an "Amended by 035" note on the 016, 023 and 025 rows
- [ ] T056 [P] Update the default role access matrix in `.specify/memory/constitution.md` with an Activity Calendar row (Admin View, Director View, Manager View own scope, Teacher and System none) and the OPERATIONS navigation items, as a PATCH version bump with sync report
- [ ] T057 Run the whole backend suite (one class at a time where slow) and the whole frontend suite (`npm test`, `npm run build`, lint); fix regressions, in particular spec 032 Field day and spec 034 tests
- [ ] T058 Walk through every section of `specs/035-manager-activity-calendar/quickstart.md` (sections 1 to 8) and record the results in `specs/035-manager-activity-calendar/quickstart-results.md`; tick `specs/035-manager-activity-calendar/checklists/requirements.md` and note any deviation in plan.md

---

## Dependencies & Execution Order

- Setup (Phase 1) first. Foundational (Phase 2) blocks everything: migration T004, then the three amendments (023, 025 and 016
  are independent of each other and can run in parallel after the migration), then the SPI adapters T025, which need all
  three. Permission and navigation (T022 to T024) are independent of the amendments.
- US1 (MVP) needs Phase 2. US2 builds on US1's service and controller. US3 needs US1 and the scope rules of US2. US4 needs
  US1 (and T040 for the self-only Manager list). US5 needs US1's source reporting and can run beside US3 and US4.
- Polish last; the "Amended by 035" notes (T054) may be written as soon as Phase 2 is done.

```text
Setup -> Foundational -> US1 -> US2 -> US3
                           \-> US4
                           \-> US5
                 all -> Polish
```

## Parallel Opportunities

- After T004: the 023 (T005 to T009), 016 (T010 to T015) and 025 (T016 to T019) amendments in parallel, plus T020.
- Within each amendment: the contract test and the regression test in parallel (different files).
- US1 tests T027 to T031 are different files and run in parallel; T034 beside T033.
- Frontend tasks of a story can run beside the backend once `activityCalendarApi.ts` and the response contract are fixed.
- T052 to T056 in Polish run in parallel.

## Implementation Strategy

1. MVP: Phases 1, 2 and 3 (feeds amended, Director and Admin month calendar with summary). Stop and validate with quickstart sections 1, 2 and 8.
2. Add US2 (Manager scope, SC-004) and US3 (Needs attention): the three P1 stories complete the deck's ask.
3. Add US4 (filters and list), then US5 (training placeholder and resilience), then Polish.
4. The amendments are additive: after each amendment run the spec 032 and 034 tests (T021) to prove `plannedBetween` is unchanged.
