---

description: "Task list for MoU Documents and Approval"
---

# Tasks: MoU Documents and Approval

**Input**: Design documents from `/specs/031-mou-documents/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/mou-approval-api.md, contracts/module-interfaces.md,
quickstart.md, and specs 012 (schoolbilling), 023 (`files` module), 015 and 027 (approval convention) implemented and merged
(they are on `main`).

**Defaults used (accepted by the user, recorded in spec.md Clarifications "plan defaults accepted")**: removing the current
document of an approved MoU keeps it approved ("approved without document"); the approver may edit signed and end dates, and the
start date only if the MoU chain stays gapless and no position was filled before the new date; attaching or replacing a document
never changes a legacy MoU's status; self approval by a Director is allowed and audited.

**Tests**: first-class tasks (Constitution Principle IX), written before the code they test: the role and scope matrix for every
endpoint (SC-002, SC-003), the file policy, concurrent decisions (SC-005), the 012 regression in every approval state (SC-006),
the V100 backfill and trigger, the public interface, ArchUnit, and frontend tests per role.

**Organization**: grouped by user story (spec.md US1 to US4). Order: Setup, Foundational (migration V100, entities, permission,
navigation, audit mapping, DRAFT row on create), US1 (documents, MVP part 1), US2 (approval, MVP part 2), US3 (lists and pending
list), US4 (public interface), Polish (docs, Postman, demo data, V100 recheck, full runs, manual walkthrough last).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US4; absent for Setup, Foundational and Polish

## Path Conventions

Backend `backend/src/main/java/com/hls/schoolbilling/{api,internal,web}/`, tests under `backend/src/test/java/com/hls/schoolbilling/`;
migration `backend/src/main/resources/db/migration`; frontend `frontend/src/features/schoolbilling/`. Run one Maven test class at a
time (`mvn -o test -Dtest=...`); Docker must be running for integration tests. Below, `B` = `backend/src/main/java/com/hls`,
`T` = `backend/src/test/java/com/hls`, `FE` = `frontend/src/features/schoolbilling`.

---

## Phase 1: Setup

- [X] T001 Verify the prerequisites and STOP and report if any is missing (do not stub): `B/files/api/FileStore` (`store`, `find`, `list`, `open`, `remove`, `MAX_BYTES`), `B/schoolbilling` (`Contract`, `ContractService.create/recordMou/cancel`, `ContractListService`, `ContractController`, `SchoolContracts`), `school.api` (`ChangeRecorder`, `StaleVersion`, `CallerContext`, `SchoolDirectory`), `organization.api` (`ScopeQueries`, `ManagerQueries.managerOfSchool`), `notification` (`TransferEventListener`, `RecipientResolver`, `MessageFactory`), `AuditVisibility`, and the highest migration number on `main` (V90 on 2026-10-24; the plan uses **V100**). Record any name that differs in research.md section 1
- [X] T002 [P] Create `FE/mouApprovalApi.ts` with types and calls for the endpoints in contracts/mou-approval-api.md (approval object, upload via `FormData`, download as blob through the auth fetch, submit, approve, reject, remove, amend terms, pending list)
- [X] T003 [P] Add `T/schoolbilling/MouModuleRulesTest.java` (ArchUnit): `schoolbilling` uses `files` only through `files.api`; nothing outside `schoolbilling` uses `schoolbilling.internal` or `.web` (extend the existing rules, do not weaken them); `notification` reads `schoolbilling.api` only

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the migration, the entities, the permission action, navigation, audit mapping and the DRAFT row created with each
MoU. No user story can start before these.

### Tests first

- [X] T004 [P] `T/schoolbilling/MouMigrationTest.java`: with Flyway to V100, every ACTIVE contract has an `APPROVED_LEGACY` row and a `LEGACY_MARKED` event; RATE_PENDING and CANCELLED contracts have none; `mou_approval` check constraints reject bad combinations (PENDING without submitter, REJECTED without reason, LEGACY with decider); `mou_document` refuses DELETE and any UPDATE except setting the removal columns once; `mou_approval_event` refuses UPDATE and DELETE; a `contract_position` salary UPDATE is refused unless the approval is PENDING and refused for any other column or for DELETE; `contract_signatory` stays final
- [X] T005 [P] `T/schoolbilling/MouApprovalStatusTest.java`: unit test of the shown-state derivation (research.md section 5): placeholder, cancelled, legacy, approved with and without a current document, draft, pending, rejected; a removed latest version falls back to the previous one as current
- [X] T006 [P] `T/identity/SchoolContractsApproveGrantTest.java`: `APPROVE` is eligible for Director and Manager, ineligible for Admin, Teacher and System; the seed gives Director and Manager `APPROVE` on an existing database (insert if missing) and leaves the View/Create/Edit seeds unchanged; the navigation item "MoU Approvals" is offered only with `APPROVE`; `AuditVisibility` hides `CONTRACT`, `MOU_DOCUMENT` and `MOU_APPROVAL` from a role without View on `SCHOOL_CONTRACTS` (System included)

### Implementation

- [X] T007 Write `backend/src/main/resources/db/migration/V100__create_mou_approval.sql` per data-model.md: `mou_approval`, `mou_document`, `mou_approval_event`, checks, indexes, append-only and removal-only triggers, the `contract_position_guard()` function replacing the position trigger, and the backfill (APPROVED_LEGACY plus LEGACY_MARKED event for every ACTIVE contract). Make T004 pass
- [X] T008 [P] Add `B/schoolbilling/internal/MouApprovalStatus` (DRAFT, PENDING, APPROVED, REJECTED, APPROVED_LEGACY), entities `MouApproval` (with `@Version`), `MouDocument`, `MouApprovalEvent`, and repositories including `MouApprovalRepository.lockByContractId` (pessimistic write) and batch lookups by contract ids
- [X] T009 Add the shown-state derivation (`MouApprovalState` logic in `internal`, used by detail, list and the public interface) so T005 passes
- [X] T010 Add `APPROVE` to `PermissionModule.SCHOOL_CONTRACTS`; in `PermissionEligibility` make `APPROVE` ineligible for Admin (as `TEACHER_TRANSFERS`); in `PermissionMatrixService.seedSchoolContracts` seed Director and Manager `APPROVE`; add the "MoU Approvals" OPERATIONS item (`/operations/school-contracts/approvals`, requires `APPROVE`, Director and Manager) to `NavigationCatalog`; confirm `AccessModelService` scope needs no change. Make T006 pass (permission part)
- [X] T011 Map `CONTRACT`, `MOU_DOCUMENT` and `MOU_APPROVAL` to `SCHOOL_CONTRACTS` in `AuditVisibility.REQUIRED_VIEW`. Make T006 pass (audit part)
- [X] T012 Create the DRAFT `mou_approval` row inside `ContractService.create` and `recordMou` (same transaction, no other behaviour change); update the `Contract` and `ContractController` Javadoc (a Manager now holds `APPROVE`; terms edit only through the pending path). Run `ContractApiTest`, `ContractAuditTest`, `ContractListApiTest`, `MappingApiTest`, `SchoolContractsPublicApiTest` unchanged and green
- [X] T013 [P] Add `MouAllowedActions` (UPLOAD, REMOVE_DOCUMENT, SUBMIT, APPROVE, REJECT, AMEND_TERMS) computed from grants, role, scope, contract state and approval status, following `expenses.AllowedActions`; and the shared `MouScope` helper (contract in scope or 404; approver check: Director, or Manager with the School in scope)

**Checkpoint**: migration applies on an empty and on a populated database; existing 012 tests green.

---

## Phase 3: User Story 1 - Attach the signed MoU document (Priority: P1) 🎯 MVP part 1

**Goal**: Admin and Director attach, replace and remove versions of the signed document; scoped users view and download it.

**Independent Test**: record a MoU, upload a PDF, download it as a Zone Manager of the School, upload a corrected version, see both versions with the latest current, remove one with a reason, and get 404 as another Zone's Manager.

### Tests for US1 (write first, expect failure)

- [X] T014 [P] [US1] `T/schoolbilling/MouDocumentApiTest.java`: upload as Admin and Director (201 with the approval object, version 1 then 2, name, type, size, who and when); a Manager holding VIEW gets 403 on upload, remove and submit, even when granted EDIT; Teacher and System 403; placeholder contract refuses with "record the MoU terms first"; cancelled contract accepts a download but refuses submit; remove needs a reason (400 without, 300 max), is Admin or Director only, keeps the status, hides the version and keeps its record; download works only for a version that is not removed
- [X] T015 [P] [US1] `T/schoolbilling/MouDocumentPolicyTest.java`: pdf, jpg, jpeg, png accepted (upper case extension too); docx, xlsx, txt, exe refused by extension before the store is called; a program renamed `.pdf`, an empty file and 10 MB + 1 refused with a clear reason and nothing stored (no `stored_file` row, no `mou_document` row)
- [X] T016 [P] [US1] `T/schoolbilling/MouDocumentScopeTest.java` (SC-003 part): Manager of another Zone gets 404 for approval detail, document list, download by `documentId`, upload and remove; a document id of another contract in the URL answers 404; the file store id is never accepted as a route
- [X] T017 [P] [US1] `T/schoolbilling/MouDocumentAuditTest.java`: upload, replace and removal each write `mou_approval_event` and Change History (`MOU_DOCUMENT`) with actor, role and the removal reason; a replacement of a legacy MoU's document leaves it `APPROVED_LEGACY`

### Implementation for US1

- [X] T018 [P] [US1] `B/schoolbilling/internal/MouDocumentPolicy`: extension check for pdf, jpg, jpeg, png and the messages of contracts/mou-approval-api.md; make T015 pass
- [X] T019 [US1] `MouDocumentService.upload`: scope and role checks, placeholder refusal, policy check, `FileStore.store(actor, "MOU_DOCUMENT", contractId, name, bytes)`, next `version_no`, `mou_document` row with copied name, type and size, event `DOCUMENT_ADDED` or `DOCUMENT_REPLACED`, Change History; keep the status unchanged here (the reset of an approved MoU is added in T030)
- [X] T020 [US1] `MouDocumentService.remove` (reason required, Admin and Director only, `FileStore.remove`, removal columns on `mou_document`, event and audit) and `download` (scope, VIEW, version not removed, `FileStore.open`)
- [X] T021 [US1] `MouApprovalQueries.detail` (documents with current mark and removal records, history, derived state, allowed actions, version) and `MouDocumentController` plus `GET /contracts/{id}/approval` in `B/schoolbilling/web`: multipart upload, download with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`, remove as `POST .../remove`; guard calls per the grant table. Make T014, T016, T017 pass
- [X] T022 [P] [US1] `FE/ApprovalStatusChip.tsx` (text plus colour, all states and the "approved without document" and "approved (before approval step)" labels) and `FE/MouDocumentList.tsx` (versions, current mark, download through the auth fetch, removed rows with reason)
- [X] T023 [P] [US1] `FE/UploadMouDocument.tsx` (labelled file input, allowed types and size shown, error and progress states, focus management) and the remove dialog with a required reason
- [X] T024 [US1] Add a documents and approval panel to `FE/SchoolContractPage.tsx` per contract, driven by `actions` from the server; tests in `FE/MouDocumentPanel.test.tsx` per role (Admin sees upload and remove, Manager sees download only, Teacher and System none), loading, empty and error states, phone width

**Checkpoint**: US1 independently demonstrable (quickstart section 1).

---

## Phase 4: User Story 2 - Approve or reject a MoU (Priority: P1) 🎯 MVP part 2

**Goal**: submit, approve, reject, resubmit, reset on a replaced document, and the approver's term edit, all audited and notified.

**Independent Test**: submit with a document, reject as the Zone Manager with a reason, upload a corrected document and resubmit as Admin, approve as the Director; see the status, approver, times, history and notifications.

### Tests for US2 (write first, expect failure)

- [X] T025 [P] [US2] `T/schoolbilling/MouApprovalApiTest.java` (SC-002): every role against submit, approve, reject, amend; submit works without a document and with one; placeholder and cancelled refuse submit; Admin 403 on approve and reject; Teacher and System 403; Director approves any School, Manager only a School in scope (404 otherwise); reject needs a reason (max 500); resubmit after reject gives round 2 and keeps the rejection in the history; a Director approves their own submission and the history shows the same actor twice; approving when not PENDING answers 409
- [X] T026 [P] [US2] `T/schoolbilling/MouConcurrentDecisionTest.java` (SC-005): two threads decide the same PENDING round (approve and approve, approve and reject); exactly one `APPROVED` or `REJECTED` event exists, the other gets 409 naming the decider and time; a stale `version` gets 409
- [X] T027 [P] [US2] `T/schoolbilling/MouTermsAmendTest.java`: rate (SAME_FOR_ALL) updates every position salary; position salaries (PER_TEACHER); signed date not in the future; end date not before start; validations of `ContractService.validate` (above zero, two decimals, 10,000,000); only while PENDING (409 otherwise, and the trigger refuses a direct salary UPDATE); `teacherCount`, `salaryMode`, signatories and School refused with 400; start date change refused when an earlier MoU ends the day before the old start or when a position was filled before the new date, accepted otherwise; Change History holds old and new values; contract and approval versions both change so a stale decider gets 409; an edit by Admin 403
- [X] T028 [P] [US2] `T/schoolbilling/MouReplaceResetTest.java`: a new version on APPROVED resets to PENDING (round + 1, submitter = uploader, `RESET_TO_PENDING`, approvers notified, document counted); on DRAFT, PENDING, REJECTED only a history entry; on APPROVED_LEGACY nothing changes; removing the current document of an APPROVED MoU keeps it APPROVED and the shown state becomes `APPROVED_WITHOUT_DOCUMENT`
- [X] T029 [P] [US2] `T/notification/MouEventListenerTest.java`: submission notifies active Directors and the School's active Zone Manager minus the submitter and never Admin; decision notifies the submitter and every active Admin minus the decider, with the reason on rejection and a note when terms were amended; a changed Zone Manager gets the notice of a pending MoU; no Zone Manager means Directors only
- [X] T030 [P] [US2] `T/schoolbilling/MouRegressionTest.java` (SC-006, FR-013): for a test month, Teacher mapping, assignment, occupancy and attendance results for the same School are identical with the MoU in DRAFT, PENDING, REJECTED, APPROVED and APPROVED_LEGACY; `PlacementMoves` and `AssignmentService` never read approval state (ArchUnit assertion added to T003's file)

### Implementation for US2

- [X] T031 [US2] `MouApprovalService.submit` (EDIT, Admin or Director, real terms, not cancelled, DRAFT or REJECTED, version check, round + 1, event `SUBMITTED` or `RESUBMITTED`, Change History, publish `MouSubmitted`)
- [X] T032 [US2] `MouApprovalService.approve` and `reject`: grant, scope (404), not cancelled, row lock through `lockByContractId`, status PENDING else 409 with who and when (copy the wording of `DecisionService.alreadyDecided`), `StaleVersion.check`, no requester check, flag `selfDecided`, events, Change History, publish `MouDecided`. Make T025 and T026 pass
- [X] T033 [US2] In `MouDocumentService.upload` add the reset rule (APPROVED to PENDING with round + 1 and `MouSubmitted(resubmitted=true)`; legacy unchanged; removal never changes status). Make T028 pass
- [X] T034 [US2] `MouTermsService.amend` plus a narrow package-private method in `ContractService` that the service alone calls (asserts PENDING, takes the same lock, applies the accepted start-date rule through a repository query for the previous contract and earliest filled position); `PATCH /contracts/{id}/terms`. Make T027 pass
- [X] T035 [US2] Add controller routes `POST /contracts/{id}/submit`, `/approve`, `/reject` to `MouApprovalController` with the guard per grant
- [X] T036 [P] [US2] Add `MouSubmitted` and `MouDecided` to `B/schoolbilling/api`; add `MOU_SUBMITTED` and `MOU_DECIDED` to `NotificationType`; `MouEventListener` in `B/notification/internal`; texts in `MessageFactory` (title, message, link to the School page). Make T029 pass
- [X] T037 [P] [US2] `FE/DecisionDialogs.tsx` (approve confirm, reject with required reason, submit confirm, 409 "already decided" message and reload), `FE/ApprovalHistory.tsx` (oldest first, role, reasons, term changes, self decision marked)
- [X] T038 [P] [US2] `FE/AmendTermsDialog.tsx` (rate or position salaries, dates; only the fields that changed are sent; validation messages; shown only when `AMEND_TERMS` is in `actions`)
- [X] T039 [US2] Wire submit, approve, reject, amend and history into the panel of `SchoolContractPage.tsx`; tests `FE/MouApprovalPanel.test.tsx`: buttons follow `actions` per role, Admin never sees Approve, reject requires a reason, 409 handling, dates DD/MM/YYYY, keyboard and focus

**Checkpoint**: US1 and US2 together are the MVP (quickstart sections 1 and 2).

---

## Phase 5: User Story 3 - What needs approval and which MoUs lack a document (Priority: P2)

**Goal**: approval and document columns and filters on the School Contracts list; a pending list for approvers.

**Independent Test**: with MoUs in four states, see each in the list, filter by state and document, and see a Zone Manager's pending list contain only their own Schools.

### Tests for US3 (write first)

- [X] T040 [P] [US3] `T/schoolbilling/MouListApiTest.java`: list rows carry `approvalState`, `documentState`, `pendingSince`; filters `approval` and `document` work alone and with `status`; a Manager's list is limited to their scope; the number of queries for a page does not grow with the number of Schools (statement-count assertion); `GET /schools/{id}` carries the `approval` summary for each contract
- [X] T041 [P] [US3] `T/schoolbilling/MouPendingListApiTest.java`: only PENDING in scope, oldest first, needs `APPROVE` (Admin 403, Teacher and System 403); Manager B sees none of Manager A's; a Zone Manager change moves visibility with no other change; a School with no Zone Manager appears for the Director only

### Implementation for US3

- [X] T042 [US3] Extend `ContractDtos` (`ContractListRow`, `ContractDto` with the `approval` block) and `ContractListService` with the two batch lookups (approval rows, current-document flags) and the `approval` and `document` filters; `ContractController` parameters. Make the list part of T040 pass
- [X] T043 [US3] `MouApprovalQueries.pending` and `GET /approvals/pending` (grant `APPROVE`, scope rule). Make T041 pass
- [X] T044 [P] [US3] `FE/ContractsListPage.tsx`: two columns (approval chip, document state), two filters, "pending since"; update `ContractsListPage.test.tsx`
- [X] T045 [P] [US3] `FE/MouApprovalsPage.tsx` (pending list with School, Zone Manager, submitter, round, document state, link to the School page), route `/operations/school-contracts/approvals` in `App.tsx`, empty, loading and error states, phone width; `FE/MouApprovalsPage.test.tsx` per role

**Checkpoint**: quickstart section 3 passes.

---

## Phase 6: User Story 4 - Others read the approval state (Priority: P2)

**Goal**: billing (022), incentives (030) and marketing (023) read the state of the MoU in effect through the public interface.

**Independent Test**: call `MouApprovals` for Schools with approved, pending, rejected, draft, no-document, legacy, placeholder and no MoU, and get the right state for each.

- [X] T046 [P] [US4] `T/schoolbilling/MouApprovalsPublicApiTest.java` (SC-007): `stateOn` and `stateOfAll` for every case in contracts/module-interfaces.md (`NO_MOU`, `NOT_APPLICABLE`, `DRAFT`, `PENDING`, `REJECTED`, `APPROVED`, `APPROVED_WITHOUT_DOCUMENT`, `APPROVED_BEFORE_APPROVAL_STEP`); `approvedOn` and `documentAttached`; `countsAsApproved` true only for the three approved states; a cancelled or ended contract is not "in effect"; the MoU chosen equals `SchoolContracts.contractOf`; `stateOfAll` gives an entry per id in a fixed number of queries; `ContractView` and `SchoolContracts` signatures unchanged
- [X] T047 [US4] `B/schoolbilling/api/MouApprovals` and `MouApprovalState`; `MouApprovalsImpl` in `internal` reusing the derivation of T009 and `SchoolContracts` lookups. Make T046 pass
- [X] T048 [US4] Extend the ArchUnit rules of T003 so other modules reach approval state only through `MouApprovals`, and run `ModulithVerificationTest`

**Checkpoint**: quickstart section 4 passes.

---

## Phase 7: Polish and cross-cutting

- [X] T049 [P] Postman collection additions for every new endpoint (upload, download, remove, submit, approve, reject, amend, pending list, approval detail) under `postman/`
- [X] T050 [P] Demo data in the `ContractDevSeeder`/dev seeder: a legacy MoU, a DRAFT with and without a document, a PENDING, a REJECTED, an APPROVED, a School with the "MoU pending" placeholder, a School with no Zone Manager; small sample files
- [X] T051 [P] Update docs: `docs/spec-roadmap.md` row 031; constitution (PATCH bump to the next patch after the highest merged, the School Contracts row of the default access matrix gains Approve for Director and Manager (own Schools), the OPERATIONS item "MoU Approvals"); a note in spec 012's contract docs that terms can be edited by the approver only through this path
- [X] T052 [P] Accessibility pass on the new screens (WCAG 2.2 AA: labels, status not by colour alone, focus order, dialog focus trap, 44 px targets at phone width) and loading, empty and error states (FR-019)
- [X] T053 **Re-check the migration number**: list `backend/src/main/resources/db/migration` on the latest `origin/main` and on every open implementation branch; if V100 is taken, rename `V100__create_mou_approval.sql` and update plan.md, data-model.md, research.md and this file before merging. Do this again immediately before the PR is marked ready
- [ ] T054 Full backend run (`mvn -o test`, Docker running) including `ModulithVerificationTest`; record the results in plan.md or quickstart.md; fix regressions in specs 012, 023, 027 and the permission tests
- [X] T055 Full frontend run (`npm test`, `npm run build`, lint) and record the result
- [ ] T056 Manual walkthrough of quickstart.md sections 1 to 5 on the running app with each role (Admin, Director, Manager A, Manager B), at desktop and phone width; record the result; this is the last task before the PR

---

## Dependencies & Execution Order

- Setup (Phase 1) first. Foundational (Phase 2) blocks everything: tests T004 to T006, then migration T007 and entities T008, then T009 to T013 (T010 and T011 are independent of T008 and T009).
- US1 needs Phase 2. US2 needs US1's service and controller (T019 to T021) because the reset rule lives in the upload path (T033) and the approval object comes from T021. US3 needs the derivation (T009) and US2 for meaningful data. US4 needs only Phase 2 (T009) and can run beside US3.
- Polish last; T053 is repeated before the PR is marked ready; T056 is the very last.

```text
Setup -> Foundational -> US1 -> US2 -> US3
                           \-----------> US4 (needs only Foundational)
                 all -> Polish (T053 recheck, T054 to T055 runs, T056 walkthrough last)
```

## Parallel Opportunities

- Tests T004 to T006 in parallel; T008 and T013 beside the migration once it is fixed.
- US1 tests T014 to T017 in parallel (different files); T018 beside T019; frontend T022 and T023 in parallel.
- US2 tests T025 to T030 in parallel; T036, T037 and T038 beside the services.
- US3 tests T040 and T041 and frontend T044 and T045 in parallel; US4 can run beside US3.
- T049 to T052 in Polish run in parallel.

## Implementation Strategy

1. MVP: Phases 1, 2, 3 and 4 (documents and approval). Stop and validate with quickstart sections 1, 2 and 5.
2. Add US3 (lists, pending list), then US4 (public interface; can be done earlier by a second developer).
3. Polish, the V100 recheck, the full runs, then the manual walkthrough last.
4. Every change to existing code (DRAFT row in `ContractService`, the two DTOs, the trigger, the permission action) is followed by the unchanged spec 012 tests (T012) to prove nothing else moved.
