# Feature Specification: Expenses

**Feature Branch**: `015-expenses`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "015-expenses: Office expenses as a workflow. An employee raises a claim with the date, amount, category, area and a note, and attaches the bill or receipt. The Admin or Director reviews it and approves it (no amount limit) or rejects it with a reason, and a rejected claim can be corrected and sent again. An approved claim is either reimbursed through the next pay run or recorded as paid. The Director and Admin see pending approvals and their age, the spend against a budget set per area and month, and the cost per School acquired and per Teacher trained. Travel and Fuel link to a visit, drive or task; Training Stay links to an induction batch; Other needs a reason. The claim, its bill and its files are ready for the mobile spec 036."

## Clarifications

### Session 2026-10-18

None yet. The defaults chosen while writing are listed under Assumptions and marked "to confirm in /speckit-clarify".

## Why this exists

Today expenses live in two spreadsheets: a daily grid with one column per Manager and a ledger of Manager, nature, amount
and comments. Bills are not kept with the entry, nobody approves the entry before the money is paid, large one-off costs
look like daily commute and food, and nobody can say how much was spent in an area against what was intended, or what a
new School or a trained Teacher cost. Decision D11 fixes the approach: employees claim travel, food and purchases, the
Admin or Director sees the details and approves with no amount limit, and approved claims that the employee paid from
their own pocket are paid back through salary. This spec is that workflow, plus the budget and cost views the Director
asked for. It also gives the later mobile spec 036 (photograph a bill and send it) a finished server side to build on.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Raise an Expense Claim with its Bill (Priority: P1) 🎯 MVP

A Manager (or an Admin or Director who spends on behalf of the office) opens OPERATIONS → My Expenses, adds a claim and
fills in the date, amount, category, area and a note, says whether they paid it themselves, and attaches the bill or
receipt. They can save it as a draft and finish later, or submit it. Once submitted it waits for review.

**Why this priority**: Without a claim that carries its bill there is nothing to review, pay or total. This alone
replaces the ledger spreadsheet.

**Independent Test**: As a Manager, raise a Travel and Fuel claim for ₹850 dated yesterday for the Manager's own Zone with
one photo of the bill, submit it, and see it in My Expenses as "Submitted"; confirm another Manager cannot see it.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they add a claim with date, amount, category, area, note and one bill and submit it,
   **Then** the claim is "Submitted", the bill is kept with it, the Admin and Director are notified, and the Manager
   sees it in their list.
2. **Given** a claim in progress, **When** the Manager saves it without submitting, **Then** it is a "Draft" that only
   they see, which they can change, delete or submit later.
3. **Given** the category "Other", **When** the Manager submits without a reason, **Then** it is refused naming the
   missing reason.
4. **Given** an amount of zero or less, a date in the future, or a date more than 60 days in the past, **When** the
   Manager submits, **Then** it is refused with the specific reason.
5. **Given** a claim with no bill, **When** the Manager submits it, **Then** it is refused unless they state why no bill
   exists; with that statement it is accepted and shown to the approver as "No bill".
6. **Given** an attachment that is not a jpg, png or pdf, or larger than 10 MB, **When** the Manager adds it, **Then**
   it is refused with the reason and the claim is unchanged.
7. **Given** the same Manager, date, amount and category as a claim already submitted or approved, **When** they submit,
   **Then** they are warned about the possible duplicate and may confirm to continue; the confirmation is recorded.
8. **Given** a Teacher or System user, **When** they look for expenses, **Then** there is no menu entry and direct access
   is refused.

---

### User Story 2 - Link a Claim to the Work it Belongs To (Priority: P1)

Some categories only make sense with the work behind them. Travel and Fuel can be linked to one of the claimant's own
planned activities (a School visit or meeting, a campus drive, or a task) so the approver can see why the journey
happened. Training Stay must be linked to an induction batch so the cost can be shown per Teacher trained. Other needs a
reason. Rent and Utilities, Marketing and Office Supplies need only the common fields.

**Why this priority**: The cost per School and per Teacher in Story 6 is only believable if claims are tied to the batch
or the work. It is P1 because Training Stay without its batch cannot be told apart from any other cost.

**Independent Test**: Raise a Training Stay claim without a batch and see it refused; link it to an induction batch and
see it accepted; raise a Travel and Fuel claim linked to the Manager's own visit and see the visit's date and place on
the claim.

**Acceptance Scenarios**:

1. **Given** the category Training Stay, **When** the claimant submits without choosing an induction batch, **Then** it is
   refused; with a batch it is accepted and the batch name and dates are shown on the claim.
2. **Given** the category Travel and Fuel, **When** the claimant chooses a link, **Then** only planned activities that
   belong to the claimant, or that fall on the claim's date, are offered, and the chosen one shows kind, date and place.
3. **Given** the category Travel and Fuel with no link, **When** the claimant submits, **Then** it is accepted (the link
   is optional) and the approver sees "not linked".
4. **Given** a category that does not take a link, **When** the claimant tries to add one, **Then** it is not offered.
5. **Given** a linked visit, drive, task or batch that is later cancelled or removed, **When** anyone opens the claim,
   **Then** the claim still shows what it was linked to at submission.

---

### User Story 3 - Review, Approve or Reject (Priority: P1)

The Admin or Director opens OPERATIONS → Expenses and sees the claims waiting, oldest first, with who, what, how much,
the category, area, the bill and the link. They open one, look at the bill, and approve it or reject it with a reason.
There is no amount limit: any claim can be approved. Nobody may decide their own claim. A rejected claim returns to the
claimant with the reason, who can correct it and submit it again; the earlier rejection stays in the claim's history.

**Why this priority**: Approval is the control the spreadsheet never had, and the Director requires it.

**Independent Test**: Submit a claim as a Manager; as the Director approve it and see the decision, who and when; submit
another, reject it without a reason (refused), then with one; as the Manager correct and resubmit it, and see the
history with both entries.

**Acceptance Scenarios**:

1. **Given** a submitted claim, **When** the Admin or Director approves it, **Then** it becomes "Approved" with the
   approver and time recorded, and the claimant is notified.
2. **Given** a submitted claim, **When** the approver rejects it without a reason, **Then** it is refused; with a reason
   it becomes "Rejected", the claimant is notified with the reason.
3. **Given** a rejected claim, **When** the claimant corrects it and submits again, **Then** it returns to "Submitted"
   as the same claim, the earlier decision and reason remain in its history, and the approvers are notified.
4. **Given** a claim raised by an Admin, **When** that same person tries to approve it, **Then** it is refused; a
   Director can. A claim raised by a Director is decided by an Admin. A user holding both roles cannot decide a claim
   they raised.
5. **Given** a submitted claim, **When** the claimant withdraws it before any decision, **Then** it returns to "Draft".
6. **Given** an approved or paid claim, **When** anyone tries to change its amount, category, date, area or bill,
   **Then** it is refused; a mistake is dealt with by reversing the approval (Story 4) or a new claim.
7. **Given** two approvers open the same claim, **When** both decide, **Then** the first decision stands and the second
   is told the claim was already decided.

---

### User Story 4 - Pay Back: Reimbursed with Salary or Recorded as Paid (Priority: P1)

An approved claim that the claimant paid from their own pocket is owed to them. By default it is added to their next pay
run as a reimbursement (spec 013b), without anyone typing it again; the claim then shows "Paid with payroll" and the
month. If the money was returned another way (cash or transfer outside the system), the Admin records the claim as paid
with the date, mode and reference. A claim the company paid directly (the "paid by company" choice, for example rent or
a supplier) is never reimbursed and moves straight to "Paid by company" when approved. The Admin or Director can reverse
an approval with a reason while the claim has not yet been taken into a pay run or recorded as paid.

**Why this priority**: D11 requires approved claims to be used in salary. Without this the approval has no financial
effect and 013b's reserved Reimbursement line has nothing to take.

**Independent Test**: Approve a ₹1,200 claim paid by the Manager; confirm the Manager's next pay run offers it as a
reimbursement line of ₹1,200; approve that run and see the claim "Paid with payroll". Approve a second claim and record
it paid by transfer with a reference. Try to reverse each paid claim and see it refused.

**Acceptance Scenarios**:

1. **Given** an approved claim paid by the claimant, **When** a pay run for a month is started, **Then** the claim is
   offered to payroll for that person as a reimbursement of its approved amount, and no claim is offered twice.
2. **Given** a claim offered to a draft run, **When** that run is cancelled or the person is left out, **Then** the claim
   is available again and still "Approved".
3. **Given** a pay run that includes the claim is approved, **Then** the claim becomes "Paid with payroll" with the run's
   month and the claim can no longer change status.
4. **Given** an approved claim paid by the claimant, **When** the Admin records it paid with date, mode and reference,
   **Then** it becomes "Paid" and payroll no longer offers it.
5. **Given** a claim marked "paid by company", **When** it is approved, **Then** it is "Paid by company" at once and is
   never offered to payroll.
6. **Given** an approved claim not yet paid or taken by payroll, **When** the Admin or Director reverses the approval
   with a reason, **Then** it returns to "Submitted" with the reversal in its history and payroll stops offering it.
7. **Given** an approved claim whose claimant has left the company, **When** the Admin looks at it, **Then** it is
   listed as owed to a person who has no pay run, and can be recorded as paid outside the system.

---

### User Story 5 - Budget versus Actual per Area (Priority: P2)

The Director or Admin sets a monthly budget for each area (Zone) and for the head office (spend with no area), and sees
for any month how much was budgeted, how much has been approved (actual), how much is still waiting (pending), what
remains, and whether the area is over. A breakdown by category and by claimant is one click away. Going over a budget
never blocks a claim: it only shows.

**Why this priority**: It answers "are we spending what we intended in each area". It is P2 because claims can be
approved and paid before any budget exists.

**Independent Test**: Set ₹20,000 for a Zone in a month, approve ₹12,000 and leave ₹5,000 pending; see budget 20,000,
actual 12,000, pending 5,000, remaining 8,000; approve a further ₹10,000 and see it marked over by ₹2,000.

**Acceptance Scenarios**:

1. **Given** an area with a budget for the month, **When** the Director opens Budget versus Actual, **Then** budget,
   actual (approved, paid and paid-by-company claims dated in the month), pending (submitted claims), remaining and the
   percentage used are shown for each area and in total, in rupees with Indian digit grouping.
2. **Given** an area with no budget for the month, **When** it is shown, **Then** it shows the actual and "No budget set"
   rather than zero.
3. **Given** a claim that takes an area over budget, **When** it is approved, **Then** it is approved normally and the
   area is shown as over budget; the approver sees the area's remaining amount while deciding.
4. **Given** a budget, **When** the Admin or Director changes it, **Then** the new figure applies and the previous
   figures, who changed them and when remain in its history.
5. **Given** an area, **When** the viewer opens its detail, **Then** the claims behind the actual are listed by category
   and by claimant, and each opens its claim.
6. **Given** a Manager, **When** they open Expenses, **Then** budgets and other people's claims are not offered.

---

### User Story 6 - Pending Approvals, Ageing and Cost per School and per Teacher (Priority: P2)

The Director and Admin see how many claims and how many rupees are waiting, grouped by how long they have waited (0-2,
3-7, 8-14 and over 14 days), so slow approval is visible. They also see two cost measures for a chosen period: the cost
per School acquired and the cost per Teacher trained, each with the figures behind it.

**Why this priority**: These are the management questions in the Director deck (item 11). They need real approved data,
so they follow the workflow.

**Independent Test**: With a period containing ₹30,000 of approved Marketing claims and Travel and Fuel claims linked to
visits, and three Schools that became active in the period, see ₹10,000 per School acquired; with ₹60,000 of approved
Training Stay claims for a batch of 12 Teachers who completed induction, see ₹5,000 per Teacher trained.

**Acceptance Scenarios**:

1. **Given** submitted claims, **When** the Director opens Pending Approvals, **Then** the count and total amount are
   shown per age group, the oldest claim is first, and each opens its claim.
2. **Given** a period, **When** the viewer opens Cost per School acquired, **Then** the approved cost of Marketing claims
   and of Travel and Fuel claims linked to marketing visits, meetings or drives dated in the period is divided by the
   Schools that became active in that period, and the cost, the number of Schools and the claims behind them are shown.
3. **Given** a period in which no School became active, **When** the measure is shown, **Then** it shows the cost and
   "No Schools acquired" instead of a division by zero.
4. **Given** a period, **When** the viewer opens Cost per Teacher trained, **Then** the approved Training Stay cost of the
   induction batches in the period is divided by the Teachers who completed those batches, with the same display rules.
5. **Given** either measure, **When** the viewer asks for one area or one batch, **Then** the figures narrow to it.
6. **Given** the Admin or Director, **When** they export the claims for a month, **Then** a CSV is produced with one row
   per claim: claimant, date, category, area, amount, status, approver, decision date, linked work, paid-by and payment
   details.

---

### User Story 7 - My Expenses: Track my Claims (Priority: P1)

A claimant sees their own claims, newest first, filtered by status (Draft, Submitted, Approved, Rejected, Paid), with
amount, category, date, the approver's note and, for rejected claims, the reason and a way to correct them. They see
what they are still owed.

**Why this priority**: Claimants will not use the system if they cannot see what happened to their money.

**Independent Test**: Raise one claim in each status and see the list filter correctly, the owed total equal to the sum
of approved, unpaid reimbursable claims, and no other person's claim.

**Acceptance Scenarios**:

1. **Given** a claimant with claims in several statuses, **When** they open My Expenses, **Then** only their claims are
   shown, newest first, with a status filter and a month filter.
2. **Given** approved reimbursable claims not yet paid, **When** the claimant looks at the top of the list, **Then** the
   total still owed to them is shown.
3. **Given** a rejected claim, **When** the claimant opens it, **Then** the reason, who rejected it and when are shown,
   and they can correct and resubmit it.
4. **Given** a claim, **When** the claimant opens it, **Then** its bills, link, history of decisions and (when paid) the
   payment details are shown.

---

### Edge Cases

- A claim for an area that has since been deactivated stays valid and reportable; new claims cannot choose an inactive
  area.
- A claim whose bill was removed by an Admin (the shared file store records a removal, not a deletion) shows "bill
  removed" with the reason; the claim does not change status.
- A Manager moved to another Zone after raising a claim: the claim keeps the area it was tagged with.
- An approved claim dated in a month for which a pay run was already approved is offered to the next run, not to the
  closed one.
- A claim for ₹0.50 or an amount with more than two decimals is refused; amounts are in rupees and paise.
- Two submissions of the same claim from a flaky connection (spec 036) create one claim, not two.
- The claimant's account is deactivated while a claim is waiting: the claim can still be decided and recorded as paid.
- An approver reverses an approval after the budget view was exported: the export is a snapshot; the live view shows the
  change and the history records it.
- A budget entered for an area in a month already closed in payroll is accepted; budgets do not depend on payroll.
- A Training Stay claim for a batch that has no completed Teachers yet counts toward the batch cost, and the cost per
  Teacher shows "No Teachers trained yet".

## Requirements *(mandatory)*

### Functional Requirements

**Raising and editing**

- **FR-001**: The system MUST let a Manager, Admin or Director raise an expense claim with: date, amount (rupees and
  paise), category, area, a note, whether the claimant paid it personally or the company paid it, and one or more bills.
- **FR-002**: The categories MUST be Rent and Utilities, Travel and Fuel, Training Stay, Marketing, Office Supplies and
  Other, a fixed list in this version.
- **FR-003**: The area MUST be one of the organization's Zones, or "Head office" for spend with no area. Only active
  Zones are offered for new claims.
- **FR-004**: A claim MUST have a status: Draft, Submitted, Approved, Rejected, Paid (with how: with payroll, or
  recorded outside the system) or Paid by company. Draft and Rejected claims are the only ones the claimant may change.
- **FR-005**: The system MUST refuse submission when the amount is not positive, the date is in the future or more than
  60 days in the past, the category is Other with no reason, the category is Training Stay with no induction batch, or
  no bill is attached and no "no bill" reason is given.
- **FR-006**: Bills MUST be stored with the shared file storage (spec 023): jpg, png or pdf, at most 10 MB each, at most
  5 per claim; each shows who added it and when. A bill on a submitted claim cannot be replaced by the claimant; a removal
  is by an Admin or Director with a reason and is recorded.
- **FR-007**: A claimant MUST be able to save a Draft, change and delete their own Drafts, withdraw a Submitted claim
  before a decision, and correct and resubmit a Rejected claim as the same claim.
- **FR-008**: The system MUST warn when a claim has the same claimant, date, amount and category as another claim that is
  not Rejected, and require explicit confirmation to continue; the confirmation is recorded.
- **FR-009**: Submitting the same claim twice (for example a retry after a lost connection) MUST NOT create two claims.

**Links**

- **FR-010**: A Travel and Fuel claim MAY be linked to one of the claimant's planned activities (visit, meeting, drive or
  task) read from the shared planned-activity interface (contract C1). A Training Stay claim MUST be linked to an
  induction batch. No other category takes a link.
- **FR-011**: A claim MUST keep the kind, date and place or name of what it was linked to as they were at submission.

**Review**

- **FR-012**: The Admin and Director MUST see all submitted claims, oldest first, with claimant, category, area, amount,
  bills, link, note, age and the area's remaining budget for the month, and MUST be able to approve or reject.
- **FR-013**: There MUST be no amount limit on approval.
- **FR-014**: A rejection MUST carry a reason. A rejected claim keeps every earlier decision and reason in its history
  when resubmitted.
- **FR-015**: A user MUST NOT approve, reject or reverse a claim they raised, even when they hold both approver roles.
- **FR-016**: A decision on an already decided claim MUST be refused with a message saying who decided it and when.
- **FR-017**: The Admin or Director MUST be able to reverse an approval, with a reason, only while the claim has not been
  paid or taken into an approved pay run.

**Payment and payroll**

- **FR-018**: An approved claim that the claimant paid personally MUST be offered to the payroll run (spec 013b) as a
  reimbursement for the claimant, once; it becomes Paid when a run containing it is approved, recording the run's month.
  A claim offered to a run that is cancelled or in which the person is left out MUST become available again.
- **FR-019**: The Admin MUST be able to record an approved personally paid claim as paid outside payroll, with date, mode
  (cash, bank transfer, UPI, other) and reference; payroll then stops offering it.
- **FR-020**: A claim marked as paid by the company MUST become "Paid by company" on approval and never go to payroll.

**Budgets and measures**

- **FR-021**: The Admin and Director MUST be able to set a monthly budget for each active Zone and for Head office, and
  change it; every change is kept with who and when.
- **FR-022**: The system MUST show for any month and area: budget, actual (Approved, Paid and Paid by company claims dated
  in the month), pending (Submitted claims), remaining, percentage used and an over-budget marker, with a breakdown by
  category and claimant. Exceeding a budget MUST NOT block a claim.
- **FR-023**: The system MUST show pending approvals grouped by age (0-2, 3-7, 8-14, over 14 days) with counts and
  totals.
- **FR-024**: The system MUST show, for a chosen period, the cost per School acquired and the cost per Teacher trained as
  defined in Assumptions, with the underlying cost, the divisor and the claims behind them, and a clear "no Schools" or
  "no Teachers" state in place of a division by zero.
- **FR-025**: The system MUST export claims for a month as CSV, subject to the Export action and the viewer's scope.
- **FR-026**: The system MUST expose to other modules, without them reading its tables: the approved claims owed to a
  person, the approved spend per area, category and period, the pending-approval counts, and the cost measures, so
  payroll (013b), reports (014), the dashboard (028) and the Android app (036) use one source.

**Visibility, audit, notification**

- **FR-027**: A claimant MUST see only their own claims; the Admin and Director see all; no one else sees any. Teacher and
  System have no access.
- **FR-028**: Every creation, edit, submission, withdrawal, decision, reversal, bill addition or removal, payment, budget
  change and export MUST be written to the audit trail (spec 003) with who, when and the before and after values.
- **FR-029**: The Admin and Director MUST be notified (spec 010) when a claim is submitted or resubmitted; the claimant
  MUST be notified when it is approved, rejected or its approval reversed.
- **FR-030**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST meet WCAG 2.2
  AA, have loading, empty and error states, and work at phone width.
- **FR-031**: Every action available to the viewer on a claim MUST be derived from their permissions and the claim's
  status, and given with the claim, so a client (web or Android) shows only what is allowed.

### Key Entities

- **Expense claim**: claimant, date, amount, category, area, note, who paid, status, optional reason (Other or no bill),
  optional link (activity or induction batch, kept as at submission), approver and decision date, payment details,
  history of every status change.
- **Bill**: a file kept with a claim (name, type, size, who added it and when, removal reason if removed).
- **Area budget**: a Zone or Head office, a month, an amount, with a history of changes.
- **Reimbursement offer**: an approved personally paid claim made available to payroll for its claimant until a run takes
  it or it is recorded as paid.
- **Cost measure**: the cost, the divisor and the claims behind cost per School acquired or per Teacher trained for a
  period.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | OPERATIONS → Expenses (all claims, approvals, pending and ageing, budget versus actual, costs, export, record payment); OPERATIONS → My Expenses | View, Create (budgets), Edit (budgets), Approve, Process (record paid, reverse), Export; own claims: View, Create, Edit, Delete | Org-wide |
| Director | OPERATIONS → Expenses; OPERATIONS → My Expenses | View, Create, Edit (budgets), Approve, Export; no Process by default; own claims as Admin | Org-wide |
| Manager (Zone Manager) | OPERATIONS → My Expenses | View, Create, Edit (own Draft and Rejected), Delete (own Draft) | Own |
| Teacher | none | none | None |
| System | none (System MUST NOT see business data) | none | None |

**New permission keys**: `EXPENSES` (View, Create, Edit, Approve, Process, Export; all claims, approvals, budgets,
cost views, payment), `MY_EXPENSES` (View, Create, Edit, Delete; a claimant's own claims). Seeded as above; Teacher and
System are not eligible. Only Admin, Director and System edit the matrix. The constitution's Default role access matrix
gets an Expenses row when this spec merges. Navigation entries and the notification types (expense awaiting approval,
decided, reversed) are added with the spec. Spec 036 adds no keys.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A claimant can raise and submit a claim with one bill in under 2 minutes on a desktop and under 3 minutes
  on a phone-width screen.
- **SC-002**: An approver can open a waiting claim, see its bill, and approve or reject it in under 30 seconds.
- **SC-003**: 100% of submitted claims carry a bill or a recorded "no bill" reason, and 100% of rejections carry a
  reason, proved by tests.
- **SC-004**: No claim is decided by the person who raised it, including a user holding both Admin and Director roles,
  proved by tests.
- **SC-005**: Every approved personally paid claim appears in exactly one pay run's reimbursements or is recorded as
  paid outside payroll, never both and never twice, proved by a test that runs, cancels and re-runs payroll.
- **SC-006**: For a month, budget, actual, pending and remaining per area match hand-computed figures for claims in every
  status, a month with no budget, a head-office claim and a rejected-then-approved claim.
- **SC-007**: Cost per School acquired and per Teacher trained equal the hand-computed figures for a sample period, and
  show a clear no-data state when the divisor is zero.
- **SC-008**: A claimant never sees another person's claim, and a Teacher or System user cannot reach any expense data,
  proved by tests across every list and detail.
- **SC-009**: After an approved claim is paid, no field of it can be changed, and every status change is in the audit
  trail with who and when.
- **SC-010**: A retried submission produces one claim, so the spec 036 offline sync can be proved not to duplicate.

## Assumptions

Items marked "to confirm in /speckit-clarify" are defaults chosen because no one could be asked; change them there.

- **Who may raise claims** (to confirm in /speckit-clarify): Managers, Admin and Director. Teachers are not included in
  this version although the requirements document lists teacher commute and food claims; the roadmap, D11 and the 036
  input speak of employees and Managers, and Teachers have no Operations menu. A Teacher claim flow is a later change.
- **Approver rule** (to confirm in /speckit-clarify): any Admin or Director may decide; there is no named approver per
  claim and no chain. The "approver" is recorded on decision. Nobody decides their own claim, so an Admin's claim is
  decided by a Director and the reverse.
- **Area is a Zone** (the deck's "Area", spec 005), plus "Head office". The claim carries the area chosen; it is not
  forced to the claimant's own Zone, and a Manager may pick any active Zone or Head office (to confirm in
  /speckit-clarify whether a Manager is limited to the Zones they are assigned).
- **Budget grain** (to confirm in /speckit-clarify): one amount per area per calendar month, no budget per category and
  no yearly roll-over; going over is shown, never blocked. "Actual" counts claims by their expense date, not by their
  approval date.
- **Reimbursement route** (to confirm in /speckit-clarify): an approved personally paid claim goes to payroll by default
  and may be recorded as paid outside the system instead. Spec 013b reserves a "Reimbursement" adjustment kind but has no
  way to receive it yet; this spec exposes the owed claims and 013b (or an amendment to it) takes them, so this spec
  states the behaviour and 013b's plan must accept it. Spend paid by the company (rent, supplier) is "paid by company"
  and never goes to payroll.
- **Bills** (to confirm in /speckit-clarify): required, with a stated reason as the only exception, no amount threshold
  below which a bill is waived. The shared file store (spec 023) accepts only jpg, png, pdf, docx, xlsx and txt up to
  10 MB, checked by extension and content; this spec allows jpg, png and pdf only. Phone formats such as HEIC or WebP
  are therefore not accepted as they are, and spec 036 must convert and compress photos to jpg before sending. Up to 5
  bills per claim.
- **Date window**: the expense date may not be in the future or older than 60 days (to confirm in /speckit-clarify);
  the figure is fixed in this version, not a setting.
- **Cost per School acquired** (to confirm in /speckit-clarify): approved cost of Marketing claims plus Travel and Fuel
  claims linked to marketing visits, meetings or campus drives, dated in the period, divided by the Schools that became
  active in the period (a prospect won, spec 023). Rent and Utilities, Office Supplies and Other are overhead and not
  included. **Cost per Teacher trained**: approved Training Stay cost of induction batches that ended in the period,
  divided by the Teachers who completed those batches (spec 016); the batch's other costs are not included.
- **Reuse of the real system**: planned activities come from the C1 interfaces that exist today for campus drives,
  marketing visits and meetings, and tasks (training sessions, spec 024, are not built, so that kind is absent). An
  induction batch is read through the existing batch lookup of the training module. Scope uses the one shared scope query
  of the organization module. Bills use the shared file store, which keeps removed files and records who removed them.
  Audit uses the audit trail, and notifications use spec 010's types, three of which this spec adds.
- **Differences from the planning documents**: the roadmap names the categories Rent and Utilities, Travel and Fuel,
  Training Stay, Marketing, Office Supplies and Other; the older requirements document names Transport, Food, Fuel,
  Substitution payout and Observation visit, and a configurable threshold above which approval is needed. This spec
  follows the roadmap and decision D11 (no limit). Substitution payments are handled by the substitution and payroll
  specs (017a, 017b), not as expense claims. The older documents call for a daily per-Manager total; the monthly
  export and the month and claimant breakdown replace it. Mobile capture and offline sync are spec 036, not here.
- **Payment of reimbursement is manual or through payroll**: the system never moves money. Paid status for a claim
  recorded outside payroll is typed in by the Admin, as D8 decides for payroll.
- **Not in this version**: categories managed by users, per-category budgets, yearly budgets, multi-level approval,
  advances, mileage rates or per-diem rules, GST on bills, OCR of bills, Teacher claims, and Zone Managers seeing their
  Zone's spend.
- **Month closing**: expenses have no month lock of their own; payroll's approved run is what fixes the reimbursement.
  A claim dated in a closed month is still accepted within the 60-day window.
- **Dashboard and reports**: spec 028 (approvals inbox, spend versus budget) and spec 014 (Expenses report) read the
  exposed figures of FR-026 and add no rules of their own.
