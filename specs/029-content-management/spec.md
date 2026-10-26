# Feature Specification: Content Management

**Feature Branch**: `spec/029-content-management`

**Created**: 2026-10-26

**Status**: Draft

**Input**: User description: "Spec 029 Content Management. Manager content tasks, versioned submissions, review and approval; the actual content file is captured (the deck's 'content prepared without output submitted' gap). Content prepared, submitted, reviewed, approved. Full input in docs/spec-inputs/029-content-management.md."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Manager Submits the Actual Content (Priority: P1)

A content piece is created for an author: a Manager, the Director, or another contributor who is not a Manager (title, category, subject, what is wanted, optional due date). When the author is a Manager and a due date is set, a linked content task appears in their My Tasks. The author prepares the content and submits a file, or someone else submits it on the author's behalf. "Content preparation in progress" is never accepted as the output: a submission without a file is refused.

**Why this priority**: This is the gap the deck names. Without the captured file, content accountability is only a status.

**Independent Test**: Create content for a Manager, see the task in My Tasks, open the content, submit a file, and see version 1 with the file, the author, the submitter and the time. Repeat for the Director as author with no task, and for a non-Manager contributor.

**Acceptance Scenarios**:

1. **Given** content created for a Manager author with a due date, **When** it is saved, **Then** the record exists in status Assigned, a linked content task exists for the Manager, and the author is notified.
1a. **Given** content created for the Director or another contributor, or with no due date, **When** it is saved, **Then** the record exists with no task and no task is required to submit or review it.
1b. **Given** a user submitting for another author, **When** the file is submitted, **Then** the version shows both the author and the person who submitted it on their behalf.
2. **Given** an assigned, draft or returned content record, **When** the Manager submits without a file, **Then** it is refused with a clear reason and nothing changes.
3. **Given** a valid file, **When** the Manager submits, **Then** a new version is created, the status becomes Submitted, and any linked task is completed with the submission as evidence.
4. **Given** earlier versions, **When** a new version is submitted, **Then** every earlier version and its file stay available and unchanged.

---

### User Story 2 - A Reviewer Approves or Returns Content (Priority: P1)

A reviewer opens the current version and its file, then approves it, or returns it for revision with a mandatory comment. The Manager sees the comment, corrects the content and submits a new version, and the cycle repeats until approval.

**Why this priority**: Review and approval is the second half of "prepared, submitted, reviewed, approved".

**Independent Test**: Submit content, return it with a comment as reviewer, see it back with the Manager with the comment, submit version 2, approve it, and see the full history of both versions, the comment and the approval date.

**Acceptance Scenarios**:

1. **Given** submitted content, **When** a reviewer approves, **Then** the status is Approved with the reviewer and approval date, and any linked task is verified.
2. **Given** submitted content, **When** a reviewer returns it without a comment, **Then** it is refused; with a comment, the status is Returned, any linked task goes back to In progress and the Manager is notified.
3. **Given** the author or the person who submitted a version, **When** they try to review it, **Then** the action is not offered and a direct call is refused, even if they hold Approve.
4. **Given** approved content, **When** viewed, **Then** it shows the approved version, reviewer, comments and approval date.

---

### User Story 3 - Management Sees and Finds Content (Priority: P2)

Admin and Director see all content, filtered by author, category, subject, status and date, open any version, and see counts on the dashboard (submitted, awaiting review, approved, overdue). Any other contributor sees only content they authored or submitted.

**Why this priority**: Visibility makes the records useful; it builds on stories 1 and 2.

**Independent Test**: With content from two Managers in different statuses, the Director filters by author and status and gets the right rows; a Manager or contributor sees only their own.

**Acceptance Scenarios**:

1. **Given** content from several Managers, **When** the Director filters, **Then** only matching rows show with current version, status and dates.
2. **Given** a Manager or contributor, **When** they ask for someone else's content, **Then** it is not found.
3. **Given** the dashboard, **When** content exists, **Then** the Content tile shows submitted, awaiting review, approved and overdue counts.

---

### User Story 4 - Any Contributor Starts Content of Their Own (Priority: P2)

A Manager, the Director or another contributor creates a content record for themselves with no assignment and no task, prepares it and submits it through the same review.

**Why this priority**: Convenience; the core flow works without it.

**Independent Test**: As the Director and as a non-Manager contributor, create content with title, category and subject, submit a file, and see it awaiting review by someone else.

**Acceptance Scenarios**:

1. **Given** a contributor, **When** they create their own content, **Then** a record exists in Draft for them with no required task.
2. **Given** the Director authored content, **When** it is submitted, **Then** it is reviewed by another user holding Approve, never by the Director.

---

### Edge Cases

- Refused file type, a file whose content does not match its type, or over the limit for its type: refused with a clear reason; nothing is submitted.
- Two people act on the same content at once: the second gets a stale-version conflict.
- The author is deactivated with content open: it stays visible to management and can be reassigned to another author.
- Nobody other than the author and submitter holds Approve: the content waits in review and management is told (see Assumptions).
- A file larger than its type allows (for example a 30 MB PDF): refused naming the type and its limit.
- The linked task is cancelled or already completed elsewhere: content continues; the link shows the task state.
- Approved content gets a new version: the new version goes back to review and the earlier approval stays in the history (see Assumptions).
- A file version is removed: only Admin or Director, with a reason, recorded; the version row stays.
- Due date passes before submission: the task overdue and escalation rules of spec 025 apply; content shows as overdue.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST keep a content record with Content ID, title (3 to 120 characters), category, subject, author, created date, current version, description, status, reviewer, comments and approval date.
- **FR-002**: Content MUST have an author who is a Manager, the Director or another contributor (see Role & Permission Impact). It MAY be created by the author, or by an Admin or Director for another author, with an optional due date.
- **FR-003**: A linked Manager task of type Content preparation and submission MUST be created through the task creation interface of spec 025 only when the author is an active Manager and a due date is set. Content with any other author, or without a due date, exists with no task, and no step requires one.
- **FR-004**: Submitting MUST require a file stored through the shared file store; a submission without a file MUST be refused.
- **FR-005**: The accepted file types and the size limit for each MUST be: video (mp4) up to 500 MB; audio (mp3, m4a, wav) up to 100 MB; PDF up to 25 MB; Word (docx) up to 25 MB; Excel (xlsx) up to 25 MB. A file MUST be refused when its type is not listed, its first bytes do not match its type, or it exceeds the limit for its type, with a message naming the type and limit.
- **FR-006**: Each submission MUST create a new numbered version with file, author, submitter (who may differ from the author), time and optional note; earlier versions MUST never be changed or overwritten.
- **FR-007**: Status MUST move only along Draft or Assigned, Submitted, Approved, or Returned and back to Submitted on resubmission.
- **FR-008**: A reviewer MUST be a user holding the Approve action on Content and MUST NOT be the author or the submitter of the version reviewed; approve records reviewer and date; return MUST require a comment.
- **FR-009**: Where a task is linked, submitting completes it with the submission as evidence; approval verifies it; a return sends it back to In progress, within the rules of spec 025.
- **FR-010**: Contributors who are not Admin or Director MUST see only content they authored or submitted; Admin and Director see all, filterable by author, category, subject, status and date.
- **FR-011**: Users who may see a content record MUST be able to open and download any of its versions; nobody outside the scope may learn it exists. Video and audio MUST be downloadable without being loaded whole into memory.
- **FR-012**: Removing a file version MUST be limited to Admin and Director, require a reason and be audited.
- **FR-013**: The system MUST notify the author on assignment, approval and return, and reviewers on submission, through the notifications module.
- **FR-014**: The system MUST provide a Content tile for the management dashboard: submitted, awaiting review, approved and overdue (overdue only where a due date exists).
- **FR-015**: Every change MUST be written to an append-only history with actor, time and before and after values, and to Change History.
- **FR-016**: Every endpoint MUST re-check the caller's grant on the Content module on the server and apply data scope; Teacher and System have no access.
- **FR-017**: Dates MUST read DD/MM/YYYY.

### Shared file store impact (checked against main)

The shared store (`files.api.FileStore`, spec 023) today accepts only jpg, png, pdf, docx, xlsx and txt, with one 10 MB limit for every file, the whole file held in memory, and an 11 MB upload ceiling in the server settings. To meet FR-005 it needs, additively and without changing other callers: new types mp4, mp3, m4a and wav with their first-byte checks; a size limit per type (other owners keep 10 MB); a higher upload ceiling for content; and streamed storage and download for large files. PDF, docx and xlsx are already accepted but at 10 MB, so content raises them to 25 MB. This amends specs 023 and 031 in the store only and is recorded for the plan.

### Key Entities

- **Content**: ID, title, category, subject, author (a user, or a named contributor without login, see clarification), created by, due date (optional), description, status, current version number, reviewer, approval date, linked task (optional), version for conflict checks.
- **Content Version**: number, file (in the shared file store), author, submitter, time, note; never changed.
- **Content Review Entry**: reviewer, decision (approved or returned), comment, time, version reviewed.
- **Content History Entry**: append-only line per change.

## Role & Permission Impact *(mandatory)*

New module **Content** (`CONTENT`) with actions View, Create, Edit, Approve. Authors and submitters are any user with Create on Content; the set is not limited to Managers. The five roles are fixed by the constitution, so no new role is added by default.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → Content | View, Create, Edit (create and submit for any author) | Org-wide |
| Director | OPERATIONS → Content | View, Create, Edit, Approve (may also author content) | Org-wide |
| Manager  | OPERATIONS → My Content | View, Create, Edit (own, or submit for someone else when named as submitter) | Authored or submitted by them |
| Teacher  | none | none, cannot be granted | None |
| System   | none | none, cannot be granted | None |

Rules: the author and the submitter of a version never review it. A contributor who is not a Manager is, by default, an existing active user with Create on Content (Admin, Director or Manager); a person with no login is recorded by name on the content by the user who submits for them. Who may hold Approve beyond the Director, and whether a separate contributor role is wanted, are points for clarification.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submitted content has a stored file; no content reaches Submitted or Approved without one.
- **SC-002**: An author submits a prepared file in under 1 minute from opening the content.
- **SC-003**: A reviewer can open the file and approve or return in under 2 minutes.
- **SC-004**: Every version, review and status change shows in the history with actor, time and values; no earlier version is ever lost.
- **SC-005**: A contributor never sees content they neither authored nor submitted, and Teacher and System are refused on every endpoint.
- **SC-006**: Lists of 1,000 content records load in under 2 seconds.

## Assumptions

- Reviewers are users holding Approve on Content (Director by default); the exact default set is a point for clarification.
- Files only, no links, in version 1. Legacy .doc, .xls and video other than mp4 are not accepted; the limits in FR-005 are defaults the owner can change in one place.
- One content piece per task, and a task only for Manager authors with a due date (spec 025 allows only active Managers as assignees; widening that is out of scope).
- If the only Approve holder is the author or submitter, the content waits and management is told rather than self-approving.
- A new version of approved content returns it to review; the earlier approval stays in history.
- Category and subject are plain text with suggestions from earlier values; a maintained list is out of scope.
- Notifications are in-app (spec 010); email, push and SMS are out of scope.
- Out of scope: in-app editing, a Teacher-facing library, scheduling or delivery to Schools, quality scoring, mobile screens, video transcoding.
