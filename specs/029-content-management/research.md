# Research: Content Management

## R1. A sixth role, and how it is assigned

- Decision: add `CONTENT_CONTRIBUTOR` to `Role`. Assignment goes through the existing 004 path (`UserAdminService`,
  `role_assignment` rows); the role picker is driven by the enum, so the UI lists it automatically once the API returns it.
- Why: the owner chose a new role (Q1 = B). Authorisation is by grant, so the same grants work for the union of roles.
- Fit: 19 characters in `VARCHAR(20)`; no check constraint on role values; `permission_matrix.role` the same.
- Compile-time safety: `AccessModelService` and other `switch (role)` statements are exhaustive over the enum and will fail to
  compile until the new case is handled, which is the desired prompt (list in tasks).
- Not required: a Manager, Teacher or other business record. A Contributor is a plain `app_user` with one role assignment.
- Mobile: `MobileRoleEligibility` is an allow-list (Teacher, Manager, Director), so the new role is refused without change; a test pins it.

## R2. Who may be an author, and the optional task

- Decision: an author is an active user who holds Create on Content (resolved by `PermissionChecker`, not by role name), so
  a later matrix change needs no code. Task creation only when the author is an active Manager (checked through
  `organization.api`) and a due date is set; `TaskCreation.createFollowUp` already refuses other assignees, so the bridge checks first and skips otherwise.
- Idempotency: source `CONTENT`, source key the content id.

## R3. Reviewer rule

- Decision: reviewer = holds Approve on Content, and is not the content's author, creator-as-submitter or the submitter of the version under review.
  "No eligible reviewer" is evaluated at submit time (count of active users holding Approve minus author and submitter); when zero, the content stays
  Submitted and a `ContentNoReviewer` event notifies active Directors and Admins once per version.
- Approve eligibility: Admin and Director only (Manager and Contributor never). Seed: Director true, Admin false.

## R4. File store extension

- Today: types jpg, png, pdf, docx, xlsx, txt; `MAX_BYTES` 10 MB; `store(..., byte[])`; multipart max 11 MB.
- Decision: add `FileLimits` (extension to max bytes) and an overload `store(actor, ownerType, ownerId, name, InputStream, long size, FileLimits)` plus
  `openStream`. New types: mp4 (`ftyp` at offset 4), mp3 (`ID3` or an MPEG frame sync `FF Fx`), m4a (`ftyp` with M4A brand), wav (`RIFF....WAVE`). The old
  `store(byte[])` keeps its 10 MB default through a default `FileLimits`, so 015, 025, 031 are unchanged. Content passes the table in FR-005.
- Why the old types stay: the policy is a per-owner whitelist plus limit; content only offers its five categories (mp4, mp3, m4a, wav, pdf, docx, xlsx).
- Multipart: set `spring.servlet.multipart.max-file-size` and `max-request-size` to 520 MB, `file-size-threshold` 2 MB so large parts go to disk. A servlet
  filter refuses a request over 11 MB (by `Content-Length`) on every upload path except `/api/v1/content/**`, so other endpoints never buffer a large body.
- Streaming: bytes are written to disk while hashing; download uses `StreamingResponseBody` with `Content-Length`, as attachment (`Content-Disposition`), plus HTTP range is out of scope for v1 (download only; browser playback of the downloaded file).
- Risk: nginx/ALB request limits in deployment (`docs/aws-deployment-and-ddos-protection.md`) must allow 500 MB; recorded as a deployment note.

## R5. Versions and approval

- Version numbers 1..n per content; `content_version` rows never updated. `last_approved_version` is kept on `content` and moved only by an approval.
  A submission on Approved content sets status Submitted; the last approved version stays until a newer one is approved.

## R6. Visibility

- Admin, Director: all. Others: `author_user_id = me OR any version submitted_by = me`. Implemented as one query predicate; other rows return 404.
  Reviewers with Approve who are neither: no access to unrelated content in v1 unless Admin or Director. (Approve is only eligible for those two.)

## R7. Contributor dashboard and menu

- Navigation catalog: OPERATIONS → "My Content" (`/operations/content/mine`) for Manager and Contributor; "Content" (`/operations/content`) for Admin and Director;
  both require View on `CONTENT`. Contributor dashboard: `DashboardRouter` shows `ContributorDashboard` (own content counts by status) from a `/api/v1/content/summary`.
- Profile, Notifications, Account items: existing items gain the Contributor role in their role sets.

## R8. Dashboard (028)

- `ContentTiles implements TileSource`: Submitted, Awaiting review, Approved, Overdue (due date passed and not approved). An approvals-inbox item
  source for submitted content visible to Approve holders.

## R9. Notifications

- Types: `CONTENT_ASSIGNED`, `CONTENT_SUBMITTED`, `CONTENT_APPROVED`, `CONTENT_RETURNED`, `CONTENT_NO_REVIEWER`. The notification type check was
  replaced by a pattern check in 025, so adding types needs no constraint change.

## R10. Alternatives rejected

- Keeping five roles and giving the third person the Manager role: rejected by the owner (Q1 = B).
- A task for every content piece: rejected, because 025 only allows Manager assignees and the Contributor holds no tasks.
- Storing video in the database: rejected; the file store keeps bytes on disk.
