# Feature Specification: Teacher Observation and Performance

**Feature Branch**: `026-teacher-observation-performance`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "Spec 026 Teacher observation and performance (MVP priority 1). A Manager observes a Teacher in the classroom with a structured observation form, after the School's Principal has given permission; the Principal's feedback is captured separately; a performance issue is opened when the Teacher needs improvement, with a corrective action and a follow-up observation. Outcome is good (continue), needs improvement (feedback, correction, follow-up) or unsuitable (hand-off to transfer or replacement, spec 027). Observations fall due on a schedule and are worked as Manager tasks (025)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Records a Classroom Observation (Priority: P1)

A Manager is due to observe a Teacher placed at one of their Schools. They visit, record whether the Principal gave permission, sit in the class, and fill in the observation form: class, subject, duration, a rating for each teaching area, strengths, areas for improvement and an overall result of Good, Needs improvement or Unsuitable.

**Why this priority**: The observation is the core record of Teacher quality; everything else hangs from it.

**Independent Test**: As a Manager, open a Teacher at one of your Schools, record Principal permission, fill in the form and submit; the observation appears in the Teacher's observation history with all ratings and cannot be changed silently afterwards. Another Manager cannot see it.

**Acceptance Scenarios**:

1. **Given** an active Teacher placed at the Manager's School today, **When** the Manager starts an observation, **Then** the form opens with the Teacher, School, Manager and date filled in and the Principal's name taken from the School's contacts when one is recorded.
2. **Given** the Principal has not yet given permission, **When** the Manager tries to submit the form, **Then** it is refused until permission is recorded (who gave it, how, and the date), or the visit is recorded as "not held" with a reason.
3. **Given** a completed form with a rating (1 to 5) for every area, **When** the Manager submits, **Then** the observation is stored as Submitted with an overall result and the average rating shown.
4. **Given** a Teacher who is not placed at any of the Manager's Schools, **When** the Manager asks for an observation of them, **Then** the answer is "not found".
5. **Given** a Submitted observation, **When** the Manager corrects it, **Then** the correction needs a reason, the earlier values stay in the history, and the Director can see both.
6. **Given** the Principal declined or was unavailable, **When** the Manager records the visit as "not held" with a reason, **Then** no ratings are needed, the observation due stays open, and the attempt shows in the history.

---

### User Story 2 - Observations Fall Due and Become Manager Tasks (Priority: P1)

Every active, placed Teacher is due an observation on a schedule. When one falls due, the Teacher's Manager gets an "observation due" notification and an Observation task, and the task is completed by submitting the observation.

**Why this priority**: The roadmap lists observation due as an event, and the deck wants Managers held accountable for observing; without a schedule it depends on memory.

**Independent Test**: With a Teacher whose last observation is older than the interval, run the daily check: one Observation task and one notification appear for that Teacher's Manager, and a second run changes nothing. Submit the observation: the task completes.

**Acceptance Scenarios**:

1. **Given** an active Teacher placed at a School and no Submitted observation in the last 60 days, **When** the daily check runs, **Then** their Manager gets one Observation task due that day, linked to the Teacher, and one notification.
2. **Given** an open Observation task for the Teacher, **When** the check runs again, **Then** no second task or notification is created.
3. **Given** the Manager submits an observation for the Teacher, **When** it is saved, **Then** the open Observation task is completed with the observation as its evidence note and the next due date is 60 days later.
4. **Given** a Teacher in training, on leave, exited or not placed, **When** the check runs, **Then** no task is created.
5. **Given** the Teacher's Manager changes by a move of the School's Manager or of the Teacher, **When** the next check runs, **Then** the open task is reassigned to the current Manager through the task module.

---

### User Story 3 - A Manager Records the Principal's Feedback (Priority: P1)

After a visit, or at any time (for example when the School phones with a complaint), the Manager records what the Principal said about a Teacher: a category (good performance, needs improvement, attendance, teaching, communication, behaviour, other), the Principal's comments and whether action is needed.

**Why this priority**: The business flow says Principal feedback is captured separately from the Manager's own view, and Schools are the paying customer.

**Independent Test**: Record Principal feedback for a Teacher with and without a linked observation; both appear in the Teacher's history; marking "action required" offers to open a performance issue.

**Acceptance Scenarios**:

1. **Given** a Teacher at the Manager's School, **When** the Manager records feedback with a category, comments of at least 10 characters, the Principal's name and the date, **Then** it is stored as Principal feedback, separate from any observation ratings.
2. **Given** feedback recorded after an observation, **When** it is saved with the observation chosen, **Then** it is linked to that observation.
3. **Given** feedback marked "action required", **When** it is saved, **Then** the Manager is offered to open a performance issue from it, carrying the category and comments.
4. **Given** a Principal Feedback task assigned to the Manager (025), **When** the Manager records the feedback, **Then** that task is completed.
5. **Given** feedback recorded in error, **When** the Manager withdraws it with a reason, **Then** it is marked Withdrawn and kept in the history.

---

### User Story 4 - Performance Issue, Corrective Action and Follow-up Observation (Priority: P1)

When an observation or Principal feedback shows the Teacher needs improvement, a performance issue is opened. The Manager gives the Teacher feedback, records a corrective action with a follow-up date and, on that date, makes a follow-up observation. If the Teacher improved, the issue is resolved and the Teacher continues. If not, a further round of correction is made, and after the limit the issue is escalated.

**Why this priority**: This is the performance management loop of the business flow (good: continue; needs improvement: feedback, correction, follow-up).

**Independent Test**: Open an issue from a Needs improvement observation, record a corrective action due in 14 days, see the follow-up Observation task appear for that date, submit a follow-up observation rated Good and see the issue resolve with the outcome Improved.

**Acceptance Scenarios**:

1. **Given** a Submitted observation with the overall result Needs improvement, **When** it is saved, **Then** a performance issue is opened automatically, with the category, source and Manager, status Open.
2. **Given** an Open issue, **When** the Manager records a corrective action (what was discussed with the Teacher, what must change, follow-up date), **Then** the issue moves to Under correction and a follow-up Observation task is created for the Manager due on that date.
3. **Given** a follow-up observation linked to the issue with the result Good, **When** it is submitted, **Then** the issue becomes Resolved - improved and the Teacher continues with no further action.
4. **Given** a follow-up observation with the result Needs improvement, **When** it is submitted and the issue has had fewer than 2 correction rounds, **Then** a new corrective action is asked for; at the limit the Manager must choose whether to refer the Teacher (Story 5) or escalate to the Director.
5. **Given** an issue with a follow-up date in the past and no follow-up observation, **When** the daily check runs, **Then** the task module marks it overdue and escalates it under the rules of spec 025; nothing is stored twice.
6. **Given** a performance issue opened from Principal feedback instead of an observation, **When** the corrective action is recorded, **Then** the same loop applies.
7. **Given** an Open issue, **When** the Director cancels it with a reason (for example raised in error), **Then** it is Cancelled and kept in the history.

---

### User Story 5 - Unsuitable Teacher Is Referred for Transfer or Replacement (Priority: P2)

When an observation is rated Unsuitable, or correction has failed, the Manager records a referral with the reason and a recommendation (transfer to another School, or replacement). The Director is told. The decision, replacement Teacher and effective date are handled in spec 027, which reads the referral.

**Why this priority**: It closes the loop but the transfer itself is spec 027; here only the recommendation and its evidence are recorded.

**Independent Test**: Submit an Unsuitable observation, add the referral with a recommendation: the issue shows Referred, the Directors get a notification, and the referral is readable by another module with its supporting observations.

**Acceptance Scenarios**:

1. **Given** an observation rated Unsuitable, **When** it is submitted, **Then** a performance issue is opened (if none is open for the Teacher) and the Manager is asked to record a referral.
2. **Given** an issue where correction has failed, **When** the Manager records a referral with a reason of at least 20 characters and a recommendation of Transfer or Replace, **Then** the issue becomes Referred and every active Director gets a notification.
3. **Given** a Referred issue, **When** spec 027 asks for the Teacher's referrals, **Then** it receives the recommendation, reason and the linked observations and feedback, read-only.
4. **Given** the transfer or replacement is later decided, **When** spec 027 reports the decision, **Then** the issue shows Resolved - transferred or Resolved - replaced; until then the Teacher stays at the School and the issue stays Referred.
5. **Given** a Referred issue, **When** the Director decides the Teacher should continue instead, **Then** they close the issue as Resolved - continue with a reason.

---

### User Story 6 - Management Reviews Observations and Performance (Priority: P2)

Admin and Director see every observation, Principal feedback and performance issue across the organisation, filtered by Zone, School, Manager, Teacher, result and status, with lists for observations due, overdue and open issues. A Manager sees the same for their own Schools. A Teacher's full history is on one page.

**Why this priority**: Management control is the point of the deck, but it reads data created by Stories 1 to 5.

**Independent Test**: Create records for two Managers; the Director sees both and can filter by Manager and result; each Manager sees only their own; the Teacher's page shows observations, feedback and issues in date order.

**Acceptance Scenarios**:

1. **Given** records across Managers, **When** the Director opens the list and filters by Manager, School or result, **Then** only matching records appear with counts.
2. **Given** a Manager, **When** they open the list, **Then** only records for Teachers at their Schools today appear (and records they authored).
3. **Given** a Teacher, **When** their performance page opens, **Then** observations, Principal feedback, issues and referrals appear in date order with the latest result at the top.
4. **Given** the Observations due list, **When** opened, **Then** it shows each active placed Teacher whose next observation is due or overdue, with the days overdue and the last result.
5. **Given** the management dashboard (028), **When** it asks for counts, **Then** it receives observations done, observations due, open issues and referrals per Manager and per Zone through a read-only summary.

---

### Edge Cases

- A Teacher moves School between a visit and the submission: the observation keeps the School where the visit happened; the Manager of that School at the time stays the observer.
- The Manager who holds the task is not the one who observed (a Director or another Manager covers): the observation is recorded by whoever performs it; the task completes only if the observer is the assignee, otherwise the assignee or the Director completes it with a note.
- Admin or Director records an observation themselves: allowed; no task is completed, and the observer is shown by name.
- A School has no Principal contact: the Manager types the Principal's name on the permission record.
- Two observations of the same Teacher on the same day: both kept; only the latest Submitted counts for the next due date.
- Ratings left blank for an area not seen: "Not observed" is allowed for any area except Overall result; the average ignores it.
- The Teacher exits while an issue is open: the issue is closed as Resolved - exited, automatically on the next check.
- A Manager records feedback that names another person (a pupil or a parent): free text is limited to 2000 characters and kept as written; no pupil data fields exist.
- The same Teacher already has an open performance issue: a new Needs improvement observation is added to that issue; no second issue opens.
- Two people save at the same time: the second gets a stale-version conflict.

## Requirements *(mandatory)*

### Functional Requirements

**Observation form**

- **FR-001**: The system MUST record a classroom observation of one Teacher at one School by one observer, with the date, class, subject and duration in minutes (5 to 240).
- **FR-002**: The form MUST carry a rating from 1 (poor) to 5 (excellent), or Not observed, for English communication, Teaching ability, Content delivery, Classroom management, Student engagement, Preparation, Confidence and Punctuality, plus an overall result of Good, Needs improvement or Unsuitable chosen by the observer.
- **FR-003**: The form MUST carry the observer's comments, strengths, areas for improvement, and optionally the Principal's comments; each up to 2000 characters. Submission MUST require at least one rating, an overall result, and strengths or areas for improvement.
- **FR-004**: The system MUST show the average of the rated areas and, as a guide only, a suggested result (4 or more Good, below 2.5 Unsuitable, otherwise Needs improvement); the observer's chosen result is the one stored.
- **FR-005**: An observation MUST be Draft until submitted; a Draft may be saved and edited by its observer only; a Submitted observation is final except through a recorded correction (FR-025).
- **FR-006**: Up to 5 files (photo, PDF, document) MAY be attached to an observation through the shared file store.

**Principal permission**

- **FR-007**: Before submitting, the observer MUST record the Principal's permission: the name of the person who gave it (defaulting to the School's recorded Principal), how it was given (in person, phone, message, written), and the date. Submitting without it is refused.
- **FR-008**: The system MUST allow the visit to be recorded as Not held, with a reason (Principal declined, Principal absent, Teacher absent, School closed, other with text); a Not held visit needs no ratings and does not satisfy a due observation.
- **FR-009**: Whether permission is required for every observation is a code default of "required" (see Assumptions).

**Principal feedback**

- **FR-010**: The system MUST record Principal feedback separately from observation ratings: Teacher, School, Principal's name, date, category (Good performance, Needs improvement, Attendance issue, Teaching issue, Communication issue, Behaviour issue, Other concern), comments (10 to 2000 characters), whether action is required, and the recorder.
- **FR-011**: Feedback MAY be linked to an observation and MAY be recorded without one.
- **FR-012**: Feedback marked action required MUST offer to open a performance issue, or to add it to the Teacher's open issue.
- **FR-013**: Feedback can be withdrawn by its recorder, or by Admin or Director, with a reason; withdrawn feedback stays in the history and no longer counts.

**Due observations and tasks**

- **FR-014**: A Teacher is due an observation when they are ACTIVE, placed at a School today, and have no Submitted observation within the observation interval (default 60 days; a Teacher with none is due 30 days after their placement start).
- **FR-015**: A daily check MUST create one Observation task per due Teacher for their Manager through the shared task-creation interface, idempotent per Teacher while one is open, due the day it is created, and publish an "observation due" event that notifications turns into one in-app notification for that Manager.
- **FR-016**: Submitting an observation by the task's assignee MUST complete the open Observation task for that Teacher; recording Principal feedback by the assignee MUST complete the open Principal Feedback task linked to that Teacher.
- **FR-017**: The check MUST reassign an open Observation task whose Teacher's Manager has changed, and MUST skip Teachers not ACTIVE or not placed.

**Performance issues**

- **FR-018**: A performance issue links to one Teacher and School and to its source (an observation or a Principal feedback), with a category and a status: Open, Under correction, Referred, Resolved (improved, continue, transferred, replaced, exited), Cancelled.
- **FR-019**: A Submitted observation with the overall result Needs improvement or Unsuitable MUST open an issue unless the Teacher already has an open one, in which case it is added to it. A feedback with action required opens or joins one only when the recorder agrees.
- **FR-020**: A corrective action records what was discussed with the Teacher, what must change, the recorder, and a follow-up date between 1 and 60 days ahead; recording it moves the issue to Under correction and creates a follow-up Observation task through the shared interface, due on the follow-up date.
- **FR-021**: A follow-up observation is a normal observation linked to the issue. Result Good resolves the issue as improved. Result Needs improvement asks for a further corrective action up to a limit of 2 rounds; after the limit the observer MUST either refer the Teacher or escalate to the Director with a note.
- **FR-022**: An Unsuitable result, or a failed correction, lets the observer record a referral with a recommendation (Transfer or Replace), a reason of at least 20 characters, and the supporting records; the issue becomes Referred and each active Director gets a notification.
- **FR-023**: The module MUST expose a read-only interface for spec 027: a Teacher's Referred issues with recommendation, reason, linked observations and feedback; and a way for 027 to report that the Teacher was transferred or replaced, which closes the issue as Resolved - transferred or Resolved - replaced.
- **FR-024**: When a Teacher exits, open issues for them close as Resolved - exited. Admin or Director can cancel an issue, or close a Referred one as Resolved - continue, with a reason.

**History and audit**

- **FR-025**: Every record is append-only in effect: a Submitted observation or feedback is corrected by a new version with a reason, never overwritten, and the earlier version stays readable to Admin and Director. Every create, submit, correction, withdrawal, corrective action, referral, status change and closing MUST be written to Change History with actor, time and before and after values.
- **FR-026**: The Teacher's performance page MUST list observations, feedback, issues and referrals in date order with the latest result first.

**Lists and reports**

- **FR-027**: The system MUST offer lists of observations, Principal feedback and issues, with filters by Zone, School, Manager, Teacher, result or category, status and date range, and with counts; and the lists Observations due, Overdue observations, Open issues and Referred.
- **FR-028**: The system MUST expose a read-only summary for the management dashboard (028): per Manager and per Zone, observations done in a period, observations due and overdue, open issues, referrals, and resolved issues.
- **FR-029**: The system MUST expose the observation as a planned activity through the task module's planned-activity feed by way of its Observation task (no separate feed).

**Access**

- **FR-030**: Every endpoint MUST re-check the caller's grant on the Teacher Performance module on the server and apply the data scope: a Manager reaches only Teachers placed today at their Schools (and records they authored); Teacher and System have no access; Admin and Director are organisation-wide. Out-of-scope records answer "not found".
- **FR-031**: A Teacher MUST NOT see their own observations, ratings, Principal feedback or issues by default; no Teacher-facing screen is built (see Assumptions).
- **FR-032**: Dates MUST read DD/MM/YYYY in screens and notification text.
- **FR-033**: This spec does not change placement, transfer or pay. A rating, issue or referral never changes a Teacher's status, School or salary by itself.

### Key Entities

- **Observation**: Teacher, School, observer (user), date, class, subject, duration, status (Draft, Submitted, Not held, Superseded), ratings per area, average, overall result, comments, strengths, areas for improvement, Principal comments, permission record, linked performance issue, version.
- **Principal Permission**: the person who gave it, the way, the date; held on the observation.
- **Principal Feedback**: Teacher, School, Principal's name, date, category, comments, action required, linked observation, recorder, status (Recorded, Withdrawn), version.
- **Performance Issue**: Teacher, School, source, category, status, opened by and on, correction rounds, closed by, closing reason.
- **Corrective Action**: per issue and round; discussion, required change, follow-up date, recorder, follow-up task reference.
- **Referral**: per issue; recommendation, reason, referring user, date, supporting records, decision outcome reported by 027.
- **Observation Interval and Round Limit**: code defaults (60 days, 2 rounds).
- **History Entry**: append-only line per change with actor, time, event, before and after.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

New module **Teacher Performance** (`TEACHER_PERFORMANCE`) with actions View, Create, Edit. Observations, feedback and issues live under it; there is no Approve action in this spec because the transfer decision belongs to spec 027.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Teacher Performance (Observations, Principal Feedback, Issues, Teacher page) | View, Create, Edit | Org-wide |
| Director | OPERATIONS → Teacher Performance | View, Create, Edit; receives referral notifications | Org-wide |
| Manager  | OPERATIONS → Teacher Performance | View, Create, Edit (own observations, feedback and issues; may be edited only by the author or an Admin or Director) | Assigned: Teachers placed today at their Schools |
| Teacher  | none | none, and cannot be granted by default (a Teacher's own view of their performance is a sensitive decision, see Assumptions) | None |
| System   | none | none, cannot be granted | None (business data) |

The constitution's default access matrix gains a Teacher Performance row (PATCH). The navigation model gains the OPERATIONS item Teacher Performance for Admin, Director and Manager; the daily due check, notifications and task integration run as the system, not as a user.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager records and submits a full observation, including Principal permission, in under 5 minutes.
- **SC-002**: 100% of Teachers who are active and placed and past their interval have an open Observation task for their Manager the morning after the daily check, with no duplicates however often the check runs.
- **SC-003**: Every Needs improvement or Unsuitable observation has an open performance issue within the same save, and no such issue stays Open without a corrective action for more than the task module's escalation window.
- **SC-004**: 100% of corrective actions create exactly one follow-up Observation task due on the follow-up date.
- **SC-005**: Every endpoint refuses Teacher and System, and a Manager never sees a Teacher outside their Schools (verified for every endpoint).
- **SC-006**: 100% of submits, corrections, withdrawals, corrective actions, referrals and closings show in the history with actor, time and before and after values, and no earlier version is lost.
- **SC-007**: The Director finds a Teacher's complete observation, feedback and issue history in under 30 seconds from the list.
- **SC-008**: The lists for 2,000 observations and 500 issues load in under 2 seconds.
- **SC-009**: Every Referred issue is readable by spec 027 with its supporting records, and no referral changes a Teacher's placement on its own.

## Assumptions

Items marked "to confirm in /speckit-clarify" are decisions taken without being able to ask.

- **Real code differs from the planning documents.** The roadmap names the modules "observation" and "performance"; this spec treats it as one feature and leaves the module layout to the plan. The `tasks` module already has the task types Observation and Principal feedback, and its creation interface (C2) refuses an assignee who is not an active Manager, so an observation by a Director or Admin creates no task. Principal contacts already exist on a School record (spec 016, amendment A8), so the Principal's name is read from there rather than being added. A Teacher has only four stored statuses (In training, Active, On leave, Exited); "Under review", "Transferred" and "Replaced" in the business flow are derived views (roadmap D3), so a performance issue is its own record and does not change a Teacher's status. Placement comes from the Teacher-to-School assignment owned by the school contract (spec 012) and read through the teacher module's public interfaces; the Manager scope uses the existing Teacher scope and School-to-Manager assignment.
- **Observation frequency** (open item 7 of the business flow): default every 60 days per Teacher and first due 30 days after placement; a code default, not a settings screen. To confirm in /speckit-clarify.
- **Principal permission** (open item 8): required for every observation by default, recorded as a statement by the Manager (name, way, date); no Principal login or portal. To confirm in /speckit-clarify.
- **Teacher's own view** (open item 16): a Teacher cannot see their observations, ratings, Principal feedback or issues by default; no Teacher-facing screen is built and the module cannot be granted to Teacher without a later spec. Sharing feedback with the Teacher is the Manager's conversation, recorded as the corrective action. To confirm in /speckit-clarify.
- **Who may observe**: Managers for their Schools' Teachers, and Admin and Director for any. Only a Manager gets a task. The Zone Manager is the Manager role.
- **Result choice**: the observer chooses the overall result; the average and suggested result are a guide only. No weighted score, appraisal grade, pay or increment link in version 1. To confirm in /speckit-clarify.
- **Correction rounds**: limit of 2 before referral or Director escalation; follow-up date 1 to 60 days. To confirm in /speckit-clarify.
- **Referral decision**: the Director or the Zone Manager approves a transfer or replacement (roadmap D5) in spec 027; here only the recommendation is recorded. Until spec 027 exists, a Director may close a Referred issue by hand.
- **Rating scale and areas** are taken from the business flow's observation form (nine items; Overall performance is the result). Class and subject are free text; no pupil data is held.
- **Notifications** are in-app (spec 010): observation due, and referral recorded for the Directors. Overdue observation and escalation reuse the task module's events. Push, SMS and email are out of scope.
- **Out of scope**: transfer and replacement (027), substitute arrangements (017a), Manager performance analytics beyond the read-only summary (028), GPS or photo verification of the visit (the Manager's own check-in and visits are in 032 and 033), School or Principal portals, Teacher self-service, scoring tied to pay or incentives, and mobile screens (a later mobile spec).
- Business time zone follows the existing setting (Asia/Kolkata); "today" uses it.
