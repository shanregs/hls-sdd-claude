# Research: Teacher Master Data

No `[NEEDS CLARIFICATION]` markers remain in the Technical Context — spec.md's Assumptions section already resolved the open scope questions. This phase resolves the technical *how*, most of which turned out to already exist in Identity's and Audit's shipped code, built ahead of time for exactly this module.

## 1. Manager-scoped viewing (FR-007): no new dependency on `organization.api` needed

**Finding**: `identity.api.ManagerScopeQueries.isAllowedForTeacher(callerId, teacherId)` already exists, already calls `organization.api.AccountabilityQueries.currentManagerForTeacher(...)` internally (with the 2-second timeout budget and fail-closed semantics FR-019/FR-020 established), and is already tested (`IdentityIntegrationTest.managerScopeGuard_...`). It was built during Identity's own implementation specifically so "any future module that owns Teacher-scoped records" would have this available without reinventing it.

**Decision**: `TeacherController` depends on `identity.api.ManagerScopeQueries` for the Manager-viewing check. `teacher` never depends on `organization.api` at all.

**Rationale**: Building a second scoping path directly against `organization.api` would duplicate logic that already exists, is already tested, and already handles the fail-closed/timeout edge cases — for no benefit. This is exactly the kind of forward-building Identity did (`ManagerScopeQueries`/`TeacherScopeQueries` existed as public interfaces before any module needed them) paying off.

**Alternatives considered**:
- *Depend on `organization.api.AccountabilityQueries` directly, duplicating `ManagerScopeGuard`'s logic in `teacher`*: rejected — no reason to re-implement a timeout-bounded, fail-closed check that's already correct and already covers the exact "is this caller the current Manager for this teacher" question FR-007 asks.

## 2. Teacher self-scoping (FR-008/FR-009): same story

**Finding**: `identity.api.TeacherScopeQueries.isAllowed(callerId, targetTeacherId)` already exists too — it compares the caller's own `User.linkedTeacherId` to the target, entirely within Identity, no cross-module call needed on its side.

**Decision**: `TeacherController` depends on `identity.api.TeacherScopeQueries` for the Teacher self-viewing check (FR-008, and its denial is what satisfies FR-009's "must not allow viewing another teacher's profile").

**Rationale**: Same as decision 1 — this is already built, tested, and denies-by-default when a caller has no linked teacher id yet (`TeacherScopeGuardTest.isAllowed_whenCallerHasNoLinkedTeacherIdYet_returnsFalse`), which is exactly the safe default FR-009 needs.

## 3. How a Teacher discovers their own `teacherId`

**Finding**: `TeacherScopeQueries.isAllowed(callerId, targetTeacherId)` requires the caller to already know `targetTeacherId` to ask the question. Nothing in the shipped codebase currently tells a logged-in Teacher what their own `teacherId` is — `User.linkedTeacherId` exists on the `identity_user` table but is only ever set via direct repository calls in test setup today (`grep` across `backend/src/main` found zero production code paths that call `User.setLinkedTeacherId`).

**Decision**: Two small, additive changes:
1. `identity.internal.TokenService.buildAccessToken(...)` gains a `UUID linkedTeacherId` parameter and adds a `"teacherId"` claim to the access token when it's non-null — the same claim-embedding pattern already used for `"roles"` (so `ManagerScopeGuard`/`TeacherScopeGuard` never need a DB round trip just to read a role or a teacher link). `issueForNewSession`/`refresh`'s four call sites in `AuthenticationService` already have the `User` object in scope, so they pass `user.getLinkedTeacherId()` through.
2. `TeacherController` adds `GET /api/v1/teachers/me`, reading `jwt.getClaim("teacherId")` directly (same generic-claim-reading pattern `OrganizationController`/`AuditController` already use for `"roles"`) and returning `403`/`404` if the caller has no linked teacher id.

**Rationale**: This is the one real gap in the otherwise-complete scoping infrastructure — everything else already existed. The fix is additive (new claim, new endpoint) and backward-compatible: no existing token consumer breaks, no existing test's expectations change, since the claim is simply absent (not malformed) for callers with no linked teacher id.

**Alternatives considered**:
- *Client-side JWT decoding in the frontend to extract `linkedTeacherId` and call `GET /teachers/{id}` directly*: rejected — every other piece of claim-reading in this codebase happens server-side in a controller (`jwt.getClaimAsStringList("roles")` etc.); introducing client-side JWT parsing would be a new pattern for no benefit over a dedicated `/me` endpoint, and keeps the frontend simpler (no token-parsing library needed).
- *A new `identity.api` method exposing `linkedTeacherId` for other modules to query by `userId`*: rejected — would require `teacher` to depend on `identity.api` for this too (already does, for scoping), but adds a second cross-module round trip per request where a JWT claim (already present on every authenticated request, zero extra cost) does the same job.

## 4. Enforcing "never deleted" (FR-004)

**Decision**: `TeacherProfileRepository` extends bare `Repository<TeacherProfile, UUID>` and declares only `save(...)` and read finder methods — no `delete`/`deleteById`, mirroring `audit.internal.AuditEntryRepository`'s and `identity.internal.AuthAuditEntryRepository`'s established pattern.

**Rationale**: Same reasoning Audit's research.md §4 already established for this codebase: a narrow repository interface makes the guarantee a compile-time fact (the method doesn't exist to call) rather than a runtime check that could have a bug. Reusing a now twice-proven pattern is lower-risk than inventing a new one.

## 5. History via the real Audit module — `teacher` becomes Audit's first genuine caller

**Decision**: `TeacherService.create/updateProfile/changeStatus` each call `audit.api.AuditWriter.record(...)` — with `sourceModule="teacher"`, `entityType="TeacherProfile"`, `entityId=<teacher id>` — inside the same method, riding the same transaction (default Spring propagation), exactly the pattern Audit's own research.md §3 designed for and its `AuditIntegrationTest` proved (via a simulated caller) but never had a real one to exercise until now.

**Rationale**: Audit's spec.md (FR-002) and Constitution Principle V both say this is precisely what `audit` exists for; building a second, bespoke `TeacherProfileHistory` table would directly duplicate a capability that already exists, is already tested, and gives Directors/Admins a single place (`GET /api/v1/audit/{entityType}/{entityId}/history`) to look up history for *any* record type, Teacher profiles included, with no Teacher-specific viewing code needed.

**Alternatives considered**:
- *A dedicated `teacher_profile_history` table, matching spec.md's literal "Profile Change Record" key entity as its own persisted table*: rejected — spec.md's Key Entities section describes a concept ("what changed, before/after, when"), not a mandated implementation; Constitution Principle V explicitly says no module should keep its own competing history table once `audit` exists for exactly this. Using `audit` instead satisfies the same requirement (FR-005) with less code and one less table to maintain.

## 6. `TeacherModuleTest` bootstrap mode: `ALL_DEPENDENCIES`, not `DIRECT_DEPENDENCIES`

**Decision**: `TeacherModuleTest` uses `@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)`, not the default `STANDALONE` and — corrected during implementation — not `DIRECT_DEPENDENCIES` either.

**Rationale**: `teacher` has real Spring bean dependencies on `identity.api` (`ManagerScopeQueries`, `TeacherScopeQueries`) and `audit.api` (`AuditWriter`) from day one — unlike Audit, which had zero dependencies when it was built and could use `STANDALONE`. This section originally planned `DIRECT_DEPENDENCIES` (reasoning by analogy with `IdentityModuleTest`'s own switch, specs/003 tasks.md T031), but implementation found that insufficient: `identity`'s own `ManagerScopeGuard` bean has a real dependency on `organization.api.AccountabilityQueries` — a *transitive*, not direct, dependency of `teacher`. `DIRECT_DEPENDENCIES` only bootstraps a module's immediate dependencies, so the context failed to start with a missing `AccountabilityQueries` bean; switching to `ALL_DEPENDENCIES` (which bootstraps the full transitive graph) fixed it.

## 7. Frontend: web pages standing in for "mobile," again

**Finding**: The feature description says "a teacher can view... their own profile from the mobile app," but no React Native mobile app exists anywhere in this codebase yet (Constitution names it as planned, Android-first, but nothing has been scaffolded). Identity already established the precedent of building teacher-facing flows as web pages in the interim (`TeacherOtpLoginPage`, alongside the staff-facing `LoginPage`).

**Decision**: Add `MyProfilePage` (Teacher-facing, read-only, calls `GET /teachers/me`) as a web page, following that precedent, alongside `TeacherProfilesPage` (Admin/Director/Manager-facing: create/edit/status-change for Admin, view for Director/Manager — the backend enforces which actions each role can actually perform, same pattern `AssignmentsPage`/`AuditHistoryPage` already use).

**Rationale**: Consistent with how this project has already handled every other "mobile" requirement so far; no reason to block this module on the mobile app existing when the backend contract is identical either way.

**Alternatives considered**:
- *Defer all frontend work until a real mobile app exists*: rejected — same reasoning Organization's research.md §8 already rejected this for AssignmentsPage: no remaining reason to defer once a working pattern (web-page stand-in) is established.

## 8. Bank details are out of scope (scope correction, 2026-09-22)

**Finding**: The original feature description included bank details for payout as a profile field. spec.md's Assumptions section now explicitly excludes them — no bank-related field, requirement, or acceptance scenario is in scope for this module.

**Decision**: `TeacherProfile` has no bank-detail fields at all; no bank-detail-specific data-protection question (encryption at rest, key management, etc.) arises in this plan.

**Rationale**: Removing the fields removes the question — there is nothing bank-related for this module's data-protection posture to address. If a payout-details feature is added later (likely alongside Payroll), that feature's own plan will need to make this call for real at that point, not this one.

## 9. Forward note (not in this plan's scope): Organization's FR-012 could now be wired for real

**Finding**: Organization's own research.md §3 (specs/003) flagged that FR-012's "unknown identifier" branch was deliberately left unreachable because "neither Teacher nor School has a real master table to check existence against," with a forward note to revisit once Teacher Master Data exists.

**Decision**: Not addressed in this plan. `teacher.api.TeacherQueries.exists(teacherId)` would be the natural hook for Organization to call, but wiring that up means changing Organization's already-shipped `AccountabilityService`/contract, which spec.md for *this* feature never asked for.

**Rationale**: Keeps this plan scoped to what specs/005's own spec.md actually requires. Flagged here, exactly the way Organization flagged its own forward note, so the next person planning a touch to Organization (or a dedicated small feature for it) has the pointer.
