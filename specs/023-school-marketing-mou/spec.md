# Feature Specification: School Marketing and MoU Pipeline

**Feature Branch**: `023-school-marketing-mou`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "023-school-marketing-mou: HLS's marketing team visits schools to sign MoUs for placing its recruited Teachers. School prospects, a marketing calendar of visits and meetings by the Director, Zone Managers and the marketing team, a pipeline from first contact to won or lost, the proposed MoU terms (number of Teachers; one salary for all or a different salary per Teacher), and a hand-off to record the signed MoU in spec 012. Module `recruitment.marketing`. Full source: docs/spec-inputs/023-school-marketing-mou.md."

## Clarifications

### Session 2026-10-05

- Q: How does this relate to the MoU (012) and to recruitment (016)? → A: This spec is the sales side up to the signature. The signed MoU, its signatories and the mapping of Teachers are spec 012, the only contract record; this spec keeps a link to it and shows its status. Recruitment (016) supplies the Teachers; the two run in parallel and meet only in the demand-versus-supply view.
- Q: Who is "the marketing team"? → A: People with the existing roles: Admin, Director, Zone Manager. No new role is added (the constitution fixes five roles). If separate marketing people need logins, they are given one of those roles through User Management (spec 004).
- Q: How long before a won prospect without an MoU is flagged, and who is told? → A: The number of days is a setting
  (default 14), editable by Admin and Director on a Marketing Settings screen and audited; spec 011 may absorb it
  later. When a won prospect passes the limit with no MoU recorded, it is flagged on the board and a notification
  (spec 010) goes once to the prospect's owner, the Zone Manager of its Zone, and every Admin and Director, since those
  last two can record the MoU.
- Q: How is a renewal handled? → A: As a new prospect linked to the same School record, so the earlier MoU and its
  history stay untouched.
- Q: Who records the MoU after a win? → A: Only Admin and Director, as in spec 012. A Zone Manager can mark a prospect as won and sees the MoU status, but cannot record the contract.
- Q: Which pipeline stages are used? → A: The deck's stages plus two (decided 2026-10-05): Prospect, Contacted, Visit,
  Follow-up, Interested, Negotiation, Final Stage, MoU and Active, plus On Hold and Lost (a reason is required for
  Lost; On Hold can return to its previous stage). **Final Stage** means the School has agreed in principle and needs a
  management review: a Director or the Zone Manager of its Zone approves it (decision D5). A prospect whose Final Stage
  is approved is called **won** in this spec; that opens the hand-off to the MoU form (spec 012). The stage becomes
  **MoU** when the MoU is recorded in 012, and **Active** when the first Teacher is placed at the School.
- Q: Who earns the ₹5,000 incentive for a closed School? → A: The prospect's owner (decided 2026-10-05). When the MoU is
  recorded and approved (decisions D5, D9), this spec supplies the owner and the School to the incentive spec (030);
  ownership changes are audited, so the owner at that time is the one named.
- Q: How are visits tracked? → A: Each visit has a status: planned, completed, missed, rescheduled or cancelled
  (decided 2026-10-05). A visit cannot be completed without an outcome (people met, discussion, next action). A planned
  visit not done by its date shows as missed until it is rescheduled or cancelled with a reason; a rescheduled visit
  keeps the original date in its history.
- Q: Do visits take attachments in version 1? → A: Yes (decided 2026-10-05). A visit can carry files (photos, brochures,
  notes). The file storage this needs is built here as a shared capability, so expense bills (spec 015), check-in photos
  (spec 032) and MoU documents (spec 031) reuse it instead of building their own; the roadmap tracks this.
- Q: Do follow-ups become tasks? → A: Yes (decided 2026-10-05). When a visit outcome sets a follow-up date, a task is
  created for the prospect's owner through the shared task interface of spec 025 (contract C2), and 025's overdue reason
  and escalation apply. Until 025 exists the prospect only shows "follow-up overdue".
- Q: Are visits part of the shared planned-activity calendar? → A: Yes (contract C1). A visit is exposed as a planned
  activity (kind, owner, date, place, status) so Manager attendance (spec 032), travel claims (spec 015) and the
  dashboards read visits the same way as drives and tasks; a school visit is a field-day activity for 032.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Track School Prospects and Visits (Priority: P1) 🎯 MVP

A Director, Zone Manager or Admin records a School prospect (name, board, address, Zone, contact person and
designation, phone, expected number of Teachers, owner) and logs activity against it: planned and completed visits,
calls, proposal meetings and follow-ups, each with a date, the HLS people attending, notes and an outcome. A calendar
(month and list) shows the activity, with the person's own entries and overdue follow-ups highlighted.

**Why this priority**: School visits are how HLS wins every MoU. Without a record of who is approaching which school,
the rest of the flow has nothing to build on.

**Independent Test**: Add a prospect in a Zone, log a planned visit by two people and a completed call with an
outcome and a follow-up date, and see them on the calendar, with the follow-up flagged once its date has passed.

**Acceptance Scenarios**:

1. **Given** no prospects, **When** a Zone Manager adds a prospect in their Zone with contact details, **Then** it is
   saved with the Zone Manager as owner and appears in the prospect list.
2. **Given** a prospect, **When** a visit is planned with a date and attendees, **Then** it shows on each attendee's
   calendar as planned, and when completed it records notes and an outcome.
3. **Given** an outcome with a follow-up date, **When** the date passes with no later activity, **Then** the prospect
   is shown as "follow-up overdue".
4. **Given** a prospect with the same name, board and Zone as an existing one, **When** it is added, **Then** it is
   refused as a duplicate and the existing one is shown.
5. **Given** a Zone Manager, **When** they open prospects, **Then** they see only those in their own Zones; a Director
   and Admin see all.
6. **Given** a person who is on a recruitment drive (spec 016) on the same date as a planned school visit,
   **When** they open their calendar, **Then** both appear so a clash is visible (shown only, not blocked).
7. **Given** a planned visit, **When** it is marked completed, **Then** an outcome (people met, discussion, next
   action) is required; without one it is refused. A planned visit whose date has passed shows as missed until it is
   rescheduled (the earlier date stays in its history) or cancelled with a reason.
8. **Given** a visit, **When** a file (photo, brochure or note) is added, **Then** it is stored with who added it and
   when, can be opened by anyone who may see the visit, and is never changed; a wrong file is removed by an Admin or
   Director with the removal recorded.
9. **Given** an outcome with a follow-up date, **When** it is saved and spec 025 is present, **Then** a task is
   created for the prospect's owner with that due date; if 025 is absent only the "follow-up overdue" flag applies.

---

### User Story 2 - Move Prospects Through the Pipeline (Priority: P1)

Each prospect has a stage: Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, MoU and Active,
or On Hold or Lost (with a reason). A board and a list show prospects by stage, owner and Zone with the next-action date. Stage changes record
who and when; a lost prospect can be reopened.

**Why this priority**: The pipeline is how the Director sees how many Schools are likely to sign and where deals stall.

**Independent Test**: Move a prospect from identified to proposal sent, then lost with a reason, then reopen it, and
see the board and the history show each step.

**Acceptance Scenarios**:

1. **Given** a prospect, **When** its stage is changed, **Then** the board updates and the history records who, when
   and the previous stage.
2. **Given** a prospect set to "lost", **When** saved, **Then** a reason is required, and it leaves the board's active
   columns but stays in the list under "lost".
3. **Given** a lost prospect, **When** it is reopened, **Then** it returns to its last active stage and the history
   keeps the loss and the reopening.
4. **Given** the board, **When** filtered by Zone, owner or stage, **Then** only matching prospects show and the
   counts match.
5. **Given** a Zone Manager, **When** they move a prospect in another Zone, **Then** it is refused (not found).
6. **Given** a prospect in Final Stage, **When** a Director or the Zone Manager of its Zone approves it, **Then** it is
   won and the hand-off opens; until approved it cannot be handed off; a rejected review returns it to Negotiation
   with the reason.
7. **Given** a prospect, **When** it is put On Hold, **Then** it leaves the active columns, keeps its stage, and
   returns to that stage when resumed; the history shows the hold and the resume.
8. **Given** the MoU is recorded in spec 012, **When** the prospect is opened, **Then** its stage is MoU, and it
   becomes Active when the first Teacher is placed at the School.

---

### User Story 3 - Draft the Proposed MoU Terms (Priority: P1)

On a prospect, the user records the proposal to the School: number of Teachers, a start month, and the salary basis,
either **one monthly amount for all Teachers** or **a different monthly amount for each Teacher** (an amount per
position), with notes. Each revision is kept, the latest is current, and the proposal is clearly a draft, not the
contract.

**Why this priority**: The terms discussed with the School are what the MoU is later signed on; capturing them here
avoids re-typing and shows the Director the expected size and value of each deal.

**Independent Test**: Record a proposal for 4 Teachers at one amount, revise it to 5 Teachers with different amounts
per position, and see both revisions with the latest marked current.

**Acceptance Scenarios**:

1. **Given** a prospect, **When** a proposal is saved for 4 Teachers at one amount, **Then** it is stored as revision
   1 with the amount, start month and notes.
2. **Given** "different for each Teacher", **When** saved, **Then** an amount is required for each of the N
   positions, each positive, before it can be saved.
3. **Given** an existing proposal, **When** it is changed, **Then** a new revision is created and the old one stays visible, unchanged.
4. **Given** a prospect, **When** the proposed value is shown, **Then** it displays the Teacher count and the monthly
   total the School would pay, worked out from the amounts.
5. **Given** a proposal, **When** viewed anywhere, **Then** it is labelled "Proposal (not a contract)".

---

### User Story 4 - Win the School and Hand Off to the MoU (Priority: P1)

When the School agrees, the user moves the prospect to Final Stage, and once a Director or the Zone Manager of its Zone approves it, the prospect is won. If the prospect is not yet a School in spec 005, it is
created as a School (in its Zone) at that point, by an Admin or Director. An Admin or Director is then taken to record
the MoU in spec 012, pre-filled from the latest proposal (School, Teacher count, salary mode and amounts, start date).
The signed MoU, with its signatories and signed date, stays in spec 012; this spec keeps only a link to it and shows
its status on the prospect: MoU recorded, how many Teachers are mapped, how many positions are vacant.

**Why this priority**: This is the point of the whole sales flow: a signed MoU that the rest of the system, mapping,
attendance and billing, can use. It is also the one story that needs spec 012 to be merged.

**Independent Test**: Mark a prospect with a proposal as won, follow the hand-off to the MoU form, see it pre-filled,
save the MoU, and see the prospect show "MoU recorded, 0 of 4 positions filled".

**Acceptance Scenarios**:

1. **Given** a prospect with a proposal, **When** it is marked won by an Admin or Director, **Then** the School
   exists (created if new) and a "Record MoU" action opens spec 012's form pre-filled from the latest proposal.
2. **Given** a Zone Manager, **When** they approve the Final Stage of a prospect in their Zone, **Then** it is won
   ("won, MoU to be recorded") and an Admin or Director is asked to record the contract; the Zone Manager cannot
   record it.
3. **Given** the MoU recorded in spec 012, **When** the prospect is opened, **Then** it shows the contract's dates,
   positions, and how many are filled and vacant, and "MoU recorded".
4. **Given** the signed MoU differs from the proposal, **When** saved, **Then** it is accepted as signed; the proposal
   is not changed and the difference is visible side by side.
5. **Given** a won prospect whose MoU has not been recorded for more than the configured number of days (default 14),
   **When** the board is opened, **Then** it is flagged "MoU not yet recorded", and the owner, the Zone Manager and
   every Admin and Director receive one notification linking to the prospect.
5a. **Given** the MoU is then recorded, **When** the prospect is opened, **Then** the flag is gone; the notification
   is not repeated for the same prospect unless the limit is changed and passed again.
6. **Given** spec 012 is not available, **When** the prospect is won, **Then** it is saved as won with "MoU to be recorded", and the hand-off is hidden.

---

### User Story 5 - See Demand and Supply (Priority: P2)

An Admin or Director opens a dashboard: visits this month, prospects by stage, win rate, Schools won per Zone and per
owner, and, for won Schools, how many positions still need Teachers (demand) next to how many recruits are ready to
deploy (supply, from spec 016). A Zone Manager sees the same limited to their own Zones.

**Why this priority**: It tells the Director whether recruitment is keeping up with the Schools won; it depends on the
stories above and on 012 and 016 for its numbers.

**Independent Test**: With three won Schools holding 9 vacant positions and 5 ready-to-deploy recruits, see demand 9,
supply 5 and a shortfall of 4.

**Acceptance Scenarios**:

1. **Given** data across Zones, **When** the dashboard is opened, **Then** visits, stage counts and win rate match
   the underlying lists for the chosen period.
2. **Given** won Schools with vacant positions, **When** opened, **Then** demand equals the total vacant positions
   and supply equals recruits ready to deploy; the shortfall is shown.
3. **Given** a Zone Manager, **When** they open it, **Then** counts cover only their Zones.
4. **Given** no data, **When** opened, **Then** an empty state explains how to start.
5. **Given** spec 016 not yet available, **When** opened, **Then** supply shows "not available" and demand still works.

---

### Edge Cases

- A prospect's owner leaves or changes: ownership can be reassigned by Admin or Director; the history keeps the previous owner.
- A prospect becomes a School record created by someone else in spec 005 meanwhile: the user is asked to link to it, not create a duplicate.
- A prospect is won but the School's Zone has no Zone Manager: the MoU cannot be recorded in 012 until one is assigned (spec 005); the prospect shows why.
- A renewal or extension after an MoU ends: a new prospect for the same School, linked to the existing School record.
- A visit is logged for a School that already has an MoU (an account visit): allowed, logged against the School without a proposal.
- Amounts are rupees with Indian digit grouping and two decimals; dates DD/MM/YYYY.
- Two users move the same prospect's stage at once: the later request is told it changed and shown the current stage.

## Requirements *(mandatory)*

### Functional Requirements

**Prospects, visits and calendar**

- **FR-001**: The system MUST let an Admin, Director or Zone Manager create a School prospect with name, board, address, Zone, contact person, designation, phone, expected Teachers and owner, refusing a duplicate (same name, board and Zone) and linking to an existing School of spec 005 when there is one.
- **FR-002**: The system MUST let them log activity on a prospect or School: type (visit, call, proposal meeting, follow-up), status (planned, completed, missed, rescheduled, cancelled; an outcome is required to complete), date, HLS attendees, notes, outcome and an optional follow-up date; and MUST show it on a month calendar and list with the person's own entries and overdue follow-ups highlighted.
- **FR-003**: The system MUST show a person's school visits and their recruitment drives (spec 016, when present) together so clashes are visible, without blocking either.

**Pipeline**

- **FR-004**: Each prospect MUST have a stage (Prospect, Contacted, Visit, Follow-up, Interested, Negotiation, Final Stage, MoU, Active, On Hold, Lost). Every change MUST record who and when; Lost MUST require a reason; a lost or on-hold prospect MAY be reopened to its last active stage, with the history kept. Final Stage MUST need at least one proposal revision and MUST be reviewed and approved by a Director or the Zone Manager of its Zone before the prospect is won; the stage MUST become MoU when the MoU is recorded in spec 012 and Active when the first Teacher is placed.
- **FR-005**: The system MUST show prospects as a board and a list by stage, with filters by Zone, owner and stage, and the next-action date.

**Proposal**

- **FR-006**: The system MUST let users record proposed terms on a prospect: number of Teachers (1 to 500), start month, and salary basis (one monthly amount for all, or an amount for each position), each amount positive with two decimals, plus notes.
- **FR-007**: Proposal revisions MUST be kept, never overwritten; the latest is current; the proposal MUST be shown as a draft and not a contract; the monthly total MUST be worked out from the amounts, not typed.

**Win and hand-off**

- **FR-008**: The system MUST let a Director or the Zone Manager of its Zone approve a prospect's Final Stage, which makes it won. If it is not yet a School in spec 005, an Admin or Director MUST create it (in the prospect's Zone) as part of winning; a Zone Manager's win MUST be accepted as "won, MoU to be recorded".
- **FR-009**: For a won prospect, an Admin or Director MUST be able to open the MoU form of spec 012 pre-filled from the latest proposal; the contract MUST be recorded only in spec 012 (with its signatories and signed date) and this spec MUST NOT hold a copy of it.
- **FR-010**: The system MUST keep the link between a prospect and its contract and MUST show, from spec 012's public interface, whether a contract exists, its dates, and how many positions are filled and vacant; a won prospect without a contract after the configured number of days MUST be flagged and notified (FR-017).

**Dashboard, scope, audit**

- **FR-011**: The system MUST show visits this month, prospects by stage, win rate, Schools won per Zone and per owner, and for won Schools the vacant positions (demand) against recruits ready to deploy (supply, spec 016) with the shortfall.
- **FR-012**: Every list, search and detail MUST be filtered by the caller's scope: Admin and Director org-wide; a Zone Manager only their Zones' prospects (Constitution Principle III), with no leakage between Zone Managers.
- **FR-013**: Every prospect, activity, stage change, proposal revision, win, loss, reassignment and hand-off MUST be written to the audit store (spec 003) with actor, roles, time, and prior and new values (Constitution Principle I).
- **FR-014**: Menu items and actions MUST be offered only when the server's access model grants them, and every endpoint MUST be tested per role and per scope boundary.
- **FR-015**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST meet WCAG 2.2 AA, have loading, empty and error states, and work at phone width.
- **FR-017**: The number of days after which a won prospect with no MoU is flagged MUST be a setting (default 14, a
  whole number from 1 to 90) that an Admin or Director can change on a Marketing Settings screen; each change MUST be
  audited with the prior and new value. A daily check MUST create one in-app notification (spec 010) per overdue
  prospect for its owner, the Zone Manager of its Zone, and every Admin and Director, and MUST NOT repeat it for the
  same prospect unless the limit is changed and passed again.
- **FR-018**: Each visit MUST carry a status (planned, completed, missed, rescheduled, cancelled); completing MUST need an outcome; a planned visit past its date MUST show as missed until rescheduled or cancelled with a reason; the history MUST keep earlier dates.
- **FR-019**: A visit MUST accept file attachments (photos, brochures, notes) stored with who added them and when, visible to anyone who may see the visit, never changed, removable only by an Admin or Director with the removal recorded. The file storage MUST be a shared capability that other specs (015, 031, 032) can reuse.
- **FR-020**: When a visit outcome sets a follow-up date and spec 025 is present, the system MUST create a task for the prospect's owner through the shared task interface; without 025 only the "follow-up overdue" flag applies.
- **FR-021**: Visits MUST be exposed to other modules as planned activities (kind, owner, date, place, status) through a public interface, so a school visit can be the activity a Manager's field day links to (spec 032).
- **FR-022**: When the MoU of a prospect's School is recorded and approved, the system MUST make the prospect's owner and School available to the incentive spec (030) through a public interface; this spec pays nothing.
- **FR-016**: The system MUST function without spec 012 or 016 being present: the calendar, pipeline and proposals work on their own; the hand-off, MoU status, demand and supply appear only when those specs are available.

### Key Entities *(include if feature involves data)*

- **School Prospect**: a School HLS is approaching: contact details, Zone, owner, stage, link to a School record (spec 005) and to the contract (spec 012) once they exist.
- **Marketing Activity**: a planned or completed visit, call, proposal meeting or follow-up, with attendees, notes, outcome, follow-up date.
- **Proposal Revision**: a draft of the MoU terms: Teacher count, start month, salary basis and amounts; each revision kept.
- **Stage History**: who moved a prospect between stages, when, and why (for lost or on hold).
- **Visit Attachment**: a file added to a visit: name, type, size, who and when; never changed.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | MARKETING → Prospects, Calendar, Pipeline, Dashboard, Settings | View, Create, Edit; create School on win; record MoU (via 012) | Org-wide |
| Director | MARKETING → Prospects, Calendar, Pipeline, Dashboard, Settings | View, Create, Edit; create School on win; record MoU (via 012) | Org-wide |
| Manager (Zone Manager) | MARKETING → Prospects, Calendar, Pipeline, Dashboard | View, Create, Edit; mark won; no School creation, no MoU | Assigned (own Zones) |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: module `MARKETING` with actions `VIEW`, `CREATE`, `EDIT` (prospects, activities, proposals,
stages, and the Marketing Settings screen, which only Admin and Director are seeded to edit) and `APPROVE` (the Final
Stage review, decision D5), seeded to the Director and the Zone Manager and not to the Admin; the Zone Manager's
approval is limited to prospects in their own Zones. Seeded as in the table; not eligible for Teacher or System. Creating a School on a win reuses the existing
`SCHOOLS` `CREATE` permission, and recording the MoU reuses `SCHOOL_CONTRACTS` `CREATE` (spec 012); neither is changed
here. Runtime-editable in Role & Permissions; only Admin, Director and System edit the matrix. The constitution's
Default role access matrix gets a Marketing row when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Zone Manager sees none of another Zone Manager's prospects, activities or proposals in any list, search or detail; proved by a test for each endpoint.
- **SC-002**: A proposal revision is never changed after it is saved; every change is a new revision, and the current one is always the latest.
- **SC-003**: A prospect marked won leads to a pre-filled MoU form in 012 in one step, and the contract is held only in 012 (no contract data is stored in this module).
- **SC-004**: Every stage change, win, loss, proposal revision and hand-off has an audit entry with actor and prior and new values.
- **SC-005**: Dashboard counts and the demand and supply figures match the underlying lists and spec 012 and 016 data exactly.
- **SC-006**: A user can log a visit with its outcome in under 1 minute and record a proposal in under 2 minutes.
- **SC-007**: With spec 012 or 016 absent, calendar, pipeline and proposals still work and nothing errors.
- **SC-009**: A visit cannot be completed without an outcome, and a planned visit past its date is shown as missed; proved by tests, with attachments stored, shown and removable only as specified.
- **SC-008**: A won prospect without a recorded MoU past the configured number of days is flagged on the board and notified once to its owner, its Zone Manager and every Admin and Director; changing the setting changes the flag and the next notification, with an audit entry.

## Assumptions

- Spec 005 (Schools, Zones, Zone Managers), spec 003 (audit), spec 004 (users and roles) and spec 010 (notifications) are implemented and merged. Spec 012 is needed only for User Story 4's hand-off and MoU status, and spec 016 only for the supply figure in User Story 5; both stories degrade gracefully (FR-016).
- "Marketing team" means users with the existing roles; no new role is added (see Clarifications).
- Prospects are scoped by Zone; a Director or Admin works across Zones; a Zone Manager only in their own.
- A prospect has one owner at a time, changeable by Admin or Director; several people may attend one visit.
- The "MoU not yet recorded" limit is a setting of this module (default 14 days); spec 011 (system settings) may later take it over. Notifications use the in-app channel of spec 010 (SMS and push later).
- A proposal's amounts are only a draft; the signed MoU (spec 012) is free to differ, and the difference is shown.
- A School is created in spec 005 only when a prospect is won, not before.
- "Won" means the Final Stage was approved (see Clarifications); the incentive is earned later, when the MoU is recorded and approved (decision D9), by the prospect's owner.
- The shared file storage is built in this spec and reused by 015, 031 and 032; its limits (file types and size) are decided in the plan.
- A renewal is a new prospect linked to the existing School (see Clarifications).
- Out of scope: a full CRM (email, campaigns, quotes), uploading the signed MoU, e-signature, billing (spec 022), mobile screens.
