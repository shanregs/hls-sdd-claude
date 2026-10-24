# Feature Specification: Manager Tasks

**Feature Branch**: `025-manager-tasks`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "Spec 025 Manager Tasks. Platform spec for Manager task management and accountability: assigned, acknowledged, in progress, completed with evidence, verified; overdue needs a reason, follow-up, escalation to the Director; Manager and management task lists (today, upcoming, overdue); task types from the deck. Exposes C2 (create a follow-up task from another module) and C1 (tasks as planned activities). Adds task assigned, overdue and escalated events to notifications."

## Clarifications

### Session 2026-10-11

Decided from `docs/spec-roadmap.md` (row 025, contracts C1 and C2, section 5, decision 6) without asking; each answer is also folded into the requirements below.

- Q: Who can hold a task? -> A: A user who is an active Manager (has the Manager role and a Manager record). Admin and Director assign and watch; they do not hold tasks.
- Q: What does "overdue" mean and is it stored? -> A: Derived, never stored: an open task (not completed, verified or cancelled) whose due date is before today in the business time zone. A task due today is "due today", not overdue.
- Q: What is "overdue needs a reason, follow-up"? -> A: The assignee records an overdue reason (required before the task can be completed) and may name a follow-up date. A follow-up date creates a new linked Follow-up task for the same Manager; the overdue task stays open, still overdue, until it is completed.
- Q: When is a task escalated, and to whom? -> A: Each task has an escalation rule of "escalate after N days overdue" (1 to 30, default 3; callers of the shared interface may set it). A daily check escalates every still-open task that is N or more days past due, once, to every active Director. Admin is not notified by default. The escalation is shown on the task and in the management list.
- Q: Who verifies completed work? -> A: A user with the Approve action on Manager Tasks (Admin and Director by default), never the task's own assignee. Verification either confirms the task (Verified) or sends it back to In progress with a reason.
- Q: What counts as evidence? -> A: A completion note of at least 10 characters is required; up to 5 files (photo, PDF, document) can be attached through the shared file store (C7) and are kept for audit.
- Q: Can an assignee decline or reassign a task? -> A: No. The assignee can only move it forward. Admin and Director (Edit) can reassign, change the due date or escalation rule, or cancel with a reason; every change is audited.
- Q: What do the Manager lists show? -> A: Today, Upcoming (due after today), Overdue, Awaiting verification, Done. A Manager sees only their own tasks. Admin and Director see all and can filter by Manager, type and status.
- Q: Does 025 create follow-up tasks for the 023 follow-up date itself? -> A: Yes. 023 already publishes a follow-up-scheduled event and says 025 will listen; the task module turns it into a Follow-up task for the prospect's owner, once per activity.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Works Through Assigned Tasks (Priority: P1)

A Manager opens My Tasks and sees what is due today, what is coming up and what is overdue. For each task they acknowledge it, start it, and complete it with a note and optional files as evidence. The task then waits for verification.

**Why this priority**: This is the accountability loop the deck asks for; without it a task is only a reminder.

**Independent Test**: Create a task for a Manager, sign in as that Manager, acknowledge, start and complete it with a note; the status moves in order, the history shows who and when, and another Manager cannot see or touch the task.

**Acceptance Scenarios**:

1. **Given** a task assigned to a Manager, **When** the Manager opens My Tasks, **Then** it appears under Today, Upcoming or Overdue by its due date and shows type, title, linked record and due date.
2. **Given** an Assigned task, **When** the Manager acknowledges it and then starts it, **Then** the status is Acknowledged then In progress and each step is recorded with the time.
3. **Given** an In progress task, **When** the Manager completes it without a note, **Then** it is refused and the task is unchanged; with a note of at least 10 characters it becomes Completed.
4. **Given** a Completed task, **When** the Manager tries to change it again, **Then** it is refused (only a verifier acts next).
5. **Given** a task assigned to Manager A, **When** Manager B asks for it by id, **Then** the answer is "not found".

---

### User Story 2 - Management Assigns, Watches and Verifies (Priority: P1)

Admin or Director creates a task of one of the nine types for a Manager with a due date, then sees all tasks by Manager, type and status, and verifies or sends back completed work.

**Why this priority**: Management accountability is the point of the spec and the dashboard (028) reads it.

**Independent Test**: As Director create a task, see it in the all-tasks list, let the Manager complete it, verify it; send another back with a reason and see it return to In progress.

**Acceptance Scenarios**:

1. **Given** the Director chooses type School visit, a Manager, a title and a due date, **When** they save, **Then** the task is Assigned and the Manager gets a "task assigned" notification.
2. **Given** a Completed task, **When** the Director verifies it, **Then** it becomes Verified; **When** the Director sends it back with a reason, **Then** it returns to In progress and the Manager is told.
3. **Given** the assignee is also the person trying to verify, **When** they verify, **Then** it is refused.
4. **Given** an open task, **When** Admin reassigns it or moves the due date or cancels it with a reason, **Then** the change is recorded with before and after values and the new assignee is notified.
5. **Given** the all-tasks list, **When** the Director filters by Manager, type or status, **Then** only matching tasks show, with overdue and escalated flags.

---

### User Story 3 - Overdue Needs a Reason and Escalates to the Director (Priority: P1)

When a task passes its due date the Manager is told and asked for a reason and an optional follow-up date. If it stays open for the escalation days, the Director is told.

**Why this priority**: The deck calls out overdue accountability; tasks must not silently rot.

**Independent Test**: Make a task overdue, run the daily check: the Manager gets one "task overdue" notification. Record a reason with a follow-up date: a linked Follow-up task appears. Run the check after the escalation days: the Directors get one "task escalated" notification and the task shows Escalated.

**Acceptance Scenarios**:

1. **Given** an open task whose due date has passed, **When** the daily check runs, **Then** the Manager gets exactly one overdue notification no matter how often the check runs.
2. **Given** an overdue task, **When** the Manager completes it without an overdue reason, **Then** it is refused; with a reason and a completion note it succeeds.
3. **Given** an overdue task, **When** the Manager records a reason and a follow-up date, **Then** a new Follow-up task linked to the original is assigned to them for that date.
4. **Given** an open task N or more days past due, **When** the daily check runs, **Then** every active Director gets one escalation notification, the task is marked Escalated with the time, and it will not escalate again.
5. **Given** a completed or cancelled task, **When** the check runs, **Then** nothing happens to it.

---

### User Story 4 - Other Modules Create Follow-up Tasks (Priority: P1)

Modules such as overdue-invoice follow-up (022), prospect follow-up (023), corrective action (026) and content (029) call one shared interface to create a task for a user, linked to their own record, with a due date and an escalation rule. The same record never gets two open tasks from the same source.

**Why this priority**: Roadmap contract C2 and decision 6; those specs depend on it.

**Independent Test**: Call the interface twice with the same source key: one task exists. Complete it; calling again creates a new one. A 023 follow-up-scheduled event produces a Follow-up task for the prospect owner.

**Acceptance Scenarios**:

1. **Given** a request with type, title, assignee, linked record, due date, escalation days and a source key, **When** the interface is called, **Then** a task is created, Assigned, with the source recorded and the assignee notified.
2. **Given** an open task with the same source key, **When** called again, **Then** the existing task is returned and nothing is created.
3. **Given** the assignee is not an active Manager, **When** called, **Then** the call is refused with a clear reason.
4. **Given** 023 publishes a follow-up date for a visit, **When** the event is handled, **Then** a Follow-up task exists for the prospect owner due that date, linked to the prospect.

---

### User Story 5 - Tasks Appear as Planned Activities (Priority: P2)

Another spec (032 field-day links, 035 calendar, 028 dashboard, 015 travel) asks for the planned activities of a Manager between two dates and gets tasks in the same shape as drives and visits.

**Why this priority**: Roadmap contract C1; consumers arrive later, so the read interface is small.

**Independent Test**: Create tasks on different dates for two Managers; the feed for one Manager and a date range returns only that Manager's non-cancelled tasks in the range with kind, id, owner, date, place and status.

**Acceptance Scenarios**:

1. **Given** tasks across dates and Managers, **When** the feed is asked for a range and an owner, **Then** only that owner's non-cancelled tasks due in the range come back; with no owner, all.
2. **Given** a task with a place, **When** listed, **Then** the place is carried; otherwise it is empty.

---

### Edge Cases

- A due date in the past is refused on manual creation; the shared interface also accepts past dates for callers that are already late, in which case the task is overdue at once.
- Due date moved on an overdue task to a future date: it stops being overdue; the earlier overdue notice stays on record and it is not notified again for that same due date.
- The assignee is deactivated or loses the Manager role: open tasks stay visible to management with a "no active assignee" flag and can be reassigned.
- Two people act on a task at once: the second gets a stale-version conflict.
- The escalation check finds no active Director: the task is still marked Escalated and it is not retried endlessly.
- Evidence file of a refused type or over 10 MB: refused with the file store's reason; a task can hold at most 5 evidence files.
- The same overdue reason is recorded twice: both entries are kept in the history; the latest is shown.
- Cancelling a Completed or Verified task is refused.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support task types School visit, Follow-up, Content preparation and submission, Recruitment, Training, Teacher assignment, Observation, Principal feedback and Substitute arrangement.
- **FR-002**: A task MUST have a type, title (3 to 120 characters), optional description (up to 2000), one assignee who is an active Manager, a due date, an escalation rule, an optional link to a record in another module (record type, record id and label), an optional place, and a source (Manual or the calling module's key).
- **FR-003**: A task MUST move only along Assigned, Acknowledged, In progress, Completed, Verified; Completed may return to In progress when sent back; any open task may be Cancelled with a reason by management.
- **FR-004**: Only the assignee acknowledges, starts, completes and records an overdue reason; only a user with Approve who is not the assignee verifies or sends back; only users with Edit on others' tasks (Admin and Director by default) reassign, change the due date or escalation rule, or cancel.
- **FR-005**: A Manager MUST be able to create a task only for themselves (a personal task); Admin and Director can create for any active Manager.
- **FR-006**: Completing a task MUST require a completion note of at least 10 characters; evidence files (up to 5) can be added while the task is open or Completed and not yet Verified, using the shared file store.
- **FR-007**: A task is overdue when it is open and its due date is before today (business time zone); this MUST be derived, never stored, so it clears the moment the due date or status changes.
- **FR-008**: Completing an overdue task MUST require an overdue reason (at least 5 characters) recorded by the assignee; the reason may carry a follow-up date that creates a linked Follow-up task for the same Manager.
- **FR-009**: A daily check MUST publish one task-overdue event per task and due date, and one task-escalated event per task once it is open for its escalation days past due; escalation notifies every active Director and marks the task Escalated with the time.
- **FR-010**: The system MUST publish task-assigned (create and reassign), task-overdue and task-escalated events and the notifications module MUST turn them into in-app notifications for the assignee, the assignee, and the Directors respectively, each linking to the task.
- **FR-011**: The system MUST offer Managers the lists Today, Upcoming, Overdue, Awaiting verification and Done, limited to their own tasks, and offer Admin and Director the same lists across all Managers filtered by Manager, type and status, with counts for each list.
- **FR-012**: The system MUST expose a module interface to create a follow-up task for a user, linked to a record, with a due date and an escalation rule (C2), idempotent per source key while a task for that key is open, refusing assignees who are not active Managers.
- **FR-013**: The system MUST expose a module interface returning tasks as planned activities (C1): kind (the task type), id, owner user, date (due date), place and status, for a date range and an optional owner, excluding cancelled tasks.
- **FR-014**: The system MUST create a Follow-up task for the prospect owner when a follow-up date is recorded in marketing (023), once per activity, due on that date.
- **FR-015**: Every change (create, reassign, due-date or escalation change, each status step, overdue reason, evidence added, verification, send back, cancellation, escalation) MUST be written to an append-only task history with actor, time, and before and after values, and to Change History for audit.
- **FR-016**: Every endpoint MUST re-check the caller's grant on the Manager Tasks module on the server and apply the data scope (Manager: own tasks only); a Manager asking for another's task gets "not found".
- **FR-017**: Teacher and System MUST have no access to tasks and cannot be granted it; Manager cannot be granted Approve.
- **FR-018**: Dates MUST read DD/MM/YYYY in the screens and text in notifications.

### Key Entities

- **Task**: type, title, description, assignee (Manager user), created by (a user or the system), due date, escalation days, status, link (record type, id, label), place, source and source key, parent task (for a follow-up created from an overdue reason), overdue reason, completion note, flags for overdue notice sent and escalated with time, version.
- **Task History Entry**: append-only line per change with actor, time, event, before and after.
- **Task Evidence**: link between a task and a stored file (the file store holds name, type, size, who, when).
- **Planned Activity (C1)**: read-only view of a task as kind, id, owner, date, place, status.

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

New module **Manager Tasks** (`TASKS`) with actions View, Create, Edit, Approve.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Manager Tasks | View, Create, Edit, Approve | Org-wide |
| Director | OPERATIONS → Manager Tasks | View, Create, Edit, Approve | Org-wide; receives escalations |
| Manager  | OPERATIONS → My Tasks | View, Create (personal tasks only), Edit (acts on own tasks: acknowledge, start, complete, reason, evidence) | Own tasks (assigned to them) |
| Teacher  | none | none, cannot be granted | None |
| System   | none | none, cannot be granted | None (business data) |

The constitution's default access matrix gains a Manager Tasks row (PATCH 2.5.3). Approve is restricted to Admin and Director by eligibility rules.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Manager sees their Today, Upcoming and Overdue lists with counts in one screen and completes a task with evidence in under 1 minute.
- **SC-002**: 100% of status changes, reassignments, due-date changes and verifications show in the task history with actor, time and before and after values.
- **SC-003**: No task is escalated twice or reported overdue twice for the same due date, however often the daily check runs.
- **SC-004**: Every endpoint refuses Teacher and System, and a Manager never sees another Manager's task (verified for every endpoint).
- **SC-005**: Calling the shared creation interface repeatedly for the same source never produces a second open task.
- **SC-006**: The lists for 500 open tasks load in under 2 seconds.
- **SC-007**: The Director is told of an escalated task on the day the escalation days are reached.

## Assumptions

- Business time zone follows the existing setting (Asia/Kolkata); "today" uses it.
- Tasks are for Managers only in version 1; Teacher tasks and Director-held tasks are out of scope.
- The escalation default of 3 days and the minimum note lengths are code defaults, not a settings screen.
- Push, SMS and email are out of scope; notifications are in-app (spec 010).
- The activity calendar view (035), the dashboard widgets (028) and the mobile screens (034) are separate specs that read the interfaces defined here.
- Bulk and recurring tasks are out of scope.
- Specs 022, 026 and 029 call the interface in their own specs; this spec only provides it and the 023 listener.

## Amended by 035

Spec 035 (Manager Activity Calendar) added `activitiesBetween` on the task feed additively (no schema change). `plannedBetween` and its `PlannedActivity` values are unchanged; the regression test is `TaskPlannedBetweenUnchangedTest`.
