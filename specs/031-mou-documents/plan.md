# Implementation Plan: MoU Documents and Approval

**Branch**: `031-mou-documents` (worktree branch `spec/031-mou-documents`) | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

## Summary

All work sits inside the existing `schoolbilling` module (spec 012); no new module. A new table `mou_approval` (one row per
MoU, created with the MoU) carries the approval status; `mou_document` links each uploaded version to a file kept in the shared
`files` module (spec 023, `FileStore`); `mou_approval_event` is the append-only approval and document history. A new service
group in `schoolbilling.internal` (`MouDocumentService`, `MouApprovalService`, `MouTermsService`, `MouApprovalQueries`) adds:
upload, replace and remove of document versions; submit, approve and reject (Director, or the Zone Manager of the MoU's School);
a narrowly scoped edit of commercial terms while pending (FR-012); a pending list; and approval and document columns and
filters on the School Contracts list and detail. One new public interface, `schoolbilling.api.MouApprovals`, answers the
approval state of the MoU in effect for a School on a date, for 022, 030 and 023. `SCHOOL_CONTRACTS` gains the action `APPROVE`
(Director and Manager; never Admin), one OPERATIONS item "MoU Approvals" requires it, and `notification` gets one listener for
two events. The frontend extends `features/schoolbilling` (list columns and filters, a document and approval panel on the
School page, a pending approvals page). See [research.md](research.md).

Read from the code, not the planning documents (research.md section 1): there is no shared approval class, "the approval
pattern" is a convention that 015 (`expenses.DecisionService`) and 027 each implement privately and both forbid self decision,
which this spec deliberately allows; the shared `FileStore` allows docx, xlsx and txt, which this spec does not; the positions
of a contract are protected by a database trigger that would refuse the FR-012 amount edit; `CONTRACT` changes are not mapped in
`AuditVisibility`; and the spec text still holds one scenario (refuse submit without a document) that its own clarifications
reverse.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers in tests), React + Vite + Vitest.
- Migration: `V100__create_mou_approval.sql`. Highest on `main` is V90 (expenses); V60 performance, V70 calendar and V80
  transfers are taken. V100 is the next decade slot; re-check against merged branches at implementation.
- Files: `FileStore.store(actor, "MOU_DOCUMENT", contractId, name, bytes)`; limit 10 MB (`FileStore.MAX_BYTES`, multipart limit
  already 11 MB in `application.yml`); this module additionally restricts the extension to pdf, jpg, jpeg and png before storing.
- Constraints: append-only `mou_approval_event` and `mou_document` (triggers), no cross-module foreign keys, audit through
  `ChangeRecorder`, 404 for anything out of scope, one decision per submission (row lock plus version), business date through
  the existing `Clock`.
- Scale: under 100 users, a few hundred MoUs; the list adds two batch queries per page, no per-row query.
- No scheduler and no job: every state is derived at read time from the rows.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | Upload, replace, remove, submit, resubmit, approve, reject, reset to pending and term edits write `mou_approval_event` (append-only, trigger) and Change History through `ChangeRecorder` with old and new values and the actor. A self approval is recorded as such (both actions carry the same actor). Document bytes are never changed or deleted; removal hides a version and keeps the reason. |
| II Five roles, RBAC | `SCHOOL_CONTRACTS` gains `APPROVE`. Seeded Director and Manager. `PermissionEligibility` makes `APPROVE` ineligible for Admin (D5) and everything stays ineligible for Teacher and System. Upload, replace, remove, submit reuse `EDIT`; read and download use `VIEW`. All runtime-editable in Role and Permissions. |
| III Data scope | Admin and Director org-wide. A Manager reads, downloads and decides only for Schools in their scope (`ScopeQueries`); anything else, including a file id, answers 404 and never reveals that a document exists. The pending list, the list filters and the public interface count the same scope rule (the public interface is system-internal and unscoped by design). |
| IV Role-based UI | Buttons follow the grants and a server-sent `actions` list on each MoU (upload, remove, submit, approve, reject, amend). One new OPERATIONS item "MoU Approvals" (requires `APPROVE`). The default access matrix row for School Contracts is updated. |
| V Formula-driven | Not touched. No pay, receivable or margin is computed. The only amounts edited are the MoU's own salary figures under FR-012, which 013a, 013b and 022 already read from the contract. |
| VI Training and substitution | Not touched (FR-013): placement, mapping, attendance and substitution do not read the approval state. |
| VII Modular monolith | `schoolbilling` gains one dependency, `files.api.FileStore`, and reads `organization.api` and `school.api` as it already does. Other modules reach approval only through `schoolbilling.api.MouApprovals` and two events; `notification` is the only listener. ArchUnit (`SchoolBillingModuleRulesTest`) and Modulith verification still hold. |
| VIII Concurrency | Request path only. The decision takes a pessimistic row lock on `mou_approval` and checks `version`; the second decider is told who decided and when (409). Term edits take the same lock, so an edit and a decision cannot interleave. |
| IX Testability | Unit tests for the state derivation and the file policy; API tests per endpoint, role and scope boundary (SC-002, SC-003); an integration test that concurrent decisions record one (SC-005); a regression test that mapping and attendance results are identical in every approval state (SC-006); ArchUnit; frontend tests for grants. |
| X Security | Server re-checks grant, scope and role on every call; extension and content checked, content type decided by the server, download as attachment with `nosniff`; free text capped (reason 500, note 1000) and escaped on output; multipart size capped. |
| XI Recruitment and marketing tracked to outcome | Not changed. 023 may later read the state through `MouApprovals`; this spec does not alter its win flow. |

Constitution change: PATCH bump (next patch after whichever spec merges first, currently 2.5.10 so 2.5.11), the School
Contracts row of the default access matrix gains Approve (Director; Manager for own Schools) and the OPERATIONS navigation gains
"MoU Approvals". No principle changes. Gate result: pass before research and after design, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/031-mou-documents/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── mou-approval-api.md
│   └── module-interfaces.md
└── tasks.md                       # produced later by speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/hls/schoolbilling/
  api/       MouApprovals (new), MouApprovalState (new record), MouSubmitted, MouDecided (events), package-info
  internal/  MouApproval, MouApprovalRepository, MouDocument, MouDocumentRepository, MouApprovalEvent, MouApprovalEventRepository,
             MouApprovalStatus, MouDocumentPolicy (pdf/jpg/png), MouDocumentService (upload, replace, remove, download),
             MouApprovalService (submit, approve, reject, lock and version rules), MouTermsService (FR-012 edit),
             MouApprovalQueries (detail, history, pending list, batch states for the list), MouAllowedActions,
             MouApprovalsImpl (public interface); existing MoUs are backfilled by the migration, no runtime marker
             changed: ContractService (creates the DRAFT row in create and recordMou), ContractDtos (approval summary on
             ContractDto and ContractListRow, list filters), ContractListService (batch approval lookup), Contract Javadoc
  web/       MouApprovalController, MouDocumentController (multipart and download); ContractController gains the filters
backend/src/main/java/com/hls/notification/internal/MouEventListener.java (+ MessageFactory texts, types MOU_SUBMITTED, MOU_DECIDED)
backend/src/main/java/com/hls/notification/api/NotificationType.java (two constants)
backend/src/main/java/com/hls/identity/ PermissionModule (APPROVE on SCHOOL_CONTRACTS), PermissionEligibility, PermissionMatrixService
             (seed), NavigationCatalog, AccessModelService (no change expected, scope already ASSIGNED for Manager)
backend/src/main/java/com/hls/audit/support/AuditVisibility.java (CONTRACT, MOU_DOCUMENT, MOU_APPROVAL -> SCHOOL_CONTRACTS)
backend/src/main/resources/db/migration/V100__create_mou_approval.sql
frontend/src/features/schoolbilling/ MouApprovalPanel, MouDocumentList, UploadMouDocument, ApprovalStatusChip, ApprovalHistory,
             DecisionDialogs, AmendTermsDialog, MouApprovalsPage, mouApprovalApi.ts, tests; ContractsListPage and
             SchoolContractPage extended; App.tsx route /operations/school-contracts/approvals
postman/ (collection additions)   docs/spec-roadmap.md (row 031), constitution row and version
```

**Structure Decision**: one module extended, no new module, no new frontend feature folder; mobile is out of scope (spec). The
only change to existing behaviour is the DRAFT row created with each MoU, one additive column group on two DTOs, one relaxed
trigger (salary only, pending only) and the new `APPROVE` action.

## Design Decisions

See [research.md](research.md). Highlights: approval is a separate row keyed by contract, so the contract stays what 012 made it;
existing MoUs are backfilled as `APPROVED_LEGACY`; "approved without document" and "approved (before approval step)" are
derived labels, not extra stored states; each upload is a new immutable `FileStore` file plus a `mou_document` row with a version
number, the current document is the highest version not removed; replacing the document of an APPROVED MoU resets it to PENDING
in the same transaction; the decision runs under a row lock with a version check; self approval is allowed and audited; the
public interface is a new `MouApprovals` interface (not a change to `ContractView`) so existing consumers do not break.

## Phases

1. Migration, entities, status derivation, DRAFT row on create and record, permission action, eligibility, seeds, navigation, audit mapping (foundation).
2. Documents: upload, versions, download, remove with reason, file policy, scope (US1).
3. Approval: submit, approve, reject, resubmit, row lock, history, notifications, term amendment (US2).
4. Pending list, list columns and filters, allowed actions (US3).
5. `MouApprovals` public interface and its tests (US4).
6. Frontend, Postman, demo data, regression test of SC-006, roadmap and constitution rows.

## Open Items (need a decision, not guessed)

The user accepted the plan defaults for the former items 1 to 4 (recorded in spec.md, Clarifications, "plan defaults accepted"):
the spec scenario was corrected, removing a document keeps an approved MoU approved, the approver edits signed and end dates (start
date only under the gapless-chain rule), and a document never changes a legacy MoU's status. Remaining:

1. **Self approval differs from 015 and 027.** Intentional (clarified, D5). Recorded so a reviewer does not "fix" it to match.
2. **Migration number.** V100 is a choice inside the free range above V90; re-check at implementation (task T001 and the final
   merge task).
