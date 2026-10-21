# Tasks: Teacher Observation and Performance

Format: `- [ ] [ID] [P?] [Story] description with file path`. `[P]` = can run in parallel (different files, no open dependency).
Stories: US1 observation, US2 plans and tasks, US3 Principal feedback, US4 performance issue and correction, US5 referral,
US6 lists and reports. Backend root `backend/src/main/java/com/hls/performance` (written `perf/`), tests under
`backend/src/test/java/com/hls/performance` (`perf-test/`), frontend `frontend/src/features/performance` (`fe/`).
Design: [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/),
[quickstart.md](quickstart.md).

## Phase 1: Setup

- [ ] T001 Migration `backend/src/main/resources/db/migration/V60__create_performance.sql`: tables observation, observation_rating, observation_attachment, principal_feedback, performance_issue, issue_link, corrective_action, referral, observation_plan, performance_history exactly as in data-model.md (columns, checks, partial unique indexes: one `current` row per `root_id`, one open issue per `teacher_id`, one OPEN plan per `teacher_id`; append-only trigger on performance_history refusing UPDATE and DELETE).
- [ ] T002 [P] `TEACHER_PERFORMANCE` permission module in `backend/src/main/java/com/hls/identity/permissions/PermissionModule.java`, `PermissionEligibility.java` (business module; Admin, Director, Manager eligible; Teacher and System never; actions View, Create, Edit only), `PermissionMatrixService.java` seeded grants (Admin, Director, Manager: View, Create, Edit).
- [ ] T003 [P] Access-model scope in `backend/src/main/java/com/hls/identity/accessmodel/AccessModelService.java` (ORG_WIDE for Admin and Director, ASSIGNED for Manager) and the OPERATIONS item "Teacher Performance" in `NavigationCatalog.java` for Admin, Director and Manager.
- [ ] T004 [P] Package skeleton with `package-info.java` for `perf/api`, `perf/internal`, `perf/web` and the Modulith allowed-dependency declaration (reads `teacher.api`, `organization.api`, `school.api`, `tasks.api`, `files.api`, `designation.api`, `identity`, `audit`).

## Phase 2: Foundation (blocks all stories)

- [ ] T005 Task-module amendment C2 in `backend/src/main/java/com/hls/tasks/api/TaskCreation.java` and `backend/src/main/java/com/hls/tasks/internal/TaskService.java`: add `reschedule`, `complete`, `cancel`, `reassign`, each by (`source`, `sourceKey`) on the open task only, returning false when none matches; history and Change History written; `reassign` refuses a non-Manager with `InvalidInputException` and publishes `TaskAssigned`.
- [ ] T006 [P] Tests for T005 in `backend/src/test/java/com/hls/tasks/TaskInterfacesTest.java`: each operation on an open task, no-op on a closed or unknown key, reassign to a non-Manager refused, events published.
- [ ] T007 [P] Enums and entities in `perf/internal`: Observation, ObservationRating, ObservationAttachment, PrincipalFeedback, PerformanceIssue, IssueLink, CorrectiveAction, Referral, ObservationPlan, PerformanceHistory and their repositories. Quote constraints: duration "5 to 240", rating "1 to 5 or Not observed", free text "up to 2000", feedback comments "10 to 2000", referral reason "at least 20", follow-up "1 to 60 days ahead", at most 5 attachments.
- [ ] T008 `perf/internal/ScopeGuard` over `TeacherScopeQueries.teacherScope`: Manager sees records whose Teacher is placed today at their Schools or that they authored; Admin and Director org-wide; out of scope answers not found; predicate reused by lists, Teacher page and summary.
- [ ] T009 `perf/internal/PerformanceRecorder`: append a PerformanceHistory row and call `ChangeRecorder` with actor, time, before and after for every event named in data-model.md.
- [ ] T010 `perf/internal/TaskBridge`: wraps `TaskCreation` (source `OBSERVATION_PLAN` key `{teacherId}`; source `PERFORMANCE_FOLLOW_UP` key `{issueId}:{round}`), reads the Teacher's current Manager through `TeacherDirectory` and `organization.api`, refuses a Teacher with no Manager with the reason.
- [ ] T011 [P] `perf/web/PerformanceExceptionAdvice` and the shared controller guard (grant check first so Teacher and System get 403, then scope, then 404).

## Phase 3: US1 A Manager records a classroom observation (P1)

Goal: draft, submit, not held, correct, attach. Test: quickstart section 2.

- [ ] T012 [US1] `perf/internal/ObservationService`: start Draft (School, observer, date, Principal name from `SchoolContacts`), save Draft by observer only, average of rated areas (one decimal, Not observed ignored) and suggested result (4 or more Good, below 2.5 Unsuitable, otherwise Needs improvement) as guide only, preview.
- [ ] T013 [US1] ObservationService submit: at least one rating, an overall result, and strengths or improvements; status SUBMITTED; permission optional and never blocking; the stored result is the observer's choice.
- [ ] T014 [US1] ObservationService not held: reason PRINCIPAL_DECLINED, PRINCIPAL_ABSENT, TEACHER_ABSENT, SCHOOL_CLOSED or OTHER with text; no ratings; never completes the task; "Principal declined" never blocks later planning or recording.
- [ ] T015 [US1] ObservationService correction: new version row with the same `root_id`, `reason` required, earlier row SUPERSEDED and readable by Admin and Director only (`?versions=true`), ratings and attachments copied.
- [ ] T016 [US1] Attachments through `files.api.FileStore` (photo, PDF, document; at most 5; remove from a Draft with reason; download as attachment after the visibility check).
- [ ] T017 [US1] `perf/web/ObservationController` for the endpoints in contracts/performance-api.md (observations section) with `actions` on each record.
- [ ] T018 [P] [US1] Tests `perf-test/ObservationApiTest.java`: draft and submit, average and suggestion table, permission absent accepted, not held, correction chain, attachment limit, Manager B gets 404, Teacher gets 403, stale version 409.

## Phase 4: US2 Plans and Manager tasks (P1)

Goal: plan, change, complete on submit, reassign, notify. Test: quickstart sections 1 and 7.

- [ ] T019 [US2] `perf/internal/PlanService`: plan for an ACTIVE placed Teacher by Admin, Director or the Teacher's Manager; reasons School feedback, Management plan, Follow-up, Other with text; one OPEN plan per Teacher, a second plan updates date and reason and calls `reschedule`; refuse In training, On leave, Exited, not placed, no Manager.
- [ ] T020 [US2] Complete on submit: ObservationService submit by the task's assignee calls `TaskBridge.complete` and marks the plan DONE; a submit by another observer leaves the task for the assignee or Director to complete with a note; Admin or Director observation creates no task.
- [ ] T021 [US2] Events `perf/api/ObservationPlanned` published on create and re-plan, and `notification/internal/PerformanceEventListener` with type `OBSERVATION_PLANNED` and text in `MessageFactory` (DD/MM/YYYY).
- [ ] T022 [US2] `perf/internal/ManagerReconcileJob` (daily, and callable when Admin or Director open the Planned list): reassign open plans' tasks to the Teacher's current Manager; cancel plans of exited Teachers.
- [ ] T023 [US2] `perf/web/PlanController` (GET list, POST, PUT, DELETE with reason).
- [ ] T024 [P] [US2] Tests `perf-test/PlanApiTest.java` and `ManagerReconcileJobTest.java`: one task however often planned, refusals with reasons, Manager plans own Teacher only, reassignment after a School Manager change, notification content.

## Phase 5: US3 Principal feedback (P1)

Goal: record, link, withdraw, offer issue or plan. Test: quickstart section 4.

- [ ] T025 [US3] `perf/internal/FeedbackService`: record (principal name, given date, category, comments "10 to 2000", action required, optional observation link), completes the open Principal Feedback task for the assignee, returns `offerIssue` when action is required.
- [ ] T026 [US3] FeedbackService correct (new version, reason) and withdraw (recorder, Admin or Director, reason; stays in history; no longer counts).
- [ ] T027 [US3] Feedback to issue (open or join) and feedback to plan (reason School feedback, due same day, feedback linked) in FeedbackService using IssueService and PlanService.
- [ ] T028 [US3] `perf/web/FeedbackController` per contracts/performance-api.md.
- [ ] T029 [P] [US3] Tests `perf-test/FeedbackApiTest.java`: validation, task completion, withdraw rules, linking, scope.

## Phase 6: US4 Performance issue, corrective action, follow-up (P1)

Goal: the correction loop with no round limit. Test: quickstart section 3.

- [ ] T030 [US4] `perf/internal/IssueService` open-or-join: submit with result Needs improvement or Unsuitable opens an issue unless the Teacher has an open one (then adds a link); feedback opens or joins only when the recorder agrees; one open issue per Teacher.
- [ ] T031 [US4] IssueService corrective action: discussed, required change, follow-up date "1 to 60 days ahead"; status UNDER_CORRECTION; `correction_rounds` incremented and shown; creates the follow-up Observation task via `TaskBridge` (key `{issueId}:{round}`) and a linked plan.
- [ ] T032 [US4] Follow-up result handling in ObservationService submit: Good resolves as IMPROVED; Needs improvement asks for the next corrective action (no limit); `escalate` with note tells the Directors via history.
- [ ] T033 [US4] IssueService close: Admin or Director cancel (reason) or close Referred as CONTINUE (reason); ManagerReconcileJob also closes issues of exited Teachers as EXITED and cancels their plans.
- [ ] T034 [US4] `perf/web/IssueController` per contracts/performance-api.md (issues section) with `actions`.
- [ ] T035 [P] [US4] Tests `perf-test/IssueLoopTest.java`: auto-open, join, round counting past three, improved resolution, cancel, exit closing, SC-003 and SC-004 (exactly one follow-up task per corrective action).

## Phase 7: US5 Referral (P2)

Goal: recommend transfer or replacement and hand off to 027. Test: quickstart section 5.

- [ ] T036 [US5] IssueService referral: recommendation TRANSFER or REPLACE, reason "at least 20" characters, supporting records from issue links; status REFERRED; publishes `perf/api/ReferralRecorded`; Unsuitable submit asks for the referral.
- [ ] T037 [US5] `notification/internal/PerformanceEventListener` type `REFERRAL_RECORDED` to every active Director (via `RecipientResolver`).
- [ ] T038 [US5] `perf/api/PerformanceReferrals` and implementation: `referredFor`, `referral`, `reportDecision` (closes only a Referred issue as TRANSFERRED or REPLACED); returns records, never entities; no change to placement, status or pay.
- [ ] T039 [P] [US5] Tests `perf-test/ReferralTest.java`: validation, Director notification, interface contents, decision closing, ArchUnit rule that only notification, 027 and 028 read `performance.api`.

## Phase 8: US6 Lists, Teacher page and summary (P2)

Goal: management review. Test: quickstart section 6.

- [ ] T040 [US6] `perf/internal/PerformanceQueries`: lists of observations, feedback and issues with filters Zone, School, Manager, Teacher, result or category, status, date range, `principalDeclined` flag, counts; Planned, Overdue, Open issues and Referred lists; same ScopeGuard predicate; indexes for 2,000 observations and 500 issues under 2 seconds.
- [ ] T041 [US6] Teacher performance page query (observations, feedback, issues, referrals in date order, latest result first, open plan) and `perf/web/TeacherPerformanceController`.
- [ ] T042 [US6] `perf/api/PerformanceSummary` and implementation (per Manager and per Zone: done, planned, overdue, open issues, referrals, resolved) plus `GET /summary`.
- [ ] T043 [P] [US6] Tests `perf-test/PerformanceQueriesTest.java`: filters, counts, Manager scope, Director sees both Managers, summary equals list counts, load test with seeded volume.
- [ ] T044 [P] Per-role authorization test for every endpoint in `perf-test/PerformanceAuthorizationTest.java`: Admin, Director, Manager in and out of scope, Teacher and System refused (SC-005).

## Phase 9: Frontend

- [ ] T045 [P] `fe/performanceApi.ts` typed client for all endpoints and the `actions` flags.
- [ ] T046 [P] `fe/ObservationsPage.tsx` and `fe/ObservationForm.tsx`: ratings per area with Not observed, live average and suggested result via preview, optional permission block ("Permission not recorded"), Not held dialog, correction dialog, attachments, DD/MM/YYYY.
- [ ] T047 [P] `fe/FeedbackPage.tsx` with record, withdraw and the offer to open an issue or plan an observation.
- [ ] T048 [P] `fe/PlannedPage.tsx` (planned date, reason, days overdue, last result) and a plan dialog.
- [ ] T049 [P] `fe/IssuePage.tsx` (round number, corrective action form, referral form, close dialogs) and the Open issues and Referred lists.
- [ ] T050 `fe/TeacherPerformancePage.tsx` and routes with route guards under OPERATIONS; hidden actions follow the server grants.
- [ ] T051 [P] Vitest `fe/__tests__/Performance.test.tsx`: menu item and buttons per role (Admin, Director, Manager), Teacher and System see none.

## Phase 10: Polish and wrap-up

- [ ] T052 `perf/internal/PerformanceDevSeeder` demo data (two Managers, drafts, submitted, Needs improvement loop, referral, planned) with a bulk option for the load test.
- [ ] T053 [P] Postman folder under `postman/HLS API` for all endpoints.
- [ ] T054 [P] Roadmap row for 026 in `docs/spec-roadmap.md` and constitution PATCH 2.5.7 (Teacher Performance row in the default access matrix and navigation) in `.specify/memory/constitution.md`.
- [ ] T055 Fix the wording gap in spec.md: FR-002 lists eight rating areas, Assumptions say "nine items".
- [ ] T056 Run the full backend and frontend suites, walk through quickstart.md, record results in `specs/026-teacher-observation-performance/quickstart-results.md`.

## Dependencies and order

- Phase 1 then Phase 2; T005 and T008 to T010 block every story.
- US1 (Phase 3) first. US2 needs T012 and T013 (submit completes the task). US3 and US4 need US1; US4 needs T019 for follow-up plans.
- US5 needs US4. US6 needs the records of US1 to US5. Frontend tasks follow their endpoints.

## Parallel examples

- After Phase 2: T018 with T024 and T029 once their services exist; T045 to T049 in parallel with backend tests.
- Within setup: T002, T003 and T004 together.

## Implementation strategy

MVP is Phases 1 to 4 (US1 and US2): a Manager is tasked, observes and submits, and the task closes. Add US3 and US4 for the performance loop, then US5 and US6, then the frontend and wrap-up. Each phase ends with its tests green before the next.
