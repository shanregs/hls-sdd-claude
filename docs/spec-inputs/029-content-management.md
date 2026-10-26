# 029 Content Management: `/speckit-specify` input

2026-10-26 (revised same day after owner direction). Roadmap row 029 (Priority 2). Depends on 005 (master data) and 025
(Manager tasks, merged). Reuses the shared file store `files.api.FileStore` (built in 023, reused by 025 and 031) and the
task creation interface C2 `tasks.api.TaskCreation`. Source: docs/HLS-core-business-flow.md sections 7.3 and 15, roadmap
sections 1 to 6 (the deck's "content prepared, submitted, reviewed, approved" and the gap "content prepared without
output submitted").

## Owner direction folded in

- Content is not Manager-only. It can be authored by a Manager, the Director, or a third person (a contributor who is
  not necessarily a Manager). Content may exist with or without a task.
- Content files are video, audio, PDF, Word and Excel, with size limits per type.

## Feature description (paste as the argument to `/speckit-specify`)

029-content-management: Content preparation for HLS, with versioned submissions, review and approval, and the actual
content file captured, so that "content preparation in progress" is never accepted as the output.

1. **Authors.** Content has an author: a Manager, the Director, or another contributor who is not a Manager. It is
   created by the author, or by an Admin or Director for another author, with an optional due date. A submission may
   be made by the author or on the author's behalf; both are recorded on the version.
2. **Task is optional.** When the author is an active Manager and a due date is set, a Manager task of type "Content
   preparation and submission" is created through C2 of spec 025 and follows its lifecycle. Otherwise there is no
   task, and no step needs one. (025 allows only active Managers as assignees.)
3. **Content record.** Content ID, title, category, subject, author, created date, current version, file,
   description, status, reviewer, comments and approval date.
4. **Submit the actual content.** A file is required. Types and limits per type: video mp4 up to 500 MB; audio mp3,
   m4a, wav up to 100 MB; PDF, docx, xlsx up to 25 MB each. Each submission is a new version; earlier versions are kept.
5. **Review.** A reviewer (Approve on Content) approves, or returns for revision with a mandatory comment. The reviewer
   is never the author or the submitter of that version, even with Approve.
6. **Revision.** A returned piece goes back to the author, who submits a new version.
7. **Lists.** A contributor sees content they authored or submitted; Admin and Director see all, filtered by author,
   category, subject, status and date, and open any version.
8. **Notifications** through spec 010; **dashboard** Content tile through the 028 tile interface.
9. Every change is audited and appears in Change History.

Out of scope: editing content in the app, a public or Teacher-facing library, scheduling or delivery to Schools,
quality scoring, mobile screens, video transcoding, links instead of files, legacy .doc/.xls.

## Shared file store check (main, spec 023)

`FileStore` accepts jpg, png, pdf, docx, xlsx, txt only; one 10 MB limit (`MAX_BYTES`) for all files; whole file in a
`byte[]`; first-byte checks per type; server upload ceiling 11 MB. Changes needed, additive so 015, 025 and 031 keep
their 10 MB: new types mp4, mp3, m4a, wav with first-byte checks; per-type limits; a higher upload ceiling for the
content endpoint; streamed storage and download for large files.

## Role & Permission Impact (to carry into the spec)

New permission module `CONTENT` (View, Create, Edit, Approve). The five roles are fixed by the constitution. Admin:
View/Create/Edit. Director: all including Approve. Manager: View/Create/Edit on own. Teacher and System: none. Non-Manager
contributors are existing users with Create (default), or people with no login recorded by name by the user who
submits for them. Files open only for users who may see the content.

## Points for `/speckit-clarify`

See specs/029-content-management/clarification-questions.md.
