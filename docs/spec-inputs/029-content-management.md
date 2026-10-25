# 029 Content Management: `/speckit-specify` input

2026-10-26. Roadmap row 029 (Priority 2). Depends on 005 (master data, Managers) and 025 (Manager tasks, merged).
Reuses the shared file store `files.api.FileStore` (built in 023, reused by 025 and 031) and the task creation
interface C2 `tasks.api.TaskCreation`. Source: docs/HLS-core-business-flow.md sections 7.3 and 15, roadmap
sections 1 to 6 (the deck's "content prepared, submitted, reviewed, approved" and the gap "content prepared without
output submitted").

## Feature description (paste as the argument to `/speckit-specify`)

029-content-management: Content preparation for HLS Managers, with versioned submissions, review and approval, and the
actual content file captured, so that "content preparation in progress" is never accepted as the output.

1. **Content assignment.** An Admin or Director assigns a content piece to a Manager: title, category, subject, a
   description of what is wanted and a due date. The assignment creates a Manager task of type "Content preparation and
   submission" through the task creation interface of spec 025 (C2), so it appears in My Tasks, the planned-activity
   feed and the overdue and escalation rules of 025. A Manager may also start a content record of their own.
2. **Content record.** Content ID, title, category, subject, Manager, created date, current version, file,
   description, status, reviewer, comments and approval date.
3. **Submit the actual content.** The Manager prepares the content and submits a file through the shared file store.
   A submission without a file is refused. Each submission is a new version (1, 2, 3...); earlier versions and their
   files are kept and never overwritten.
4. **Review.** A reviewer (a user with the Approve action on Content) opens the current version and its file, then
   approves it, or returns it for revision with a mandatory comment. The reviewer cannot be the submitting Manager.
5. **Revision and resubmission.** A returned content piece goes back to the Manager with the comment; the Manager
   corrects and submits a new version. The history of versions, reviews, comments and decisions is kept.
6. **Task link.** Submitting content completes the linked task with the submission as evidence; approval verifies it;
   a return sends it back to In progress, as the task rules of 025 allow. An overdue content task follows 025.
7. **Content library and lists.** Manager: own content (draft, submitted, in review, returned, approved). Admin and
   Director: all content, filtered by Manager, category, subject, status and date, with open of any version.
8. **Notifications.** Content assigned, submitted (to reviewers), approved and returned (to the Manager), through
   spec 010. Dashboard "Content" tile through the 028 tile interface (submitted, awaiting review, approved, overdue).
9. Every change is audited and appears in Change History. Scope follows the access model: a Manager sees only their
   own content; Admin and Director see all.

Out of scope: editing content inside the app, a public or Teacher-facing content library, deleting approved content,
content scheduling or delivery to Schools, plagiarism or quality scoring, mobile screens (a later spec), video
transcoding.

## Role & Permission Impact (to carry into the spec)

New permission module `CONTENT` (View, Create, Edit, Approve), seeded as: Manager View/Create/Edit (own), Director all
including Approve, Admin View/Create/Edit. Approve is not eligible for Admin if that follows the 025 and 031 pattern.
Teacher and System: none. Files only open for users who may see the content (never a public link).

## Cross-spec impact

025: `CONTENT_SUBMISSION` task type exists; 029 calls C2 and reacts to task completion. 023/031 file store: owner
type `CONTENT_VERSION`. 028: Content tile. 010: new events. 003: audit visibility for the new entities.

## Points for `/speckit-clarify`

- Who reviews: Director only, Admin too, or the Manager's Zone head?
- Allowed file types and maximum size (reuse the store's policy or stricter)?
- Can a Manager create content with no assigned task, and can one task cover several pieces?
- Is a link (URL) accepted instead of a file, or file only?
- Can approved content be superseded by a new version, and what happens to its approved status then?
