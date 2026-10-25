# Feature Specification: Content Management

**Feature Branch**: `spec/029-content-management`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Spec 029 Content Management. Manager content tasks, versioned submissions, review and approval; the actual content file is captured (the deck's 'content prepared without output submitted' gap). Content prepared, submitted, reviewed, approved. Full input in docs/spec-inputs/029-content-management.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Submits the Actual Content (Priority: P1)

An Admin or Director assigns a content piece to a Manager (title, category, subject, what is wanted, due date). It shows in the Manager's My Tasks as a content task. The Manager prepares the content and submits a file. "Content preparation in progress" is never accepted as the output: a submission without a file is refused.

**Why this priority**: This is the gap the deck names. Without the captured file, content accountability is only a status.

**Independent Test**: Assign content to a Manager, see the task in My Tasks, open the content, submit a file, and see version 1 with the file, the submitter and the time.

**Acceptance Scenarios**:

1. **Given** an assignment, **When** it is saved, **Then** a content record exists in status Assigned, a linked content task exists for the Manager, and the Manager is notified.
2. **Given** an assigned or returned content record, **When** the Manager submits without a file, **Then** it is refused with a clear reason and nothing changes.
3. **Given** a valid file, **When** the Manager submits, **Then** a new version is created, the status becomes Submitted, and the linked task is completed with the submission as evidence.
4. **Given** earlier versions, **When** a new version is submitted, **Then** every earlier version and its file stay available and unchanged.

---

### User Story 2 - A Reviewer Approves or Returns Content (Priority: P1)

A reviewer opens the current version and its file, then approves it, or returns it for revision with a mandatory comment. The Manager sees the comment, corrects the content and submits a new version, and the cycle repeats until approval.

**Why this priority**: Review and approval is the second half of "prepared, submitted, reviewed, approved".

**Independent Test**: Submit content, return it with a comment as reviewer, see it back with the Manager with the comment, submit version 2, approve it, and see the full history of both versions, the comment and the approval date.

**Acceptance Scenarios**:

1. **Given** submitted content, **When** a reviewer approves, **Then** the status is Approved with the reviewer and approval date, and the linked task is verified.
2. **Given** submitted content, **When** a reviewer returns it without a comment, **Then** it is refused; with a comment, the status is Returned, the linked task goes back to In progress and the Manager is notified.
3. **Given** the submitting Manager, **When** they try to review their own content, **Then** the action is not offered and a direct call is refused.
4. **Given** approved content, **When** viewed, **Then** it shows the approved version, reviewer, comments and approval date.

---

### User Story 3 - Management Sees and Finds Content (Priority: P2)

Admin and Director see all content, filtered by Manager, category, subject, status and date, open any version, and see counts on the dashboard (submitted, awaiting review, approved, overdue). A Manager sees only their own content.

**Why this priority**: Visibility makes the records useful; it builds on stories 1 and 2.

**Independent Test**: With content from two Managers in different statuses, the Director filters by Manager and status and gets the right rows; a Manager sees only their own.

**Acceptance Scenarios**:

1. **Given** content from several Managers, **When** the Director filters, **Then** only matching rows show with current version, status and dates.
2. **Given** a Manager, **When** they ask for another Manager's content, **Then** it is not found.
3. **Given** the dashboard, **When** content exists, **Then** the Content tile shows submitted, awaiting review, approved and overdue counts.

---

### User Story 4 - A Manager Starts Content of Their Own (Priority: P3)

A Manager creates a content record for themselves without an assignment, prepares it and submits it through the same review.

**Why this priority**: Convenience; the core flow works without it.

**Independent Test**: As a Manager, create content with title, category and subject, submit a file, and see it awaiting review.

**Acceptance Scenarios**:

1. **Given** a Manager, **When** they create their own content, **Then** a record exists in Draft for them with no required task.

---

### Edge Cases

- Refused file type or over the store's size limit: refused with the store's reason; nothing is submitted.
- Two people act on the same content at once: the second gets a stale-version conflict.
- The Manager is deactivated with content open: it stays visible to management and can be reassigned.
- The linked task is cancelled or already completed elsewhere: content continues; the link shows the task state.
- Approved content gets a new version: the new version goes back to review and the earlier approval stays in the history (see Assumptions).
- A file version is removed: only Admin or Director, with a reason, recorded; the version row stays.
- Due date passes before submission: the task overdue and escalation rules of spec 025 apply; content shows as overdue.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST keep a content record with Content ID, title (3 to 120 characters), category, subject, Manager, created date, current version, description, status, reviewer, comments and approval date.
- **FR-002**: Admin and Director MUST be able to assign content to an active Manager with a due date; a Manager MUST be able to create content for themselves.
- **FR-003**: Assigning content MUST create a linked Manager task of type Content preparation and submission through the task creation interface of spec 025, once per content record.
- **FR-004**: Submitting MUST require a file stored through the shared file store; a submission without a file MUST be refused.
- **FR-005**: Each submission MUST create a new numbered version with file, submitter, time and optional note; earlier versions MUST never be changed or overwritten.
- **FR-006**: Status MUST move only along Draft or Assigned, Submitted, Approved, or Returned and back to Submitted on resubmission.
- **FR-007**: A reviewer MUST be a user with the Approve action on Content and MUST NOT be the submitting Manager; approve records reviewer and date; return MUST require a comment.
- **FR-008**: Submitting completes the linked task with the submission as evidence; approval verifies it; a return sends it back to In progress, within the rules of spec 025.
- **FR-009**: Managers MUST see only their own content; Admin and Director see all, filterable by Manager, category, subject, status and date.
- **FR-010**: Users who may see a content record MUST be able to open and download any of its versions; nobody outside the scope may learn it exists.
- **FR-011**: Removing a file version MUST be limited to Admin and Director, require a reason and be audited.
- **FR-012**: The system MUST notify the Manager on assignment, approval and return, and reviewers on submission, through the notifications module.
- **FR-013**: The system MUST provide a Content tile for the management dashboard: submitted, awaiting review, approved and overdue.
- **FR-014**: Every change MUST be written to an append-only history with actor, time and before and after values, and to Change History.
- **FR-015**: Every endpoint MUST re-check the caller's grant on the Content module on the server and apply data scope; Teacher and System have no access.
- **FR-016**: Dates MUST read DD/MM/YYYY.

### Key Entities

- **Content**: ID, title, category, subject, Manager, assigned by, due date, description, status, current version number, reviewer, approval date, linked task, version for conflict checks.
- **Content Version**: number, file (in the shared file store), submitter, time, note; never changed.
- **Content Review Entry**: reviewer, decision (approved or returned), comment, time, version reviewed.
- **Content History Entry**: append-only line per change.

## Role & Permission Impact *(mandatory)*

New module **Content** (`CONTENT`) with actions View, Create, Edit, Approve.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Content | View, Create, Edit | Org-wide |
| Director | OPERATIONS → Content | View, Create, Edit, Approve | Org-wide |
| Manager  | OPERATIONS → My Content | View, Create, Edit (own: prepare and submit) | Own content |
| Teacher  | none | none, cannot be granted | None |
| System   | none | none, cannot be granted | None |

Approve is held by Director by default; whether Admin or Manager may hold it is a point for clarification.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submitted content has a stored file; no content reaches Submitted or Approved without one.
- **SC-002**: A Manager submits a prepared file in under 1 minute from opening the content.
- **SC-003**: A reviewer can open the file and approve or return in under 2 minutes.
- **SC-004**: Every version, review and status change shows in the history with actor, time and values; no earlier version is ever lost.
- **SC-005**: A Manager never sees another Manager's content, and Teacher and System are refused on every endpoint.
- **SC-006**: Lists of 1,000 content records load in under 2 seconds.

## Assumptions

- Reviewers are users holding Approve on Content (Director by default); the exact default set is a point for clarification.
- Files only, no links, in version 1; allowed types and size follow the shared file store policy.
- One content piece per task.
- A new version of approved content returns it to review; the earlier approval stays in history.
- Category and subject are plain text with suggestions from earlier values; a maintained list is out of scope.
- Notifications are in-app (spec 010); email, push and SMS are out of scope.
- Out of scope: in-app editing, a Teacher-facing library, scheduling or delivery to Schools, quality scoring, mobile screens, video transcoding.
