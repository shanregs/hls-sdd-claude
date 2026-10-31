# Feature Specification: Substitute Payout Reconciliation

**Feature Branch**: `spec/017b-payout-reconciliation`

**Created**: 2026-11-01

**Status**: Draft. Specified on recommended defaults; open questions are in `clarification-questions.md` and every default is marked "pending owner confirmation" below.

**Input**: User description: "017b-payout-reconciliation: Each live substitute credit from spec 017a becomes a Substitute credit pay adjustment in the next pay run (013b) of the substitute, once per credit, and never inside an approved run. The pay of a substitute day comes from the substitute's own salary (013a). Admin and Director see, for every credit, where it stands (not offered, waiting, held by a draft run, in a run awaiting approval, paid in a run, voided before payment, voided after payment and needing a correction), and a list of mismatches between the days credited and the days paid, per Teacher, School and month. A daily job repairs missed events. A credit voided after payment is never taken back automatically; the Admin makes a Correction. Roadmap row 017 (017b), constitution Principle VI (substitution assignment and payout reconciliation), decisions D2, D7 and D8."

## Clarifications

### Session 2026-11-01 (assumptions pending owner confirmation)

Nobody was available to answer, so every question below is settled on its recommended default. Each is also written in
`clarification-questions.md` with the alternatives. If the owner picks another option, this spec, the plan and the tasks change as
that file says.

- Q1: How is the pay for one substitute day worked out? -> A (assumed, pending owner confirmation): the substitute's own daily value, the same value the pay run already uses for one day of that Teacher on that date (salary in effect that day divided by the month's working days, 013a), times the credit's day value (0.50 or 1.00), rounded as the pay run rounds. No new rate table.
- Q2: Is it extra pay or does it replace something? -> A (assumed, pending owner confirmation): extra pay on top of the month's salary, a Substitute credit adjustment line. The substitute's own attendance and salary are untouched.
- Q3: Which pay run pays it, and when is a credit offered? -> A (assumed, pending owner confirmation): a credit is offered once its date is in a month that has ended and whose attendance is locked; the next draft run that includes the substitute takes it (the rule 013b already has for waiting adjustments), whatever the credit's month.
- Q4: What if a credit is changed or voided after a run took it? -> A (assumed, pending owner confirmation): before the run is approved the line is released (draft) or the change waits (awaiting approval); after approval nothing is taken back automatically, the credit shows "Voided after payment, correction needed" and the Admin records a Correction adjustment.
- Q5: Do credits created before go-live, or in months already approved, get paid? -> A (assumed, pending owner confirmation): only credits dated in months from the go-live month (a system setting) are offered; earlier credits are listed as "before reconciliation start" and never paid by this feature.
- Q6: Does the Manager covering a class personally earn anything? -> A (assumed, pending owner confirmation): no. Only an assigned substitute Teacher is credited (017a); a Manager's pay is not touched.
- Q7: Is the School billed for the substitute day? -> A (assumed, pending owner confirmation): no. School billing (022) and the margin report (014b) are not changed here; whether the School pays for cover is a separate business decision.
- Q8: One adjustment per credit or one per month? -> A (assumed, pending owner confirmation): one adjustment per credit, so each line on the payslip and in reconciliation traces to one School and one date.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A substitute day is paid in the next pay run (Priority: P1)

A Zone Manager assigns a Teacher to cover an absent colleague's class (017a). Once that month has ended and its attendance is
locked, the credit reaches payroll by itself: the substitute's next pay run, when computed, carries a Substitute credit line for
that day (School, date, day value, amount). Nobody types it. When the Director approves the run the credit shows "Paid in
run <month>".

**Why this priority**: This is the whole point of the spec. Without it a substitute works a day and is never paid, and the credit
data of 017a is dead data.

**Independent Test**: Assign a substitute for a full day in a past, locked month, compute the run, see a line of one day's value on
the substitute, approve the run, and see the credit "Paid in run" with the run's month and the amount.

**Acceptance Scenarios**:

1. **Given** a live credit of 1.00 day dated in a locked month, **When** the Admin computes the next run that includes the substitute, **Then** the substitute's line shows a Substitute credit adjustment equal to one day's value for that date, naming the School and date.
2. **Given** a credit of 0.50 day, **When** the run is computed, **Then** the amount is half of one day's value, rounded as the run rounds.
3. **Given** a credit dated in a month that has not ended or is not locked, **When** anyone looks at reconciliation, **Then** it shows "Waiting for month lock" and no adjustment exists.
4. **Given** a credit already taken by a draft run, **When** the Admin computes the run again, **Then** the credit appears once, never twice.
5. **Given** an approved run that carried the credit, **When** the Director looks at the credit, **Then** it shows "Paid in run <month>" with the run, amount and date approved.
6. **Given** a Teacher assigned to cover on a day they are also marked absent, **When** credits are read, **Then** the credit follows whatever 017a says is live; this feature adds no rule of its own about who may be credited.

---

### User Story 2 - Admin and Director see every credit and where it stands (Priority: P1)

Under PAYROLL & FINANCE → Substitute Payouts, the Admin or Director sees a list of substitute credits for a month (or range)
with Teacher, School, date, day value, amount and a status (the full list is FR-007). Totals per Teacher, School and month show
days credited, days paid and the difference.

**Why this priority**: Payroll people must be able to prove that every covered day was paid once, and find the ones that were not.

**Independent Test**: With one credit in each state, open the list and filter by status, Teacher, School and month; each credit shows the
right status and the totals add up to the rows.

**Acceptance Scenarios**:

1. **Given** credits in several states, **When** the Admin opens the screen for a month, **Then** each credit shows its status, and filters by Teacher, School, Zone, month and status work.
2. **Given** the month's credits, **When** the totals are shown, **Then** days credited equals days paid plus days in each unpaid state, per Teacher, School and month, and the sum of rows equals the footer.
3. **Given** any list, **When** the user exports, **Then** a CSV with the same rows and filters downloads, with formula-safe cells, dates DD/MM/YYYY and amounts in Rupees.
4. **Given** a Zone Manager, **When** they open the screen, **Then** they see only credits for Schools in their Zone, read-only.
5. **Given** a user without the right, **When** they call the data, **Then** it is refused and the item is not in their menu.

---

### User Story 3 - Mismatches are listed and can be acted on (Priority: P1)

A Mismatches tab lists every credit that cannot be paid or whose payment no longer matches, each with the reason and the next step:
the substitute is not on the pay run (not an active payroll Teacher, no salary in effect, no divisor for that date), the credit was
changed or voided after a run took it (draft: released; awaiting approval: waits; approved: correction needed), the amount no
longer equals what the pay rules now give, or the run that took it was cancelled. For "correction needed" the Admin can start a
Correction adjustment from the row, with the amount and reason prefilled and editable, and it is linked to the credit.

**Why this priority**: Money questions after approval are where trust is lost. A visible list with a clear next step closes the loop
that 013b left open.

**Independent Test**: Void a credit after its run was approved; see it under Mismatches as "Voided after payment, correction needed" with
the amount paid; start the Correction from the row; see it as a waiting Correction adjustment in payroll, and the row marked "Correction
raised" until the next run applies it.

**Acceptance Scenarios**:

1. **Given** a substitute with no salary in effect on the credit date, **When** reconciliation runs, **Then** the credit is a mismatch "cannot be priced: no salary in effect" and no adjustment is made; once a salary exists, the next reconcile offers it.
2. **Given** a credit voided (substitute changed, absence withdrawn) while its adjustment is waiting or held by a draft run, **When** reconciliation runs, **Then** the adjustment is withdrawn or released, and the credit shows "Voided before payment".
3. **Given** a credit voided while its run awaits approval, **When** reconciliation runs, **Then** it shows "Voided, run awaiting approval" and, once the run is approved, becomes "Voided after payment, correction needed" (or, if the run is returned and recomputed, "Voided before payment").
4. **Given** a credit voided after the run was approved, **When** the Admin opens the row, **Then** the amount paid, the run and a "Raise correction" action (Admin only) are shown, and nothing is changed automatically.
5. **Given** a correction raised from a row, **When** it is applied by a later approved run, **Then** the mismatch is marked resolved with the run's month.
6. **Given** a substitute whose line in the run is "cannot pay" (013b: for example no salary or an incomplete month), **When** the run is computed, **Then** the credit stays "Waiting for next run" with the run's reason shown as a mismatch; this feature adds no pay-hold of its own (013b records payment status after approval).

---

### User Story 4 - A Teacher sees their own substitute pay (Priority: P2)

A Teacher opens My Substitutions (017a) and sees, beside each day they covered, whether it has been paid: "Waiting", "In your
next payslip" or "Paid in <month> payslip", with the amount only once the run is approved. They see nothing of other Teachers
and not the absent Teacher's name.

**Why this priority**: It answers the Teacher's first question without a call to the Admin, but payroll works without it.

**Independent Test**: As the substitute, open My Substitutions before and after the run is approved and see the status change; as another Teacher,
see none of it.

**Acceptance Scenarios**:

1. **Given** a substitute with credits in several states, **When** they open My Substitutions, **Then** each day shows its pay status in plain words, and the amount only for an approved run.
2. **Given** a draft or awaiting-approval run, **When** the Teacher looks, **Then** no amount is shown (the run may change).
3. **Given** another Teacher's credit, **When** requested by id, **Then** the answer is not found.

---

### User Story 5 - Missed events are repaired and everything is audited (Priority: P2)

A daily job compares live credits with the adjustments payroll holds and fixes any gap (a credit never offered, an adjustment
whose credit was voided, a duplicate), writing what it did. Every offer, withdrawal, repair and raised correction is audited with
who or what did it.

**Why this priority**: Events can be lost; money must not depend on perfect delivery. It is secondary to the happy path.

**Independent Test**: Remove the offer for a live credit directly in the test data and run the job; the offer comes back once, and a second run changes nothing.

**Acceptance Scenarios**:

1. **Given** a live credit with no adjustment in a locked month, **When** the job runs, **Then** it is offered once and the repair is recorded.
2. **Given** an adjustment whose credit is no longer live and whose run is not approved, **When** the job runs, **Then** the adjustment is withdrawn and the credit shows "Voided before payment".
3. **Given** the job runs twice in a row, **When** the second finishes, **Then** nothing changed and no duplicate exists.
4. **Given** any change by this feature, **When** the Director opens the audit, **Then** it names the credit, the old and new status, the amount, and the actor or "reconcile job".

### Edge Cases

- A credit is moved to another substitute after the first one was offered: the first is voided and handled as in User Story 3, the new one is a new credit and is offered on its own.
- A half-day absence with two substitutes (0.50 each, 017a): two credits, two lines.
- The substitute's salary changes mid-month: the day's value uses the salary in effect on the credit date, as the pay run does.
- The substitute left the organization before the run: the credit is a mismatch "not on the pay run"; the Admin decides outside the system or adds a supplementary run (013b).
- A run is cancelled or returned: its credits go back to "Waiting for next run" with no change of amount unless the pay rules now give another.
- The amount the pay rules give changes between offer and run (a salary revision applied late): the offer is refreshed while it is unapplied; a difference after approval is a mismatch, not a silent change.
- A credit of zero amount (zero salary): shown as a mismatch "nothing to pay", never offered.
- Two credits for the same substitute on the same date in different Schools (day values add up to at most 1.00, 017a): two lines.
- The month of a credit is already approved before go-live (Q5): "Before reconciliation start", no adjustment.
- The reconciliation list is large: paged, with totals computed on the whole filter, not the page.
- The user holds both Admin and Director roles: they may do everything either may; the run that applies a correction they raised still follows the 013b rule that the submitter cannot approve.

## Requirements *(mandatory)*

### Functional Requirements

**Offer to payroll**

- **FR-001**: The system MUST offer each live substitute credit (017a) to payroll exactly once as a Substitute credit adjustment for the substitute, carrying the School, the date, the day value and the amount, identified by the credit so that a repeat never creates a second.
- **FR-002**: The system MUST offer a credit only when its date falls in a month that has ended and whose attendance is locked (013b rule) and on or after the go-live month (Q5); before that it is shown with the reason and no adjustment exists.
- **FR-003**: The amount MUST be the substitute's value for one day on the credit date, as the pay rules already compute it (013a), times the credit's day value, rounded as the pay run rounds. The system MUST NOT keep its own rate table. If the pay rules cannot give a value, the credit is a mismatch with their reason and nothing is offered.
- **FR-004**: The system MUST NOT create, change or remove anything in an approved run. A draft run takes waiting offers at compute and releases them on return or cancel; an approved run applies them (013b behaviour, unchanged).
- **FR-005**: The adjustment kind Substitute credit MUST be created only by this feature; manual recording of that kind stays refused (013b rule), with the reason text saying it arrives through this feature.
- **FR-006**: The payslip and the run line MUST show each Substitute credit with School, date and day value (the label already reserved by 013b).

**Reconciliation**

- **FR-007**: The system MUST show for every credit exactly one status from: Before reconciliation start; Waiting for month lock; Cannot be priced (mismatch); Waiting for next run; In draft run; In a run awaiting approval; Paid in run; Voided before payment; Voided, run awaiting approval; Voided after payment, correction needed; Correction raised; Corrected.
- **FR-008**: The system MUST update a credit's status and the offer when 017a changes or voids the credit (event), and again by the daily job if the event was missed.
- **FR-009**: For a credit voided or changed while its offer is waiting or held by a draft run, the system MUST withdraw the offer (or replace it with the new amount). While the run awaits approval it MUST wait. After approval it MUST NOT touch the run and MUST mark the credit "Voided after payment, correction needed" with the amount paid.
- **FR-010**: The system MUST total, per Teacher, School and month, the days credited, the days paid (credits whose adjustment was applied), the days still unpaid by state and the amount paid, such that credited equals paid plus unpaid for the same filter.
- **FR-011**: The Mismatches view MUST list: credits that cannot be priced; credits whose substitute is not on the pay run (reason from payroll); credits voided or changed after payment; credits whose applied amount differs from what the pay rules give now; and credits whose offer is missing past the next run. Each row MUST show the reason and the next step.
- **FR-012**: An Admin MUST be able to raise a Correction adjustment (013b kind Correction) from a "correction needed" row, with the amount (the paid amount negated, or the difference) and a reason prefilled, both editable; the correction is linked to the credit, uses the existing manual-correction rules (013b), and the row becomes "Correction raised", then "Corrected" when applied.
- **FR-013**: The system MUST NOT take money back automatically in any case.

**Screens, roles, data scope**

- **FR-014**: A new PAYROLL & FINANCE item "Substitute Payouts" MUST show the list, totals, mismatches and detail of a credit (including its history of statuses), with filters by month or range, Teacher, School, Zone and status, paged and sortable.
- **FR-015**: The list and mismatches MUST export as CSV with the filters in force; cells beginning with a formula character are neutralised; dates DD/MM/YYYY, amounts as numbers in Rupees (the screen shows Indian grouping).
- **FR-016**: Access MUST follow the permission matrix: Admin and Director View and Export org-wide; only the Admin raises a correction (Edit); a Manager views credits of Schools in their own Zone (Zone Manager), read-only; a Teacher sees only their own pay status inside My Substitutions (017a), with no new menu; System has none. Out-of-scope items answer "not found".
- **FR-017**: Server-side checks MUST apply on every call; the menu follows the grants and the UI shows only the actions the server allows.

**Reliability and audit**

- **FR-018**: A daily reconcile job MUST bring offers into line with live credits (offer missing, withdraw stale, refresh unapplied amount) and MUST be idempotent. It MUST record each repair.
- **FR-019**: Every offer, withdrawal, refresh, status change by event, repair and raised correction MUST write an audit entry (Principle I) naming the credit, the old and new status, the amount and the actor or "reconcile job". The status history is append-only.
- **FR-020**: Concurrent runs of the job and events on the same credit MUST NOT double-offer; the offer is idempotent per credit.
- **FR-021**: Payroll MUST NOT depend on this feature's module, and this feature MUST read credits only through the 017a public interface and payroll only through its public interface (Principle VII).

### Key Entities *(include if feature involves data)*

- **Credit payout record**: one per 017a credit: credit id, substitute, School, absent Teacher (internal only), date, day value, amount offered, status, run and month paid in, the adjustment reference, reason of the mismatch, timestamps. Never shown to the substitute with the absent Teacher's name.
- **Payout event**: append-only history of status changes of a credit payout record, with actor.
- **Correction reference**: a field on the credit payout record naming the Correction adjustment raised from it (no separate table).
- **Substitute credit adjustment** (013b, existing): the line in the run, created through the new intake; its source reference is the credit.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Substitute Payouts | Notes |
| --- | --- | --- |
| Admin | View, Edit (raise correction), Export, org-wide | Does not approve runs by this feature |
| Director | View, Export, org-wide | Approves runs in 013b |
| Manager (Zone Manager) | View, own Zone Schools only | Read-only |
| Teacher | none; own pay status inside My Substitutions | |
| System | none | |

Constitution: a PATCH amendment adds a "Substitute Payouts" row to the Default role access matrix and a PAYROLL & FINANCE navigation item; no principle changes (number 2.5.17 proposed, renumber at merge). Principle VII holds: new package `substitution.payout`; `payroll.api` gains an additive `SubstituteCreditIntake` (amendment B to 013b); the event `PayrollAdjustmentApplied` already exists.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every live credit in a locked month on or after go-live appears in exactly one pay run's adjustments, never zero and never two, in 100% of test scenarios including repeated events and repeated job runs.
- **SC-002**: For any month, days credited equal days paid plus days unpaid by state for every Teacher, School and month, and the screen totals equal the sum of rows.
- **SC-003**: No approved run changes because of this feature, in any scenario (voids, changes, job runs after approval).
- **SC-004**: A credit voided after approval is visible as "correction needed" within one minute when the event is delivered, and within one day (the job) when it is not.
- **SC-005**: A payroll user finds the status of a given Teacher's substitute days for a month in under 30 seconds and raises a correction in under 1 minute.
- **SC-006**: A Zone Manager sees only their Schools' credits; a Teacher sees only their own; 100% of out-of-scope requests answer "not found".
- **SC-007**: The CSV equals the screen rows for the same filters and opens in a spreadsheet without executing any cell.
- **SC-008**: Each Substitute credit line on a payslip can be traced to one School and one date.

## Assumptions

All pending owner confirmation unless noted (see Clarifications and `clarification-questions.md`).

- Pay of a substitute day uses the substitute's own day value (Q1) and is extra pay (Q2).
- Credits are offered after the month is locked and paid by the next draft run (Q3); one adjustment per credit (Q8).
- No automatic clawback; Admin Correction after approval (Q4). Go-live month is a setting `substitution.payout-start-month` (Q5).
- Manager cover is unpaid (Q6); Schools are not billed (Q7).
- Not in scope: payment of the money (outside the system, 013b D8), changes to 017a rules, to School billing (022), to margin (014b), mobile screens, notifications to the substitute (the payslip is the notice in this version), rates per School or per Teacher level.
- Business dates use the application clock; amounts are Indian Rupees with two decimals.
- Migration number: the highest on main at writing is V150; this feature will take V160. Re-check at implementation against merged branches.
