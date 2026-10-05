---

description: "Task list for feature implementation"
---

# Tasks: Campus Recruitment, Job Offers and Induction

**Input**: Design documents from `/specs/016-campus-recruitment/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/recruitment-api.md, quickstart.md, and
**specs 001-005, 008-010 and 012 merged** (this feature adds permission modules and a navigation section to 002's
matrix and catalog, creates Teachers of 005, writes training-day marks into 008, and listens to an event that it adds
to 012).

**Tests**: included as first-class tasks. Constitution Principle IX requires rule tests before the services that use
them, per-role and per-scope tests on every endpoint, and UI tests per role. The spec's invariants (one Teacher per
acceptance, offers never change, one open offer, no double induction booking, one source of truth for induction
attendance, salary written once from the offer) are only trustworthy with tests.

**Organization**: grouped by user story in priority order (spec.md US1-US5). The foundation (the migration, the three
public interfaces and the 008 and 012 changes) comes first because every story uses it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{recruitment,training,teacher,attendance,schoolbilling,identity}/...`, tests
under `backend/src/test/java/com/hls/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/recruitment/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`).

---

## Phase 1: Setup

- [X] T001 Create the module skeletons `backend/src/main/java/com/hls/recruitment/{api,internal,web}/` and `backend/src/main/java/com/hls/training/{api,internal,web}/` with `package-info.java` (each `api` package a `@NamedInterface`) and `backend/src/test/java/com/hls/recruitment/RecruitmentModuleRulesTest.java` (ArchUnit: nothing outside a module uses its `internal` or `web`; `schoolbilling` and `teacher` do not depend on `recruitment` or `training`; `recruitment` and `training` reach other modules only through their `api` packages; **`recruitment` does not depend on `training` at all**); add both packages to the base packages of `backend/src/main/java/com/hls/MasterDataExceptionAdvice.java`
- [ ] T002 Create `frontend/src/features/recruitment/` with `recruitmentApi.ts` (types and calls for every endpoint in contracts/recruitment-api.md; money as strings)

---

## Phase 2: Foundational (blocking prerequisites)

- [X] T003 Write `backend/src/main/resources/db/migration/V22__create_recruitment_tables.sql` per data-model.md: `college` (unique `(lower(name), lower(city))`), `campus_drive`, `campus_drive_date`, `campus_drive_interviewer`, `candidate` (unique `(drive_id, phone_key)`, outcome in SELECTED/WAITLISTED/REJECTED), `candidate_outcome_history`, `assessment_score` (criterion in SPEAKING/ENGLISH/COMMUNICATION, score between 1 and 5), `job_offer` (status in DRAFT/ISSUED/ACCEPTED/DECLINED/EXPIRED/SUPERSEDED, monthly_salary > 0, response_deadline >= offer_date, no stipend column; a `phone_key` column copied from the candidate; partial unique on (phone_key) where status in (DRAFT, ISSUED); partial unique on (phone_key) where status = ACCEPTED; a trigger rejecting UPDATE of role, monthly_salary, allowances, terms, expected_joining, offer_date and response_deadline when OLD.status <> DRAFT; index on (status, response_deadline)), `induction_batch` (ends_on >= starts_on, seat_limit >= 1), `induction_enrolment` (unique (batch_id, teacher_id); gist exclusion on teacher_id and the date range for enrolments whose result is null or COMPLETED), `induction_absence` (unique (enrolment_id, absent_on)); and `ALTER TABLE attendance_mark ALTER COLUMN school_id DROP NOT NULL` with a check allowing a null School only for the training status code
- [X] T004 [P] Add permission modules `RECRUITMENT(VIEW, CREATE, EDIT)`, `OFFERS(VIEW, CREATE, EDIT)` and `INDUCTION(VIEW, CREATE, EDIT)` to `identity/permissions/PermissionModule.java`; add them to `PermissionEligibility` (Teacher and System excluded); seed in `PermissionMatrixService` per the spec: Director all three in full; Admin `RECRUITMENT` and `INDUCTION` in full and `OFFERS` VIEW only; Manager `RECRUITMENT` VIEW, CREATE, EDIT (scoped in the service to own drives) and `OFFERS` VIEW, no `INDUCTION`
- [X] T005 [P] Add a RECRUITMENT navigation section to `identity/accessmodel/NavigationCatalog.java` with items Campus Drives (`/recruitment/drives`, RECRUITMENT VIEW), Candidates (`/recruitment/candidates`), Offers (`/recruitment/offers`, OFFERS VIEW), Induction (`/recruitment/induction`, INDUCTION VIEW) and Dashboard (`/recruitment/dashboard`, RECRUITMENT VIEW), placed after OPERATIONS; add the data-scope entries in `AccessModelService.java`
- [X] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model tests under `backend/src/test/java/com/hls/identity/` for the three modules and the RECRUITMENT section per role (Zone Manager sees no Induction; Teacher and System see nothing)
- [X] T007 [P] `backend/src/test/java/com/hls/teacher/TeacherRegistryTest.java` (rules first): `createTrainee` makes a Teacher IN_TRAINING with no salary entry; `findMatches` normalizes phone and lower-cases e-mail and reports status; `activate` moves IN_TRAINING to ACTIVE only; `exit` follows the status machine; `recordFirstSalary` writes one entry with the given effective date and refuses a second for the same Teacher; `activeTeacherIds`
- [X] T008 Add `backend/src/main/java/com/hls/teacher/api/TeacherRegistry.java` per contracts/recruitment-api.md and `backend/src/main/java/com/hls/teacher/internal/TeacherRegistryImpl.java` over `TeacherService` and `SalaryService` (the actor's roles are those of an Admin acting for the system; audit through `ChangeRecorder` as the existing services do)
- [X] T009 [P] `backend/src/test/java/com/hls/attendance/TrainingAttendanceTest.java` and cases added to `RollupCalculatorTest.java` (rules first): a training-day mark with no School is accepted; an ordinary mark still needs a School; a locked month refuses it with the lock reason; a TRAINING mark on a day the Teacher is not placed counts into `trainingAvailable` and `trainingAttended` but not into `workingDays` or `weightedTotal`; a half day counts 0.5; five induction days read back as five training days (spec SC-007)
- [X] T065 Audit every reader of `attendance_mark.school_id` and make each cope with a null School (the mark view factory, the grid, the Teacher month view, the mark history and the CSV export under `backend/src/main/java/com/hls/attendance/internal/`); add `backend/src/test/java/com/hls/attendance/UnplacedTrainingMarkReadersTest.java` rendering an unplaced Teacher's month, mark history and CSV export with a training day; do this together with T010
- [X] T010 Implement the 008 amendment A5: `attendance/api/TrainingAttendance.java` and `attendance/internal/TrainingAttendanceImpl.java` (writes and clears school-less training-day marks through `MarkService` with mark history, respecting the month lock); adjust `MarkService` so only the training path may pass a null School; change `RollupCalculator.compute` to count TRAINING marks on NOT_PLACED days as described; keep every existing attendance test passing unchanged
- [X] T011 [P] `backend/src/test/java/com/hls/schoolbilling/TeacherFirstAssignedTest.java`: the event is published once when a Teacher's first assignment row of any status is created (in the same transaction, so a refused assignment publishes nothing), and not on a later move, a re-map or a scheduled change
- [X] T012 Spec 012 amendment A4: add `schoolbilling/api/TeacherFirstAssigned.java` (`record(UUID teacherId, UUID schoolId, LocalDate startsOn)`) and publish it from `schoolbilling/internal/AssignmentService.java` when the Teacher has no earlier assignment row; the existing 012 tests must still pass
- [X] T068 [P] School contacts (amendment A8 to spec 005): `school/internal/{SchoolContact,SchoolContactRepository,SchoolContactService}.java`, `GET/PUT /api/v1/schools/{id}/contacts` in `school/web/SchoolContactController.java` (roles `SCHOOLS` VIEW/EDIT, Manager only in Zone, audited through `ChangeRecorder`, role in PRINCIPAL/ACCOUNTANT, phone max 20, email max 200); a `school/api/SchoolContacts` read interface; tests `school/SchoolContactApiTest.java` per role and scope
- [ ] T069 [P] [US1] College contacts: `college_contact` entity inside `recruitment/internal`, the `placementOfficer` and `principal` fields in the college create/update requests and views, shown on the College screen; tests in `CollegeApiTest.java`
- [ ] T070 Frontend: principal and accountant section in `frontend/src/features/schools/SchoolDialog.tsx` with `schoolsApi.ts` calls and a Vitest case; placement officer and principal fields in the College form under `frontend/src/features/recruitment/`
- [ ] T013 Entities, enums and repositories in `recruitment/internal` (`College`, `CampusDrive`, `Candidate`, `CandidateOutcomeHistory`, `AssessmentScore`, `JobOffer`, `OfferStatus`, `Outcome`) and `training/internal` (`InductionBatch`, `InductionEnrolment`, `InductionAbsence`); insert-only entities have no setters; Hibernate only validates the schema

**Checkpoint**: migration applies; permissions and navigation seeded; the three public interfaces and the two amendments pass their tests; existing attendance and 012 suites unchanged.

---

## Phase 3: User Story 1 - Schedule Campus Drives and Record Candidates (P1) 🎯 MVP

**Goal**: drives on a calendar, candidates by entry or CSV, outcomes with history, assessment scores.

**Independent Test**: schedule a drive for two dates with two interviewers, import 10 candidates (one invalid), mark 4 selected, 2 waitlisted, 4 rejected, add a score per criterion, and see the calendar, the counts and the history.

### Tests for User Story 1

- [ ] T014 [P] [US1] `backend/src/test/java/com/hls/recruitment/DriveServiceTest.java` (rules first): a drive needs a college and at least one date; a duplicate college (same name and city) is refused; cancel needs a reason; "held, no candidates" is derived; a Zone Manager may change only a drive they scheduled or are an interviewer on (403 "not your drive"), Admin and Director any
- [ ] T015 [P] [US1] `backend/src/test/java/com/hls/recruitment/CandidateImportTest.java`: a CSV of 10 rows with one invalid saves 9 and reports the row and reason; the same file again saves none (same phone for the same drive); phone numbers are normalized; a 500-row file imports in chunks without timing out; a malformed header is a 400
- [ ] T016 [P] [US1] `backend/src/test/java/com/hls/recruitment/OutcomeAssessmentTest.java`: an outcome change keeps the earlier one in the history with who and when; waitlisted can become selected; a re-assessment adds rows and the latest is current; scores outside 1 to 5 or an unknown criterion are 400
- [ ] T017 [P] [US1] `backend/src/test/java/com/hls/recruitment/DriveApiTest.java`: the per-role and per-scope matrix of contracts/recruitment-api.md for colleges, drives, candidates, outcomes and assessment (Zone Manager reads all and writes only own; Teacher and System 403; 401 without a token); phone and e-mail are absent from the response for a role without `RECRUITMENT` VIEW
- [ ] T018 [P] [US1] `backend/src/test/java/com/hls/recruitment/RecruitmentAuditTest.java`: each college, drive, candidate, outcome, assessment and import has an audit entry with actor and prior and new values; candidate phone numbers do not appear in any log line (capture the log appender)
- [ ] T019 [P] [US1] `frontend/src/features/recruitment/DrivesPage.test.tsx` and `DriveDetailPage.test.tsx`: calendar and list, schedule dialog, import result with invalid rows, outcome and score forms, write controls hidden for a Zone Manager on another's drive, axe in both themes

### Implementation for User Story 1

- [ ] T020 [US1] `recruitment/internal/{CollegeService,DriveService}.java`: colleges, drives, dates, interviewers, status changes, the own-drive rule on every write, `ChangeRecorder` audit
- [ ] T021 [US1] `recruitment/internal/{CandidateService,CsvImporter}.java`: single entry, chunked CSV import (a transaction per 100 rows), phone normalization, duplicate rule, outcome and history, contact details withheld by role
- [ ] T022 [US1] `recruitment/internal/AssessmentService.java`: scores per criterion with remarks, assessor and time, latest set current
- [ ] T023 [US1] `recruitment/web/{DriveController,CandidateController}.java` per the contract with `PermissionGuard` on `RECRUITMENT`; `recruitment/api/DriveActivities.java` and its implementation (contract C1)
- [ ] T024 [P] [US1] `frontend/src/features/recruitment/{DrivesPage,DriveDetailPage}.tsx` and routes `/recruitment/drives`, `/recruitment/drives/:id`, `/recruitment/candidates` in `frontend/src/App.tsx`

**Checkpoint**: drives and candidates work end to end for each role.

---

## Phase 4: User Story 2 - Send, Track and Accept Job Offers (P1)

**Goal**: offers that never change after issue, one open offer per candidate, expiry, a printable letter.

**Independent Test**: issue an offer, supersede it, let another expire, decline a third, and see each status with the package kept.

### Tests for User Story 2

- [ ] T025 [P] [US2] `backend/src/test/java/com/hls/recruitment/OfferServiceTest.java` (rules first): only a SELECTED candidate can be offered; a second open offer is refused (service and unique index), including for the same person selected at two drives (same phone); an issued offer cannot be edited (service and trigger, including a direct SQL update); supersede creates a new offer and marks the old SUPERSEDED in one transaction; decline needs a reason; no stipend field exists
- [ ] T026 [P] [US2] `backend/src/test/java/com/hls/recruitment/OfferExpiryTest.java`: an issued offer past its deadline becomes EXPIRED by the job and the candidate can be offered again; acceptance after the deadline is refused even before the job runs; the job is off in `IntegrationTestBase`
- [ ] T027 [P] [US2] `backend/src/test/java/com/hls/recruitment/OfferApiTest.java`: the offer rows of the role matrix (Director issues, supersedes, accepts, declines; Admin reads but is 403 on those; Zone Manager reads all; Teacher and System 403)
- [ ] T028 [P] [US2] `backend/src/test/java/com/hls/recruitment/OfferLetterTest.java`: the letter shows name, designation, package, terms, training information and status; user text is HTML-escaped; a draft's letter is regenerated after a change
- [ ] T029 [P] [US2] `frontend/src/features/recruitment/OffersPage.test.tsx`: offers by status, send and supersede dialogs, letter view, actions hidden for Admin and Zone Manager, axe in both themes

### Implementation for User Story 2

- [ ] T030 [US2] `recruitment/internal/{OfferService,OfferLetterRenderer}.java`: draft, issue (who and when), supersede, decline, letter rendering with a print stylesheet; audit entity `JOB_OFFER`
- [ ] T031 [US2] `recruitment/internal/OfferExpiryJob.java` (daily, `hls.recruitment.offer-expiry.enabled`, off in `IntegrationTestBase`)
- [ ] T032 [US2] `recruitment/web/OfferController.java` per the contract with `PermissionGuard` on `OFFERS`, including `GET /offers/{id}/letter`
- [ ] T033 [P] [US2] `frontend/src/features/recruitment/{OffersPage,OfferLetterView}.tsx` and route `/recruitment/offers`

**Checkpoint**: offers work end to end; nothing is accepted yet.

---

## Phase 5: User Story 3 - An Accepted Offer Becomes a Teacher in Training (P1)

**Goal**: exactly one Teacher per acceptance, no re-entry, duplicates never merged, first salary written once from the offer.

**Independent Test**: accept an offer and see one Teacher in training with no salary entry; accept again and see no second one; map the trained Teacher to a School and see one salary entry equal to the offer's.

### Tests for User Story 3

- [ ] T034 [P] [US3] `backend/src/test/java/com/hls/recruitment/AcceptanceTest.java` (rules first): acceptance creates one Teacher IN_TRAINING with the candidate's details and no salary entry, stores the Teacher id on the offer and candidate and shows the candidate as joined; a second acceptance and two simultaneous acceptances leave exactly one Teacher; an expired or non-issued offer is refused; no induction batch with room leaves the Teacher in "to be enrolled"
- [ ] T035 [P] [US3] `backend/src/test/java/com/hls/recruitment/DuplicateTeacherTest.java`: a phone or e-mail match with a Teacher who is working, on leave, in training or assigned refuses the acceptance and names the Teacher, creating, linking and mapping nothing; a match with an exited Teacher needs `confirmNewRecord` and leaves the exited record unchanged
- [ ] T036 [P] [US3] `backend/src/test/java/com/hls/recruitment/FirstSalaryListenerTest.java`: mapping a Teacher created from an accepted offer to a School position writes one salary entry equal to the offer's monthly salary effective on the assignment start; a later move writes none; a Teacher not created from an offer is untouched; a trained but unplaced Teacher has no salary entry
- [ ] T037 [P] [US3] `frontend/src/features/recruitment/OfferAcceptDialog.test.tsx`: the accept flow, the "existing Teacher" refusal naming the Teacher, the confirm-new-record step for an exited match

### Implementation for User Story 3

- [ ] T038 [US3] `recruitment/internal/AcceptanceService.java`: one transaction with a `PESSIMISTIC_WRITE` lock on the offer, duplicate match through `TeacherRegistry.findMatches`, create through `TeacherRegistry.createTrainee`, store the Teacher id, audit entity `TEACHER_FROM_OFFER`, publish `OfferAccepted` (no call to `training`); idempotent
- [ ] T039 [US3] `recruitment/internal/FirstSalaryListener.java` (synchronous `@EventListener` on `TeacherFirstAssigned`): for a Teacher created from an accepted offer with no salary entry, call `TeacherRegistry.recordFirstSalary`; audit entity `TEACHER_FIRST_SALARY`
- [ ] T040 [P] [US3] `frontend/src/features/recruitment/OfferAcceptDialog.tsx` and the accept action on `OffersPage.tsx`

**Checkpoint**: the recruit-to-Teacher link and the salary rule hold.

---

## Phase 6: User Story 4 - Run the One-Month Induction (P1)

**Goal**: batches, enrolment, attendance in 008, sign-off, next batch or release, ready to deploy.

**Independent Test**: create a batch for a month, enrol 5 recruits, record attendance, sign off 4 as completed and 1 as not completed, and see 4 active and the fifth in the next batch.

### Tests for User Story 4

- [ ] T041 [P] [US4] `backend/src/test/java/com/hls/training/BatchRulesTest.java` (rules first): enrolment past the seat limit is refused (under a row lock, two simultaneous requests for the last seat let one through); a Teacher cannot be in two batches on overlapping dates (database exclusion); a cancelled batch leaves its recruits "to be enrolled"
- [ ] T042 [P] [US4] `backend/src/test/java/com/hls/training/InductionAttendanceTest.java`: a present or half day writes a training-day mark in 008 with no School; an absence writes none and keeps the reason; a correction keeps the history; a locked month refuses with the lock reason; the recruit's rollup shows the same training days (SC-007)
- [ ] T043 [P] [US4] `backend/src/test/java/com/hls/training/SignOffTest.java`: completed makes the Teacher ACTIVE; not completed allows next batch (a new enrolment, the earlier sign-off kept, earlier induction days kept) or release (the Teacher exits with the reason)
- [ ] T044 [P] [US4] `backend/src/test/java/com/hls/training/ReadyToDeployTest.java`: an ACTIVE Teacher with a completed enrolment and no School is listed, and drops off once mapped in spec 012; a Teacher still in training cannot be mapped (the 012 rule)
- [ ] T045 [P] [US4] `backend/src/test/java/com/hls/training/InductionApiTest.java`: the induction rows of the role matrix (Admin and Director act; Zone Manager, Teacher and System 403)
- [ ] T046 [P] [US4] `frontend/src/features/recruitment/InductionPage.test.tsx`: batches, roster, attendance entry, sign-off, ready-to-deploy list, no Induction menu for a Zone Manager, axe in both themes

- [ ] T066 [P] [US4] `backend/src/test/java/com/hls/training/OfferAcceptedEnrolmentTest.java`: acceptance enrols the Teacher in the next batch with room; with no batch with room the Teacher stays in "to be enrolled"; two simultaneous acceptances for the last seat enrol one and leave the other waiting; `training` receives the event and `recruitment` has no dependency on `training` (ArchUnit)

### Implementation for User Story 4

- [ ] T067 [US4] `training/internal/OfferAcceptedListener.java` (synchronous `@EventListener` on `recruitment.api.OfferAccepted`): enrol the Teacher in the next batch with room under the batch row lock, or leave them to be enrolled
- [ ] T047 [US4] `training/internal/{BatchService,EnrolmentService}.java`: batches, enrolment with the seat-limit lock and the dates copied for the exclusion constraint, "to be enrolled" list
- [ ] T048 [US4] `training/internal/InductionAttendanceService.java`: present and half days through `TrainingAttendance`, absences into `induction_absence`, corrections, the lock error passed through
- [ ] T049 [US4] `training/internal/SignOffService.java`: sign-off, `TeacherRegistry.activate`, next batch, release through `TeacherRegistry.exit`; `training/api/InductionBatchView.java` for spec 015
- [ ] T050 [US4] `training/web/BatchController.java` per the contract with `PermissionGuard` on `INDUCTION`; `GET /ready-to-deploy` from `TeacherRegistry.activeTeacherIds` and `TeacherPlacementSource`
- [ ] T051 [P] [US4] `frontend/src/features/recruitment/InductionPage.tsx` and route `/recruitment/induction`

**Checkpoint**: recruits become active and ready to deploy.

---

## Phase 7: User Story 5 - See the Recruitment Pipeline (P2)

**Goal**: the funnel through placement, the joining ratio, per college and season.

**Independent Test**: with data across two colleges, each count matches its list and the joining ratio equals joined divided by selected.

### Tests for User Story 5

- [ ] T052 [P] [US5] `backend/src/test/java/com/hls/recruitment/DashboardTest.java`: counts per college and season (label or date range) equal the underlying lists; the joining ratio is joined over selected and null with no one selected; placed and active read spec 012 and the Teacher status; the "my drives" filter; an empty state returns zeros
- [ ] T053 [P] [US5] `frontend/src/features/recruitment/RecruitmentDashboard.test.tsx`: role fixtures, filters, empty state, axe in both themes

### Implementation for User Story 5

- [ ] T054 [US5] `recruitment/internal/RecruitmentDashboardService.java` (grouped SQL, no per-row queries) and `recruitment/web/DashboardController.java`
- [ ] T055 [P] [US5] `frontend/src/features/recruitment/RecruitmentDashboard.tsx` and route `/recruitment/dashboard`

**Checkpoint**: the full spec is functional.

---

## Phase 8: Polish and Cross-Cutting Concerns

- [ ] T056 [P] `recruitment/internal/RecruitmentDevSeeder.java` (only with the demo flag, idempotent): one college, a held drive with six candidates, offers in several statuses, a batch and "Ready Rani" who completed it
- [ ] T057 [P] Role-by-role UI test of the RECRUITMENT menu and every action visibility (Admin, Director, Zone Manager, Teacher, System) in `frontend/src/features/recruitment/` and the navigation tests
- [ ] T058 [P] Add the new pages and dialogs to the axe harness in `frontend/src/a11y/a11y.test.tsx` (light and dark) and record the keyboard-only and screen-reader pass and the phone-width check in `specs/016-campus-recruitment/quickstart-results.md`
- [ ] T059 [P] Add a "016 Campus Recruitment" folder to the Postman collection in `postman/HLS API/` and refresh the schema dump `docs/db/schema-v22.sql`
- [ ] T060 [P] Constitution amendment 2.4.0 in `.specify/memory/constitution.md`: add the RECRUITMENT navigation section to Principle IV (MARKETING follows with spec 023) and the Recruitment, Offers and Induction rows to the Default role access matrix, with a Sync Impact Report note
- [ ] T061 [P] Update `docs/spec-roadmap.md` (016 status; A4 and A5 done) and `docs/running-locally.md` (the recruitment flow and demo data)
- [ ] T062 Review before the PR: run the `java-reviewer` agent on `recruitment`, `training`, and the `teacher`, `attendance` and `schoolbilling` changes, and the `database-reviewer` agent on `V22` (the trigger, the partial unique indexes, the gist exclusion, the attendance column change); address findings
- [ ] T063 Walk through every scenario in `quickstart.md` on the local app (database on Docker port 5433) and write the outcomes to `specs/016-campus-recruitment/quickstart-results.md`
- [ ] T064 Run the full backend and frontend suites once, then open the PR

---

## Dependencies and Execution Order

- Phase 1 then Phase 2. T003 blocks every table-using task. T007 before T008, T009 before T010, T011 before T012 (rules first); T013 after T003.
- **US1 before US2**: offers need selected candidates. **US2 before US3**: acceptance needs an issued offer. **US3 before US4**: induction enrols the Teacher that acceptance creates. US5 reads all of them.
- T065 ships with T010 (the readers and the training mark path together); T066 before T067; T067 needs T047 and the event from T038.
- T038 needs T008 (`TeacherRegistry`); T039 needs T012 (the 012 event) and T008; T048 needs T010 (`TrainingAttendance`); T049 needs T008.
- Frontend tasks marked [P] in a phase can start once that phase's API contract is fixed; they need only the typed client from T002.
- Polish after all stories; T062 to T064 last.

### Parallel opportunities

- Phase 2: T004, T005, T006, T007, T009, T011 together; then T008, T010, T012.
- US1: T014 to T019 together; T024 alongside T023.
- US2: T025 to T029 together; T033 alongside T032.
- US3: T034 to T037 together; T040 alongside T039.
- US4: T041 to T046 together; T051 alongside T050.
- Polish: T056 to T061 together.

## Implementation Strategy

- **MVP**: Phases 1 to 3 (US1): drives, candidates, outcomes and assessment, with the foundation proven. It already gives the Director a recruitment calendar and results.
- **Complete slice**: add US2 and US3 (offers and the Teacher in training), then US4 (induction and ready to deploy), then US5 (the dashboard).
- **Spec 023 follow-up (after both are merged)**: spec 023 defines `recruitment.api.SupplySource` for its demand-versus-supply figure and ships a default that reports "not available". Once 023 is merged, add one small bean in `training/internal` implementing it from `ready-to-deploy`; it is not a task here because 016 may merge first and the interface would not exist yet.
- Land the foundation, especially the attendance (A5) and 012 (A4) changes, with their existing suites green before any story code: if anything is going to break, it breaks there.
