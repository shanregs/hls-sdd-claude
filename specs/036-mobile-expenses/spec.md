# Feature Specification: Mobile Expenses

**Feature Branch**: `036-mobile-expenses`

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "036-mobile-expenses: Expense screens for the HLS Android app, built on the app shell, sign-in and server-driven menus of spec 018 and the expense rules and APIs of spec 015, which stay unchanged. Raise an expense with a bill photo, work linked to the category, offline capture with later sync, My Expenses with status filter, and review (approve or reject) for the Director and Admin where the server offers it. Phone photos (HEIC, WebP) are converted to jpg before upload. My Expenses appears in the Teacher menu."

## Why this exists

Expense bills are created in the field: at a petrol pump, a lodge, a shop. Spec 015 gives the server side (claims, bills,
approval, payment) but a Manager or Teacher standing with a receipt in hand has only the web to use, and often no signal.
This spec puts the claim on the phone: photograph the bill, fill a few fields, send it, or save it on the phone and let it
send itself when the signal returns. It adds screens only. Every rule (categories, 60-day window, bill required, 5 bills,
10 MB, duplicates, links, approval, no amount limit) stays in spec 015 and the server stays the source of truth.

## Clarifications

### Session 2026-10-23

No questions were asked in this session. The points below were decided with documented defaults (see Assumptions) and are
listed under **Open Questions for the User** for confirmation before planning.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Raise an Expense with a Bill Photo (Priority: P1) 🎯 MVP

A Manager (or Teacher, Admin or Director) opens OPERATIONS → My Expenses in the app and adds a claim: amount, date,
category, area, note, and whether they paid it themselves. The screen shows the fields the server requires for the chosen
category (a reason for Other, an induction batch for Training Stay, an optional visit or drive for Travel and Fuel). They
photograph the bill with the camera, or pick an image from the phone, see a preview, may retake it, and then save as a
Draft or submit.

**Why this priority**: a claim with its bill, raised where the bill is, replaces carrying receipts to the web.

**Independent Test**: as a Manager with signal, raise a Travel and Fuel claim for 850 rupees dated yesterday for the
Manager's own Zone, photograph a bill, submit, and see it Submitted in the list and in the web My Expenses.

**Acceptance Scenarios**:

1. **Given** a signed-in claimant with the My Expenses entry in the menu, **When** they fill the fields, photograph a bill
   and submit, **Then** the claim is Submitted exactly as it would be from the web, with the bill kept on it.
2. **Given** the category Other, **When** the claimant leaves the reason empty, **Then** the screen marks the reason as
   required before sending, and if the server still refuses, its reason is shown and the entries are kept.
3. **Given** the category Training Stay, **When** the claimant opens the link field, **Then** open induction batches from the
   server are offered and one must be chosen.
4. **Given** the category Travel and Fuel and a date, **When** the claimant opens the link field, **Then** their planned
   visits, drives and tasks for that date are offered from the server, and choosing one is optional.
5. **Given** a taken photo, **When** the preview appears, **Then** the claimant can retake or remove it before sending.
6. **Given** a photo in HEIC or WebP form (or any camera format the server does not accept), **When** it is added, **Then**
   the app converts it to jpg and keeps it under 10 MB before upload, and the claimant sees no error.
7. **Given** no bill is available, **When** the claimant gives a stated "no bill" reason, **Then** the claim can be submitted
   as the server allows.
8. **Given** the server warns of a possible duplicate (same date, amount and category), **When** the claimant is asked,
   **Then** they may confirm and continue or stop.

---

### User Story 2 - Capture Offline and Send Later (Priority: P1)

With no signal, the claimant fills the claim and photographs the bill as usual. The claim and its photo are saved on the
phone as a Draft marked "waiting to send". When the connection returns the app sends them by itself. The claimant can see,
edit and delete waiting drafts. A draft the server refuses on sync stays on the phone with the server's reason, for
correction.

**Why this priority**: this is exactly where field bills are made. It is the first offline capture in the app and sets the
pattern for later specs.

**Independent Test**: turn the phone to aeroplane mode, capture a claim with a bill, see "waiting to send"; restore signal
and see it sent once and shown as Submitted, with no second copy.

**Acceptance Scenarios**:

1. **Given** no signal, **When** the claimant submits, **Then** the claim and photo are stored on the phone, marked "waiting
   to send", and nothing is lost if the app is closed.
2. **Given** waiting drafts and a restored connection, **When** the app runs, **Then** each is sent automatically in the
   order captured, and the mark clears when the server accepts it.
3. **Given** a send that is cut off part way and retried, **When** the server receives it again, **Then** only one claim
   exists (spec 015 FR-009; the same client request identity is reused on retry).
4. **Given** the server refuses a waiting draft (for example the date is now over 60 days old), **When** sync runs,
   **Then** the draft stays on the phone with the reason shown and can be corrected and sent again, or deleted.
5. **Given** waiting drafts, **When** the claimant opens the list, **Then** they are shown at the top with their mark and can
   be opened, edited or deleted; a count of waiting drafts is visible.
6. **Given** the phone is shared and the claimant signs out, or a different user signs in, **When** that happens, **Then**
   one user never sees or sends another user's waiting drafts (see Assumptions).

---

### User Story 3 - My Expenses: Track My Claims (Priority: P1)

The claimant sees their own claims, newest first, filtered by status (Draft, Submitted, Approved, Rejected, Paid, Paid by
company), with amount, category, date and the decision note, and the total still owed to them. A Rejected claim shows who
rejected it, when and why, and can be corrected and resubmitted as the same claim. A Submitted claim can be withdrawn
before a decision; a Draft can be deleted. Which buttons appear is decided by the server's `allowedActions`.

**Why this priority**: claimants stop using an app that does not tell them what happened to their money.

**Independent Test**: with one claim in each status, check each filter, the owed total, and that no one else's claim shows.

**Acceptance Scenarios**:

1. **Given** claims in several statuses, **When** the claimant opens My Expenses, **Then** only their own are shown, newest
   first, and the status filter narrows the list.
2. **Given** a Rejected claim, **When** it is opened, **Then** the reason and decision history are shown with a Correct and
   Resubmit button only if the server offers it.
3. **Given** a claim opened, **Then** its bills (viewable), link, history and, when paid, the payment details are shown.
4. **Given** a Submitted claim with no decision yet, **When** the claimant withdraws it, **Then** it becomes a Draft.
5. **Given** the phone is offline, **When** the list is opened, **Then** the last loaded list is shown marked as possibly out
   of date, and waiting drafts are shown.

---

### User Story 4 - Teacher Menu and Role Fit (Priority: P1)

My Expenses appears in the Teacher's menu (as spec 015 grants it) as well as for Manager, Admin and Director. System has
no expense entry. Entries and actions come only from the server's menu and `allowedActions`.

**Why this priority**: Teachers raise expenses under 015; without the entry the feature does not reach them.

**Independent Test**: sign in as a Teacher and see only My Expenses (no Expenses review); sign in as System and see none.

**Acceptance Scenarios**:

1. **Given** a Teacher, **When** the menu loads, **Then** OPERATIONS → My Expenses is offered and the Teacher can complete
   Stories 1 to 3.
2. **Given** a System user, **When** the menu loads, **Then** there is no expense entry, and direct access is refused.
3. **Given** an administrator removes the permission for a role, **When** the menu reloads, **Then** the entry disappears
   with no app update.

---

### User Story 5 - Review Pending Claims on the Phone (Priority: P3)

Where the server's menu offers Expenses to the user (Admin and Director), the approver sees submitted claims in their scope,
oldest first, opens one to see the bill photo, details and link, and approves, or rejects with a required reason. A user
never sees approve or reject on a claim they raised. Budgets, ageing, cost views, payment recording and export stay on the
web.

**Why this priority**: useful to approvers on the move but not needed to capture or track claims; it can be dropped without
affecting Stories 1 to 4.

**Independent Test**: as a Director, open a Submitted claim of a Manager with a bill, view the photo and approve; reject
another with a reason; confirm neither button appears on the Director's own claim.

**Acceptance Scenarios**:

1. **Given** submitted claims, **When** the approver opens the review list, **Then** they are shown oldest first with
   claimant, category, area, amount and age.
2. **Given** a claim opened, **When** the approver views the bill, **Then** the photo or pdf opens full screen with zoom.
3. **Given** the approver rejects, **When** no reason is entered, **Then** the action is blocked until a reason is given.
4. **Given** a claim already decided elsewhere, **When** the approver acts, **Then** the server's refusal naming who decided
   and when is shown in plain language and the list refreshes.
5. **Given** there is no amount limit in 015, **Then** the app applies no amount limit either.

---

### Edge Cases

- A photo larger than 10 MB after conversion: the app lowers its quality or size until it fits, or refuses with the reason.
- A photo that cannot be converted or read: the claimant is told and may retake it; the claim is unchanged.
- Camera or gallery permission denied: the app explains and offers the other source; the "no bill" reason remains available.
- Phone storage nearly full when saving a draft offline: the claimant is told that the draft was not saved.
- The sign-in session expires while drafts wait: drafts are kept and sent after the user signs in again as the same person.
- More than 5 bills: the sixth is refused with the reason (015 limit).
- The same bill sent twice after a retry: one claim only; a repeat claim with the same date, amount and category prompts the
  server's duplicate warning.
- A waiting draft whose linked visit or batch has since changed or closed: the server's refusal is shown and the claimant
  re-chooses the link.
- A claim edited on the web while a draft of the same claim waits on the phone: the server's version check refuses a stale
  update and the claimant sees the reason and the current values (see Assumptions).
- Time or date on the phone is wrong: date limits are decided by the server, not the phone.
- The location of the device cannot be read: the request carries the reason instead, as in spec 018, and nothing is blocked.

## Requirements *(mandatory)*

### Functional Requirements

**Raise and bills**

- **FR-001**: The app MUST let an entitled user add a claim with amount, date, category (the server's list), area, note and
  who paid, and save it as a Draft or submit it, using the spec 015 APIs unchanged.
- **FR-002**: The app MUST show the fields the server requires for the chosen category (reason for Other, batch for Training
  Stay, optional link for Travel and Fuel) and offer link choices from the server.
- **FR-003**: The app MUST let the user take a bill photo with the camera or pick an image or pdf, show a preview, and allow
  retake or removal before sending.
- **FR-004**: The app MUST convert photos that the file store does not accept (HEIC, WebP and other phone formats) to jpg and
  keep each file at or under 10 MB before upload; png, jpg and pdf pass as they are when within limits.
- **FR-005**: The app MUST compress bill images so that they stay legible (text readable) while keeping upload size small.
- **FR-006**: The app MUST honour the 015 limits (up to 5 bills per claim, "no bill" reason allowed, date within the last 60
  days, amount positive) and show server refusals in plain language while keeping all entered data.
- **FR-007**: The app MUST show the duplicate warning from the server and send the user's confirmation when they choose to
  continue.

**Offline**

- **FR-008**: When the phone has no usable connection, the app MUST save the claim and its photos on the device as a Draft
  marked "waiting to send".
- **FR-009**: The app MUST send waiting drafts automatically when a connection returns and when the app is opened, in the
  order captured, and clear the mark only when the server accepts them.
- **FR-010**: Each claim MUST carry a client request identity created when first captured and reused on every retry so a
  retried send never creates two claims (spec 015 FR-009).
- **FR-011**: The user MUST be able to see, open, edit and delete waiting drafts.
- **FR-012**: A draft refused by the server on sync MUST stay on the phone with the server's reason and a way to correct and
  resend or delete it; it MUST NOT be silently dropped.
- **FR-013**: Waiting drafts MUST be stored in protected app storage, belong to the user who captured them, and never be
  shown or sent under another user (see Assumptions for sign-out).
- **FR-014**: The server remains the source of truth; the app MUST NOT apply any rule that the server does not, other than
  clear, early hints.

**My Expenses**

- **FR-015**: The app MUST list the user's own claims newest first with a status filter, amount, category, date, decision
  note, and the total still owed.
- **FR-016**: A claim's detail MUST show its bills, link, decision history and, when paid, payment details.
- **FR-017**: The app MUST offer Edit, Delete, Withdraw, Correct and Resubmit, and Add Bill only when the server's
  `allowedActions` for that claim include them.
- **FR-018**: The last loaded list MUST remain readable offline, marked as possibly out of date.

**Menu and permissions**

- **FR-019**: Menu entries MUST appear only because the server's access model offers them (spec 018); My Expenses MUST appear
  for the Teacher, Manager, Admin and Director; System MUST NOT see any expense entry.
- **FR-020**: This spec MUST add no new permission keys and no new server rules; it uses `MY_EXPENSES` and `EXPENSES` from 015.

**Review (lowest priority)**

- **FR-021**: Where the server offers Expenses to the user, the app MUST list submitted claims oldest first, show the bill,
  and let the approver approve or reject (reason required), with buttons driven by `allowedActions`; a user MUST NOT be
  offered approval of their own claim.
- **FR-022**: Budget versus actual, ageing, per-School and per-Teacher cost, payment recording, reversal and export MUST NOT
  be on the phone in this version.

**Cross-cutting**

- **FR-023**: Every request MUST carry the device location or the reason it is missing, as in spec 018; location never blocks
  an action.
- **FR-024**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST be readable at
  large font sizes and usable with a screen reader.
- **FR-025**: Server refusals, permission refusals and network errors MUST be shown in plain language and MUST NOT lose what
  the user entered.
- **FR-026**: OCR or automatic reading of bills, push notifications, other offline features and iOS are out of scope.

### Key Entities

- **Expense claim**: owned by spec 015; the app shows and edits it only through the 015 APIs.
- **Bill**: an image or pdf stored with the claim in the shared file store; on the phone a bill is first a local file.
- **Waiting draft**: the phone's own record of a claim not yet accepted by the server: its fields, its photos, its client
  request identity, its owner, its state (waiting, sending, refused with a reason) and its capture time. It exists only on
  the phone until the server accepts it.
- **Link option**: a visit, drive, task or induction batch offered by the server for a category and date.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | OPERATIONS → My Expenses; OPERATIONS → Expenses (review list only on the phone) | My Expenses: View, Create, Edit, Delete; Expenses: View, Approve (as 015 grants) | Own claims; org-wide for review |
| Director | OPERATIONS → My Expenses; OPERATIONS → Expenses (review list only on the phone) | As Admin, without Process | Own claims; org-wide for review |
| Manager | OPERATIONS → My Expenses | View, Create, Edit (own Draft and Rejected), Delete (own Draft) | Own |
| Teacher | OPERATIONS → My Expenses (added to the Teacher menu) | As Manager | Own |
| System | none (System MUST NOT see business data) | none | None |

No new permission keys: this spec uses `MY_EXPENSES` and `EXPENSES` added by spec 015. Only Admin, Director and System edit
the role matrix. The Teacher menu entry is seeded by this spec only if 015 has not already seeded it for the mobile menu
(see Open Questions). The mobile client reads entries and `allowedActions` from the server and hard-codes none.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A claimant can raise a claim with a bill photo in under 90 seconds on a phone with signal.
- **SC-002**: 100% of claims captured offline are sent once, with no duplicate claim, after signal returns and the app is
  opened.
- **SC-003**: 100% of HEIC and WebP photos picked or taken are accepted after conversion; no claimant is shown a file-type
  error for a common phone photo.
- **SC-004**: No uploaded bill photo exceeds 10 MB and bill text stays readable in the approver's view.
- **SC-005**: A refused waiting draft is always visible with its reason; none is lost silently.
- **SC-006**: A user never sees another user's claim, bill or waiting draft, including on a shared phone.
- **SC-007**: A Teacher can reach My Expenses in the app; a System user cannot reach any expense screen.
- **SC-008**: Claims raised on the phone and on the web look the same to the approver and in reports.

## Assumptions

- Spec 015 contracts (`/my-expenses`, `/expenses`, bills, link options) are final and unchanged; any gap found is raised
  against 015, not worked around.
- Spec 015 (Clarification 2026-10-18) lets Teachers raise claims and adds My Expenses to the Teacher menu. This overrides the
  input note that Teachers have no mobile expense screens.
- Drafts live in the app's encrypted storage. On sign-out the drafts of that user stay on the phone, hidden, and are sent
  only after the same user signs in again; a different user signing in never sees them. The user is warned at sign-out that
  unsent claims remain.
- The expense date limit on the phone follows the server (future not allowed, no more than 60 days back); the phone shows the
  rule early but the server decides.
- Photo limits: jpg, png or pdf after conversion, at most 10 MB each, at most 5 per claim (015); long edge reduced to about
  2000 pixels with good quality.
- Duplicate detection is the server's (same claimant, date, amount, category) plus the client request identity on retry; the
  app does not compare photos.
- A sync conflict is decided by the server's `version` check: the server wins, the user sees the reason and current values.
- The phone approver view is included as the lowest-priority story, reading from the web Expenses permission; it can be
  removed to leave web-only review.
- There is no limit on the number of waiting drafts other than phone storage.
- Language and format follow spec 018 (English, rupees, DD/MM/YYYY).

## Open Questions for the User

1. **Draft storage on sign-out**: keep a signed-out user's unsent drafts on the phone (default, hidden from others, sent after
   the same user signs in) or delete them at sign-out?
2. **Phone approval**: keep Story 5 (approve or reject on the phone, Director and Admin) in version 1 (default), or web-only?
3. **Photo limits**: confirm the 10 MB, 5 bills, jpg/png/pdf limits from 015 and the default long edge of about 2000 pixels.
4. **Date cap on the phone**: confirm that the phone only echoes the server's 60-day rule rather than a stricter local cap.
5. **Sync conflict**: confirm the server wins when a claim was edited on the web while a draft waits.
6. **Teacher menu seed**: confirm 015 seeds My Expenses for Teacher on the mobile menu, or whether 036 must add it.
7. **Duplicate photo**: is the server's date, amount and category warning enough, or should the same photo sent twice also be
   caught?
