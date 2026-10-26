# 030 Manager Incentives: `/speckit-specify` input

2026-10-27. Roadmap row 030 (Priority 2; becomes core once payroll is built). Built from `docs/spec-roadmap.md` (row 030,
decisions D5, D9, D12, contracts C3 and C4), `docs/hls-speckit-specify-inputs.md`, `HLS-core-business-flow.md` §14
("Manager Incentive") and its open questions 12 and 13, and the merged specs 013b, 031, 023, 012 and 025.

Depends on 012 (the MoU), 023 (the prospect's owner), 031 (MoU approval) and 013b (the pay run). Module `incentive`
(new). Decisions D5, D9 and D12 apply.

## What the sources already settle

- **Amount (core flow §14, roadmap):** Rs 5,000 per confirmed/closed School. Open question 13 of the core flow ("exact
  Rs 5,000 rules and exceptions") is not answered by any document; the unanswered parts stay as clarification questions.
- **Who earns it (023, decided 2026-10-05):** the owner of the prospect that was won. 023 exposes
  `recruitment.api.ProspectOwners.ownerOfSchool(schoolId)`; 023 pays nothing (FR-022).
- **When it is earned (D9):** when the signed MoU is recorded and approved, not at Final Stage and not at payment.
- **MoU approval (031, merged):** `schoolbilling.api.MouApprovals.stateOn/stateOfAll` reports the state of the MoU in
  effect (`countsAsApproved` is true for APPROVED, APPROVED_WITHOUT_DOCUMENT and APPROVED_BEFORE_APPROVAL_STEP).
  `schoolbilling.api.MouDecided` (outcome APPROVED or REJECTED, contractId, schoolId, decidedBy, approvedOn via the state)
  is published when a MoU is decided. 031 only reports; "incentives wait for approved" is 030's rule. Re-approval after a
  document replacement sends a MoU back to pending, so approval can be seen more than once for one School.
- **Who approves the incentive (D5):** the Director or the Zone Manager. A Manager may approve an incentive they earned;
  the audit trail records the approver (so the approver and the earner may be the same person). An Admin cannot approve.
- **How it is paid (D9, 013b, C4):** added to that month's Manager payroll. 013b has adjustment kind `INCENTIVE`
  reserved ("spec 030"; today `AdjustmentService` refuses it as reserved). 015 added the intake pattern
  `payroll.api.ReimbursementIntake` (`offer`, `withdraw`, `status` per `sourceRef`, idempotent, attached to a draft at
  compute, applied at approval) and the event `payroll.api.PayrollAdjustmentApplied(sourceRef, kind, runId, month)`.
  030 needs the same for kind INCENTIVE (an additive amendment A to 013b; see Open items).
- **Pay-run rules to honour (013b):** an approved run is immutable; a waiting adjustment is taken by the next run that
  includes the person; an adjustment for a person who cannot be paid stays waiting; payment status is entered manually
  (D8); payslips show the adjustment line by kind.
- **Core flow §14 fields to track:** Manager, School, closure date, closure status, amount, eligibility, approval status,
  payment status. Workflow: School confirmed, MoU verified, incentive eligible, Rs 5,000, approved, paid.
- **Dashboard (028, roadmap):** the Director approvals inbox lists incentives; 028 tiles for 030 arrive with this spec by
  implementing the reporting SPI (approval and Manager-column SPI). 010 event: "030 incentive approved".

## Feature description (paste as the argument to `/speckit-specify`)

030-manager-incentives: A Manager earns Rs 5,000 for each School they closed. The incentive is created automatically
when the School's signed MoU is recorded and approved (decision D9, spec 031), goes to the owner of the prospect that
became the School (spec 023), is approved by the Director or the School's Zone Manager (decision D5; a Manager may
approve an incentive they earned, and the audit trail records who approved), and is paid by being added to that
month's Manager payroll as an incentive adjustment line (spec 013b). Module `incentive`; screens under OPERATIONS.

1. **Earning.** When the MoU of a School that came from a won prospect becomes approved (031), the system records one
   incentive of the configured amount (default Rs 5,000, a setting) for the prospect's owner: Manager, School, MoU,
   closure date (the MoU approval date), amount, status. One incentive per School, ever: re-approval of the same or a
   later MoU, or a re-opened School, does not create a second one. A School with no won prospect or no owner creates no
   incentive and is listed as "no earner" so the Director can see it. An owner who is not a Manager (for example a
   Director) earns nothing here unless clarified.
2. **Approval.** A pending incentive is approved by the Director or the Zone Manager of the School's Zone (an Admin
   cannot). A Manager who is the earner may approve their own; the audit trail records approver and earner, and the
   screen marks "self-approved". Rejection needs a reason. A Zone Manager cannot approve for a School outside their Zone.
3. **Payment through payroll.** Approval offers the incentive to payroll (013b adjustment kind INCENTIVE, idempotent by
   source reference). The next run that includes the Manager takes it as its own line; the run's approval marks it
   applied and the incentive becomes "paid in run for month X"; actual payment status stays in 013b (D8). If the run is
   not yet approved the incentive can be withdrawn with a reason; once applied it cannot change. "That month's payroll"
   is the month the incentive was approved in, as far as the 013b rules allow (see the open question on month).
4. **Lists and views.** OPERATIONS list for Director, Admin (read) and Zone Manager (own Zones): filter by status, Manager,
   Zone, month; a Manager sees their own incentives (earned, approved, in payroll, paid) in their own pages.
   Totals per Manager and month. CSV export.
5. **Reversal.** If the MoU is later rejected or its approval is withdrawn before the incentive is applied by a run, the
   incentive goes back to pending review (not paid). After it is paid, nothing is clawed back automatically; a Correction
   adjustment in 013b is how it is reversed.
6. **Notifications and dashboard.** Approvers are told of a pending incentive; the earner is told on approval or
   rejection (010). The Director approvals inbox (028) lists pending incentives; 028 tiles via the reporting SPI.

Out of scope: any incentive other than the per-School one (recruitment, targets, slabs), tax, clawback rules, paying
outside payroll, changing MoU approval (031) or the pay run beyond opening the INCENTIVE kind.

## Roles and access (to confirm in the spec's permission section)

New permission `MANAGER_INCENTIVES` (view, Admin and Director), approval for the Director and Zone Manager of the
School's Zone, Manager sees own. Audit through 003 for every create, approve, reject, withdraw. Permission matrix and
menu follow the access-model rules of 002 (constitution Principles II to IV).

## Interfaces consumed and offered

- Consumes: `schoolbilling.api.MouApprovals`, event `MouDecided`, `recruitment.api.ProspectOwners`, `school` (Zone and
  Zone Manager), `identity`/`teacher`-style directory for "is a Manager" (005a designations), `settings`, `audit`,
  `notification`.
- Needs from 013b: `payroll.api` intake for kind INCENTIVE (like `ReimbursementIntake`) and the existing
  `PayrollAdjustmentApplied` event (kind INCENTIVE). The RESERVED_BY refusal in `AdjustmentService` must keep refusing
  manual creation of kind Incentive or allow it only through the intake.
- Offers: a read interface of incentives for 028 (tiles, approvals inbox) and 014 (Manager productivity).

## Open items to clarify (do not guess)

1. What counts as the closure date and whether re-approval after a document change can create a second incentive.
2. Whether a prospect owner who is not a Manager (Director, Zone Manager, marketing user) earns.
3. Which payroll month: the month of MoU approval, or of incentive approval.
4. What happens when a MoU is un-approved or the earner has left or has no pay record.
5. Whether the amount is fixed per School or can differ (exceptions, shared credit when two people worked a School).
