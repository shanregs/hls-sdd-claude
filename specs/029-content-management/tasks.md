# Tasks: Content Management

Format: `[ID] [P?] [Story] description`. Stories: US1 author submits, US2 review, US3 management lists and dashboard, US4 own content,
US5 Content Contributor role. [P] = can run in parallel (different files).

## Phase 0: Constitution and documents

- [ ] T001 Constitution 3.0.0 (MAJOR, six roles) decided by the owner 2026-10-26: remove the word PROPOSED from the Sync Impact Report and the version line in `.specify/memory/constitution.md` when this spec is implemented; bring the Default role access matrix and Principles II to IV into line (text drafted).
- [x] T002 [P] Doc notes: roadmap row 029 and role count in `docs/spec-roadmap.md`; "five roles" wording in specs 002 and 004 spec.md (amendment note, not a rewrite); running docs.

## Phase 1: Sixth role in identity (US5)

- [x] T003 [US5] `Role.CONTENT_CONTRIBUTOR`; fix every exhaustive `switch (role)` (`AccessModelService.masterDataScopeFor`, `dashboardScopeFor`, scope at ~line 188) and any other compile error.
- [x] T004 [US5] `PermissionModule.CONTENT(VIEW, CREATE, EDIT, APPROVE)`; `PermissionEligibility`: add `CONTENT` to business modules (System none), Approve only Admin and Director, Teacher none, Contributor eligible only for CONTENT (View, Create, Edit), NOTIFICATIONS (View), dashboard and account modules; all other modules ineligible for the Contributor.
- [x] T005 [US5] `PermissionMatrixService` seed: CONTENT grants per data-model.md; Contributor rows for dashboard, notifications, account; verify existing seeded rows for the five roles are unchanged.
- [x] T006 [US5] `NavigationCatalog`: "Content" (Admin, Director) and "My Content" (Manager, Contributor) under OPERATIONS; add the Contributor to the role sets of Notifications, Profile and Logout items; Contributor dashboard entry.
- [x] T007 [US5] `UserAdminService`/role endpoints: accept and list the sixth role; creating a user with only this role needs no Manager/Teacher record; last-Admin rule untouched; `MobileRoleEligibility` unchanged (refuses).
- [x] T008 [US5] `AuditVisibility`: `CONTENT`, `CONTENT_VERSION`, `CONTENT_REVIEW` require View on `CONTENT`.
- [x] T009 [P] [US5] Tests: Role enum value fits 20 characters; per-role access model for all six roles; Contributor refused on a sample of endpoints of every other module (table-driven); mobile sign-in refused; union of roles (Manager + Contributor); matrix API returns six columns; existing five-role expectations unchanged.

## Phase 2: Migration and file store (US1)

- [x] T010 Migration `V95__create_content.sql`: content, content_version (update trigger except removal fields), content_review, content_history (append-only trigger), code sequence, indexes.
- [x] T011 [P] `files.api`: `FileLimits`, streamed `store` overload, `openStream`; `FileTypePolicy`: mp4, mp3, m4a, wav with first-byte checks; per-type limit; default limits equal the present 10 MB behaviour.
- [x] T012 `FileStoreImpl` streaming write to disk (no whole-file array) and streamed read; error messages name the type and limit.
- [x] T013 Server settings (decided: large-upload ceiling and plain downloads, no range playback, are accepted for v1): multipart max file and request 520 MB, disk threshold; early-refusal filter (over 11 MB by Content-Length) on every upload path outside `/api/v1/content/**`; `docker/nginx.locations.conf` body limit for `/api/v1/content/` only; the deployment note is already in `docs/aws-deployment-and-ddos-protection.md` section 8.
- [x] T014 [P] Tests: each new type accepted with correct bytes and refused with wrong bytes; limits per type (just under, just over); a 15 MB expense bill and MoU document still refused/accepted exactly as before; a 500 MB upload to a non-content endpoint refused early; legacy `byte[]` callers unchanged.

## Phase 3: Lifecycle (US1, US2, US4)

- [x] T015 [US1] Entities and repositories: Content, ContentVersion, ContentReview, ContentHistory, status enum.
- [x] T016 [US1] `ContentService`: create (self or for another author; author must hold Create), edit, submit version (file required, numbered, status), history and Change History on every step.
- [x] T017 [US2] `ContentService`: approve and return (Approve; not author or submitter; comment required for return); resubmission after Approved keeps `last_approved_version`.
- [x] T018 [US2] `ReviewerResolver` and `ContentNoReviewer` event, once per version.
- [x] T019 [US1] File removal by Admin or Director with reason; downloads streamed as attachments gated by visibility.
- [x] T020 [US1] 025 amendment: `tasks.api.TaskProgress` (complete with note, verify, send back by source and source key); `ContentTaskBridge` creates the task only for active Manager authors with a due date and drives its status; regression tests in `tasks` for the new interface.
- [x] T021 `ContentController` per contracts/content-api.md with a grant check on every endpoint.
- [x] T022 [P] Tests: lifecycle API; no file refused; version immutability; self-review refused for author and for submitter; Director-authored content not approvable by the Director; no-reviewer notice once; task created/not created cases; stale-version 409; per-role authorization for every endpoint across six roles; visibility (other users get 404).

## Phase 4: Lists, dashboard, notifications (US3)

- [x] T023 [US3] `ContentQueryService`: list filters, summary, scope predicate (authored or submitted), overdue only where a due date exists.
- [x] T024 [US3] `ContentTiles` (TileSource) and approvals-inbox source for submitted content; permission-gated like the other tiles.
- [x] T025 `ContentEventListener` and `MessageFactory` texts for the five notification types; recipients per contracts/module-interfaces.md.
- [x] T026 [P] Tests: filters, scope, tile counts, inbox item, notification recipients and content.
- [x] T027 ArchUnit: only `notification` and `reporting` read `content.api`; `content` depends on `files.api`, `tasks.api`, `organization.api` only through published APIs.

## Phase 5: Frontend (US1 to US5)

- [x] T028 `frontend/src/features/content`: `contentApi`, My Content / Content list with filters, detail page (versions, reviews, history, comments), create dialog, submit-version dialog with type/limit hints and progress for large files, review actions, file removal.
- [x] T029 [US5] `DashboardRouter` + `ContributorDashboard` (own content counts); role types; user dialogs and `userManagementApi` list the sixth role; `RolePermissionsGrid` shows six columns; a11y tests include the Contributor.
- [x] T030 [P] Frontend tests: menu per role (hidden, not disabled), Contributor sees only its items, refused type/size messages, review buttons hidden for author, five-role snapshots unchanged.

## Phase 6: Demo data, docs, walkthrough

- [x] T031 `ContentDevSeeder`: a Contributor user, content in every status, sample small files; `DevDataSeeder` adds the Contributor.
- [x] T032 [P] Postman requests for the content API; update `docs/spec-roadmap.md` row 029 status and the constitution default matrix (already drafted).
- [ ] T033 Run backend and frontend suites; walk through `quickstart.md`; record results in `quickstart-results.md`.

## Dependencies

T001 gates merge, not coding. T003 to T008 before T009 and everything role-dependent. T010 before T015. T011 to T013 before T016 file submission. T020 before T016 task linking is complete. T021 before T028.
MVP: T003 to T008, T010 to T012, T015 to T017, T021, T028 (Contributor submits, Director reviews).
