# Feature Specification: MoU Documents and Approval

**Feature Branch**: `031-mou-documents`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "031-mou-documents: Signed MoU document upload and the MoU approval step. The signed MoU (the contract recorded in spec 012) gets its signed document (scan or PDF) attached, and the MoU is approved by the Director or the Zone Manager (decision D5) before it counts as approved for others (billing 022, incentives 030). Module `schoolbilling`. Depends on 012; reuses the shared file storage (C7, built in 023) and the approval pattern (C3, defined in 015). Sources: docs/spec-roadmap.md row 031, docs/spec-inputs/012-school-contracts.md, specs/012-school-contracts."

## Clarifications

### Session 2026-10-23

Decided in advance (roadmap decision D5 and the 012 spec); the rest are documented defaults listed under "Open questions".

- Q: Who approves a signed MoU? → A: The **Director or the Zone Manager** (D5). The audit trail records the approver. An Admin cannot approve.
- Q: Which Zone Manager? → A: The Zone Manager of the MoU's School (spec 005), read from there. A Zone Manager cannot approve a MoU of a School outside their Zone.
- Q: Does this change how a MoU is recorded? → A: No. Spec 012 stays the only record of the MoU terms, signatories and mapping and is not changed in how a MoU is created. This spec adds a document and an approval status to a MoU that exists.
- Q: Which spec owns file storage and the approval pattern? → A: File storage is the shared capability built in spec 023 (C7); the approval pattern (pending, approved, rejected with reason, resubmitted, audit entry) is defined in spec 015 (C3). This spec reuses both and builds neither.

### Session 2026-10-24

- Q: Is one approval enough, or must both the Director and the Zone Manager approve? → A: One is enough: the Director or the School's Zone Manager. No second step.
- Q: If the document is replaced after a MoU is approved, does the approval stay? → A: No. The MoU goes back to pending and needs approval again; it does not count as approved until then.
- Q: What happens to MoUs recorded in spec 012 before this approval step exists? → A: They count as approved but are labelled "approved (before approval step)"; a document can still be attached later.
- Q: Can a Director approve a MoU they recorded and submitted themselves? → A: Yes. It is allowed, and the audit trail records both actions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach the signed MoU document (Priority: P1) 🎯 MVP

After a MoU is recorded (spec 012), an Admin or Director uploads the signed document, a scan or PDF, to it. Anyone who may see the MoU can open or download the document. The document is kept as it was uploaded and is never overwritten; a wrong file is replaced by uploading a new version, and the earlier versions stay in the history.

**Why this priority**: The signed paper is the proof behind the contract. Without it stored, approval and billing rest on a record nobody can check.

**Independent Test**: Record a MoU, upload a PDF, see it on the MoU with who added it and when, download it as another permitted user, upload a corrected version, and see both versions with the latest shown as current.

**Acceptance Scenarios**:

1. **Given** a MoU without a document, **When** an Admin uploads a PDF, **Then** the document shows on the MoU with file name, type, size, who added it and when, and the MoU shows "document attached".
2. **Given** a MoU with a document, **When** an Admin uploads another file, **Then** it becomes the current version, the earlier file stays listed as an earlier version, and nothing is overwritten.
3. **Given** a file type or size that is not allowed, **When** it is uploaded, **Then** it is refused with a clear reason and nothing is stored.
4. **Given** a Zone Manager of the School, **When** they open the MoU, **Then** they can view and download the document but not upload, replace or remove it.
5. **Given** a Zone Manager of another Zone, **When** they ask for the document by any route, **Then** it is refused as not found.

---

### User Story 2 - Approve or reject a MoU (Priority: P1)

A MoU that has its signed document is submitted for approval. The Director or the School's Zone Manager reviews the terms and the document and approves it, or rejects it with a reason. A rejected MoU goes back to the Admin or Director who recorded it; they correct the document (or, when the terms are wrong, record a new MoU under spec 012) and resubmit it. Every step is audited with the approver.

**Why this priority**: D5 makes approval the control over contracts that billing and incentives rely on.

**Independent Test**: Submit a MoU with a document; as its Zone Manager reject with a reason; as Admin upload a corrected document and resubmit; as the Director approve; see the status, approver and times on the MoU and in the audit trail.

**Acceptance Scenarios**:

1. **Given** a MoU with a current document, **When** an Admin or Director submits it, **Then** its status is "pending approval" and the approvers can see it in their pending list.
2. **Given** a MoU with no document, **When** it is submitted, **Then** it is refused with "attach the signed document first".
3. **Given** a pending MoU, **When** the Director or the School's Zone Manager approves it, **Then** it is "approved" with the approver and time recorded.
4. **Given** a pending MoU, **When** an approver rejects it, **Then** a reason is required, the status is "rejected", and the reason is shown to the person who submitted it.
5. **Given** a rejected MoU, **When** a new document version is uploaded and the MoU is resubmitted, **Then** the status returns to "pending approval" and the earlier rejection stays in the history.
6. **Given** an Admin, **When** they try to approve, **Then** the action is not offered and the server refuses it.
7. **Given** two approvers acting on the same pending MoU at once, **When** both submit, **Then** exactly one decision is recorded and the other is told it was already decided.
8. **Given** a Director who recorded and submitted a MoU, **When** the same Director approves it, **Then** the approval is allowed and the audit records both actions.

---

### User Story 3 - See what needs approval and which MoUs lack a document (Priority: P2)

The Director and each Zone Manager have a pending list of the MoUs waiting for them. The School Contracts list (spec 012) shows each MoU's approval status and document state, and can be filtered by status, so Admin and Director can chase MoUs with no document or a rejection.

**Why this priority**: It makes the approval step usable day to day, but the document and decision work without it.

**Independent Test**: With four MoUs (no document, pending, approved, rejected), see each in the list with its status, filter by status, and see a Zone Manager's pending list contain only their own Schools.

**Acceptance Scenarios**:

1. **Given** MoUs in all four states, **When** the list is filtered by a status, **Then** only those MoUs show, each with its document and approval state.
2. **Given** a Zone Manager, **When** they open the pending list, **Then** only pending MoUs of their own Schools are shown.
3. **Given** a MoU is submitted, approved or rejected, **When** the event happens, **Then** the relevant people are notified through the notification service (spec 010), see FR-016.

---

### User Story 4 - Others read the approval state (Priority: P2)

Billing (spec 022), incentives (spec 030) and the marketing pipeline (spec 023) can ask, for a School on a date, whether the MoU in effect is approved and has a document. This spec only reports the state; whether a consumer waits for approval is that spec's rule.

**Why this priority**: The roadmap says 030 depends on the approval step; the answer must be available through the public interface.

**Independent Test**: Call the interface for Schools with approved, pending, rejected, no-document and pre-existing MoUs and receive the right state for each.

**Acceptance Scenarios**:

1. **Given** an approved MoU in effect on a date, **When** the interface is asked, **Then** it answers "approved" with the approval date.
2. **Given** a MoU in any other state, **When** asked, **Then** it answers that state and never "approved".
3. **Given** a MoU recorded before this spec went live, **When** asked, **Then** it answers "approved (before approval step)", see the assumption on existing MoUs.

---

### Edge Cases

- A MoU that is ended or replaced by a newer MoU keeps its document, approval status and history; the newer MoU starts with its own status.
- A "MoU pending" placeholder (spec 012: carried-over placements, no terms recorded) cannot take a document or be submitted until its terms are recorded.
- A cancelled MoU (spec 012) cannot be submitted or approved; its document stays readable.
- If the School's Zone Manager changes while a MoU is pending, the new Zone Manager sees it and the old one no longer does.
- A School with no Zone Manager: only the Director can approve.
- Upload fails half way, or the file is empty or unreadable: nothing is attached and the MoU keeps its earlier state.
- Replacing the document of an approved MoU: allowed for Admin and Director; the new version becomes current, the MoU goes back to pending and needs approval again, and until then it does not count as approved. The change is audited.
- Removing a document: only Admin or Director, with a reason; the removal is recorded in the audit history rather than disappearing silently.
- Dates show as DD/MM/YYYY.

## Requirements *(mandatory)*

### Functional Requirements

**Document**

- **FR-001**: The system MUST let an Admin or Director attach a signed document (PDF, or a scan as JPG or PNG) to a MoU recorded under spec 012, stored with file name, type, size, who added it and when, using the shared file storage (C7).
- **FR-002**: Uploading again MUST keep every earlier version; only the latest is "current". No stored version is ever changed. Uploading a new version to an approved MoU MUST return it to pending approval.
- **FR-003**: The system MUST refuse files of a type or size outside the allowed limits, empty files and unreadable files, with a clear reason. The limits are set in the plan, with a default of 10 MB per file and PDF, JPG and PNG only.
- **FR-004**: Anyone who may see the MoU (by scope) MUST be able to view and download its current document and its earlier versions. Nobody outside the scope MAY learn that the document exists.
- **FR-005**: Removing a document version MUST be limited to Admin and Director, MUST require a reason, and MUST be audited.

**Approval**

- **FR-006**: A MoU MUST carry an approval status: *no document*, *ready to submit* (has a document), *pending approval*, *approved* or *rejected*, following the shared approval pattern (C3).
- **FR-007**: An Admin or Director MUST be able to submit a MoU for approval only when it has a current document and real terms (not a "MoU pending" placeholder) and is not cancelled.
- **FR-008**: Only the Director, or the Zone Manager of the MoU's School, MAY approve or reject (D5). Admin, Teacher and System MUST NOT. Either permitted role's decision settles the MoU.
- **FR-009**: Rejection MUST require a reason. The reason, the approver and the time MUST be kept and shown to the submitter.
- **FR-010**: A rejected MoU MUST be resubmittable after the document is corrected; each submission and decision MUST stay in the MoU's approval history.
- **FR-011**: A decision MUST be taken once: concurrent decisions on the same submission MUST result in exactly one recorded decision.
- **FR-012**: Approval MUST NOT change the MoU's terms, signatories or mapping (spec 012 rule: a MoU is never edited). When the terms are wrong, a new MoU is recorded and goes through approval on its own.
- **FR-013**: Teacher mapping, attendance and the School Contracts list under spec 012 MUST keep working whatever the approval status is. This spec does not block them.

**Visibility and integration**

- **FR-014**: The School Contracts list and the MoU detail MUST show the document state and approval status, and the list MUST filter by them. The Director and each Zone Manager MUST have a pending-approval list limited to their scope.
- **FR-015**: Every upload, version replacement, removal, submission, approval, rejection and resubmission MUST be written to the audit store (spec 003) with actor, role, time, and prior and new values.
- **FR-016**: Submission and decisions MUST raise notification events (spec 010): to the approvers on submission, to the submitter on approval or rejection.
- **FR-017**: The `schoolbilling` public interface MUST answer, for a School on a date, the approval status of the MoU in effect, its approval date and whether a document is attached, without consumers reading this module's tables. Consumers (022, 030, 023) decide their own use of the answer.
- **FR-018**: Every list, search, detail and file download MUST be filtered by the caller's scope with no leakage between Zone Managers (Constitution Principle III). Menu items and actions MUST be offered only when the server's access model grants them.
- **FR-019**: Screens MUST meet WCAG 2.2 AA, have loading, empty and error states, and work at phone width.

### Key Entities *(include if feature involves data)*

- **MoU Document**: a file attached to a MoU: name, type, size, who added it, when, version number, current or earlier, removal record if removed. Never changed.
- **MoU Approval**: the approval state of one MoU: status, submitter and time, approver and time, rejection reason, and the history of earlier submissions and decisions. The MoU terms themselves stay in spec 012's Contract.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Contracts | View; upload, replace and remove document; submit for approval | Org-wide |
| Director | OPERATIONS → School Contracts (and pending approvals) | View; upload, replace, remove; submit; approve and reject | Org-wide |
| Manager (Zone Manager) | OPERATIONS → School Contracts (and pending approvals) | View and download; approve and reject | Assigned (own Zone's Schools) |
| Teacher  | none | none | None |
| System   | none (must not see school or teacher business data) | none | None |

**New permission keys**: in module `SCHOOL_CONTRACTS` (spec 012), a new action `APPROVE` (Director and Zone Manager). Document upload, replace and removal and submission reuse the existing `EDIT`; viewing documents uses `VIEW`. Seeded as in the table; not eligible for Teacher or System; runtime-editable in Role & Permissions. Only Admin, Director and System may edit the role-to-permission matrix; this spec does not change that. The Zone Manager's approval is limited to their own Zone's Schools by scope.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin can attach a signed document and submit a MoU for approval in under 2 minutes.
- **SC-002**: No MoU can reach "approved" without a current document and a decision by the Director or the School's Zone Manager; proved by tests for every role.
- **SC-003**: A Zone Manager sees, opens and decides on none of another Zone's MoU documents or approvals; proved by a test for every endpoint, including file download.
- **SC-004**: Every upload, removal, submission and decision has an audit entry with actor and prior and new values.
- **SC-005**: Two simultaneous decisions on one submission record exactly one decision.
- **SC-006**: Teacher mapping and attendance results for a test month are identical before and after this spec, whatever the approval states are.
- **SC-007**: Billing, incentives and marketing can read the approval state of any School's MoU in effect through the public interface alone.

## Assumptions

- Spec 012 (MoU contract, mapping, `schoolbilling` module) is in place; this spec adds to it without altering how a MoU is recorded.
- The shared file storage (C7, spec 023) and the approval pattern (C3, spec 015) exist when this spec is planned. If either is not yet merged, the plan builds only the part it needs, in the shared form the roadmap describes, not a private one.
- MoUs recorded before this spec goes live are treated as "approved (before approval step)" so nothing in billing or payroll stops; an Admin or Director may still attach a document to them.
- Approval is informational for others: this spec reports the state; deciding that billing (022) or incentives (030) wait for "approved" is the rule of those specs.
- One approval is enough (Director or Zone Manager, not both). There is no amount limit.
- A single current document per MoU, with earlier versions kept; multiple unrelated attachments (annexures) are not modelled.
- Mobile screens, e-signature, generation of MoU text from a template, and expiry or renewal reminders are out of scope.
- The constitution's default role access matrix gets its row update when this spec merges, as with 012.

## Open questions for the user (defaults used above; for `/speckit-clarify`)

1. **Does approval gate anything here?** Default: no. Mapping, attendance and listing continue regardless; billing (022) and incentives (030) decide for themselves. Alternative: block Teacher mapping until approved.
2. **Is a document mandatory for approval?** Default yes (cannot submit without one).
3. **File limits**: default PDF, JPG, PNG up to 10 MB each; confirm or change.
4. **Notifications**: default to notify approvers on submission and the submitter on decision; confirm whether the Admin should also be told.
5. **Wrong terms found during approval**: default is to reject and record a new MoU under 012 (no edits). Confirm there is no need to edit terms during approval.
