# Feature Specification: Content Management

**Feature Branch**: `spec/029-content-management`

**Created**: 2026-10-26

**Status**: Draft (clarified 2026-10-26)

**Input**: User description: "Spec 029 Content Management. Manager content tasks, versioned submissions, review and approval; the actual content file is captured (the deck's 'content prepared without output submitted' gap). Content prepared, submitted, reviewed, approved. Full input in docs/spec-inputs/029-content-management.md."

## Clarifications

### Session 2026-10-26

Answered by the owner against `clarification-questions.md` (Q1 = B, Q2 to Q5 = A) and folded into the requirements below.

- Q: Who is the contributor who is not a Manager? -> A: A new sixth role, **Content Contributor**, with its own sign-in. This amends Constitution Principle II (five fixed roles becomes six) and the access model of specs 002 and 004; the amendment is part of this spec's plan.
- Q: Who holds Approve and who approves the Director's own content? -> A: The Director holds Approve by default; Admin can be granted it in the matrix; Director-authored content is approved by an Admin who has been granted Approve, and otherwise waits and management is told. The author and submitter of a version never approve it.
- Q: Are the size limits and formats right? -> A: Yes: video mp4 up to 500 MB, audio mp3, m4a and wav up to 100 MB, PDF, Word (docx) and Excel (xlsx) up to 25 MB each; no legacy .doc or .xls and no mov or mkv.
- Q: What happens when approved content gets a new version? -> A: It goes back to review; the earlier approved version stays as the last approved one.
- Q: What can a Content Contributor see? -> A: Only content they authored or submitted, in version 1.
- Decided: the constitution amendment is MAJOR 3.0.0. Admin is eligible for Approve but is NOT seeded with it (only the Director is); granting it is a matrix change. The 025 task interface gains a small `TaskProgress` interface so content can complete, verify and send back its linked task. The large-upload ceiling and plain downloads (no range or in-browser streaming playback) are accepted for version 1, with the deployment change recorded in `docs/aws-deployment-and-ddos-protection.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An Author Submits the Actual Content (Priority: P1)

A content piece is created for an author: a Manager, the Director, an Admin or a Content Contributor (title, category, subject, what is wanted, optional due date). When the author is a Manager and a due date is set, a linked content task appears in their My Tasks. The author prepares the content and submits a file, or another permitted user submits it on the author's behalf. "Content preparation in progress" is never accepted as the output: a submission without a file is refused.

**Why this priority**: This is the gap the deck names. Without the captured file, content accountability is only a status.

**Independent Test**: Create content for a Manager, see the task in My Tasks, open the content, submit a file, and see version 1 with the file, the author, the submitter and the time. Repeat for the Director as author and for a Content Contributor, with no task.

**Acceptance Scenarios**:

1. **Given** content created for a Manager author with a due date, **When** it is saved, **Then** the record exists in status Assigned, a linked content task exists for the Manager, and the author is notified.
2. **Given** content created for the Director, a Content Contributor or an Admin, or with no due date, **When** it is saved, **Then** the record exists with no task and no task is required to submit or review it.
3. **Given** a user submitting for another author, **When** the file is submitted, **Then** the version shows both the author and the person who submitted it.
4. **Given** an assigned, draft or returned content record, **When** the author submits without a file, **Then** it is refused with a clear reason and nothing changes.
5. **Given** a valid file, **When** it is submitted, **Then** a new version is created, the status becomes Submitted, and any linked task is completed with the submission as evidence.
6. **Given** earlier versions, **When** a new version is submitted, **Then** every earlier version and its file stay available and unchanged.

---

### User Story 2 - A Reviewer Approves or Returns Content (Priority: P1)

A reviewer opens the current version and its file, then approves it, or returns it for revision with a mandatory comment. The author sees the comment, corrects the content and submits a new version, and the cycle repeats until approval.

**Why this priority**: Review and approval is the second half of "prepared, submitted, reviewed, approved".

**Independent Test**: Submit content, return it with a comment as reviewer, see it back with the author with the comment, submit version 2, approve it, and see the full history of both versions, the comment and the approval date.

**Acceptance Scenarios**:

1. **Given** submitted content, **When** a reviewer approves, **Then** the status is Approved with the reviewer and approval date, and any linked task is verified.
2. **Given** submitted content, **When** a reviewer returns it without a comment, **Then** it is refused; with a comment, the status is Returned, any linked task goes back to In progress and the author is notified.
3. **Given** the author or the person who submitted a version, **When** they try to review it, **Then** the action is not offered and a direct call is refused, even if they hold Approve.
4. **Given** approved content, **When** viewed, **Then** it shows the approved version, reviewer, comments and approval date.
5. **Given** approved content, **When** a new version is submitted, **Then** the content returns to Submitted, the earlier approved version stays marked as the last approved, and the reviewer is notified.
6. **Given** Director-authored content, **When** it is submitted, **Then** only a user other than the Director who holds Approve can approve it; if none exists, it waits and management is told.

---

### User Story 3 - Management Sees and Finds Content (Priority: P2)

Admin and Director see all content, filtered by author, category, subject, status and date, open any version, and see counts on the dashboard (submitted, awaiting review, approved, overdue). A Manager or Content Contributor sees only content they authored or submitted.

**Why this priority**: Visibility makes the records useful; it builds on stories 1 and 2.

**Independent Test**: With content from several authors in different statuses, the Director filters by author and status and gets the right rows; a Manager and a Contributor each see only their own.

**Acceptance Scenarios**:

1. **Given** content from several authors, **When** the Director filters, **Then** only matching rows show with current version, status and dates.
2. **Given** a Manager or Content Contributor, **When** they ask for someone else's content, **Then** it is not found.
3. **Given** the dashboard, **When** content exists, **Then** the Content tile shows submitted, awaiting review, approved and overdue counts.

---

### User Story 4 - Any Author Starts Content of Their Own (Priority: P2)

A Manager, the Director, an Admin or a Content Contributor creates a content record for themselves with no assignment and no task, prepares it and submits it through the same review.

**Why this priority**: The Contributor role exists for people who are not assigned tasks; the core flow works without it, but they need it.

**Independent Test**: As a Content Contributor, create content with title, category and subject, submit a file, and see it awaiting review by someone else.

**Acceptance Scenarios**:

1. **Given** a Content Contributor, **When** they create their own content, **Then** a record exists in Draft for them with no task.

---

### User Story 5 - Admin Gives Someone the Content Contributor Role (Priority: P1)

An Admin (or System, who manages users) creates a user with the Content Contributor role, alone or together with other roles, through User Management. The user signs in, sees only the Dashboard, My Content, Notifications and Account, and nothing else.

**Why this priority**: Without the role nobody outside the existing five can author content.

**Independent Test**: Create a user with only the Content Contributor role, sign in, see exactly that menu, and get refused on every other module by direct call.

**Acceptance Scenarios**:

1. **Given** the user create screen, **When** Admin picks Content Contributor (alone or with other roles), **Then** the account is created with that role and no Manager or Teacher record is required.
2. **Given** a Contributor, **When** they sign in, **Then** they land on a Contributor dashboard (counts of their own content by status) and the menu shows only authorized items.
3. **Given** a Contributor, **When** they call any endpoint of another module, **Then** it is refused; the mobile app refuses their sign-in as for any role without a mobile experience.
4. **Given** a user who also holds Manager, **When** the Contributor role is added, **Then** menus, actions and scope are the union of both roles.
5. **Given** the last Admin rule, **When** roles change, **Then** adding or removing Contributor never affects it.

---

### Edge Cases

- Refused file type, a file whose content does not match its type, or over the limit for its type: refused with a clear reason naming the type and its limit (for example a 30 MB PDF); nothing is submitted.
- Two people act on the same content at once: the second gets a stale-version conflict.
- The author is deactivated, or loses the Contributor role, with content open: it stays visible to management and can be reassigned to another author.
- The author is a Manager who later has no task: content continues; the task is optional.
- Nobody other than the author and submitter holds Approve: the content waits in review and management is told.
- The linked task is cancelled or already completed elsewhere: content continues; the link shows the task state.
- A file version is removed: only Admin or Director, with a reason, recorded; the version row stays.
- Due date passes before submission: the task overdue and escalation rules of spec 025 apply to the linked task; content shows as overdue only where a due date exists.
- A Contributor is given another role and then loses it: scope shrinks to Contributor scope immediately.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST keep a content record with Content ID, title (3 to 120 characters), category, subject, author, created date, current version, description, status, reviewer, comments and approval date.
- **FR-002**: Content MUST have an author who is an active user holding Create on Content (by default Admin, Director, Manager or Content Contributor). It MAY be created by the author, or by an Admin or Director for another author, with an optional due date.
- **FR-003**: A linked Manager task of type Content preparation and submission MUST be created through the task creation interface of spec 025 only when the author is an active Manager and a due date is set. Content with any other author, or without a due date, exists with no task, and no step requires one.
- **FR-004**: Submitting MUST require a file stored through the shared file store; a submission without a file MUST be refused.
- **FR-005**: The accepted file types and the size limit for each MUST be: video (mp4) up to 500 MB; audio (mp3, m4a, wav) up to 100 MB; PDF up to 25 MB; Word (docx) up to 25 MB; Excel (xlsx) up to 25 MB. A file MUST be refused when its type is not listed, its first bytes do not match its type, or it exceeds the limit for its type, with a message naming the type and limit.
- **FR-006**: Each submission MUST create a new numbered version with file, author, submitter (who may differ from the author), time and optional note; earlier versions MUST never be changed or overwritten.
- **FR-007**: Status MUST move only along Draft or Assigned, Submitted, Approved, or Returned and back to Submitted on resubmission. A new version of Approved content returns it to Submitted and keeps the earlier approved version marked as the last approved.
- **FR-008**: A reviewer MUST be a user holding the Approve action on Content and MUST NOT be the author or the submitter of the version reviewed; approve records reviewer and date; return MUST require a comment. When no eligible reviewer exists, the content stays in review and every active Director and Admin is notified once.
- **FR-009**: Where a task is linked, submitting completes it with the submission as evidence; approval verifies it; a return sends it back to In progress, within the rules of spec 025.
- **FR-010**: Users who are neither Admin nor Director MUST see only content they authored or submitted; Admin and Director see all, filterable by author, category, subject, status and date.
- **FR-011**: Users who may see a content record MUST be able to open and download any of its versions; nobody outside the scope may learn it exists. Video and audio MUST be downloadable without being loaded whole into memory.
- **FR-012**: Removing a file version MUST be limited to Admin and Director, require a reason and be audited.
- **FR-013**: The system MUST notify the author on assignment, approval and return, and reviewers on submission, through the notifications module.
- **FR-014**: The system MUST provide a Content tile for the management dashboard: submitted, awaiting review, approved and overdue (overdue only where a due date exists).
- **FR-015**: Every change MUST be written to an append-only history with actor, time and before and after values, and to Change History.
- **FR-016**: Every endpoint MUST re-check the caller's grant on the Content module on the server and apply data scope; Teacher and System have no access.
- **FR-017**: Dates MUST read DD/MM/YYYY.
- **FR-018**: The system MUST have a sixth fixed role, **Content Contributor**, that can be assigned in User Management (spec 004) alone or with other roles, needs no Manager, Teacher or other business record, is recognised at sign-in like every role, and has web access only.
- **FR-019**: The Content Contributor default grants MUST be: Dashboard (View, own), Content (View, Create, Edit on own), Notifications (own) and Account (Profile View and Edit own). Every other module MUST be ineligible for the role, shown as not applicable in the permission matrix and refused by the server. Approve on Content MUST be ineligible for the role.
- **FR-020**: Menus and dashboards MUST be derived from the server's access model for the new role as for the others (hidden, not disabled); the Contributor has one landing dashboard showing counts of their own content by status.
- **FR-021**: The permission matrix MUST seed the Content module for all roles as in the table below, remain editable by Admin, Director and System, and show Content Contributor as a sixth column.

### Shared file store impact (checked against main)

The shared store (`files.api.FileStore`, spec 023) today accepts only jpg, png, pdf, docx, xlsx and txt, with one 10 MB limit for every file, the whole file held in memory, and an 11 MB upload ceiling in the server settings. To meet FR-005 it needs, additively and without changing other callers: new types mp4, mp3, m4a and wav with their first-byte checks; a size limit per type (other owners keep 10 MB); a higher upload ceiling guarded so other endpoints still refuse early; and streamed storage and download for large files. This amends specs 023 and 031 in the store only.

### Key Entities

- **Content**: ID, title, category, subject, author (a user), created by, due date (optional), description, status, current version number, last approved version number, reviewer, approval date, linked task (optional), version for conflict checks.
- **Content Version**: number, file (in the shared file store), author, submitter, time, note; never changed.
- **Content Review Entry**: reviewer, decision (approved or returned), comment, time, version reviewed.
- **Content History Entry**: append-only line per change.
- **Content Contributor role**: the sixth fixed role; assigned through role assignment like the others.

## Role & Permission Impact *(mandatory)*

New module **Content** (`CONTENT`) with actions View, Create, Edit, Approve, and a sixth role **Content Contributor** (constitution amendment, see the plan). Roles are fixed: users cannot create, rename or delete them; the sixth is added by this amendment.

| Role | Menu (section → item) | Default actions | Data scope |
| ---- | --------------------- | --------------- | ---------- |
| Admin | OPERATIONS → Content | View, Create, Edit (Approve can be granted) | Org-wide |
| Director | OPERATIONS → Content | View, Create, Edit, Approve | Org-wide |
| Manager | OPERATIONS → My Content | View, Create, Edit (own, or submit for another when permitted); never Approve | Authored or submitted by them |
| Teacher | none | none, cannot be granted | None |
| System | none | none, cannot be granted | None |
| Content Contributor | OPERATIONS → My Content; Dashboard; Notifications; Account | View, Create, Edit (own); never Approve | Authored or submitted by them |

Content Contributor elsewhere: no access to any other module and cannot be granted any. It cannot sign in to the mobile app. User Management lists it as a sixth option; System and Admin can assign it. The constitution's default access matrix gains a Content row and a Content Contributor column.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submitted content has a stored file; no content reaches Submitted or Approved without one.
- **SC-002**: An author submits a prepared file in under 1 minute from opening the content (excluding upload time of large video).
- **SC-003**: A reviewer can open the file and approve or return in under 2 minutes.
- **SC-004**: Every version, review and status change shows in the history with actor, time and values; no earlier version is ever lost.
- **SC-005**: A user never sees content they neither authored nor submitted unless they are Admin or Director, and Teacher and System are refused on every endpoint.
- **SC-006**: Lists of 1,000 content records load in under 2 seconds.
- **SC-007**: A user with only the Content Contributor role is refused on every endpoint outside Content, Notifications, the dashboard and Account (verified for every endpoint).
- **SC-008**: Existing users and menus of the five earlier roles are unchanged after the sixth role is added.

## Assumptions

- Reviewers are users holding Approve on Content: Director by default; Admin only when granted in the matrix (decided, not seeded).
- Files only, no links, in version 1. The limits in FR-005 are defaults the owner can change in one place.
- One content piece per task, and a task only for Manager authors with a due date (spec 025 allows only active Managers as assignees; widening that is out of scope).
- A Content Contributor has no salary, attendance, leave or expense record and is not a payroll subject.
- Notifications are in-app (spec 010); email, push and SMS are out of scope.
- Category and subject are plain text with suggestions from earlier values.
- Out of scope: in-app editing, a Teacher-facing library, scheduling or delivery to Schools, quality scoring, mobile screens, video transcoding, links instead of files, people with no login as authors.
