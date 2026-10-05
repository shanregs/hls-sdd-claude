# Feature Specification: Campus Recruitment, Job Offers and Induction

**Feature Branch**: `016-campus-recruitment`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "016-campus-recruitment: HLS recruits teachers from colleges. Admin, Director and Zone Managers schedule campus drives to colleges; candidates are recorded with an interview outcome; selected candidates get a Job Offer with a package (offered, accepted, declined, expired); an accepted offer creates the Teacher record (spec 005) with no re-entry and enrols them in the next one-month induction batch; induction has attendance and a sign-off, after which the Teacher is active and ready to be mapped to a School's MoU position (spec 012). A dashboard shows drives, candidates, offers and joiners per college and season. Module `recruitment` plus the induction part of `training`. Full source: docs/spec-inputs/016-campus-recruitment.md."

## Clarifications

### Session 2026-10-05

- Q: How does this relate to the school side? → A: HLS runs two activities in parallel. This spec supplies the
  Teachers (colleges, offers, induction); school marketing (spec 023) wins the Schools; the signed MoU and the
  mapping of Teachers to its positions are spec 012. This spec never records a School, an MoU or a mapping.
- Q: Which Teacher statuses are used? → A: The existing ones of spec 005 (in training, active, on leave, exited);
  no new status is added. An accepted offer creates the Teacher "in training"; a signed-off induction makes the
  Teacher "active", which is what "ready to deploy" means (active and not yet mapped to a School).
- Q: Who sends and accepts offers? → A: For now the Director only. Admin can view offers. This is the seeded default of
  the permission matrix, so it can be widened later in Role & Permissions without a code change.
- Q: What is a recruitment season? → A: A flexible label chosen on each drive (for example "2026-27"), not fixed dates.
  The dashboard filters by that label or by a date range.
- Q: What if the candidate is already a Teacher? → A: If the matching Teacher is not exited (working, on leave, in
  training, or assigned to a School), the offer cannot be accepted and the candidate is not mapped or linked; the user
  is told who the existing Teacher is. If the matching Teacher has exited, the user may confirm creating a new Teacher
  record; the exited record stays as it is.
- Q: Where is a recruit's daily induction attendance stored? → A: In spec 008's attendance records, as a training-day
  mark (code T) for the Teacher on that date, written through a public interface of the `attendance` module. There is
  no separate induction register. A recruit has no School, so spec 008 must accept a training-day mark with no School;
  this is a small change to 008 made when this spec is implemented, and ordinary school marks still need a School.
- Q: Can a Zone Manager see drives they did not schedule and are not an interviewer on? → A: Yes. Every Zone Manager
  can see all drives, candidates, outcomes and offers, read-only for those they did not schedule and are not an
  interviewer on. They can change only drives they scheduled or attend, and the candidates and outcomes on them.
  Recruitment data is not Zone-bound (colleges are not in a Zone), so Principle III's Zone scope does not apply to it.
- Q: What happens to the induction days already recorded when a recruit who did not complete moves to the next batch?
  → A: They stay as recorded, because they happened. The Teacher stays "in training" and is enrolled in the next batch
  for its full dates; each enrolment has its own sign-off, so the earlier "not completed" result stays visible.
- Q: Is the induction paid? → A: No (decided 2026-10-05, Director deck: training is unpaid, decision D2). The offer has no stipend field, payroll pays nothing for induction days, and the earlier optional stipend is removed.
- Q: How long are the personal details of rejected, declined or expired candidates kept? → A: Indefinitely, as history.
  Nothing is cleared automatically and no removal feature is built in this spec. (A retention or removal rule for
  candidate data can be added later if HLS needs one.)
- Q: When does the Teacher's salary start, and how is it recorded? → A: When the Teacher reports to the School (the first
  School assignment in spec 012), not at acceptance (D2). Acceptance creates the Teacher without a salary history entry;
  the offered monthly salary stays on the accepted offer. When the Teacher is first assigned to a School (under a contract, with or without a position yet), the
  system writes the accepted offer's monthly salary to the Teacher's salary history (spec 005) with the assignment's
  start date as the effective date, with no retyping (decided 2026-10-05); an Admin or Director can correct it. A
  trained Teacher may wait unplaced for any length of time and is not paid until then. Spec 012 publishes an event on a
  Teacher's first assignment for this (a small addition to 012, listed in Assumptions).
- Q: Is the offer letter generated here? → A: Yes. The offer is turned into a printable letter (candidate name,
  designation "Trainee / English Trainer", package, terms, training information, expected joining), and its status is
  tracked: draft, issued, accepted, declined, expired, superseded ("pending" is an issued offer awaiting a reply).
  E-mailing the letter and e-signature are out of scope.
- Q: Is a student who accepts an offer a user who can log in? → A: No. The Teacher record created on acceptance is
  master data only. A login is created by an Admin or Director through User Management (spec 004), as today.
- Q: Does the interview record a structured assessment? → A: Yes (decided 2026-10-05). Besides the outcome, each candidate
  gets a score for each of a short fixed set of criteria (group speaking, English, communication) and remarks, with who
  assessed and when; the outcome stays selected, waitlisted or rejected. The Director sees assessed versus selected.
- Q: Where are food and accommodation costs during induction recorded? → A: Only in Expenses (spec 015), linked to the
  induction batch (decided 2026-10-05). Induction has no cost fields here; this spec exposes the batch so a Training Stay
  expense can be linked to it, and cost per Teacher trained comes from expenses.
- Q: How far does the recruitment funnel go? → A: Through placement (decided 2026-10-05). The dashboard shows drives,
  assessed, selected, offered, accepted, inducted, ready to deploy, placed in a School (spec 012) and active, and the
  joining ratio: candidates who actually joined divided by those selected. These are read from existing data.
- Q: Are drives part of the shared planned-activity calendar? → A: Yes (contract C1 in the roadmap). A drive is exposed
  as a planned activity (kind, owner, date, place, status) so Manager attendance (spec 032), travel claims (spec 015)
  and the dashboards read drives the same way as visits and tasks.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Schedule Campus Drives and Record Candidates (Priority: P1) 🎯 MVP

An Admin, Director or Zone Manager schedules a campus drive: the college (picked from a reusable College list, or
added on the spot), the date or dates, the venue and the HLS staff attending as interviewers. A calendar (month and
list) shows the drives. For a drive, candidates are recorded one by one or imported from a sheet (name, phone, email,
degree, year of study or passing, notes), and each gets an interview outcome: selected, waitlisted or rejected, with
who recorded it and when. A waitlisted candidate can be moved to selected later.

**Why this priority**: This is the front of the recruitment flow. Without drives and candidates there is nothing to
offer.

**Independent Test**: Schedule a drive at a college for two dates with two interviewers, import 10 candidates, mark 4
selected, 2 waitlisted and 4 rejected, then see the drive on the calendar and the outcome counts on it.

**Acceptance Scenarios**:

1. **Given** no drives, **When** a Zone Manager schedules a drive with a college, dates, venue and interviewers,
   **Then** it appears on the calendar as "planned" and on the college's history.
2. **Given** a college not yet in the list, **When** it is added while scheduling, **Then** it is saved once and
   offered next time; a second entry with the same name and city is refused as a duplicate.
3. **Given** a drive, **When** a sheet of 10 candidates is imported, **Then** valid rows are saved, invalid rows are
   listed with the reason, and nothing is saved twice if the same sheet is imported again (same phone for the same drive).
4. **Given** a candidate, **When** an outcome is recorded, **Then** it shows who recorded it and when, and a later
   change keeps the earlier one in the history.
4a. **Given** a candidate, **When** the interviewer scores each criterion (group speaking, English, communication) and
   records remarks with the outcome, **Then** the scores, the assessor and the time are saved and shown on the candidate
   and in the drive's results; a later change keeps the earlier scores in the history.
5. **Given** a drive date in the past with no candidates, **When** the calendar is opened, **Then** it shows as
   "held, no candidates" so it can be completed; a drive can be cancelled with a reason.
6. **Given** a Zone Manager, **When** they open a drive they are not an interviewer on and did not schedule, **Then**
   they can see it and its candidates and outcomes but cannot change anything on it; on their own drives they can.

---

### User Story 2 - Send, Track and Accept Job Offers (Priority: P1)

A Director generates and issues a Job Offer to a selected candidate: role, the package (the monthly salary HLS pays, allowances, any notice or bond terms; induction is unpaid), offer date and a response deadline. The system produces a printable offer letter from these
details (designation "Trainee / English Trainer", training information, expected joining). The offer moves draft →
issued → accepted, declined or expired. Offers are never edited after they are issued (a draft can be changed and the letter regenerated): a changed package is a new
offer that replaces the old one, and the old one stays visible. Acceptance is recorded by the Director on the
candidate's reply.

**Why this priority**: The offer with its package is what turns a candidate into a hire, and the salary agreed here
is what is recorded when the Teacher starts at a School.

**Independent Test**: Send an offer with a package, record acceptance, then see the offer accepted and the package
kept; send a revised offer to another candidate and see the first one superseded; let a third pass its deadline and see it expire.

**Acceptance Scenarios**:

1. **Given** a selected candidate, **When** a Director sends an offer with role, package, offer date and deadline,
   **Then** it is saved as "offered" with the package and the sender; a candidate who is not selected cannot be offered.
2. **Given** an offered candidate, **When** a revised package is sent, **Then** a new offer is created, the old one
   becomes "superseded", and both are visible with their packages.
3. **Given** an offer past its deadline and not answered, **When** the deadline passes, **Then** it becomes "expired"
   and the candidate returns to the waitlist or selected pool for a new offer.
4. **Given** an offer, **When** it is declined, **Then** it is closed with the reason and the candidate cannot be
   offered again without a new offer.
5. **Given** a Zone Manager, **When** they open an offer, **Then** they can see any offer but cannot send, change or accept one; an Admin can see all offers but cannot send or accept one by default.
6. **Given** a candidate who already has an open offer, **When** a second open offer is attempted, **Then** it is
   refused; only a supersede replaces it.

---

### User Story 3 - An Accepted Offer Becomes a Teacher in Training (Priority: P1)

When an offer is accepted, the system creates the Teacher record (spec 005) with the candidate's name, contact details
and status "in training", with no salary history entry yet (the salary starts at the first School assignment and is then written from the accepted offer), and enrols the
Teacher in the next induction batch that has room. Nobody types the candidate's details again. If the candidate is
already a Teacher who has not exited, the offer cannot be accepted and nothing is created, linked or mapped; a candidate
whose earlier Teacher record has exited may be hired again as a new Teacher record.

**Why this priority**: This removes the re-entry and joins recruitment to the rest of the system; later specs
(attendance, payroll, mapping to Schools) all start from that Teacher record.

**Independent Test**: Accept an offer and see one new Teacher "in training" with the candidate's details and no salary
history entry yet, enrolled in the next batch, with the package kept on the offer; accept the same offer again and see no
second Teacher.

**Acceptance Scenarios**:

1. **Given** an accepted offer, **When** it is saved, **Then** a Teacher is created with the candidate's details,
   status "in training" and no salary history entry; the package stays on the accepted offer, and the candidate shows as "joined".
2. **Given** no induction batch with room, **When** an offer is accepted, **Then** the Teacher is created and waits
   in a "to be enrolled" list until a batch is created.
3. **Given** a candidate whose phone or email matches a Teacher who is working, on leave, in training or assigned to
   a School, **When** the offer is accepted, **Then** it is refused with the existing Teacher named, and no Teacher is
   created, linked or mapped.
3a. **Given** a match with a Teacher who has exited, **When** the offer is accepted, **Then** the user confirms a new
   Teacher record; the exited record is unchanged.
4. **Given** the same acceptance sent twice, **When** processed, **Then** exactly one Teacher exists.
5. **Given** a created Teacher, **When** a Zone Manager looks at Teachers (spec 005), **Then** they do not see this
   Teacher until it has a School in their scope, as for any unplaced Teacher; they can still see the candidate and
   the accepted offer in Recruitment.

---

### User Story 4 - Run the One-Month Induction (Priority: P1)

An Admin or Director creates an induction batch (name, start and end date, trainer, venue physical or virtual, and a
seat limit) and enrols recruits. Daily attendance is recorded for the batch (present or absent, with a reason; half days
allowed). Each present day is written as a training-day mark in the attendance records of spec 008, so it appears in
the Teacher's attendance and training-day figures there. At the end, each recruit is signed
off as completed or not completed with remarks. A completed recruit becomes "active" and is ready to deploy: a School
position can then be assigned in spec 012. A recruit who did not complete can be moved to the next batch or released
(exited) with a reason.

**Why this priority**: Induction is the step between an offer and a deployable Teacher; without a sign-off nobody
knows who is ready for a School.

**Independent Test**: Create a batch for a month, enrol 5 recruits, record attendance for several days, sign off 4 as
completed and 1 as not completed, and see the 4 as active and the fifth moved to the next batch.

**Acceptance Scenarios**:

1. **Given** a batch, **When** a recruit is enrolled, **Then** they appear in its roster; a recruit cannot be in two
   batches on overlapping dates, and a batch cannot exceed its seat limit.
2. **Given** a batch day, **When** attendance is recorded, **Then** each present recruit gets a training-day mark
   (whole or half day) in spec 008's records for that date, with who recorded it and when, and an absent recruit gets
   none; a later correction keeps the earlier value in history, as 008 does for any mark.
3. **Given** the batch end date, **When** a recruit is signed off as completed, **Then** their status becomes
   "active" and they appear in the "ready to deploy" list (active, no current School assignment).
4. **Given** a recruit signed off as not completed, **When** the user chooses "next batch", **Then** they are
   enrolled in the next batch for its full dates, their earlier induction days stay as recorded, the earlier
   sign-off stays visible, and the new enrolment gets its own sign-off; when the user chooses "release", **Then** the Teacher exits with the reason.
5. **Given** a recruit with induction days, **When** their attendance for the month is opened in spec 008, **Then**
   the induction days show as training days with no School, and they are totalled with the same rules and weights as
   any training day.
5a. **Given** a month that is locked in spec 008, **When** induction attendance is recorded or corrected for a day in
   it, **Then** it is refused with the lock reason; the month must be reopened through 008's own workflow.
6. **Given** a Zone Manager, **When** they open induction, **Then** they have no access.

---

### User Story 5 - See the Recruitment Pipeline (Priority: P2)

An Admin or Director opens a dashboard: drives scheduled and held, candidates interviewed, selected, offered, accepted
and completed induction, per college and per season (a label chosen on each drive, such as "2026-27", or a date range), plus the list
of recruits ready to deploy. Zone Managers see the same counts for all drives, with a "my drives" filter.

**Why this priority**: It shows where the funnel leaks and how many Teachers will be available to the schools; it
depends on the four stories before it.

**Independent Test**: With drives, candidates and offers entered, see each count for a college and for the season,
and see the ready-to-deploy list with the number of recruits.

**Acceptance Scenarios**:

1. **Given** data across two colleges, **When** the dashboard is opened, **Then** each shows its drives, candidates,
   offers and joiners, with counts that match the underlying lists.
2. **Given** a season label or a date range, **When** it is applied, **Then** only drives with that label or dates in that range count.
3. **Given** a Zone Manager, **When** they open the dashboard, **Then** the counts cover all drives, with a "my drives" filter that limits them to drives they scheduled or attend.
4. **Given** no data, **When** the dashboard is opened, **Then** an empty state explains how to start.
5. **Given** recruits who completed induction, **When** the ready-to-deploy list is opened, **Then** each shows
   joining date and induction batch, and drops off once the Teacher is assigned to a School (spec 012).
6. **Given** candidates selected and some who joined, **When** the dashboard is opened, **Then** it shows the joining
   ratio (joined divided by selected) and the counts placed in a School and active, per college and season.

---

### Edge Cases

- A candidate is selected in two drives: allowed, but only one open offer at a time for the person (matched by phone).
- An offer is accepted after its deadline: refused as expired; a new offer is needed.
- A drive is cancelled after candidates were recorded: the candidates stay, with the drive marked cancelled.
- A recruit leaves during induction: the Teacher exits with a reason (spec 005 rules); the seat frees from the next day.
- Two users accept the same offer at once: one Teacher is created, the second request is told it is already accepted.
- A batch is cancelled before it starts: enrolled recruits wait in "to be enrolled".
- The candidate's phone or email matches a Teacher: never merged silently; refused if that Teacher is not exited, new record only after confirmation if exited.
- Amounts are rupees with Indian digit grouping and two decimals; dates DD/MM/YYYY.
- A very large sheet import (hundreds of rows) saves valid rows and reports the rest without timing out.

## Requirements *(mandatory)*

### Functional Requirements

**Drives and colleges**

- **FR-001**: The system MUST let an Admin, Director or Zone Manager schedule a campus drive with a college, one or more dates, a venue, the attending HLS interviewers and a status (planned, held, cancelled with a reason); and MUST show drives on a month calendar and in a list.
- **FR-002**: The system MUST keep a College list (name, city and the college's contacts: a **placement officer** and a **principal**, each with name, phone and email) with no duplicate of the same name and city, and show each college's drive history.

**Candidates and outcomes**

- **FR-003**: The system MUST record candidates per drive (name, phone, email, degree, year, notes), by single entry and by sheet import that reports invalid rows with reasons and does not duplicate a candidate (same phone for the same drive) on re-import.
- **FR-004**: The system MUST record an interview outcome (selected, waitlisted, rejected) with who and when, keep the history of changes, and allow waitlisted to become selected. It MUST also record a score per assessment criterion (group speaking, English, communication) and remarks, with the assessor and time, and keep earlier scores when they change.

**Job offers**

- **FR-005**: The system MUST let a Director generate and issue a Job Offer to a selected candidate with role, package (monthly salary, allowances, notice or bond terms as text; induction is unpaid, so there is no stipend), offer date and response deadline; the system MUST generate a printable offer letter from the offer data; the offer status MUST be draft, issued, accepted, declined, expired or superseded, and issuing MUST record who and when.
- **FR-006**: An offer MUST NOT be edited after it is sent; a changed package MUST be a new offer that supersedes the open one. A person MUST have at most one open offer, identified by phone number, even when the same person was selected at two drives.
- **FR-007**: An offer MUST expire when its deadline passes unanswered, and acceptance after expiry MUST be refused.

**Teacher creation and induction**

- **FR-008**: On acceptance the system MUST create the Teacher (spec 005) with the candidate's details, status "in training" and no salary history entry, exactly once, through the `teacher` public interface; a candidate whose phone or email matches a Teacher who has not exited MUST NOT be accepted (no Teacher created, linked or mapped), and a match with an exited Teacher MUST need the user's confirmation to create a new record; records are never merged silently.
- **FR-008a**: When a Teacher created from an accepted offer is first assigned to a School (under a contract, with or without a position yet) (spec 012 publishes the first-assignment event), the system MUST write the accepted offer's monthly salary to the Teacher's salary history (spec 005) with the assignment start date as the effective date, exactly once; an Admin or Director MAY correct it afterwards. A trained Teacher who is not yet placed MUST receive no salary.
- **FR-008b**: On acceptance the system MUST enrol the Teacher in the next induction batch that has room, or leave them in the "to be enrolled" list when there is none. `recruitment` MUST NOT call `training`: it publishes an event and `training` enrols, so the two modules do not depend on each other in a cycle.
- **FR-009**: The system MUST let an Admin or Director create induction batches (name, dates, trainer, venue, seat limit), enrol recruits (no overlapping batches, no more than the seat limit), and record daily attendance with half days and corrections that keep history; each present day MUST be written as a training-day mark (code T) in the attendance records of spec 008 through the `attendance` public interface.
- **FR-010**: Induction attendance MUST have no second source of truth: it lives only in spec 008's records. Spec 008 MUST accept a training-day mark for a Teacher with no current School (the School is empty for these marks only), and induction marks MUST respect 008's month lock, status codes and weights.
- **FR-011**: At the end of a batch an Admin or Director MUST sign off each enrolment as completed or not completed with remarks (a recruit moved to the next batch has a new enrolment and sign-off; earlier ones are kept). Completed MUST set the Teacher's status to active (ready to deploy); not completed MUST allow moving to the next batch or releasing (exit) with a reason.
- **FR-012**: The system MUST list recruits who are active, completed induction and have no current School assignment ("ready to deploy"), reading the assignment through the `teacher` public interface.

**Dashboard, scope, audit**

- **FR-013**: The system MUST show drives scheduled and held, candidates assessed, selected, offered, accepted and completed induction, the ready-to-deploy count, the number placed in a School and active, and the joining ratio (joined divided by selected), per college and season.
- **FR-014**: Every list, search and detail MUST apply the caller's access: Admin and Director read and write org-wide as permitted; a Zone Manager reads all drives, candidates, outcomes and offers but can change only drives they scheduled or attend and the candidates and outcomes on them. Every write endpoint MUST refuse a Zone Manager outside that set with a clear "not your drive" message.
- **FR-015**: Every drive, outcome, offer, acceptance, Teacher creation, enrolment, attendance entry and sign-off MUST be written to the audit store (spec 003) with actor, roles, time, and prior and new values (Constitution Principle I).
- **FR-016**: Menu items and actions MUST be offered only when the server's access model grants them, and every endpoint MUST be tested per role and per scope boundary.
- **FR-002a**: The system MUST let an Admin or Director (and a Zone Manager for Schools in their Zones) record a **principal** and an **accountant** contact (name, phone, email, each optional) on a School, as an amendment to spec 005; changes are audited and the contacts are shown on the School screen.
- **FR-017**: Candidate phone numbers and emails MUST be shown only to roles holding `RECRUITMENT` `VIEW` (which includes every Zone Manager) and never logged in plain text. Candidate details are kept as history and are not cleared automatically, including those of rejected, declined and expired candidates.
- **FR-019**: Drives MUST be exposed to other modules as planned activities (kind, owner, date, place, status) through a public interface, and induction batches MUST be exposed by identifier so that Training Stay expenses (spec 015) can be linked to a batch; this spec stores no food or accommodation cost.
- **FR-018**: Amounts MUST show in rupees with Indian digit grouping and dates as DD/MM/YYYY; screens MUST meet WCAG 2.2 AA, have loading, empty and error states, and work at phone width.

### Key Entities *(include if feature involves data)*

- **College**: an institution HLS recruits from: name, city, placement officer and principal contacts; has many drives.
- **School contact** (amendment A8 to spec 005): a School also keeps a **principal** and an **accountant** contact (name, phone, email), next to its existing contact person and billing contact; used by marketing (spec 023) and billing (spec 022).
- **Campus Drive**: a scheduled visit to a college: dates, venue, interviewers, status.
- **Candidate**: a person interviewed at a drive: contact details, qualification, outcome history.
- **Job Offer**: an offer to a selected candidate: role, package, dates, status; never edited, superseded by a new offer.
- **Induction Batch**: a one-month training batch: dates, trainer, venue, seat limit, roster.
- **Induction Enrolment**: a recruit in a batch: daily attendance and the sign-off result.
- **Teacher (spec 005)**: created from an accepted offer, owned by the `teacher` module; this spec holds only the link to it.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | RECRUITMENT → Campus Drives, Candidates, Offers, Induction, Dashboard | Drives, Candidates, Induction: View, Create, Edit; Offers: View | Org-wide |
| Director | RECRUITMENT → Campus Drives, Candidates, Offers, Induction, Dashboard | View, Create, Edit (all, including sending and accepting offers) | Org-wide |
| Manager (Zone Manager) | RECRUITMENT → Campus Drives, Candidates, Offers, Dashboard | Read all drives, candidates, outcomes, offers; Create, Edit drives, candidates and outcomes on own drives only; Offers: View | Read org-wide; write own drives |
| Teacher  | none | none | None |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: `RECRUITMENT` (`VIEW`, `CREATE`, `EDIT`; drives, candidates, outcomes), `OFFERS` (`VIEW`,
`CREATE`, `EDIT`; sending, superseding and accepting offers) and `INDUCTION` (`VIEW`, `CREATE`, `EDIT`; batches,
attendance, sign-off). Seeded: Director has all three in full; Admin has `RECRUITMENT` and `INDUCTION` in full and
`OFFERS` `VIEW` only; the Zone Manager has `RECRUITMENT` in full (scoped to own drives), `OFFERS` `VIEW` and no
`INDUCTION`. Keeping offers in their own module is what lets Admin or others be given offer rights later in Role &
Permissions without a code change. Teacher and System not eligible. Only Admin, Director and System edit the matrix.
The constitution's Default role access matrix gets Recruitment, Offers and Induction rows when this spec merges.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An accepted offer produces exactly one Teacher with the candidate's details, with no re-typing, and keeps the offered package on the offer and creates no salary entry at acceptance, including when acceptance is sent twice or two users accept at once.
- **SC-002**: A candidate never has more than one open offer, and an offer's package never changes after it is sent (a change is a new offer).
- **SC-003**: A Zone Manager can change nothing on a drive they did not schedule and are not an interviewer on, nor any offer, induction or other restricted record; proved by a test for each write endpoint, and Teacher and System get no access.
- **SC-004**: Every drive, outcome, offer, acceptance, enrolment, attendance entry and sign-off has an audit entry with actor and prior and new values.
- **SC-005**: Dashboard counts match the underlying lists exactly for any college and season.
- **SC-006**: An Admin can schedule a drive and import 50 candidates in under 5 minutes, and send an offer in under 1 minute.
- **SC-007**: A recruit's induction days appear once, as training-day marks in spec 008, and equal the training days that 008 reports for them for the month, with no double counting and none lost to a missing School or a locked month.
- **SC-008**: A recruit who completed induction appears in "ready to deploy" and disappears once assigned to a School in spec 012.

## Assumptions

- Spec 008 gains one change here: training-day marks with no School are allowed (existing school marks are unchanged). Spec 005 (Teachers, status, salary history), spec 003 (audit), spec 008 (attendance training days) and spec 004 (logins) are implemented and merged; spec 010 (notifications) is optional here.
- "Ready to deploy" is not a new status: it is an active Teacher with no current School assignment (see Clarifications).
- The marketing side (spec 023) and the MoU and mapping (spec 012) are separate; this spec only supplies Teachers.
- Spec 012 refuses to map a Teacher who is still in training; this spec's induction sign-off (which makes the Teacher active) is what lets a recruit be mapped.
- A season is a free label on each drive (no fixed start or end), so the dashboard can group drives however HLS runs its recruitment year.
- Candidate personal data is kept as history with no automatic clearing (see Clarifications); access is limited by the RECRUITMENT permission and nothing is logged in plain text.
- Candidates are not users and do not log in; a recruit has no login until an Admin or Director creates one (spec 004).
- Offer letters are generated here from the offer data and their status is tracked; e-mailing the letter, e-signature, resume parsing and mobile screens are out of scope.
- Induction is unpaid. Payroll (spec 013) pays nothing for induction days, and a trained Teacher who has not yet been placed is not paid either; salary starts at the first School assignment, when it is written from the accepted offer (FR-008a).
- Spec 012 gains one small addition: an event published when a Teacher is first assigned to a School (under a contract, with or without a position yet), so this spec can write the salary entry without 012 depending on recruitment.
- Spec 015 (expenses) links Training Stay expenses to induction batches; the cost is not stored here.
- Weekend and monthly refresher training is a later spec (024).
- Zone Managers read all recruitment records and write only to drives they scheduled or are interviewers on (see Clarifications); colleges are not Zone-bound.
