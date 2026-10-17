---

description: "Task list for feature implementation"
---

# Tasks: Mobile Visits and Tasks

**Input**: Design documents from `/specs/034-mobile-visits-tasks/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/server-calls.md, contracts/screens.md, quickstart.md, and
**specs 018, 019, 020, 023, 025 and 033 implemented** (this feature adds screens to the app shell of 018, reuses the month, date,
photo, upload and action pieces of 019, 020 and 033, and calls the marketing activity and task endpoints of 023 and 025 unchanged;
there is no backend work).

**Tests**: included as first-class tasks. Constitution Principle IX requires per-scope tests (two Managers), menu-visibility tests per
role, and tests that every refusal leaves things unchanged and that nothing is shown as done before the server confirms it (SC-004 to
SC-006, SC-008).

**Organization**: grouped by user story in priority order (spec.md US1-US7). The shared plumbing (API clients, menu actions, business
date, photo pieces, messages, routes) comes first because both screens use it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US7; absent for Setup/Foundational/Polish

## Path Conventions

`MOB` = `mobile/src`, `MOBT` = `mobile/__tests__`. All work is under `mobile/` (plus `docs/` in Polish); `backend/` and `frontend/` are
not changed. Run commands from the `mobile` folder with JDK 17 (see `docs/running-mobile.md`). The real server shapes are in
`data-model.md` (they follow the code and the running server, not only the contract documents). In tests, `fireEvent` is asynchronous:
always `await` it, and do not use `waitFor` in tests that mock the clock; use `findBy*` or an `act` with a short wait.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: folders and realistic test data.

- [ ] T001 Create the folders `MOB/visits/`, `MOB/tasks/`, `MOB/media/`, `MOB/home/` and `MOBT/visitsTasks/`, and confirm `npm test`, `npm run lint`, `npm run typecheck` and `npm run check:manifest` pass from `mobile/` before any change
- [ ] T002 [P] Create `MOBT/support/visitTaskFixtures.ts` with builders that copy the real shapes of data-model.md: `visit({status, effectiveStatus, type, date, version, outcome, followUpOn, cancelReason, prospect, attendees, attachments, dateHistory, rescheduled})` for the server's `ActivityDto` (`status` one of `PLANNED`, `COMPLETED`, `CANCELLED`; `effectiveStatus` also `MISSED`; `type` one of `VISIT`, `CALL`, `PROPOSAL_MEETING`, `FOLLOW_UP`; `prospect {id, name}` or `school {id, name}`; `attachments [{fileId, name, sizeBytes, contentType}]`; `dateHistory [{from, to}]`), `prospect({won, schoolId, contactPerson, zoneName})`, `colleague()`, `taskRow({status, dueOn, overdue, daysOverdue, escalated, type, typeLabel})` (`id, type, typeLabel, title, status` one of `ASSIGNED`, `ACKNOWLEDGED`, `IN_PROGRESS`, `COMPLETED`, `VERIFIED`, `CANCELLED`, `dueOn, assigneeUserId, assigneeName, assigneeActive, linkedType, linkedId, linkedLabel, place, overdue, daysOverdue, escalated`), `taskDetail({actions, history, evidence, overdueReason, completionNote, followUpTaskIds})` (`task, description, createdBy, createdByName, escalateAfterDays, source, overdueReason, completionNote, completedAt, verifiedAt, verifiedByName, escalatedAt, cancelReason, parentTaskId, followUpTaskIds, history [{event, actorUserId, actorName, at, before, after, note}], evidence [{fileId, name, contentType, sizeBytes, addedAt, addedBy}], actions {acknowledge, start, complete, overdueReason, addEvidence, verify, sendBack, edit, cancel}, version`), `taskSummary()` (`today, upcoming, overdue, awaiting, escalated, done`), `taskTypes()` (nine `{value, label}`) and `visitTaskModel({tasks, visits, create, edit})` for the access model of a Manager with OPERATIONS → My Tasks (`/operations/tasks`) and MARKETING → Marketing Calendar (`/marketing/calendar`), each with View and the grants asked for
- [ ] T003 [P] Create `MOBT/support/marketingServer.ts` that registers on the existing `FakeServer`: `GET /api/v1/marketing/activities` (filters `from`, `to`, `mine`), `GET /api/v1/marketing/activities/{id}`, `POST .../activities`, `POST .../activities/{id}/complete`, `.../reschedule`, `.../cancel` (each checking the sent `version` and answering 409 "The visit was changed meanwhile." on a stale one, 409 when the stored status is not `PLANNED`), `POST .../activities/{id}/attachments` (multipart; 409 "A visit can have at most 10 files." at 10), `GET /api/v1/marketing/files/{fileId}` (bytes with `Content-Type`, `Content-Length`, `Content-Disposition`), `GET /api/v1/marketing/prospects?query=&page=&size=` and `GET /api/v1/recruitment/interviewers`; it applies plans, completions, moves, cancels and uploads to its own state, derives `effectiveStatus` `MISSED` for a planned visit before the server date, records every call (method, path, body, headers) for the request-shape tests, and takes refusal hooks like `MOBT/support/managerServer.ts`
- [ ] T004 [P] Create `MOBT/support/tasksServer.ts` that registers `GET /api/v1/tasks?view=&page=&size=` (the views `today`, `upcoming`, `overdue`, `awaiting`, `done`, `all`, paged), `GET .../tasks/summary`, `GET .../tasks/types`, `GET .../tasks/assignees`, `GET .../tasks/{id}`, `POST .../tasks`, `POST .../tasks/{id}/acknowledge`, `/start`, `/overdue-reason`, `/complete`, `/evidence` (multipart; 409 "A task can have at most 5 evidence files." at 5), `GET .../tasks/files/{fileId}`; each step answers the whole updated detail with its `actions` recomputed the way spec 025 does (acknowledge only from Assigned, start from Acknowledged, complete from Acknowledged or In progress and, when overdue, only after a reason; `addEvidence` false at 5), records every call, and takes refusal hooks
- [ ] T005 Create `MOBT/support/visitTaskApp.tsx` with `openVisitTaskScreen(menuLabel, setup)`: like `openManagerScreen` in `MOBT/support/managerApp.tsx`, it signs in with a stored session, installs the marketing and task routes with the setup's overrides, uses `visitTaskModel()` unless a model is given, and opens a screen from the drawer by its menu label (null stays on Home); the server's date is 2026-10-05 unless set

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the API clients, the menu-actions hook, the business date, the shared photo pieces, the messages and the routes both screens use.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [ ] T006 Create `MOB/api/marketingApi.ts` with the types of data-model.md (`Visit`, `VisitStatus` one of `PLANNED`, `COMPLETED`, `CANCELLED`, `EffectiveVisitStatus` adding `MISSED`, `VisitKind` one of `VISIT`, `CALL`, `PROPOSAL_MEETING`, `FOLLOW_UP`, `Prospect`, `Colleague`, `AttachmentRef`) and typed calls for every endpoint of contracts/server-calls.md "Visits": `listVisits(from, to)` (always `mine=true`; returns `content` only), `getVisit(id)`, `planVisit({prospectId, type, date, attendeeUserIds?, notes?})`, `completeVisit(id, {outcome, notes?, followUpOn?, version})`, `rescheduleVisit(id, {date, version})`, `cancelVisit(id, {reason, version})`, `attachVisitPhoto(id, file)` (uses `upload` of `MOB/api/httpClient.ts`), `openVisitFile(fileId)` (uses `fetchBinary`, returns the data address, type, size and name as `openPhoto` of `MOB/api/managerAttendanceApi.ts` does), `listProspects({query, page, size})` and `listColleagues()`; the app computes none of the statuses it returns
- [ ] T007 [P] Create `MOB/api/tasksApi.ts` with the types of data-model.md (`TaskRow`, `TaskDetail`, `TaskActions`, `TaskSummary`, `TaskStatus`, `TaskView` one of `today`, `upcoming`, `overdue`, `awaiting`, `done`, `all`, `TaskType`, `Assignee`) and typed calls: `listTasks(view, page, size)` returning `{content, page, size, totalElements}`, `getTaskSummary()`, `getTask(id)`, `acknowledgeTask(id)`, `startTask(id)`, `giveOverdueReason(id, {reason, followUpOn?})`, `completeTask(id, {note})`, `addTaskEvidence(id, file)`, `openTaskFile(fileId)`, `listTaskTypes()`, `listAssignees()` and `createTask({type, title, description?, assigneeUserId, dueOn, place?})`; every step answers the whole `TaskDetail`
- [ ] T008 [P] Create `MOBT/api/visitsTasksContract.test.ts`: one request-shape test per call of T006 and T007 against the fake servers (bodies, the `version` sent back, `mine=true`, `from` and `to` as ISO dates, the multipart field name `file`, the page size, no body for acknowledge and start), and that a refusal reaches the caller as an `ApiError` carrying the server's `reason`
- [ ] T009 Create `MOB/access/useMenuActions.ts` with `useMenuActions(route)` returning `{canCreate, canEdit, canDelete}` from the `actions` of the access-model menu item whose route is `route` (no role is read); change `MOB/manager/useManagerActions.ts` to call it and keep its `{canCreate, canDelete}` result so spec 033 behaves exactly as before; add `MOBT/access/useMenuActions.test.tsx` (each grant alone, no matching item, the same route in two sections) and run `MOBT/manager` unchanged
- [ ] T010 [P] Add `businessToday(now = serverNow())` to `MOB/attendance/serverClock.ts` returning the business date (UTC+05:30) as `YYYY-MM-DD`, and `monthBounds(month)` returning the first and last date of a `YYYY-MM` to `MOB/attendance/monthRange.ts`; extend `MOBT/attendance/monthRange.test.ts` and add `MOBT/attendance/businessToday.test.ts` (31 December 20:00 UTC is already 1 January in India; a wrong phone clock is corrected by the server clock)
- [ ] T011 Register the two screens and shell navigation: in `MOB/access/screenRegistry.ts` add keys `visits` and `tasks` mapped from `/marketing/calendar` and `/operations/tasks`; in `MOB/navigation/AppShell.tsx` render `VisitsScreen` and `TasksScreen` (placeholder components in `MOB/screens/VisitsScreen.tsx` and `MOB/screens/TasksScreen.tsx` until the story tasks), titles "Marketing Calendar" and "My Tasks", and add `taskView?: TaskView` to `RouteState` so Home can open Overdue; extend `MOBT/manager/routes.test.ts`-style checks in `MOBT/visitsTasks/routes.test.ts` (both routes map, an unknown marketing route such as `/marketing/prospects` still shows nothing, the menu shows the two entries only when the model has them)
- [ ] T012 [P] Create the shared photo pieces in `MOB/media/`: `PhotoSlots.tsx` (props `{photos, onChange, limit, alreadyAttached, disabled}`: "Take a photo" and "Choose from gallery" through `MOB/manager/PhotoPicker.tsx`'s picker calls, a thumbnail and name per picked photo with Remove, "n of limit" counting `alreadyAttached + photos.length`, and the add controls hidden when the limit is reached), `AttachmentList.tsx` (props `{files, open}`: each file's name, type and size from the server, and a thumbnail loaded with `open(fileId)` for `image/*` files only; a document shows its name, type and size and "Open it on the web"; a failed thumbnail says so with Try again; bytes held in memory only) and `uploadPhotos.ts` (`uploadPhotos(photos, send)` uploads one after another, never throws, and returns `{failed: [{name, reason}]}` with the server's reason for each); add `MOBT/visitsTasks/media.test.tsx` (the count and the limit, documents are not thumbnailed, one failure does not stop the next upload, a refusal reason is returned as the server wrote it)
- [ ] T013 [P] Create `MOB/visits/visitMessages.ts` (`visitStatusText(effective)` Planned, Completed, Cancelled, Missed; `visitKindText(type)` Visit, Call, Proposal meeting, Follow-up) and `MOB/tasks/taskMessages.ts` (`taskStatusText(status)` Assigned, Acknowledged, In progress, Completed, Verified, Cancelled; `overdueText(row)` "N days overdue" / "1 day overdue"; `moreNeededText(have, need)` "N more characters needed"; `historyEventText(event)` for `CREATED` and the events the server writes, falling back to the event name); add `MOBT/visitsTasks/messages.test.ts`

**Checkpoint**: clients, hooks, helpers, shared pieces and routes work; no screen is complete yet.

---

## Phase 3: User Story 1 - A Manager Sees Their Visits (Priority: P1) 🎯 MVP

**Goal**: the month's visits grouped today, later and earlier, filtered by status, with a detail inside the screen.

**Independent Test**: sign in as a Manager with a visit today, one next week and one last week never completed, and confirm the list groups and labels them as the server does and the detail shows the server's fields (quickstart scenario 3).

### Tests for User Story 1

- [ ] T014 [P] [US1] `MOBT/visitsTasks/visitList.test.tsx`: the screen opens on the current month (`2026-10`) and asks the server for `from=2026-10-01&to=2026-10-31&mine=true`; the visits show in groups "Today" (date equal to the server date), "Later" in date order and "Earlier" newest first, each with the prospect's name, the date as DD/MM/YYYY, the kind in words and the server's `effectiveStatus` in words (a planned visit before today as Missed because the server says so, never worked out in the app: a fixture whose dates are all past but `effectiveStatus` is `PLANNED` shows Planned); the "moved" mark shows when `rescheduled`; the filter chips All, Planned, Completed, Cancelled, Missed show only rows of that `effectiveStatus` and are kept while a detail is opened and closed; the month arrows load the other month (previous, current and next year reachable, no other); an empty month says "No visits this month." and points to Plan a visit when Create is granted; a failed load shows the server's reason with Try again and no stale rows; no connection says so; a pull down reloads from the server; `drives` in the response are not shown; a visit of another Manager does not appear (the server's list is trusted, two Managers' fixtures)
- [ ] T015 [P] [US1] `MOBT/visitsTasks/visitDetail.test.tsx`: opening a visit shows the target (prospect or school name), date, kind, attending colleagues, notes; for a completed visit the outcome, the follow-up date and "The follow-up date has passed" only when `followUpOverdue`; for a cancelled one the reason; "Moved from DD/MM/YYYY" for each `dateHistory` entry; attachments by name, type and size with a thumbnail for an image and none for a PDF; Back (the hardware back handler) closes the detail and not the screen; a visit that the server no longer finds (404) says so and returns to the list; Complete visit, Reschedule and Cancel visit are shown exactly when the menu grants Edit and the stored `status` is `PLANNED` (also for an `effectiveStatus` of `MISSED`) and never for a completed or cancelled visit, whatever the date

### Implementation for User Story 1

- [ ] T016 [P] [US1] Create `MOB/visits/useVisits.ts`: `useVisits(month)` loads `listVisits(monthBounds(month))` and returns `{status: "loading"|"ready"|"error"|"noConnection", visits, message, reload}`; a result belongs to the month it was loaded for, a failed load drops the rows, `reload({quiet})` keeps them until the new ones arrive; add `groupVisits(visits, today)` returning `{today, later, earlier}` by date only (earlier newest first) and `filterVisits(visits, filter)` by `effectiveStatus`, tested in `MOBT/visitsTasks/useVisits.test.tsx`
- [ ] T017 [P] [US1] Create `MOB/visits/VisitCard.tsx`: one row with the target's name, `DD/MM/YYYY`, `visitKindText`, `visitStatusText` (Missed styled apart and never by colour alone), a "moved" mark, a spoken label such as "Visit to Green Valley Public School, 06/10/2026, planned", press to open; 48 dp target
- [ ] T018 [US1] Create `MOB/visits/VisitDetail.tsx` per contracts/screens.md "Visits" → Detail: the fields of T015, `AttachmentList` with `openVisitFile`, and the buttons Complete visit, Reschedule and Cancel visit (shown by the rule of T015, using `useMenuActions("/marketing/calendar")` and the stored `status`), Add photo when Create is granted and the visit has fewer than 10 files; the buttons open the forms of the later stories (stubs until then)
- [ ] T019 [US1] Create `MOB/screens/VisitsScreen.tsx` (replacing the placeholder): the `MonthPicker` of spec 019 with `kind="leave"`, the filter chips, the grouped list of `VisitCard`, the detail via `useInnerBack`, pull-to-refresh, the empty, error and no-connection states, and "Plan a visit" (inactive until US4) when Create is granted; fetch the visit again with `getVisit` when a detail opens so the version is current

**Checkpoint**: US1 is complete and demonstrable on its own (the MVP).

---

## Phase 4: User Story 2 - A Manager Logs a Visit (Priority: P1)

**Goal**: complete a planned visit with an outcome, optional notes, follow-up date and photos uploaded after completion.

**Independent Test**: complete a planned visit with an outcome, a follow-up date and a photo, and confirm Completed with the outcome and photo, and a follow-up task in My Tasks (quickstart scenario 4).

### Tests for User Story 2

- [ ] T020 [P] [US2] `MOBT/visitsTasks/completeVisit.test.tsx`: Send stays disabled while the outcome is empty or only spaces and the form says an outcome is needed ("who was met, what was discussed and the next action"); outcome and notes accept at most 1000 characters each and show "N characters left"; the follow-up date is chosen with the date chooser and is optional; Send posts `{outcome, notes, followUpOn, version}` (trimmed, absent fields left out, the `version` of the visit as last loaded) to `/activities/{id}/complete`; on success the detail shows Completed with the outcome as the server returned it; a picked photo is uploaded **after** the completion (the request order is complete, then attachments), one request per photo with the multipart field `file`; a visit completed with no photo makes no attachment request and is accepted (a photo is never required); the follow-up task appears in My Tasks after the tasks screen next loads (the fake server creates it)
- [ ] T021 [P] [US2] `MOBT/visitsTasks/completeVisitRefusals.test.tsx`: for each refusal (no outcome 400, follow-up date before the visit 400, a stale version 409 "The visit was changed meanwhile.", a visit no longer planned 409, a visit not found 404) the server's reason is shown in plain language, nothing is shown as completed and the entries (outcome, notes, date, photos) are kept; after a stale version or a visit no longer planned the visit is reloaded and its new state is shown; a refused or failed photo (400 not an allowed type, 409 "A visit can have at most 10 files.", a lost connection) leaves the visit Completed, names the photo and the server's reason, and Add photo remains in the detail to try again; offline at Send says "No connection. Nothing was sent.", keeps every entry and shows nothing as completed; a double tap on Send sends one request; the form is not offered without the Edit grant

### Implementation for User Story 2

- [ ] T022 [US2] Create `MOB/visits/CompleteVisitForm.tsx`: Outcome (multiline, `maxLength` 1000, hint "Who was met, what was discussed and the next action"), Notes (1000), a follow-up `DateChooser` from `MOB/leave/DateChooser.tsx`, `PhotoSlots` (limit `10 - attachments.length`, optional, labelled "none required"), and Send using `useAction`: it calls `completeVisit`, then `uploadPhotos` with `attachVisitPhoto`, and returns the visit and the failed photos; Send is disabled while the outcome is blank; the form keeps every entry on a refusal and clears only after the server confirms
- [ ] T023 [US2] Wire `CompleteVisitForm` into `MOB/visits/VisitDetail.tsx` and `MOB/screens/VisitsScreen.tsx`: Complete visit opens it, a success replaces the detail with the server's visit and reloads the month quietly, failed photos are listed under the visit with the server's reasons and an Add photo action (`PhotoSlots` + `uploadPhotos`, shown when Create is granted and fewer than 10 files); a 409 reloads the visit with `getVisit`

**Checkpoint**: US1 and US2 give a Manager the visit diary: see it, then log it.

---

## Phase 5: User Story 3 - A Manager Works Through Their Tasks (Priority: P1)

**Goal**: the six views, a detail, and every step driven by the server's `actions`, with evidence.

**Independent Test**: with a new task, a task in progress and an overdue task, acknowledge, start, complete with a note and a photo, and give a reason on the overdue one (quickstart scenarios 6 and 7).

### Tests for User Story 3

- [ ] T024 [P] [US3] `MOBT/visitsTasks/taskList.test.tsx`: the screen opens on Today and asks `GET /tasks?view=today&page=0&size=25`; the view chooser offers Today, Upcoming, Overdue, Awaiting verification, Done and All with the counts of `/tasks/summary`; each view shows the server's rows with title, type label, due date as DD/MM/YYYY, status in words, "N days overdue" from `daysOverdue` only when `overdue`, and "Escalated" only when `escalated`; Load more asks the next page and appends it, hidden when all rows are loaded; a failed page keeps the earlier pages and shows the reason; a failed load shows the server's reason with Try again and drops the rows; no connection says so; empty views say "Nothing here."; the chosen view is kept while a detail is opened and closed; opening Home's overdue card opens Overdue; a Director's fixture (rows of other Managers, every `actions` flag false) lists them with no step buttons
- [ ] T025 [P] [US3] `MOBT/visitsTasks/taskSteps.test.tsx`: Acknowledge, Start, Give a reason, Complete and Add photo are each shown exactly when `actions.acknowledge`, `start`, `overdueReason`, `complete`, `addEvidence` is true for that task and never from its status, due date or the phone's clock (a wrong phone clock and a task whose status says Assigned but whose flags are all false show no buttons); verify, send back, edit and cancel are never shown even when their flags are true; Acknowledge and Start post with no body and replace the detail with the server's answer only after the 2xx; the detail shows who assigned it (`createdByName`), the due date, place, linked label, description, the overdue reason, the completion note, "Verified by", the follow-up links as a count, and the history oldest first with who and when
- [ ] T026 [P] [US3] `MOBT/visitsTasks/taskOverdueComplete.test.tsx`: Give a reason keeps Send disabled until the reason has at least 5 characters and shows "N more characters needed"; it posts `{reason, followUpOn}` (date optional, chosen with the date chooser) and the detail then shows the reason and Complete (the fake server turns `complete` on); Complete keeps Send disabled until the note has at least 10 characters with "N more characters needed", posts `{note}` trimmed, and on success shows Completed and moves the task to Awaiting verification on the next load; for each refusal (the task changed on the web 409, not the Manager's 404, a rule 400) the server's reason is shown, the task is reloaded and the entries are kept; offline says "No connection. Nothing was sent." and shows nothing as done; a double tap sends one request
- [ ] T027 [P] [US3] `MOBT/visitsTasks/taskEvidence.test.tsx`: the evidence shows "n of 5" from the server's list, each file with name, type and size and a thumbnail for an image; Add photo (camera or gallery) is shown only while `actions.addEvidence` is true and disappears at 5; picked photos upload one by one to `/tasks/{id}/evidence` (multipart `file`) and the detail takes the server's latest answer after each; a photo refused (409 "A task can have at most 5 evidence files.", 400 type) or failed names the photo and the reason, keeps the others and never changes the task's status; completing a task with no photo is accepted (a photo is never required)

### Implementation for User Story 3

- [ ] T028 [P] [US3] Create `MOB/tasks/useTasks.ts`: `useTasks(view)` loads pages of 25 with `listTasks` and returns `{status, rows, total, loadMore, loadingMore, message, reload}`; a result belongs to the view it was loaded for, a failed first page drops the rows, a failed later page keeps the earlier ones and reports it; add `useTaskSummary()` returning the counts or an error; tested in `MOBT/visitsTasks/useTasks.test.tsx`
- [ ] T029 [P] [US3] Create `MOB/tasks/TaskCard.tsx`: title, type label, due date, status, overdue days and escalated mark from the server, a spoken label such as "Visit the school and meet the Principal, due 12/10/2026, assigned, 5 days overdue", press to open; 48 dp target
- [ ] T030 [US3] Create `MOB/tasks/TaskDetail.tsx` per contracts/screens.md "Tasks" → Detail: loads `getTask(id)`, shows every field of T025 and the history, and renders `TaskSteps` and `EvidenceSection`; a 404 says "not found" and returns to the list
- [ ] T031 [US3] Create `MOB/tasks/TaskSteps.tsx`: the buttons of T025 from the `actions` flags, each through `useAction`; Give a reason and Complete open a small form (reason at least 5 characters, optional follow-up `DateChooser`; note at least 10 characters) with `moreNeededText`; every success replaces the detail with the server's answer, every refusal shows the server's reason, reloads the task and keeps the entries
- [ ] T032 [US3] Create `MOB/tasks/EvidenceSection.tsx`: "Evidence (n of 5)", `AttachmentList` with `openTaskFile`, and `PhotoSlots` (limit 5, `alreadyAttached` from the server's list) shown while `actions.addEvidence` is true, uploading with `uploadPhotos` and `addTaskEvidence` and keeping the server's latest detail
- [ ] T033 [US3] Create `MOB/screens/TasksScreen.tsx` (replacing the placeholder): the view chooser with counts (`useTaskSummary`), the list of `TaskCard` with Load more, the detail via `useInnerBack`, pull-to-refresh (list, counts and open detail), the initial view from `RouteState.taskView` (default Today), and the empty, error and no-connection states; reload the list and counts quietly after any step

**Checkpoint**: a Manager can work their tasks on the phone.

---

## Phase 6: User Story 4 - A Manager Plans, Moves and Cancels a Visit (Priority: P2)

**Goal**: plan a visit against a prospect, move a planned visit, cancel it with a reason.

**Independent Test**: plan a visit for a prospect next Tuesday, move it to Wednesday, cancel another with a reason, and see each in the list and history (quickstart scenario 5).

### Tests for User Story 4

- [ ] T034 [P] [US4] `MOBT/visitsTasks/planVisit.test.tsx`: Plan a visit is offered only with the Create grant; the prospect search asks `GET /marketing/prospects?query=<typed>&page=0&size=25` as the Manager types and shows name, Zone and contact person, a won prospect marked as a School; the kind (Visit, Call, Proposal meeting, Follow-up), the date (date chooser, no time), colleagues from `/recruitment/interviewers` (optional) and notes (at most 1000 characters, "N characters left"); Send is disabled without a prospect and a date and the form says which is missing; it posts `{prospectId, type, date, attendeeUserIds, notes}` (no `attendeeUserIds` when none are chosen) and the new visit appears in the month list and opens in its detail; refusals (a date in the past or a prospect outside the Zones 404, a rule 400) show the server's reason with every entry kept; offline keeps the entries and shows nothing as planned
- [ ] T035 [P] [US4] `MOBT/visitsTasks/rescheduleCancel.test.tsx`: Reschedule opens the date chooser and Send is disabled until a date is chosen; it posts `{date, version}` and the detail shows the new date and "Moved from" the old one; moving to the same date shows the server's 409 reason; Cancel visit asks for a reason (at most 300 characters, "N characters left"), is disabled while it is blank, posts `{reason, version}` and the detail shows Cancelled with the reason; a stale version (409) shows the server's reason and reloads the visit with the entries kept; a visit that is not planned has neither button; offline says "No connection. Nothing was sent." and keeps the entries

### Implementation for User Story 4

- [ ] T036 [US4] Create `MOB/visits/PlanVisitForm.tsx`: a debounced prospect search list (25 at a time, "Load more"), the kind choice, the date chooser, a colleague multi-select (optional) and notes, Send through `useAction`; on success it opens the new visit's detail; the form keeps every entry on a refusal
- [ ] T037 [US4] Create `MOB/visits/RescheduleDialog.tsx` (a `DateChooser` and Send, reusing `ReasonDialog`'s refusal handling pattern from `MOB/leave/ReasonDialog.tsx`) and `MOB/visits/CancelVisitDialog.tsx` (a required reason of at most 300 characters through `ReasonDialog` with `required` and `maxLength={300}`)
- [ ] T038 [US4] Wire Plan a visit, Reschedule and Cancel visit into `MOB/screens/VisitsScreen.tsx` and `MOB/visits/VisitDetail.tsx`; every success replaces the detail with the server's visit and reloads the month quietly; a 409 reloads the visit

**Checkpoint**: planning, moving and cancelling work, so the calendar of US1 can be kept from the phone.

---

## Phase 7: User Story 5 - Home Shows Today's Visits and Overdue Tasks (Priority: P2)

**Goal**: two Home cards with the server's counts.

**Independent Test**: with two visits today and three overdue tasks, Home shows "2 visits today" and "3 overdue" and each opens the right list (quickstart scenario 2).

### Tests for User Story 5

- [ ] T039 [P] [US5] `MOBT/visitsTasks/homeCards.test.tsx`: a user whose menu offers `/marketing/calendar` sees "N visits today" counting only visits of today with `effectiveStatus` `PLANNED` from `GET /marketing/activities?from=<today>&to=<today>&mine=true`; a user whose menu offers `/operations/tasks` sees "N overdue, M due today" from `/tasks/summary`; the visits card opens the visits screen and the tasks card opens the tasks screen on Overdue; each card loads and fails on its own ("could not be loaded" with Try again, the other unaffected, no stale count shown as current); a user whose menu offers neither (a Teacher) sees neither and neither request is made; the "coming soon" skeleton cards for MARKETING and OPERATIONS are not shown for a section that now has a screen; a pull down on Home reloads both cards

### Implementation for User Story 5

- [ ] T040 [US5] Create `MOB/home/VisitsTasksCards.tsx` (`VisitsCard` and `TasksCard`, each with its own loading, error and Try again) and add them to `MOB/screens/HomeScreen.tsx` when the model's navigation contains the matching route, with the home pull-to-refresh reloading them; the visits card counts the server's list for `businessToday()`, and opening it uses `openRoute("/marketing/calendar")` and the tasks card `openRoute("/operations/tasks", {taskView: "overdue"})`

**Checkpoint**: Home is a daily briefing for visits and tasks.

---

## Phase 8: User Story 6 - Menus, Permissions and Safety Come From the Server (Priority: P2)

**Goal**: prove the screens and buttons exist only because the server says so, and that nothing is ever shown as done unless confirmed.

**Independent Test**: sign in as a Manager, a Teacher and a Director and confirm who sees what; remove a permission and confirm the button disappears; change a task on the web while open on the phone and confirm the phone's action is refused with the server's reason (quickstart scenarios 1, 8 and 11).

### Tests for User Story 6

- [ ] T041 [P] [US6] `MOBT/visitsTasks/menuVisibility.test.tsx`: a Manager's menu shows OPERATIONS → My Tasks and MARKETING → Marketing Calendar exactly as the access model returns them; a Teacher shows neither; a Director's model (labels "Manager Tasks" and "Marketing Calendar") shows both; removing Create hides Plan a visit, Add photo on a visit and Add a task; removing Edit hides Complete visit, Reschedule and Cancel visit while the screens still load; task step buttons follow `actions` whatever the menu grants; a source scan of `MOB/visits`, `MOB/tasks`, `MOB/media`, `MOB/home`, `MOB/screens/VisitsScreen.tsx`, `MOB/screens/TasksScreen.tsx` and `MOB/api/marketingApi.ts`, `MOB/api/tasksApi.ts` finds no role name (`MANAGER`, `DIRECTOR`, `TEACHER`, `ADMIN`) and no `.roles` read
- [ ] T042 [P] [US6] `MOBT/visitsTasks/neverFalseSuccess.test.tsx`: table-driven like `MOBT/manager/neverFalseSuccess.test.tsx`: for complete visit, add visit photo, plan visit, reschedule, cancel visit, acknowledge, start, overdue reason, complete task and add task evidence, run each against (a) the server's refusal 409, (b) a 500, (c) a lost connection and (d) a 401 that cannot be renewed: in every run nothing is shown as done, the server's reason or "No connection. Nothing was sent." is shown (except after a forced sign-out), and every entered value is kept (SC-004, SC-005); a photo failure after a successful action never turns the action into a failure (SC-008)
- [ ] T043 [P] [US6] `MOBT/a11y/visitsTasks.a11y.test.tsx`: the visits list and detail, the task list and detail, the forms and the Home cards pass the checks of `MOBT/a11y/manager.a11y.test.tsx` in light and dark themes and at font scale 1.3: every button and radio has a name, rows announce their text, status is never told by colour alone (Missed and overdue carry words), live regions announce refusals, 48 dp targets

### Implementation for User Story 6

- [ ] T044 [US6] Fix whatever T041 to T043 find; make sure every screen of this feature takes its Create and Edit buttons from `useMenuActions` and its task step buttons from the `actions` flags, and that a 409 or 404 from any step reloads the visit or task and keeps the entries

**Checkpoint**: all core stories work and the safety properties are tested.

---

## Phase 9: User Story 7 - A Manager Adds a Task for Themselves (Priority: P3)

**Goal**: add a task for oneself when the server lists the caller as an assignee.

**Independent Test**: add a task for tomorrow and see it under Upcoming; as a Director the control is not offered (quickstart scenario 9).

### Tests for User Story 7

- [ ] T045 [P] [US7] `MOBT/visitsTasks/addTask.test.tsx`: "Add a task for me" is offered only when the menu grants Create and `GET /tasks/assignees` contains the caller's own user id (a Manager's list is only themselves; a Director's list of all Managers without their own id hides it; no assignee request is made without Create); the type list is the server's `GET /tasks/types`; title (3 to 120 characters, "N characters left"), optional description, due date (date chooser) and optional place; Send is disabled until a type, a title of at least 3 characters and a date are given; it posts `{type, title, description, assigneeUserId, dueOn, place}` with the caller's own id, and the new task opens in its detail and appears under Upcoming or Today; refusals (a due date in the past, a rule) show the server's reason with the entries kept; offline keeps the entries and shows nothing as added

### Implementation for User Story 7

- [ ] T046 [US7] Create `MOB/tasks/AddTaskForm.tsx` and the entry button in `MOB/screens/TasksScreen.tsx`: it loads `listTaskTypes` and `listAssignees` when opened, finds the caller's id from the signed-in user, and is offered only by the rule of T045; Send through `useAction`; the caller's own id is always the assignee

**Checkpoint**: all seven stories work.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T047 [P] Update `docs/running-mobile.md` with the visits and tasks walkthrough (the prerequisites of quickstart.md, the camera or gallery for photos, and that no rebuild is needed since spec 033)
- [ ] T048 [P] Update `docs/spec-roadmap.md` row 034 to "Implemented" with the number of app tests and "device pass pending", and correct rows 023 and 025 from "PR not yet opened" to merged
- [ ] T049 Run `npm run typecheck`, `npm run lint`, `npm test` and `npm run check:manifest` from `mobile/` and fix everything they report; confirm the 019, 020, 021 and 033 tests pass unchanged
- [ ] T050 Run the walkthrough in `quickstart.md` end to end on the emulator (steps 1 to 11) and record the results in `specs/034-mobile-visits-tasks/quickstart-results.md`, including the times for SC-001 and SC-002; steps that need a person at the device (camera, TalkBack, timings) are marked **not done** when not done, never as passed

---

## Dependencies & Execution Order

- **Setup (Phase 1)** first. **Foundational (Phase 2)** needs Phase 1 and blocks every story.
- **US1** needs Phase 2 only (the MVP). **US2** needs US1's detail (T018, T019). **US3** is independent of US1 and US2 after Phase 2 and can be built in parallel with them. **US4** needs US1's screen and detail. **US5** needs the lists to open (T019, T033) but only its own cards otherwise. **US6** tests everything and comes last. **US7** needs US3's screen.
- **Polish** last.
- Within a story: tests first (they may be written to fail), then the pieces marked [P], then the screen.

### Parallel opportunities

- Phase 1: T002, T003 and T004 together.
- Phase 2: T007, T010, T012 and T013 together once T006 exists; T008 after T006 and T007.
- US1 and US3 can proceed in parallel after Phase 2 (different folders and screens).
- US1: T014 and T015 together; T016 and T017 together. US3: T024 to T027 together; T028 and T029 together.
- US6: T041, T042 and T043 together.

## Implementation Strategy

**MVP**: Phase 1, Phase 2 and User Story 1 (see the month's visits). Stop and run quickstart scenarios 1 to 3.
Then add US2 (log a visit) and US3 (tasks), which are the other two P1 stories; then US4, US5 and US6; and finally US7 (the lowest priority,
which can be cut without affecting the others).
