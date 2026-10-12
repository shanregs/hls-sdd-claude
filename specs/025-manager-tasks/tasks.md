# Tasks: Manager Tasks

Format: `[ID] [P?] [Story] description`. Stories: US1 Manager works tasks, US2 management, US3 overdue and escalation,
US4 C2 and 023 listener, US5 C1.

## Phase 1: Foundation

- [x] T001 Migration `V45__create_task_tables.sql` (task, task_history with append-only trigger, task_evidence, notification type check).
- [x] T002 `TASKS` permission module, eligibility (Admin, Director, Manager only; business module; Approve not for Manager), seeded grants, access-model scope, two navigation items.
- [x] T003 `tasks.api` types: TaskType, TaskCreation, TaskActivities, PlannedActivity, TaskAssigned, TaskOverdue, TaskEscalated.
- [x] T004 Entities and repositories: Task, TaskStatus, TaskHistory, TaskEvidence.

## Phase 2: US1 and US2 lifecycle

- [x] T005 [US1] TaskService: create, acknowledge, start, complete (note rule), history and Change History on every step.
- [x] T006 [US2] TaskService: edit/reassign/due change, verify, send back, cancel; verifier not assignee.
- [x] T007 [US1] TaskQueryService: lists (today, upcoming, overdue, awaiting, done, all), summary, detail, scope for Manager.
- [x] T008 [US1] Evidence upload and download through FileStore, max 5.
- [x] T009 TaskController with permission guard on every endpoint.
- [x] T010 [P] Tests: lifecycle API, scope (Manager B gets 404), validation, per-role authorization for every endpoint, menu items.

## Phase 3: US3 overdue and escalation

- [x] T011 [US3] Overdue reason with optional follow-up task; completion of overdue task requires a reason.
- [x] T012 [US3] DeadlineJob: overdue notice once per due date, escalation once, to Directors; idempotent.
- [x] T013 [US3] Notification types, TaskEventListener, message texts.
- [x] T014 [P] Tests: job idempotency, escalation recipients, notification content, closed tasks ignored.

## Phase 4: US4 and US5 interfaces

- [x] T015 [US4] TaskCreationImpl with idempotent source key and Manager validation.
- [x] T016 [US4] FollowUpListener for 023 FollowUpScheduled.
- [x] T017 [US5] TaskActivitiesImpl.
- [x] T018 [P] Tests: C2 idempotency and refusal, listener, C1 range and owner filters, ArchUnit module rules.

## Phase 5: Frontend and wrap-up

- [x] T019 Frontend tasks feature: lists with tabs and counts, detail with actions, create form for management; Vitest tests per role.
- [x] T020 Dev demo data (TaskDevSeeder), Postman folder, quickstart results.
- [x] T021 Roadmap row 025, constitution 2.5.3 matrix row, full build and test run.
