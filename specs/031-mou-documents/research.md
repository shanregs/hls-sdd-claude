# Research: MoU Documents and Approval

Decisions taken while planning, with what was read in the code. Format: Decision, Rationale, Alternatives.

## 1. What the code offers today, and where it differs from the planning documents

Read on `main` at b00063e (specs 012, 015, 023, 025, 026, 027, 028, 035 merged).

- **The MoU is `schoolbilling.internal.Contract`** (table `contract`): state RATE_PENDING ("MoU pending" placeholder), ACTIVE,
  CANCELLED; `salary_mode`, `teacher_count`, `rate`, `signed_on`, `starts_on`, `ends_on`, `@Version`, `created_by`. Positions
  (`contract_position`, with `salary`) and signatories (`contract_signatory`) are child rows. `ContractService.create` inserts an
  ACTIVE contract at once (it ends the covering one the day before); `recordMou` turns a RATE_PENDING one into ACTIVE. There is
  no "submit" or "draft" in 012: a recorded MoU is live immediately.
- **Public read interface** `schoolbilling.api.SchoolContracts` (`contractOf`, `positionOf`, `contractsOverlapping`, `occupancyOf`,
  `occupancyOfAll`, `unmappedTeachers`) and `ContractView`, a record. Used today by 023 (`ContractStatusReader`,
  `DashboardService`, `WinService`), 027 (`MovementQueries`) and the permission seeder; not yet by 022 or 030.
- **Shared file storage** is the `files` module: `files.api.FileStore` (`store`, `find`, `list`, `open`, `remove`), table
  `stored_file` (V23), owner given as `(ownerType, ownerId)`, 10 MB cap, bytes kept on removal. 023 uses it through
  `AttachmentService` with its own link table `ActivityAttachment`, the shape this spec copies.
- **Approval pattern.** There is no shared approval class. `expenses.internal.DecisionService` (015) and the 027 transfer
  service each implement the same convention privately: status row, pessimistic `lockById`, `StaleVersion.check`, a history
  table, a server-sent allowed-actions list (`AllowedActions`), an event for `notification`. Both **refuse** a decision by the
  person who raised the item.
- **Permissions.** `PermissionModule.SCHOOL_CONTRACTS(VIEW, CREATE, EDIT)`; `PermissionEligibility` restricts it to Admin,
  Director, Manager; `PermissionMatrixService.seedSchoolContracts` seeds Admin and Director View/Create/Edit and Manager View.
  Seeding is "insert if missing", so a new action is seeded on existing databases by the same method. Data scope in
  `AccessModelService`: Admin and Director org-wide, Manager assigned. Navigation: one item "School Contracts"
  (`/operations/school-contracts`, requires View).
- **Notifications.** `NotificationType` is an enum, the column is `VARCHAR(60)` since V50, one listener class per source module
  (`TransferEventListener` is the model), texts in `MessageFactory`, recipients through `RecipientResolver`
  (`activeDirectors`, `activeAdminsAndDirectors`, `teacherUser`) and `ManagerQueries.managerOfSchool`.
  `MOU_NOT_RECORDED` already exists for 023 ("won prospect, no MoU yet") and is unrelated.
- **Audit.** `ChangeRecorder.record(actor, entityType, id, field, before, after)` and `recordLifecycle`. `AuditVisibility`
  maps entity types to the module whose View is needed to see them.
- **Frontend.** `features/schoolbilling`: `ContractsListPage`, `SchoolContractPage`, `MouFormDialog`, `ContractStatusChip`,
  `schoolContractsApi.ts`; routes `/operations/school-contracts` and `/operations/school-contracts/schools/:schoolId`.
  Uploads use `FormData` through the auth fetch (`marketingApi.uploadAttachment`).
- **Migrations on main**: V1 to V53, V60 (026), V70 (035), V80 (027), V90 (015). **Next free: V100**, used here. The earlier
  specs left V54 to V59 empty; this spec does not use them.

### Gaps and conflicts with merged code (each has a decision below)

1. **File types.** `FileTypePolicy` allows jpg, png, pdf, docx, xlsx and txt; this spec allows pdf, jpg and png only. `FileStore`
   has no per-call type list. Decision 4.
2. **Version history.** `FileStore.list` returns only files that are not removed and has no version number; removal details are
   not exposed. Decision 3.
3. **Contract rows are immutable by convention and the positions by trigger.** `trg_contract_position_final` refuses any UPDATE of
   a position, so the FR-012 amount edit cannot be written. `Contract` has no trigger, only the Javadoc rule "never edited".
   Decision 8.
4. **Self decision.** 015 (FR-015) and 027 forbid deciding your own item; this spec, following D5 and its clarification,
   allows a Director to approve a MoU they submitted. Intentional; not copied from 015.
5. **No shared approval code.** The spec says it "reuses the approval pattern (C3)". There is nothing to import; the plan reuses
   the convention (lock, version, history, allowed actions, event) and builds it in `schoolbilling`. A shared library is not
   extracted here (three specs differ in who may decide and in side effects).
6. **Audit visibility hole.** `AuditVisibility.REQUIRED_VIEW` has no entry for `CONTRACT` (012) or `ACTIVITY_ATTACHMENT` (023), so
   those entity types are not hidden from a role without the module's View, including System, which must not see School
   business data. This spec maps `CONTRACT`, `MOU_DOCUMENT` and `MOU_APPROVAL` to `SCHOOL_CONTRACTS`; the 023 entry is noted for
   whoever owns 023.
7. **Spec text inconsistency.** The clarifications make the document optional (and add "approved without document"), but User
   Story 2 scenario 2 and the story intro still say a MoU without a document is refused at submit. The plan follows the
   clarifications; spec.md should be corrected. The task brief for this plan listed "document mandatory before submit" and "wrong
   terms means reject and record a new MoU" as open defaults; the spec has since decided otherwise (optional document, FR-012
   approver edit), and the plan follows the spec.
8. **012 and the approval step.** A MoU recorded by 012 is live at once for billing, payroll and mapping. That stays (FR-013).
   Only the new state is reported; nothing in 012 waits for it.
9. **The `Contract` and `ContractController` Javadoc** say a Zone Manager "only reads". After this spec a Manager
   holds `APPROVE` (approve, reject, amend terms of pending MoUs). Javadoc to be updated.
10. **`PermissionEligibility` lets a Manager be granted `CREATE` and `EDIT` on School Contracts** (default seeds are View only).
    A Director could therefore grant a Manager upload rights; the scope check on every call still limits them to their Schools.
    Not changed here.

## 2. Where the state lives: a `mou_approval` row beside the contract

**Decision**: a new table `mou_approval`, one row per contract that has real terms, holding the stored status, submission and
decision facts and the version. The `contract` table gains no column. The row is created (status DRAFT) in the same transaction
as `ContractService.create` and `recordMou`.
**Rationale**: the spec says 012 stays the only record of the terms and creation is unchanged; a separate row keeps the
contract's optimistic `version` from moving on every document upload, and the approval can be locked on its own for the
decision. The row is in the same module, so a foreign key to `contract` is allowed.
**Alternatives**: columns on `contract` (every upload would bump the contract version and invalidate 012's own forms; mixes
approval into a row declared final); derive everything from the event table (no row to lock, hard to guarantee one decision).

## 3. Documents: `FileStore` for bytes, `mou_document` for versions

**Decision**: each upload calls `FileStore.store(actor, "MOU_DOCUMENT", contractId, name, bytes)` and inserts a `mou_document`
row with `version_no` (1, 2, 3 per contract), the file id, and a copy of name, content type and size. The current document is
the highest `version_no` with no removal. Removal calls `FileStore.remove` (bytes kept) and sets the removal columns on the
`mou_document` row (reason, who, when), which stays listed in the history as "removed". Rows are never deleted.
**Rationale**: `FileStore` hides removed files, so the module needs its own record to show "removed on date by X because Y" and
to keep numbering stable; copying name, type and size keeps the history readable without the store. Same shape as 023's link
table.
**Alternatives**: rely on `FileStore.list` order (loses removed versions and numbering); store bytes in the module (the spec
and C7 say reuse the shared store).

Download goes through the module's controller: it loads the `mou_document` row, checks the contract is in scope and the
caller's `VIEW`, then `FileStore.open(fileId)`. The file id alone never works on its own (404 when out of scope).

## 4. File policy: pdf, jpg, png, 10 MB

**Decision**: `MouDocumentPolicy` checks the extension is pdf, jpg, jpeg or png before calling `FileStore.store`, which then
verifies the first bytes match and the 10 MB cap (and refuses empty files). Messages: "Only PDF, JPG or PNG files can be
uploaded." plus the store's own. The 10 MB limit equals `FileStore.MAX_BYTES`, so there is nothing to configure.
**Rationale**: `FileStore` is 023's code; changing its signature to take a type list touches a merged module for one caller.
Checking the extension first, then letting the store check content, gives the same guarantee (a docx is refused by extension;
a renamed program by content).
**Alternatives**: add an overload `store(..., Set<String> allowedExtensions)` to `FileStore` (cleaner, but changes 023; can be done
later without changing this spec's behaviour).

## 5. Statuses and derived labels

**Decision**: stored status on `mou_approval`: `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`, `APPROVED_LEGACY`. Shown state
(`approvalState` in the API and in `MouApprovalState`):

| Condition | Shown |
| --- | --- |
| contract RATE_PENDING (no row) | `NOT_APPLICABLE` ("MoU pending") |
| contract CANCELLED | `CANCELLED` |
| `APPROVED_LEGACY` | `APPROVED_BEFORE_APPROVAL_STEP` |
| `APPROVED` and a current document | `APPROVED` |
| `APPROVED` and no current document | `APPROVED_WITHOUT_DOCUMENT` |
| `DRAFT`, `PENDING`, `REJECTED` | same name |

The document state is separate: `NONE` or `ATTACHED` (with the current version number). "Approved without document" is derived,
not stored, so removing a document or approving one that never had a document needs no state change.
**Rationale**: FR-006 lists five states; two of them are views of `APPROVED` plus the document; storing them would let the two
facts drift. `APPROVED_LEGACY` is stored because it is a different fact (decided by no one) that must survive later edits.
**Alternatives**: store all five (drift); treat legacy as plain APPROVED with a null approver (cannot be told from a data gap).

## 6. Lifecycle and transitions

```
(create / recordMou)            -> DRAFT
DRAFT    --submit-->               PENDING     (round 1)
PENDING  --approve (D or ZM)-->    APPROVED
PENDING  --reject  (D or ZM)-->    REJECTED    (reason required)
REJECTED --submit-->               PENDING     (round + 1, RESUBMITTED)
APPROVED --new document version--> PENDING     (round + 1, RESET_TO_PENDING; submitter = uploader)
migration backfill              -> APPROVED_LEGACY (never changes)
```

- Submit needs real terms (not RATE_PENDING) and not CANCELLED (FR-007); it needs **no** document (decision 1.7).
- A new document version on DRAFT, PENDING or REJECTED leaves the status as it is (history entry only); on APPROVED it resets to
  PENDING and notifies the approvers as a submission; on APPROVED_LEGACY it changes nothing (open item 4).
- Removing a document never changes the status (open item 2).
- A cancelled contract can be neither submitted nor decided; its documents stay readable (spec edge case).
- A MoU that is ended or replaced keeps its approval row; the next MoU has its own (spec edge case).
- A pending MoU whose contract is cancelled or ended meanwhile: the decision is refused when the contract is CANCELLED
  (409 "this MoU was cancelled"); an ended one can still be decided.

## 7. Who may decide, self approval and concurrency

**Decision**: `APPROVE` on `SCHOOL_CONTRACTS`, eligible for Director and Manager only, seeded for both. On approve, reject and
amend the server checks in order: grant; the contract exists and is in the caller's scope (a Manager outside the School's
scope gets 404, so an unrelated Zone Manager cannot tell it exists); the contract is not CANCELLED; the lock then the status is
PENDING (otherwise 409 with who decided and when, copied from `DecisionService.alreadyDecided`); `version` matches
(`StaleVersion.check`). There is **no requester check**: a Director may approve what they submitted. The history stores the
same user as submitter and decider and the audit record is flagged `selfDecided`.
**Rationale**: D5 and the clarification. The Zone Manager is "the Zone Manager of the MoU's School" which the scope rule gives
(`ScopeQueries.scopeOf(...).allowsSchool`, the same test 012 uses in `contractInScope`); a Zone Manager change while pending
therefore moves visibility with no migration (spec edge case). A School with no Zone Manager has no Manager whose scope
covers it, so only the Director can approve (spec edge case).
**Alternatives**: compare the caller with `managerOfSchool(...).userId` (stricter, but 012 and 027 use scope, and two sources
could disagree).

## 8. FR-012: the approver corrects commercial terms

**Decision**: `PATCH /api/v1/school-contracts/contracts/{id}/terms` (grant `APPROVE`, same scope and lock as a decision, only
while PENDING). Editable: `rate` (SAME_FOR_ALL), the `salary` of each position (PER_TEACHER; for SAME_FOR_ALL the rate edit
also sets every position's salary to the new rate), `signedOn`, `endsOn`, and `startsOn` under open item 3. Not editable:
signatories, `teacher_count`, `salary_mode`, titles, the School and the Teacher mapping. The same validations as
`ContractService.validate` apply (above zero, two decimals, at most 10,000,000, signed date not in the future). Each changed
field is written to Change History (`CONTRACT`, field, old, new) and one `TERMS_AMENDED` event with the list; the contract and the
approval versions both change, so a decider working from the old screen gets 409 and sees the new terms.
**Mechanism**: `ContractService` gets a narrow package-private method used only by `MouTermsService`, which asserts the approval
is PENDING. Migration V100 replaces the trigger function of `contract_position` so that an UPDATE is allowed only when only
`salary` changed **and** `mou_approval.status = 'PENDING'` for the contract; DELETE and every other column stay refused.
`contract_signatory` keeps its trigger.
**Rationale**: the spec makes this a deliberate exception and asks the plan to add a narrowly scoped path "kept unavailable
outside pending approval"; a trigger enforces it below the application as well as above it. Teacher count is left out because
changing it would add or remove positions and break mappings.
**Alternatives**: edit freely and rely on the service (the existing positions trigger would block it anyway); drop the trigger
(loses the guarantee for every other case); replace the contract with a new row (loses the pending approval, which the spec
rules out for this path, and 012's create ends the previous MoU).

Start-date rule (open item 3): `startsOn` may change only if no earlier contract of the School ends on the day before the old
start (otherwise a gap or an overlap would appear, and the exclusion constraint `ex_contract_school_dates` would catch only the
overlap) and no assignment on the contract's positions starts before the new date. If either fails the answer is a 409 saying
to reject the MoU and record a new one.

## 9. Notifications

**Decision**: two events in `schoolbilling.api`, `MouSubmitted(contractId, schoolId, submittedBy, resubmitted)` and
`MouDecided(contractId, schoolId, outcome, reason, submittedBy, decidedBy, termsAmended)`, and one listener in `notification`
(`MouEventListener`) with types `MOU_SUBMITTED` and `MOU_DECIDED` (the V50 column is wide enough). Recipients: on submission, the
active Directors and the School's Zone Manager (if active), minus the submitter; on decision, the submitter plus every active
Admin, minus the decider. Admin is not told of a submission (clarification). Published in the publisher's transaction, as in
`TransferEventListener`.
**Rationale**: follows the existing pattern; approvers are resolved at event time so a changed Zone Manager gets the notice.
**Alternatives**: resolve approvers through the matrix like `expenseReviewers` (the Zone Manager is a per-School person, not a
role set).

## 10. Public interface for 022, 030 and 023

**Decision**: a new `schoolbilling.api.MouApprovals` with `stateOn(schoolId, date)` and `stateOfAll(schoolIds, date)` returning
`MouApprovalState` (contract id, shown state, `approvedOn` as an instant or null, `documentAttached`, `countsAsApproved`). The
`ContractView` record is **not** changed.
**Rationale**: adding record components would break every caller that constructs `ContractView` (tests and 023 fixtures), and
consumers then take a second read for something that changes on a different cadence. A separate interface keeps `SchoolContracts`
stable and lets 022 and 030 read only what they need. `countsAsApproved` is true for APPROVED, APPROVED_WITHOUT_DOCUMENT and
APPROVED_BEFORE_APPROVAL_STEP so consumers do not repeat the mapping, but each consumer still decides what it does with it.
**Alternatives**: extend `ContractView` (breaking); events only (consumers need a point-in-time answer).

The MoU "in effect" on a date is the same one `SchoolContracts.contractOf` returns (live, dates include the date). When there is
none the answer state is `NO_MOU`.

## 11. List, detail and pending list

**Decision**: `ContractListRow` gains `approvalState`, `documentState` and `pendingSince`; `ContractDto` gains an `approval` block
(state, round, submitted and decided facts, document summary, `actions`, `approvalVersion`). `GET /api/v1/school-contracts` gains
`approval` (state filter) and `document` (`NONE`/`ATTACHED`). `ContractListService` loads all approval rows and current-document
flags for the page's contracts in two queries. A new `GET /api/v1/school-contracts/approvals/pending` returns PENDING MoUs in scope,
oldest first, for callers holding `APPROVE`.
**Rationale**: matches 012's single-batch list design; the pending list is the Director's and Zone Manager's work queue (US3).

## 12. Frontend

**Decision**: extend `features/schoolbilling`. `SchoolContractPage` shows, per contract, an approval chip, a documents panel
(versions, current marked, download, upload or replace, remove with reason) and an approval panel (history, submit, approve,
reject with reason, amend terms while pending). `ContractsListPage` gets two columns and two filters. A new page
`MouApprovalsPage` at `/operations/school-contracts/approvals` lists the pending MoUs. Downloads fetch the bytes with the auth
fetch and open a blob (the JWT is a header, so a plain link would not work). Buttons come from the server `actions` list. Phone
width and WCAG 2.2 AA: native file input with a visible label, status text not colour only, focus kept in dialogs.

## 13. Tests worth naming

SC-002 (every role tries every action), SC-003 (Zone Manager of another Zone against every endpoint including the download),
SC-005 (two concurrent approvals, one recorded, the other 409 with the decider named), SC-006 (mapping and attendance results
for a test month equal in DRAFT, PENDING, APPROVED, REJECTED and LEGACY), the V100 backfill (every ACTIVE contract becomes
legacy, RATE_PENDING none), the trigger (salary edit refused when not PENDING, any other position column refused), and the
file policy (docx, txt, renamed program, empty, 10 MB + 1 refused with nothing stored).

## 14. What is not decided here

Whether 022 blocks invoicing, or 030 waits for approval, on a given state: their own rules. MoU text generation, e-signature,
expiry reminders, annexures and mobile screens: out of scope (spec).
