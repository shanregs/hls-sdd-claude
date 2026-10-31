# Feature Specification: Substitution Workflow

**Feature Branch**: `017a-substitution`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "017a-substitution: When a Teacher is absent, the system raises a substitution requirement by itself (no one has to remember to). The Zone Manager is alerted (spec 010); the Teachers who are free that day are checked; a substitute is assigned; the School is told; or, if nobody is free, the Manager covers the class personally. The substitute is credited for the day, not the absent Teacher. Payout into payroll is spec 017b and out of scope here, but the credit data is exposed through a public interface. Includes amendment A2 to spec 008: attendance publishes an absence event, flags each loss-of-pay day for payroll, and lets the Manager record an optional Principal confirmation. Roadmap: row 017, row 008 (A2), section 5, decisions D2 and D7."

## Clarifications

### Session 2026-10-11

- Q: What counts as an "absence" that raises a requirement? → A: Any attendance day whose status is in the leave category: an absence marked by the Teacher or the Manager, and a day of approved leave (spec 009). In both cases a class is left without its Teacher. A half day raises a requirement for half a day. The requirement shows which of the two it was.
- Q: Is a requirement raised for a day in the past or the future? → A: Yes, for any date the mark is written on (approved leave is often for future days; an absence is often entered a day late). Nothing is raised for a non-working day.
- Q: What if the absence is later corrected or leave is revoked? → A: The requirement is cancelled with the reason "absence withdrawn", any substitute credit on it is void, and the substitute is told. If the day becomes an absence again, the same requirement is reopened.
- Q: Who is alerted? → A: The School's Manager when active; otherwise every active Admin and Director (the same rule leave uses). The alert links to the requirement.
- Q: Who counts as "available"? → A: An ACTIVE Teacher, other than the absent one, with no leave or absence on the date, not already covering another class that day (the day values of their substitutions on the date add up to at most one day), and either not placed in any School on the date (the "Available" view of decision D3) or placed in a School for which the date is a non-working day. A Teacher placed in a School on a working day is teaching there and is shown as unavailable with that reason. The Manager sees every active Teacher with an available/unavailable flag and the reason; only an available Teacher can be chosen, and the server re-checks it.
- Q: Does the substitute's own attendance change? → A: No. The substitute's attendance is untouched (they have no placement in the absent Teacher's School, and spec 008 refuses a mark there). The credit is a separate record: substitute, absent Teacher, School, date, day value. Payroll (017b) decides how it is paid.
- Q: When is the credit created, and can it be undone? → A: From the moment a substitute is assigned. Changing the substitute, releasing the requirement, recording that the Manager covers, or withdrawal of the absence ends the credit of the previous substitute; the earlier entries stay in the history. The absent Teacher never has a credit.
- Q: What does "the School is informed" mean in the system? → A: The Manager (or Admin/Director) records that the School was told: who was told (the Principal on the School record is offered by default), how (phone, in person, email, message, other) and an optional note. It is recorded once per outcome; a changed outcome (new substitute, Manager covering) needs the School informed again. The system does not send messages to the School in this version (no SMS/email channel yet).
- Q: Who may act, and what is the data scope? → A: Admin and Director act on every requirement; a Zone Manager on the requirements of their Schools; a Teacher only reads the days they were assigned to cover (My Substitutions). System sees nothing. The Manager is not required to be the one who raises: the requirement exists already.
- Q: What does "Manager covers" record? → A: That nobody was assigned and the Manager covers the class personally, with an optional note. The covering person is the acting Manager, or the School's Manager when an Admin or Director records it. It carries no credit and no pay.
- Q: Does the Principal confirmation (D7) change who marks attendance? → A: No. The Teacher still marks their own day and the Manager can mark or correct any day. After a day is marked, the Manager (or Admin/Director) may optionally record that the Principal confirmed it: confirmed or not confirmed, the Principal's name, and a note. It never blocks the mark and is audited. It is not available on a day in a locked month.
- Q: How does attendance flag a loss-of-pay day? → A: Each leave-category mark carries a loss-of-pay flag, set when the mark is written: true for an absence with no approved leave behind it and for a day made by a Loss-of-Pay leave request (spec 009 A3); false for other approved leave. Payroll reads the flag per day through a public attendance interface. The 013a classifier keeps working as it does and may move to the flag later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An Absence Raises a Requirement and the Manager Is Told (Priority: P1) 🎯 MVP

A Teacher is marked absent (or their leave is approved). Without anyone doing anything, a substitution requirement for that
School and date appears, and the Zone Manager gets a notification that a substitute is needed with a link to it. Attendance
publishes the absence as an event and flags whether the day is a loss-of-pay day.

**Why this priority**: Today a class left uncovered goes unnoticed until someone complains. The automatic requirement and the
alert are the whole point of the deck's flow; every later step hangs on them.

**Independent Test**: Mark a placed Teacher absent for a date; see one open requirement for their School and date, one
"substitute needed" notification for the School's Manager, and the mark's loss-of-pay flag set.

**Acceptance Scenarios**:

1. **Given** a placed Teacher, **When** their day is marked absent, **Then** exactly one open requirement exists for that
   School and date, with the day value, and the Manager of the School is notified once.
2. **Given** a School whose Manager is inactive or missing, **When** a requirement is raised, **Then** every active Admin and
   Director is notified instead.
3. **Given** approved leave for several days, **When** it is approved, **Then** one requirement per working day is raised,
   and the Manager gets one notification per requirement (the notification names the Teacher and the date).
4. **Given** a day marked absent twice or corrected from absent to absent, **When** the second write happens, **Then** no
   second requirement and no second notification appears; a changed day value updates the open requirement.
5. **Given** an absence that is corrected to Present, or leave that is revoked, **When** the mark changes, **Then** the
   requirement is cancelled (reason "absence withdrawn"), any assignment on it is voided, and the substitute is notified.
6. **Given** a Present, Holiday or Training mark, **When** it is written, **Then** no requirement is raised.
7. **Given** an absence with no approved leave, **When** it is written, **Then** the mark is flagged loss of pay; an approved
   Casual leave day is not, and a Loss-of-Pay leave day is.

---

### User Story 2 - Find an Available Teacher and Assign a Substitute (Priority: P1)

The Manager opens OPERATIONS → Substitutions, sees the open requirements in their Schools, opens one, sees the Teachers
who are free that day (and why the others are not), picks one and assigns. The substitute is notified, the requirement
shows the substitute, and a credit of that day exists for the substitute and not for the absent Teacher.

**Why this priority**: This is the decision the Manager actually makes. Without it the alert has nothing to lead to.

**Independent Test**: With one unplaced active Teacher and one placed Teacher, open a requirement, see the unplaced one as
available and the placed one as unavailable with a reason, assign the unplaced one, and read one credit for them.

**Acceptance Scenarios**:

1. **Given** an open requirement, **When** the candidates are listed, **Then** every active Teacher except the absent one is
   shown with available or unavailable and the reason (on leave or absent that day, placed in a School on a working day,
   already covering that day).
2. **Given** an available Teacher, **When** the Manager assigns them, **Then** the requirement becomes Assigned, the
   substitute is notified, a credit for that date and day value exists for the substitute, and the absent Teacher has none.
3. **Given** an unavailable Teacher, **When** an assignment is attempted (even by calling the server directly), **Then** it is
   refused with the reason and nothing changes.
4. **Given** an assigned requirement, **When** the Manager picks a different Teacher, **Then** the earlier credit ends, the
   new one begins, both substitutes are notified, and the history shows both.
5. **Given** a requirement, **When** two people assign at the same moment, **Then** one succeeds and the other is told the
   requirement changed (optimistic version check).
6. **Given** a half-day absence, **When** a substitute is assigned, **Then** the credit is half a day, and the same Teacher
   can also cover another half day on that date.

---

### User Story 3 - Tell the School, or the Manager Covers (Priority: P1)

After the outcome is set, the Manager records that the School was informed. If nobody is available the Manager records
that they will cover the class personally; the requirement is then closed as covered by the Manager, with no credit.

**Why this priority**: The deck requires both closing steps; without them requirements stay open forever.

**Independent Test**: Assign a substitute, record the School informed (Principal, phone); then on a second requirement with
no available Teacher record Manager cover and see the status and no credit.

**Acceptance Scenarios**:

1. **Given** an assigned requirement, **When** the Manager records the School informed with a method, **Then** it shows who
   was told, how, by whom and when, and the Director and Admin are not bothered but the Manager's own record is complete.
2. **Given** a requirement with no outcome, **When** the Manager tries to record the School informed, **Then** it is refused
   (there is nothing to tell yet).
3. **Given** no available Teacher, **When** the Manager records "I will cover", **Then** the status is Covered by Manager, the
   covering Manager is named, there is no credit, and informing the School is still recorded separately.
4. **Given** an Assigned requirement, **When** the outcome changes (new substitute or Manager cover), **Then** the School
   informed record is cleared (kept in the history) and must be recorded again.
5. **Given** a Covered-by-Manager requirement, **When** a Teacher later becomes free and the Manager assigns them, **Then**
   the status becomes Assigned and the credit begins.

---

### User Story 4 - My Substitutions for the Teacher (Priority: P2)

A Teacher opens My Substitutions and sees the days they were assigned to cover (date, School, day value, status), from
today onward and recent past, and what they are credited. They are notified when assigned or when an assignment ends.

**Why this priority**: The substitute must know where to go; the notification carries a link here. The workflow works
without the screen.

**Independent Test**: Assign a Teacher, sign in as them, see the row; assign someone else and see the row gone and a
notification.

**Acceptance Scenarios**:

1. **Given** a Teacher with assignments, **When** they open My Substitutions, **Then** they see only their own, with School
   and day value, and never the absent Teacher's name beyond "a colleague" unless the School's Manager chooses otherwise
   (the absent Teacher's name is not shown to the substitute).
2. **Given** another Teacher's requirement id, **When** a Teacher requests it, **Then** the answer is not found.

---

### User Story 5 - Read the Credit Data (Priority: P2)

Payroll (017b) and reports ask a public interface for the credited days of a Teacher or many Teachers in a range. The
answer is the active credits only, each with the substitute, the School, the date and the day value, so 017b never reads
substitution tables.

**Independent Test**: Assign, change the substitute, cancel the absence; the interface returns only credits that are live.

**Acceptance Scenarios**:

1. **Given** an assigned requirement, **When** credits are read for the substitute and month, **Then** one credit appears;
   for the absent Teacher none.
2. **Given** a withdrawn absence or a changed substitute, **When** credits are read, **Then** the voided credit is absent.
3. **Given** many Teachers, **When** the bulk form is used, **Then** the number of queries does not depend on the count.

---

### Edge Cases

- The absent Teacher's month is locked: the requirement and the credit are not attendance and are unaffected; the lock only
  stops the mark itself.
- A requirement exists and the absent Teacher is transferred or exits: it stays with the School of the day it was raised for.
- The Manager of the School changes after the requirement is raised: scope follows the current School assignment.
- A substitute is later marked on leave on the date: the assignment stays but is flagged "substitute now unavailable" on the
  detail; the Manager must reassign. (No automatic change.)
- The same day is marked absent by the Teacher and the Manager later corrects it: still one requirement.
- A candidate is both unplaced and has an absence that day: unavailable (absence).
- A requirement for a past date can still be assigned or covered (to record who did the work and to credit it).
- Audit: every create, assign, reassign, release, cover, inform and cancel writes an audit entry and a history row.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Attendance MUST publish an event, inside the transaction of the change, when a day becomes a leave-category
  day (absent or approved leave) and another when such a day stops being one (cleared, corrected, leave revoked). The
  event carries the Teacher, School, date, day value, kind (absent or leave), loss-of-pay flag, and the actor. (008 A2)
- **FR-002**: Attendance MUST store a loss-of-pay flag on every mark, true exactly for an absence with no approved leave and
  for a day made by a Loss-of-Pay leave request, and MUST expose a Teacher's loss-of-pay days of a month and of a date range,
  and in bulk for many Teachers, through its public interface. (008 A2)
- **FR-003**: The system MUST let the Manager (and Admin and Director) record, change and clear an optional Principal
  confirmation on a day: confirmed or not confirmed, the Principal's name, an optional note, who recorded it and when. It MUST
  never block or alter the mark, MUST be refused in a locked month, MUST be audited, and MUST be shown in the day's detail.
  (008 A2, D7)
- **FR-004**: On the absence event the substitution module MUST create one requirement per Teacher and date, or update the
  open one, or reopen a cancelled one, and MUST NOT create duplicates when the same event is delivered twice.
- **FR-005**: On the withdrawal event the module MUST cancel the requirement, void the credit, and notify the substitute if
  one was assigned.
- **FR-006**: The system MUST notify the School's active Manager, or else every active Admin and Director, once when a
  requirement is raised (substitute needed), and MUST NOT notify again for the same requirement unless it was cancelled and
  re-raised.
- **FR-007**: The system MUST list a requirement's candidates: every active Teacher but the absent one, each with an
  available flag and a reason when unavailable, by the rule in the clarifications. It MUST re-check availability when
  assigning, and MUST refuse an unavailable Teacher.
- **FR-008**: The system MUST let an authorized user assign a substitute to an open, assigned (reassign) or manager-covered
  requirement, release an assigned requirement back to open, and record that the Manager covers; each with an optional note
  (a reason is required to release), using an optimistic version check.
- **FR-009**: The system MUST create the substitution credit when a substitute is assigned and end it when the substitute
  changes, the requirement is released or covered by the Manager, or the absence is withdrawn. The absent Teacher MUST NOT
  receive a credit. History rows are never changed or deleted.
- **FR-010**: The system MUST let an authorized user record that the School was informed (who, how, note) once per outcome,
  only when an outcome exists, and clear it when the outcome changes.
- **FR-011**: The system MUST notify the substitute when assigned and when an assignment ends, and MUST notify the
  School's Manager (when someone else acted) when the School is informed.
- **FR-012**: The system MUST show OPERATIONS → Substitutions for Admin, Director and Zone Manager: a list filterable by
  status, School and date range (default: open and assigned requirements), and a detail with candidates, actions, history and
  the School informed record. A Teacher MUST see only My Substitutions.
- **FR-013**: The system MUST scope every read and write: a Zone Manager only requirements of their Schools; a Teacher only
  their own assignments; an out-of-scope id MUST read as not found; System has no access. Checked on the server on every
  endpoint.
- **FR-014**: The system MUST audit every requirement change (raise, update, cancel, assign, reassign, release, cover,
  inform) and every Principal confirmation change, with who, when, and prior and new values, and MUST keep an append-only
  history of the status changes per requirement.
- **FR-015**: The system MUST expose the live credits to other modules as one public read interface (by Teacher and month,
  by Teachers and date range) and MUST NOT let payroll read substitution tables. (For 017b.)
- **FR-016**: The system MUST NOT create or depend on tasks (spec 025); alerts are notifications only.

### Key Entities

- **Substitution Requirement**: one per absent Teacher and date; School; day value; kind (absent or leave); status
  (Open, Assigned, Covered by Manager, Cancelled); substitute Teacher (when Assigned); covering Manager (when Covered);
  School informed record; version.
- **Requirement History Entry**: append-only row for every status or substitute change: from, to, actor, time, note.
- **Substitution Credit** (derived from Assigned requirements): substitute, absent Teacher, School, date, day value.
- **Loss-of-Pay Flag** (on an attendance mark): true when the day is unpaid.
- **Principal Confirmation** (on an attendance mark): confirmed or not, Principal name, note, who and when.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Substitutions | View, Edit | Org-wide |
| Director | OPERATIONS → Substitutions | View, Edit | Org-wide |
| Manager (Zone Manager) | OPERATIONS → Substitutions | View, Edit | Assigned (their Schools) |
| Teacher  | MY ATTENDANCE → My Substitutions | View | Own |
| System   | none (System MUST NOT see business data) | none | None |

**New permission keys**: `SUBSTITUTION` (`VIEW`, `EDIT`: list, detail, candidates; assign, release, cover, inform;
eligible for Admin, Director, Manager only) and `MY_SUBSTITUTIONS` (`VIEW`; Teacher only). There is no `CREATE` because
requirements are raised by absences, never by hand, and no `DELETE` because nothing is deleted. The Principal
confirmation is recorded under the existing attendance keys (`TEACHER_ATTENDANCE` / `ATTENDANCE` `EDIT`). Only Admin,
Director and System edit the matrix. The constitution's Default role access matrix gets a Substitutions row when this spec
merges. New notification types: `SUBSTITUTE_NEEDED`, `SUBSTITUTE_ASSIGNED`, `SUBSTITUTE_ENDED`, `SUBSTITUTE_SCHOOL_INFORMED`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every leave-category day written produces exactly one requirement and one alert, and a repeated write of the
  same day produces none; verified over absent, leave, half day, corrected, cleared and revoked cases.
- **SC-002**: The credit data never lists the absent Teacher and never lists a voided credit; proved on assign, reassign,
  release, cover and withdraw.
- **SC-003**: An unavailable Teacher can never be assigned, through the screen or the server; proved for each reason.
- **SC-004**: A Zone Manager sees and changes only requirements of their Schools, a Teacher only their own assignments,
  System nothing; proved by a test per endpoint and role.
- **SC-005**: A Manager goes from the alert to a recorded outcome in under 2 minutes for a requirement with candidates.
- **SC-006**: Every change has an audit entry; the history of a requirement can be rebuilt from its history rows alone.
- **SC-007**: Existing attendance and leave behaviour and tests are unchanged except for the added event and flag.

## Assumptions

- Spec 008 (attendance), 009 (leave, including the Loss-of-Pay type), 010 (notifications), 012 (contracts), 005 (Teachers,
  Schools, Managers, contacts) are implemented; 013a is merged but this spec does not depend on it. 008 amendment A2 is
  built here.
- Spec 025 (tasks) is being built in parallel; this spec uses notifications only (roadmap C2 is not used).
- The School is informed by a person, outside the system; the system records it. Sending SMS/email to the School waits for
  a channel spec.
- Half-day absence raises a half-day requirement; one requirement per Teacher and date.
- Substitute payout and its reconciliation are spec 017b. This spec only exposes credits.
- Amounts are not involved here; dates are shown DD/MM/YYYY (Constitution, Additional Constraints).
- Out of scope: tasks, escalation of unresolved requirements (arrives with 025), recurring substitution, substitute
  attendance marks, SMS/email to the School, mobile screens.

> **Amended by spec 040 (2026-10-28):** A Manager cover recorded on the duty day is flagged same-day; the Manager cover entries are listed apart from Teacher credits (`GET /api/v1/substitutions/manager-covers`).
