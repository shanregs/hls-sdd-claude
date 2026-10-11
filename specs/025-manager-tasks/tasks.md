# Tasks: Manager Tasks

Format: `[ID] [P?] [Story] description`. Stories: US1 Manager works tasks, US2 management, US3 overdue and escalation,
US4 C2 and 023 listener, US5 C1.

## Phase 1: Foundation

- [ ] T001 Migration `V45__create_task_tables.sql` (task, task_history with append-only trigger, task_evidence, notification type check).
- [ ] T002 `TASKS` permission module, eligibility (Admin, Director, Manager only; business module; Approve not for Manager), seeded grants, access-model scope, two navigation items.
- [ ] T003 `tasks.api` types: TaskType, TaskCreation, TaskActivities, PlannedActivity, TaskAssigned, TaskOverdue, TaskEscalated.
- [ ] T004 Entities and repositories: Task, TaskStatus, TaskHistory, TaskEvidence.

## Phase 2: US1 and US2 lifecycle

- [ ] T005 [US1] TaskService: create, acknowledge, start, complete (note rule), history and Change History on every step.
- [ ] T006 [US2] TaskService: edit/reassign/due change, verify, send back, cancel; verifier not assignee.
- [ ] T007 [US1] TaskQueryService: lists (today, upcoming, overdue, awaiting, done, all), summary, detail, scope for Manager.
- [ ] T008 [US1] Evidence upload and download through FileStore, max 5.
- [ ] T009 TaskController with permission guard on every endpoint.
- [ ] T010 [P] Tests: lifecycle API, scope (Manager B gets 404), validation, per-role authorization for every endpoint, menu items.

## Phase 3: US3 overdue and escalation

- [ ] T011 [US3] Overdue reason with optional follow-up task; completion of overdue task requires a reason.
- [ ] T012 [US3] DeadlineJob: overdue notice once per due date, escalation once, to Directors; idempotent.
- [ ] T013 [US3] Notification types, TaskEventListener, message texts.
- [ ] T014 [P] Tests: job idempotency, escalation recipients, notification content, closed tasks ignored.

## Phase 4: US4 and US5 interfaces

- [ ] T015 [US4] TaskCreationImpl with idempotent source key and Manager validation.
- [ ] T016 [US4] FollowUpListener for 023 FollowUpScheduled.
- [ ] T017 [US5] TaskActivitiesImpl.
- [ ] T018 [P] Tests: C2 idempotency and refusal, listener, C1 range and owner filters, ArchUnit module rules.

## Phase 5: Frontend and wrap-up

- [ ] T019 Frontend tasks feature: lists with tabs and counts, detail with actions, create form for management; Vitest tests per role.
- [ ] T020 Dev demo data (TaskDevSeeder), Postman folder, quickstart results.
- [ ] T021 Roadmap row 025, constitution 2.5.3 matrix row, full build and test run.
