---

description: "Task list for feature implementation"
---

# Tasks: School Marketing and MoU Pipeline

**Input**: Design documents from `/specs/023-school-marketing-mou/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/marketing-api.md, quickstart.md, and **specs
001-005, 010 and 012 merged** (this feature adds a permission module and navigation section to 002's matrix and
catalog, reads contracts of 012, creates Schools of 005, and notifies through 010). Spec 016 is optional (the supply
figure); spec 025 is optional (follow-up tasks).

**Tests**: included as first-class tasks (Principle IX). The spec's invariants (Zone scope, proposals and files never
change, an outcome to complete a visit, the Final Stage review rule, MoU and Active derived from 012, one overdue
notice per prospect and limit) are only trustworthy with tests. Rule unit tests come before the services that use them.

**Organization**: grouped by user story in priority order (spec.md US1-US5). The foundation (the migration, the `files`
module and the small interfaces in other modules) comes first.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1-US5; absent for Setup/Foundational/Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/{recruitment/marketing,recruitment/api,files,school,organization,schoolbilling,notification,identity}/...`,
tests under `backend/src/test/java/com/hls/...`; migration `backend/src/main/resources/db/migration`; frontend
`frontend/src/features/{marketing,schoolbilling}/...`. Run one Maven test class at a time (`mvn -o test -Dtest=...`).

---

## Phase 1: Setup

- [X] T001 Create the sub-package skeleton `backend/src/main/java/com/hls/recruitment/marketing/{internal,web}/` and, if spec 016 has not created it yet, `backend/src/main/java/com/hls/recruitment/api/` with `package-info.java` (a `@NamedInterface`); create the `files` module skeleton `backend/src/main/java/com/hls/files/{api,internal}/`; add `backend/src/test/java/com/hls/recruitment/marketing/MarketingModuleRulesTest.java` (ArchUnit: nothing outside `recruitment` uses its `internal` or `web`; nothing outside `files` uses `files.internal`; `school`, `organization`, `schoolbilling` and `notification` depend on no marketing class); add `com.hls.recruitment` and `com.hls.files` to the base packages of `backend/src/main/java/com/hls/MasterDataExceptionAdvice.java` if missing
- [X] T002 Create `frontend/src/features/marketing/` with `marketingApi.ts` (types and calls for every endpoint in contracts/marketing-api.md; money as strings)

---

## Phase 2: Foundational (blocking prerequisites)

- [X] T003 Write `backend/src/main/resources/db/migration/V23__create_marketing_tables.sql` per data-model.md: `marketing_prospect` (stage in PROSPECT, CONTACTED, VISIT, FOLLOW_UP, INTERESTED, NEGOTIATION, FINAL_STAGE, ON_HOLD, LOST; unique `name_key`; expected_teachers between 1 and 500; lost_reason required when LOST), `prospect_stage_history` (with `kind` STAGE, REVIEW_APPROVED, REVIEW_REJECTED) and `prospect_owner_history` (insert-only), `marketing_activity` (type in VISIT, CALL, PROPOSAL_MEETING, FOLLOW_UP; status in PLANNED, COMPLETED, CANCELLED; check exactly one of prospect_id and school_id; outcome required when COMPLETED; cancel_reason required when CANCELLED), `activity_attendee`, `activity_date_history`, `activity_attachment`, `proposal_revision` and `proposal_position` (checks as spec 012; a trigger rejecting UPDATE and DELETE on both), `marketing_setting` with the row `mou_overdue_days = 14` (check 1 to 90), `overdue_notice` (PK (prospect_id, limit_days)), `stored_file` (index on (owner_type, owner_id); rows only updated to record a removal); and re-create the `notification.type` check adding `MOU_NOT_RECORDED` while keeping every existing value
- [X] T004 [P] Add permission modules `MARKETING(VIEW, CREATE, EDIT, APPROVE)` and `MARKETING_SETTINGS(VIEW, EDIT)` to `identity/permissions/PermissionModule.java`; add it to `PermissionEligibility` (Teacher and System excluded); seed in `PermissionMatrixService`: Admin VIEW, CREATE, EDIT; Director all four; Manager VIEW, CREATE, EDIT and APPROVE (scoped in the service to own Zones); `MARKETING_SETTINGS` VIEW and EDIT to Admin and Director only
- [X] T005 [P] Add a MARKETING navigation section to `identity/accessmodel/NavigationCatalog.java` with Prospects (`/marketing/prospects`), Calendar (`/marketing/calendar`), Pipeline (`/marketing/pipeline`), Dashboard (`/marketing/dashboard`) and Settings (`/marketing/settings`, by `MARKETING_SETTINGS` VIEW), each by its permission; add the data-scope entries (Assigned for Manager) in `AccessModelService.java`
- [X] T006 [P] Extend `PermissionEligibilityTest`, `NavigationSectionOrderTest` and the access-model tests under `backend/src/test/java/com/hls/identity/` for the module and the MARKETING section per role (Settings only for Admin and Director; Teacher and System nothing)
- [X] T007 [P] `backend/src/test/java/com/hls/files/FileStoreTest.java` (rules first): store and open a file (bytes and checksum round-trip); a type outside jpg, png, pdf, docx, xlsx, txt, a mismatched extension and content, a file over 10 MB are refused; the stored name is generated, never the client's; a removal records who, when and why, hides the file and keeps the bytes; a file is never updated
- [X] T008 Implement the `files` module: `files/api/FileStore.java` (`store`, `open`, `remove`; `FileRef`), `files/internal/{StoredFile,StoredFileRepository,FileStoreImpl,FileTypePolicy}.java` writing under `hls.files.directory` (`yyyy/MM/<uuid>`), SHA-256, magic-number check; add the property and a Docker volume note to `docs/running-locally.md` and the compose file if one exists
- [X] T009 [P] Add `school/api/SchoolRegistry.java` and `school/internal/SchoolRegistryImpl.java` (create a School for a Place from a profile, through `SchoolService.create`, audited as the existing service does) with `backend/src/test/java/com/hls/school/SchoolRegistryTest.java`; add `ManagerQueries.managersOfZone(UUID zoneId)` to `organization/api/ManagerQueries.java` with its implementation and a test
- [X] T010 [P] Spec 012 amendment A6: add `Occupancy occupancyOf(UUID schoolId, LocalDate on)` and the batch form `Map<UUID, Occupancy> occupancyOfAll(Collection<UUID> schoolIds, LocalDate on)` (positions, filled, vacant of the contract in effect, 0 when none) to `schoolbilling/api/SchoolContracts.java` and `SchoolContractsImpl.java`, with a test in `SchoolContractsPublicApiTest`
- [X] T011 [P] Add `MOU_NOT_RECORDED` to `notification/api/NotificationType.java`, the message in `MessageFactory`, and `notification/internal/MarketingEventListener.java` handling `recruitment.api.WonProspectOverdue` (recipients: the owner, the Zone Manager(s) of the Zone through `managersOfZone`, every active Admin and Director; one notification each, link `/marketing/prospects/{id}`), with `backend/src/test/java/com/hls/notification/MarketingNotificationTest.java`
- [X] T012 Define the public types in `recruitment/api/`: `MarketingActivities`, `PlannedActivity`, `ProspectOwners`, `SupplySource`, `FollowUpScheduled`, `WonProspectOverdue`, `ProspectWon`; add `recruitment/marketing/internal/DefaultSupplySource.java` (`@ConditionalOnMissingBean`, returns empty)
- [X] T013 Entities, enums and repositories in `recruitment/marketing/internal`: `Prospect`, `StageHistory`, `OwnerHistory`, `MarketingActivity`, `ActivityAttendee`, `ActivityDateHistory`, `ActivityAttachment`, `ProposalRevision`, `ProposalPosition`, `MarketingSetting`, `OverdueNotice`; insert-only entities have no setters; Hibernate only validates the schema

**Checkpoint**: migration applies; permission and navigation seeded; the files module and the small interfaces pass their tests; existing suites unchanged.

---

## Phase 3: User Story 1 - Track School Prospects and Visits (P1) 🎯 MVP

**Goal**: prospects, visits with statuses and outcomes, attachments, calendar, Zone scope.

**Independent Test**: add a prospect, plan a visit by two people, complete it with an outcome and a follow-up date, attach a photo, reschedule another, and see the calendar, "missed" and "follow-up overdue".

### Tests for User Story 1

- [X] T014 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/ProspectServiceTest.java` (rules first): a duplicate (same normalized name, board and Zone) is refused with the existing one; contact fields are validated; owner changes by Admin and Director are recorded in the history and refused for a Zone Manager
- [X] T015 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/ActivityServiceTest.java`: completing without an outcome is refused; a planned activity past its date is MISSED until rescheduled or cancelled; reschedule keeps the earlier date in the history; cancel needs a reason; a follow-up date on an outcome publishes `FollowUpScheduled`; "follow-up overdue" is derived and clears with a later activity; an account visit to a School with an MoU is allowed without a proposal
- [X] T016 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/AttachmentTest.java`: upload on a visit (type, size, ten-file limits), download as an attachment with `nosniff`, a caller who cannot see the visit gets 404, removal only by Admin or Director with a reason and the bytes kept
- [X] T017 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/ProspectApiTest.java`: the per-role and per-Zone matrix of contracts/marketing-api.md for prospects and activities (Zone Manager A against Zone Manager B in list, search, detail, calendar and download; Teacher and System 403; 401 without a token)
- [X] T018 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/MarketingAuditTest.java`: each prospect, activity, attachment add and removal and owner change has an audit entry with actor and prior and new values; prospect phone numbers do not appear in logs
- [X] T019 [P] [US1] `frontend/src/features/marketing/ProspectsPage.test.tsx`, `MarketingCalendar.test.tsx`, `ActivityDialog.test.tsx` and `AttachmentList.test.tsx`: list and filters, add and duplicate refusal, calendar with own entries and overdue highlighted, complete needs an outcome, missed and rescheduled badges, upload errors, axe in both themes

- [X] T058 [P] [US1] `backend/src/test/java/com/hls/recruitment/marketing/CalendarDrivesTest.java`: the calendar and the "mine" list also show the person's recruitment drives when `DriveActivities` (spec 016) is present, a clash is visible and nothing is blocked, and with 016 absent the calendar works unchanged

### Implementation for User Story 1

- [X] T020 [US1] `recruitment/marketing/internal/{ProspectService,ActivityService}.java`: prospects, owner history, activities with statuses, attendees, reschedule history, derived missed and overdue, the Zone scope through `ScopeQueries` on every query, `ChangeRecorder` audit, `FollowUpScheduled` publication
- [X] T021 [US1] `recruitment/marketing/internal/AttachmentService.java` over `FileStore` (limits, visibility check, removal rule)
- [X] T022 [US1] `recruitment/marketing/web/{ProspectController,ActivityController,FileController}.java` per the contract with `PermissionGuard` on `MARKETING`; `MarketingActivities` implementation (contract C1)
- [X] T059 [US1] In `ActivityService` and `MarketingActivities`, merge the person's drives from `DriveActivities` through an `ObjectProvider` (absent when 016 is not merged) into the calendar and the "mine" list
- [X] T023 [P] [US1] `frontend/src/features/marketing/{ProspectsPage,MarketingCalendar,ActivityDialog,AttachmentList}.tsx` and routes `/marketing/prospects`, `/marketing/calendar` in `frontend/src/App.tsx`

**Checkpoint**: prospects, visits and files work end to end for each role.

---

## Phase 4: User Story 2 - Move Prospects Through the Pipeline (P1)

**Goal**: the stages, hold, loss, reopen, and the Final Stage review by Director or Zone Manager.

**Independent Test**: move a prospect through the stages, hold and resume, lose and reopen, and approve and reject Final Stage reviews.

### Tests for User Story 2

- [X] T024 [P] [US2] `backend/src/test/java/com/hls/recruitment/marketing/PipelineServiceTest.java` (rules first): allowed moves among the seven active stages; Lost needs a reason and reopens to the last active stage; On Hold returns to the stage it left; Final Stage needs a proposal revision; every change records who and when; two simultaneous moves: the later is told the stage changed
- [X] T025 [P] [US2] `backend/src/test/java/com/hls/recruitment/marketing/ReviewTest.java`: a Director approves any, a Zone Manager only a prospect in their Zone (another Zone is 404), Admin is 403 by default; approval makes the prospect won; a rejection needs a reason and returns it to Negotiation; the audit entry names the approver
- [X] T026 [P] [US2] `backend/src/test/java/com/hls/recruitment/marketing/PipelineApiTest.java`: the pipeline board counts match the list; filters by Zone, owner and stage; per-role and per-Zone matrix for stage, review and owner endpoints
- [X] T027 [P] [US2] `frontend/src/features/marketing/PipelineBoard.test.tsx`: columns and counts, moving a card, hold and lost dialogs, review buttons only for roles that hold APPROVE, axe in both themes

### Implementation for User Story 2

- [X] T028 [US2] `recruitment/marketing/internal/PipelineService.java`: stage moves, hold, lost, reopen, the Final Stage review (permission and Zone rule), stage and review history
- [X] T029 [US2] `recruitment/marketing/web/PipelineController.java` (`/prospects/{id}/stage`, `/review`, `/owner`, `/pipeline`) with `PermissionGuard` on `MARKETING`
- [X] T030 [P] [US2] `frontend/src/features/marketing/PipelineBoard.tsx` and route `/marketing/pipeline`

**Checkpoint**: the pipeline and the review work.

---

## Phase 5: User Story 3 - Draft the Proposed MoU Terms (P1)

**Goal**: proposal revisions that never change, same salary for all or one per position.

**Independent Test**: record a proposal for 4 Teachers at one amount, revise to 5 with amounts per position, and see both revisions with the latest current.

### Tests for User Story 3

- [X] T031 [P] [US3] `backend/src/test/java/com/hls/recruitment/marketing/ProposalServiceTest.java` (rules first): same-for-all needs a positive rate and no positions; per-position needs exactly N positive amounts; Teacher count 1 to 500; amounts have at most two decimals; a revision is new, the old one unchanged (service and trigger, including a direct SQL update); the monthly total is computed
- [X] T032 [P] [US3] `backend/src/test/java/com/hls/recruitment/marketing/ProposalApiTest.java`: per-role and per-Zone matrix; the response labels it "Proposal (not a contract)"
- [X] T033 [P] [US3] `frontend/src/features/marketing/ProposalForm.test.tsx`: both salary modes, validation, revision list, the draft label, axe in both themes

### Implementation for User Story 3

- [X] T034 [US3] `recruitment/marketing/internal/ProposalService.java` and `recruitment/marketing/web/ProposalController.java`
- [X] T035 [P] [US3] `frontend/src/features/marketing/{ProposalForm,ProspectDetailPage}.tsx` and route `/marketing/prospects/:id` (overview, activities, proposal, history)

**Checkpoint**: proposals work and unlock Final Stage.

---

## Phase 6: User Story 4 - Win the School and Hand Off to the MoU (P1)

**Goal**: create the School on a win, hand off to spec 012 pre-filled, show the MoU status, flag and notify overdue ones.

**Independent Test**: approve a prospect, create its School, follow the hand-off to the pre-filled MoU form, save it, and see MoU then Active on the prospect; leave another past the limit and see one notification.

### Tests for User Story 4

- [X] T036 [P] [US4] `backend/src/test/java/com/hls/recruitment/marketing/WinServiceTest.java`: only Admin and Director create the School (a Zone Manager gets 403); a Place of the prospect's Zone and a confirmed billing contact are required (the School's other fields come from the prospect); an existing School of the same name and Place is offered for linking, never duplicated; the School is created only on a win; without 012 the prospect is saved "won, MoU to be recorded" and no hand-off is offered
- [X] T037 [P] [US4] `backend/src/test/java/com/hls/recruitment/marketing/ContractStatusTest.java`: the effective stage is MoU when the School has a live contract and Active when a position is filled; the prospect shows dates, positions, filled and vacant from 012; the signed MoU may differ from the proposal and the difference is returned side by side; no contract data is stored here
- [X] T038 [P] [US4] `backend/src/test/java/com/hls/recruitment/marketing/OverdueJobTest.java`: a won prospect without a contract past the limit is flagged and publishes `WonProspectOverdue` once; a second run publishes nothing; changing the limit (audited) and passing it again publishes again; recording the MoU clears the flag; the job is off in `IntegrationTestBase`
- [X] T039 [P] [US4] `backend/src/test/java/com/hls/recruitment/marketing/SettingsApiTest.java`: holders of `MARKETING_SETTINGS` (Admin and Director by default) read and change `mouOverdueDays` (1 to 90, version required, audited); Zone Manager 403; Teacher and System 403
- [X] T040 [P] [US4] `frontend/src/features/marketing/WinDialog.test.tsx`: pick a Place, create or link a School, the hand-off navigates to the 012 contract page with the proposal in the router state; a Zone Manager sees no create or record action

### Implementation for User Story 4

- [X] T041 [US4] `recruitment/marketing/internal/{WinService,ContractStatusReader}.java`: the win and School creation through `SchoolRegistry`, the link, the derived MoU and Active stage through `SchoolContracts` (`ObjectProvider` so the module starts without 012), the side-by-side difference
- [X] T042 [US4] `recruitment/marketing/internal/{OverdueJob,SettingsService}.java` and `recruitment/marketing/web/SettingsController.java`; `ProspectOwners` and `ProspectWon`
- [X] T043 [US4] Spec 012 amendment A7 (frontend): `frontend/src/features/schoolbilling/SchoolContractPage.tsx` and `MouFormDialog.tsx` accept initial values from the router state (Teacher count, salary mode and amounts, start month) and open the MoU form pre-filled; extend `SchoolContractPage.test.tsx` for it
- [X] T060 [US4] Win with contacts: `WinService` saves the optional principal and accountant contacts of the new School through the school module's contacts service (016 amendment A8; rebase after 016 merges), and `WinDialog.tsx` shows the two optional contact blocks; extend `WinServiceTest` and `WinDialog.test.tsx`
- [X] T044 [P] [US4] `frontend/src/features/marketing/{WinDialog,MarketingSettingsPage}.tsx`, the MoU status panel and the "MoU not yet recorded" flag in `ProspectDetailPage.tsx` and `PipelineBoard.tsx`; route `/marketing/settings`

**Checkpoint**: the hand-off, the status and the alerts work.

---

## Phase 7: User Story 5 - See Demand and Supply (P2)

**Goal**: visits, stages, win rate, Schools won, demand against supply.

**Independent Test**: with three won Schools holding 9 vacant positions and 5 ready-to-deploy recruits, see demand 9, supply 5, shortfall 4.

### Tests for User Story 5

- [X] T045 [P] [US5] `backend/src/test/java/com/hls/recruitment/marketing/DashboardTest.java`: visits this month, prospects by stage, win rate (won divided by won plus lost), Schools won per Zone and owner match the lists; demand is the sum of vacant positions of won Schools with a live contract, and the counts by derived stage come from one batch call to 012 (a test with 50 won prospects asserts a fixed number of queries); supply comes from `SupplySource` and is `null` ("not available") when none is provided; a Zone Manager's numbers cover only their Zones; an empty state returns zeros
- [X] T046 [P] [US5] `frontend/src/features/marketing/MarketingDashboard.test.tsx`: role fixtures, "not available" supply, empty state, axe in both themes

### Implementation for User Story 5

- [X] T047 [US5] `recruitment/marketing/internal/DashboardService.java` (grouped SQL) and `recruitment/marketing/web/DashboardController.java`
- [X] T048 [P] [US5] `frontend/src/features/marketing/MarketingDashboard.tsx` and route `/marketing/dashboard`

**Checkpoint**: the full spec is functional.

---

## Phase 8: Polish and Cross-Cutting Concerns

- [X] T049 [P] `recruitment/marketing/internal/MarketingDevSeeder.java` (only with the demo flag, idempotent): the prospects described in quickstart.md, with one proposal, one visit with an attachment-free outcome and a won prospect whose School has an MoU
- [X] T050 [P] Role-by-role UI test of the MARKETING menu and every action visibility (Admin, Director, Zone Manager, Teacher, System) in `frontend/src/features/marketing/` and the navigation tests
- [X] T051 [P] Add the new pages and dialogs to the axe harness in `frontend/src/a11y/a11y.test.tsx` (light and dark) and record the keyboard-only and screen-reader pass and the phone-width check in `specs/023-school-marketing-mou/quickstart-results.md`
- [X] T052 [P] Add a "023 School Marketing" folder to the Postman collection in `postman/HLS API/` and refresh `docs/db/schema-v23.sql`
- [X] T053 [P] Constitution amendment in `.specify/memory/constitution.md`: the MARKETING navigation section (Principle IV), the `files` module (Principle VII) and a Marketing row in the Default role access matrix, with a Sync Impact Report note (the version is taken at merge, after spec 016's 2.4.0)
- [X] T054 [P] Update `docs/spec-roadmap.md` (023 status; the shared file storage contract C7 is now built here) and `docs/running-locally.md` (the marketing flow, the files directory and demo data)
- [x] T055 Review before the PR: run the `java-reviewer` agent on `recruitment/marketing`, `files` and the `school`, `organization`, `schoolbilling` and `notification` changes, and the `database-reviewer` agent on `V23`; address findings
- [ ] T056 Walk through every scenario in `quickstart.md` on the local app (database on Docker port 5433) and write the outcomes to `specs/023-school-marketing-mou/quickstart-results.md`
- [ ] T057 Run the full backend and frontend suites once, then open the PR

---

## Dependencies and Execution Order

- Phase 1 then Phase 2. T003 blocks every table-using task; T007 before T008; T013 after T003; T012 before T020.
- **US1 before US2** (stage moves need prospects), **US3 before the Final Stage rule** in US2 is tested (T024 uses a proposal; write T031 to T034 before T024 runs, or stub a revision through the repository), **US2 before US4** (the review makes a prospect won), US5 reads all.
- T058 before T059; T059 needs T020 and T022.
- T041 needs T009 (`SchoolRegistry`) and T010 (`occupancyOf`); T042 needs T011 and T009 (`managersOfZone`); T043 needs T041's hand-off contract.
- Frontend tasks marked [P] in a phase can start once that phase's API contract is fixed; they need only the typed client from T002.
- Polish after all stories; T055 to T057 last.

### Parallel opportunities

- Phase 2: T004, T005, T006, T007, T009, T010, T011 together, then T008, T012, T013.
- US1: T014 to T019 together; T023 alongside T022.
- US2: T024 to T027 together; T030 alongside T029.
- US3: T031 to T033 together; T035 alongside T034.
- US4: T036 to T040 together; T044 alongside T042 and T043.
- US5: T045 and T046 together.
- Polish: T049 to T054 together.

## Implementation Strategy

- **MVP**: Phases 1 to 3 (US1): prospects, visits with outcomes, attachments and the Zone-scoped calendar. It already replaces the paper trail of school visits.
- **Complete slice**: add US2 (pipeline and review), US3 (proposals), US4 (win, hand-off, MoU status, alerts), then US5 (the dashboard).
- Land the foundation (the `files` module and the four small interfaces) with the existing suites green before any story code.
- Merge order with spec 016: either order works; the second to merge renumbers its migration and rebases the shared `recruitment/api` package skeleton, the constitution amendment and the navigation catalog.
