# Feature Specification: Manager Incentives

**Feature Branch**: `spec/030-manager-incentives`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "030-manager-incentives: A Manager earns Rs 5,000 for each School they closed. The incentive is created when the School's signed MoU is recorded and approved (decision D9, spec 031), goes to the owner of the prospect that became the School (spec 023), is approved by the Director or the School's Zone Manager (decision D5; a Manager may approve an incentive they earned, and the audit trail records who approved), and is paid by being added to that month's Manager payroll (spec 013b). Full input: docs/spec-inputs/030-manager-incentives.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Incentive is earned when the MoU is approved (Priority: P1)

When the signed MoU of a School that came from a won prospect becomes approved (spec 031), the system records one incentive of Rs 5,000 for the Manager who owned that prospect (spec 023). It starts as "pending approval". Nobody types it in.

**Why this priority**: This is the business rule (D9). Without it no Manager is rewarded and no later step exists.

**Independent Test**: Win a prospect owned by a Manager, record its MoU, approve the MoU, and see exactly one pending incentive for that Manager and School with the MoU approval date as the closure date.

**Acceptance Scenarios**:

1. **Given** a School won from a prospect owned by a Manager, **When** its MoU is approved, **Then** one incentive of the configured amount exists for that Manager and School, status pending approval, closure date the MoU approval date.
2. **Given** a School whose MoU is only pending or rejected, **When** anyone looks at incentives, **Then** none exists for it.
3. **Given** a School that already has an incentive, **When** its MoU is re-approved (after a document change) or a later MoU of the same School is approved, **Then** no second incentive is created.
4. **Given** a MoU approved for a School with no won prospect or no owner, **When** the Director opens the incentive list, **Then** the School appears under "no earner" with the reason and no incentive is created.
5. **Given** a MoU that was already approved before this feature goes live, **When** the feature starts, **Then** no incentive is created for it (see Assumptions).

---

### User Story 2 - Director or Zone Manager approves the incentive (Priority: P1)

The Director, or the Zone Manager of the School's Zone, reviews a pending incentive (earner, School, MoU, approval date) and approves it or rejects it with a reason. A Manager who earned it may approve it; the screen marks it "approved by the earner" and the audit trail records both the earner and the approver. An Admin cannot approve.

**Why this priority**: D5 makes approval the control over money paid out.

**Independent Test**: As the School's Zone Manager approve one incentive; as the Director reject another with a reason; as the earning Manager (who is the Zone Manager) approve their own; as Admin try and be refused; check the audit entries.

**Acceptance Scenarios**:

1. **Given** a pending incentive, **When** the Director approves it, **Then** it becomes approved and the approver and time are kept.
2. **Given** a pending incentive of a School in the Zone Manager's Zone, **When** that Zone Manager approves it, **Then** it becomes approved; an incentive of another Zone is not visible or decidable by them.
3. **Given** a Manager who earned an incentive and is also the School's Zone Manager, **When** they approve it, **Then** it is approved, shown as approved by the earner, and the audit record names earner and approver.
4. **Given** an Admin, a Teacher or System, **When** they try to approve or reject, **Then** it is refused.
5. **Given** a pending incentive, **When** it is rejected without a reason, **Then** it is refused; with a reason it becomes rejected and the reason is shown to the earner.
6. **Given** two people deciding the same incentive at once, **Then** exactly one decision is recorded.

---

### User Story 3 - Approved incentive is paid in that month's Manager payroll (Priority: P1)

An approved incentive is offered to payroll (spec 013b) as an incentive adjustment for the Manager. The next pay run that includes that Manager shows it as its own line, adds it to the net pay and, when the Director approves the run, marks the incentive "paid in run" for that month. The actual payment status stays in payroll (decision D8).

**Why this priority**: Paying through payroll is the point of the feature (D9, roadmap).

**Independent Test**: Approve an incentive, start the month's run, see the line and the net pay, approve the run, and see the incentive marked as in that run.

**Acceptance Scenarios**:

1. **Given** an approved incentive, **When** payroll computes a run that includes the Manager, **Then** the run shows an incentive line for the amount and the net pay includes it.
2. **Given** the run is approved, **Then** the incentive shows as paid in that run and month and can no longer change.
3. **Given** the Manager cannot be paid in the run, **Then** the incentive stays waiting and a later run takes it.
4. **Given** an approved incentive held by a run still awaiting approval, **When** it is withdrawn, **Then** the system refuses until the run is returned; before a run holds it, withdrawal with a reason succeeds and nothing is paid.
5. **Given** the same incentive offered twice, **Then** payroll holds it once.

---

### User Story 4 - Lists, totals and notifications (Priority: P2)

The Director and Admin see all incentives; a Zone Manager sees those of their Zone's Schools; a Manager sees their own (earned, approved, in payroll, paid). Filters by status, Manager, Zone and month; totals per Manager and month; CSV export. Approvers are notified when one is pending, the earner when it is approved or rejected, and the Director approvals inbox lists pending incentives.

**Why this priority**: Makes the flow usable and visible, but earning, approval and payment work without it.

**Independent Test**: Create incentives in two Zones; check each role sees exactly its scope; export the CSV and match the totals.

**Acceptance Scenarios**:

1. **Given** incentives across Zones, **When** a Zone Manager opens the list, **Then** only their Zone's incentives appear, with no leakage by search or direct link.
2. **Given** a Manager, **When** they open their incentives, **Then** only their own appear with status and the month paid.
3. **Given** a new pending incentive, **Then** the School's Zone Manager and the Director are notified once; on a decision the earner is notified.
4. **Given** a filtered list, **When** exported, **Then** the file has the same rows and totals.

---

### User Story 5 - MoU un-approved or paid in error (Priority: P3)

If the MoU stops counting as approved (rejected, or sent back to pending by a document replacement) before payroll applies the incentive, the incentive is held and not paid until the MoU is approved again. After a run has applied it, nothing is taken back automatically; the Admin uses a Correction adjustment in payroll.

**Why this priority**: Protects against paying for an unsigned School; rare.

**Independent Test**: Approve an incentive, send its MoU back to pending, start the run: the incentive is not taken; re-approve the MoU and it is.

**Acceptance Scenarios**:

1. **Given** an approved incentive not yet applied, **When** its MoU no longer counts as approved, **Then** the incentive is held ("MoU not approved") and not taken by a run.
2. **Given** a held incentive, **When** the MoU is approved again, **Then** it returns to its earlier state without a second incentive.
3. **Given** an incentive already paid in an approved run, **When** the MoU later changes, **Then** the incentive does not change.

---

### Edge Cases

- The earner has left, is not a Manager any more, or has no pay record: the incentive stays approved and waiting; payroll lists the person as unable to be paid with the reason.
- The prospect's owner is a Director, Zone Manager or marketing user who is not a payroll Manager: no incentive is created and the School is listed under "no earner" (default; see Assumptions).
- The owner of the prospect is changed after the win: the owner when the MoU is approved earns it; later changes do not move an existing incentive.
- A School with no Zone Manager: only the Director can approve.
- The Zone Manager of the Zone changes while an incentive is pending: the new one sees it, the old one no longer does.
- The configured amount changes: existing incentives keep the amount they were created with; only new ones use the new amount.
- The same MoU-approved event is delivered twice: still one incentive per School.
- Dates show as DD/MM/YYYY; amounts in Rupees.

## Requirements *(mandatory)*

### Functional Requirements

**Earning**

- **FR-001**: When the MoU in effect for a School becomes approved (any state that counts as approved in spec 031), the system MUST create one incentive for the owner of the prospect that became that School (spec 023), with Manager, School, MoU, closure date (the MoU approval date), amount and status pending approval.
- **FR-002**: There MUST be at most one incentive per School for all time. Re-approval, a later MoU, or a duplicate event MUST NOT create another.
- **FR-003**: The amount MUST come from a setting (default Rs 5,000, spec 011) and be frozen on the incentive when it is created.
- **FR-004**: A School whose MoU is approved but that has no won prospect, no owner, or an owner who is not a payroll Manager MUST create no incentive and MUST appear in a "no earner" list with the reason, visible to Director and Admin.
- **FR-005**: Earning MUST be automatic; no user can create, edit the amount of, or delete an incentive by hand.

**Approval**

- **FR-006**: Only the Director, or the Zone Manager of the School's Zone, MAY approve or reject an incentive (D5). Admin, Teacher and System MUST NOT. A School with no Zone Manager MUST be decided by the Director only.
- **FR-007**: A Manager MAY approve an incentive they earned (D5) when they are otherwise permitted to (the Zone Manager of that School's Zone); the incentive MUST then be marked as approved by the earner, and the audit record MUST name both earner and approver.
- **FR-008**: Rejection MUST require a reason (at most 500 characters) that is kept and shown to the earner. A decision MUST be taken once; concurrent decisions MUST result in exactly one recorded decision.

**Payment through payroll**

- **FR-009**: Approving an incentive MUST offer it to payroll (spec 013b) as an incentive adjustment for the earner for the incentive's amount, exactly once per incentive.
- **FR-010**: The next pay run that includes the earner MUST show the incentive as its own line and include it in the net pay; the approval of that run MUST mark the incentive paid in that run and month. A person who cannot be paid in a run leaves the incentive waiting.
- **FR-011**: An approved incentive that is withdrawn MUST be released from payroll with a reason only when no run awaiting approval holds it; once applied by an approved run it MUST NOT change. Actual payment status (paid, date, mode, reference) stays in payroll (D8).
- **FR-012**: An approved but not yet applied incentive whose MoU no longer counts as approved MUST be held and not taken by a run until the MoU counts as approved again.

**Views and integration**

- **FR-013**: The system MUST provide a list of incentives filtered by status, Manager, Zone and month, with totals per Manager and month and a CSV export, for Director and Admin (view), Zone Managers (own Zone, and approval) and Managers (own only).
- **FR-014**: Every creation, approval, rejection, hold, release and application MUST be written to the audit store (spec 003) with actor, role, time and prior and new values.
- **FR-015**: Approvers MUST be notified when an incentive is pending, and the earner when it is approved or rejected (spec 010). Pending incentives MUST appear in the Director approvals inbox, and 030 MUST supply its tiles and counts to the management dashboard (spec 028).
- **FR-016**: Every list, detail, search and export MUST be filtered by the caller's scope with no leakage between Zone Managers or between Managers (Constitution Principle III). Menu items and actions MUST be offered only when the server's access model grants them.
- **FR-017**: Screens MUST meet WCAG 2.2 AA, have loading, empty and error states, and work at phone width.

### Key Entities *(include if feature involves data)*

- **Incentive**: one per School: earner (Manager), School, MoU, closure date, amount, status (pending approval, approved, rejected, held, paid in run), approver, decision time, rejection reason, "approved by the earner" flag, payroll run and month once applied.
- **No-earner record**: a School with an approved MoU for which no incentive could be created, with the reason.
- **Incentive history**: the audited sequence of earning, decisions, holds, releases and payroll application for one incentive.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Incentives | View, Export | Org-wide |
| Director | OPERATIONS → Incentives (and approvals inbox) | View, Approve, Export | Org-wide |
| Manager  | OPERATIONS → Incentives (Zone Manager: Zone's Schools; any Manager: own) | View own; a Zone Manager approves within own Zone, including one they earned | Assigned (Zone) and Own |
| Teacher  | none | none | None |
| System   | none | none | None |

**New permission keys**: `MANAGER_INCENTIVES` with actions `VIEW`, `APPROVE`, `EXPORT`. Seeded as in the table; editable at runtime like other permissions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every School whose MoU is approved and whose prospect has a Manager owner has exactly one incentive, with no manual step; proved by tests including repeated and duplicate approvals.
- **SC-002**: No incentive reaches approved without a decision by the Director or the School's Zone Manager; proved by a test for every role.
- **SC-003**: An approved incentive appears as one line in the next run that includes the Manager and is counted in the net pay once; no incentive is ever paid twice, in any sequence of recompute, return and approval of runs.
- **SC-004**: A Zone Manager sees, opens and decides on none of another Zone's incentives; a Manager sees only their own; proved by a test for every endpoint including export.
- **SC-005**: Every earning, decision, hold, release and payroll application has an audit entry with actor and prior and new values, and a self-approved incentive is identifiable from the record alone.
- **SC-006**: A Director can approve a pending incentive in under 1 minute from the approvals inbox.
- **SC-007**: A run approved before this feature is unchanged by it; the pay of Teachers and of Managers with no incentive is identical before and after.

## Assumptions

Defaults used where the sources are silent. Each open choice is listed with options in `clarification-questions.md`; these defaults stand until the user answers.

- Specs 012, 023, 031 and 013b are merged. 030 reads MoU approval through the `MouApprovals` interface and the `MouDecided` event (031), the owner through `ProspectOwners` (023), and pays through payroll's adjustment mechanism with the kind Incentive that 013b reserved. Opening that kind to this spec needs a small additive amendment to 013b (an intake like the one spec 015 added for reimbursements); no existing pay behaviour changes.
- Closure date means the date the MoU was approved. The incentive is earned once per School, ever.
- The amount is one fixed setting for all Schools (default Rs 5,000); no exceptions, slabs, shared credit between two people, or part amounts.
- Only the owner of the prospect earns, and only a Manager who is paid through payroll. Otherwise the School is listed as "no earner" and no incentive is made; the Director can see why.
- The payroll month is the month of the run that takes it: the next run that includes the Manager after the incentive is approved (the rule 013b already uses for waiting adjustments).
- MoUs approved before this feature goes live create no incentive.
- If the MoU stops counting as approved before payroll applies the incentive, it is held; after payment it is never taken back automatically (a Correction adjustment is used instead).
- Approval needs one decision (Director or Zone Manager), no amount limit and no second step.
- Mobile screens, targets or slab schemes, tax, clawback rules, payment outside payroll, and changing the MoU approval or the pay run beyond opening the Incentive kind are out of scope.
- The constitution's default role access matrix gets its row update when this spec merges, as with 012 and 031.
