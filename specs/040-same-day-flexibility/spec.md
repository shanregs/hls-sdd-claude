# Feature Specification: Same-Day Flexibility Without Prior Approval

**Feature Branch**: `040-same-day-flexibility`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "Same-day flexibility without prior approval. Teachers under a Zone Manager usually take ad-hoc leave the same morning, the Zone Manager often covers the class as the substitute, and Managers do same-day tasks such as school visits for observation. Leave (009) needs approval, 'Manager covers' (017a) carries no credit or substitution record, and Manager tasks (025) wait for verification and visits are planned first. Wanted: same-day leave that takes effect at once; same-day tasks and visits logged on the spot; the Zone Manager's cover recorded as a real substitution. All same-day items are auto-accepted, flagged 'same-day' in audit and reports only, with no review queue and no caps."

## Clarifications

### Session 2026-10-28

- Q: What do you want to change? → A: Same-day leave, same-day tasks and visits, and the Zone Manager's substitution.
- Q: What happens afterwards to items done without prior approval? → A: Auto-accepted. They take effect at once and are marked "same-day" in the audit trail and reports. There is no review queue and no monthly limit.
- Q: Which leave type may a Teacher pick for their own same-day leave, and how is the day paid? → A: Any type. Every Teacher leave day, same-day or normally approved, is deducted (a day, or half a day) when payroll runs, whatever the type; the type is kept as the reason. This amends specs 013a and 013b, which until now deducted only unapproved absences and Loss-of-Pay leave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record Leave for Today and It Takes Effect Immediately (Priority: P1)

A Teacher falls ill in the morning. They open Apply Leave, choose today as the date (or ask their Manager to do it for them), pick a leave type and save. The day is at once an approved leave day: attendance shows Leave (L), the Manager is notified, and a substitution requirement is raised. Nobody has to approve it.

**Why this priority**: This is the commonest case in the field. Today the Teacher's absence is either left as an unapproved absence (flagged loss of pay) or waits for an approver who may be on a school visit.

**Independent Test**: As a Teacher apply for leave dated today and see the day marked L with no pending step; as a Manager record the same for a Teacher in scope and see the same result.

**Acceptance Scenarios**:

1. **Given** a placed Teacher and a working day today, **When** they submit leave whose dates are all today, **Then** the request is saved already Approved, marked "same-day", the day shows L in attendance, and the Manager is notified.
2. **Given** a Manager, Admin or Director, **When** they record same-day leave for a Teacher in their scope, **Then** it takes effect the same way and the audit shows who recorded it on whose behalf.
3. **Given** same-day leave of any type, **When** it is saved, **Then** the Teacher may pick any leave type, and the day (half a day for a half day) is deducted when payroll runs.
4. **Given** a normally approved leave day of any type, **When** payroll runs, **Then** it is deducted in the same way (a change from today, where only Loss-of-Pay leave is deducted).
5. **Given** a request that covers today and later days, **When** it is submitted, **Then** only the part of the request that is today is auto-approved and the later days follow the normal approval, with the request showing both parts.
6. **Given** today's attendance was already marked by a Manager with a status other than L, **When** same-day leave is recorded, **Then** it is refused with the reason and the Manager corrects the day on purpose (same rule as spec 009).
7. **Given** today falls in a locked month or is not a working day, **When** same-day leave is attempted, **Then** it is refused with the reason.
8. **Given** a Teacher who was marked absent earlier today with no leave behind it, **When** same-day leave is recorded for today, **Then** the mark becomes approved leave of the chosen type, is deducted once at payroll run (never twice), and the earlier value stays in the day's history.

---

### User Story 2 - Zone Manager Covers the Class and It Counts as a Substitution (Priority: P1)

When a Teacher is on ad-hoc leave, the Zone Manager often goes to the school and takes the class. Today "Manager covers" only closes the requirement with no record of who did the work. The Manager records that they covered the class and it is kept as a real substitution entry for that Manager, with the date, School, day value and the Teacher replaced, visible in their history and reports.

**Why this priority**: Management needs to see how much substitution work Managers do, and the work should be recorded the moment it happens, not set up beforehand.

**Independent Test**: Take a substitution requirement raised by today's leave, record "I covered" as the Zone Manager, and see a substitution entry attributed to that Manager and counted in the substitution reports.

**Acceptance Scenarios**:

1. **Given** an open substitution requirement for today, **When** the Zone Manager records that they covered it, **Then** the requirement becomes Covered by Manager and a substitution entry exists for that Manager with the date, School, day value and replaced Teacher.
2. **Given** a requirement for today, **When** a Manager records cover after the class has already been taken (end of the same day), **Then** it is accepted with no prior assignment step.
3. **Given** a Manager substitution entry, **When** substitution credits and reports are read, **Then** it appears labelled "Manager cover" and "same-day", clearly separate from Teacher substitute credits, and it carries no Teacher credit or extra pay unless a rule outside this spec gives one.
4. **Given** a Manager substitution, **When** a Teacher substitute is assigned instead later the same day, **Then** the Manager entry ends (earlier entries stay in the history) and the credit passes to the Teacher, as for any change of outcome in spec 017a.
5. **Given** a Manager covered class, **When** the School has not been told, **Then** the "School informed" record is still asked for, as in spec 017a.

---

### User Story 3 - Log a Task or School Visit for Today on the Spot (Priority: P1)

A Manager is asked to observe a Teacher or visit a school today. They do not have time to plan it first. They open the Manager's tasks (or visits), choose "Log for today", pick the type, School and (if relevant) Teacher, add a note, and save it as started (tasks) or completed. It is accepted at once, with no planning step and no verification wait.

**Why this priority**: Field work happens without notice. Forcing a plan-then-complete-then-verify chain means the work is either not recorded or recorded late.

**Independent Test**: As a Manager, log a School visit for today as completed with a note; it appears in Today and Done, the activity calendar and the day's reports, with no verification pending.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they log an unplanned task of any task type for today, **Then** it is created for themselves, marked "same-day", and can be saved as In progress or Completed in one step; a School visit is logged as Completed (a visit has no In-progress state).
2. **Given** a same-day task saved as Completed, **When** it is stored, **Then** its status is final (Completed, accepted) and it does not appear under Awaiting verification.
3. **Given** a School visit or observation done today without a plan, **When** the Manager logs it, **Then** the visit and, for observations, the Teacher's observation due are satisfied, with the same-day mark visible in the history.
4. **Given** a same-day logged item, **When** Admin or Director look at tasks, visits or reports, **Then** it is shown and can be filtered by the "same-day" mark; it is not placed in any review queue.
5. **Given** a task that was already planned for today by someone else, **When** the Manager marks it "done on the spot", **Then** it needs no verification, and the original assigner stays in the history.
6. **Given** a Manager, **When** they try to log a same-day item for another Manager or a date other than today, **Then** it is refused (past and future dates follow the normal rules).
7. **Given** the mobile Manager app, **When** the Manager logs a same-day task or visit offline, **Then** it is stored on the phone and sent later, keeping today's date as the date of the activity (as per spec 034 offline behaviour).

---

### User Story 4 - Same-Day Items Are Visible in Audit and Reports (Priority: P2)

Admin and Director can see which leave days, substitutions, tasks and visits were done same-day without prior approval, by Manager and by period, without having to approve anything.

**Why this priority**: There is no approval gate any more, so the audit trail and reports are the only record of the flexibility being used.

**Independent Test**: Record one same-day leave, one Manager cover and one same-day task; filter each list and the audit trail by "same-day" and see exactly those items with who recorded them and when.

**Acceptance Scenarios**:

1. **Given** same-day items exist, **When** Admin or Director filter leave, substitutions, tasks or visits by "same-day", **Then** only those are listed.
2. **Given** a same-day item, **When** its audit entry is read, **Then** it shows who recorded it, for whom, the time, and that it was auto-accepted.
3. **Given** a month's leave and substitution reports, **When** they are produced, **Then** they show the number of same-day items per Manager and per School.

### Edge Cases

- Leave recorded after the working day has ended (still the same calendar day in the business time zone): treated as same-day. After midnight it follows the normal back-dated rules of spec 009.
- A Teacher cancels same-day leave later the same day: allowed; the day's marks and the requirement it raised are withdrawn as in spec 009, and any Manager substitution entry ends.
- Two people record same-day leave for the same Teacher at once: exactly one succeeds, the other is told it already exists.
- Same-day leave for a half day: raises a half-day requirement; a Manager cover is recorded as a half day.
- A Manager covers a class but the leave is later withdrawn: the Manager substitution entry is withdrawn too, with history kept.
- A Teacher on leave is already assigned as a substitute elsewhere today: flagged "substitute now unavailable" per spec 017a.
- Task or visit logged for a School or Teacher outside the Manager's scope: answered "not found".
- No monthly limit applies to same-day use; there is no cap to hit and nothing to override.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A placed Teacher MUST be able to submit leave dated entirely today, and a Manager, Admin or Director MUST be able to record it for a Teacher in their scope; the request is Approved at once with no approver step and is marked "same-day".
- **FR-002**: Same-day leave MUST mark the day in attendance as Leave (L) at once, notify the Manager, and raise the substitution requirement as spec 017a does for approved leave.
- **FR-003**: A Teacher MAY pick any leave type for same-day leave. Payroll MUST deduct every Teacher leave day (same-day or normally approved, any type) at payroll run, a half day counting as half, valued as in spec 013a; the leave type is kept as the reason only. This amends the 013a/013b rule that deducted only unapproved absences and Loss-of-Pay leave. If the Teacher was already marked absent today without leave, recording same-day leave MUST replace that mark with approved leave and the day MUST still be deducted only once.
- **FR-004**: Same-day leave MUST obey the existing guards of spec 009: working day, locked month, no overlap with another request, and refusal when a Manager, Admin or Director set a different non-L status on the day.
- **FR-005**: A request that spans today and later days MUST auto-approve only today's portion; later days follow the normal approval flow.
- **FR-006**: There is no leave balance in the system; same-day leave MUST NOT introduce one.
- **FR-007**: A Zone Manager (or Admin/Director on their behalf) MUST be able to record that they covered a class, including after the class was taken and with no prior assignment, and the system MUST keep a substitution entry for the covering Manager with date, School, day value and replaced Teacher.
- **FR-008**: A Manager substitution entry MUST be labelled "Manager cover", MUST appear in substitution histories and reports apart from Teacher substitute credits, and MUST carry no Teacher credit or pay.
- **FR-009**: Changing the outcome of a requirement (a Teacher substitute assigned, requirement released, absence withdrawn) MUST end the Manager's substitution entry, keeping it in the history.
- **FR-010**: A Manager MUST be able to log a task or School visit for today for themselves with no plan, and save a task as In progress or Completed, or a visit as Completed, in one step; the item is marked "same-day".
- **FR-011**: A same-day Completed task or visit MUST be final at once and MUST NOT enter Awaiting verification; a planned task the Manager marks "done on the spot" MUST behave the same way.
- **FR-012**: Logging a same-day observation MUST satisfy the Teacher's open observation due, as submitting a planned one does in spec 026.
- **FR-013**: Same-day logging MUST apply to today only, in the business time zone; other dates follow the normal rules, and a Manager MUST NOT log same-day items for another Manager.
- **FR-014**: Same-day items MUST be flagged "same-day" in the audit trail (who, for whom, when, auto-accepted) and MUST be filterable by that flag in the leave, substitution, task, visit and calendar lists and in the reports.
- **FR-015**: There MUST be no review queue, no confirmation step and no cap on the number of same-day items.
- **FR-016**: Cancelling same-day leave the same day MUST withdraw its marks and its substitution requirement, and end any Manager substitution entry.
- **FR-017**: The Manager mobile app MUST offer same-day logging of tasks and visits, including offline, keeping today's date as the date of the activity.

### Key Entities

- **Same-day mark**: A flag on a leave request, a substitution outcome, a task or a visit, saying it was recorded for today without prior approval or planning. It changes no status and starts no review.
- **Same-day leave request**: An Approved leave request created for today. It carries the recorder and the person recorded for.
- **Manager substitution entry**: A record that a Zone Manager covered a class for a Teacher on a date, with School and day value; it has no Teacher credit.
- **Same-day task / visit**: A Manager's own task or School visit logged for today and saved straight to In progress or Completed, with no verification step.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | ATTENDANCE & LEAVE → Leave Management, Substitutions; PLANNING & TRAINING → Manager Tasks; MARKETING → visits in the Marketing Calendar and Activity Calendar (existing items gain the same-day flag and filter) | Existing grants; record same-day leave and cover for anyone | Org-wide |
| Director | Same items as Admin | Existing grants; record same-day leave and cover for anyone | Org-wide |
| Manager  | Existing Leave Management, Substitutions, My Tasks and visit items | Record same-day leave for assigned Teachers; record own cover; log own same-day tasks and visits | Assigned |
| Teacher  | LEAVE → Apply Leave (today now takes effect at once) | Create same-day leave for self | Own |
| System   | None | None | None |

**New permission keys**: None; same-day recording uses the existing Approve action of Leave Management (the right to approve leave includes recording it for today), and the existing Create action of Substitution, Manager Tasks and Visits. The same-day filter needs only View.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Teacher or Manager records same-day leave in under 1 minute and the day shows as approved leave immediately, with no one else's action needed.
- **SC-002**: A Manager logs a completed same-day task or visit in under 1 minute, and 0 same-day items ever wait for verification or review.
- **SC-003**: 100% of Teacher leave days, whatever the type and however approved, are deducted exactly once (a day, or half a day) in the payroll run of their month, matching hand-computed figures.
- **SC-004**: 100% of classes covered by a Zone Manager appear as Manager substitution entries attributed to that Manager, so a month's substitution report matches what the Managers actually covered.
- **SC-005**: Every same-day item can be found by filtering on the same-day mark, and its audit entry shows who recorded it, for whom and when, in 100% of cases.
- **SC-006**: When two people record same-day leave for the same Teacher at once, exactly one succeeds in 100% of trials.

## Assumptions

- "Same-day" means the current date in the business time zone (Asia/Kolkata), including after working hours on that day.
- Leave types are unchanged and do not decide pay; every Teacher leave day is deducted at payroll run. Manager attendance (spec 032) keeps its own unpaid-day rules.
- The system has no leave balances (spec 009), so nothing is deducted.
- A Manager cover earns no pay or credit; any incentive for it is left to a later spec (030).
- Only the Manager's own tasks and visits are logged on the spot; assigning work to others stays with Admin and Director.
- Locked months, non-working days and overlap rules stay as they are and are not bypassed.
- Amends specs 008 (attendance), 009 (leave), 017a (substitution), 025 (manager tasks), 026 (observation), 033/034 (visits and mobile) and the unpaid-day rule in salary structures and payroll run (013a, 013b); the plan will list each change as an amendment.
