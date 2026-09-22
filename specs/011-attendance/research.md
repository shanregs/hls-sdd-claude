# Research: Daily Attendance Capture & Monthly Rollup

Three real design gaps surfaced during the initial planning pass that spec.md's Assumptions section named but didn't fully resolve at the technical level: (1) there is no existing "which School is this Teacher currently assigned to" query anywhere in the codebase yet, (2) there is no `payroll` module yet to actually trigger a lock, and (3) "training days total" has no training-calendar data source to compare "attended" against. All three are resolved below the same way this codebase has already resolved comparable forward-reference gaps in specs/002/003/006/009: build the real capability Attendance owns now, accept a narrower, explicitly-documented stand-in for the piece owned by a module that doesn't exist yet, and correct it in a later spec once that module ships. Two more gaps surfaced in later passes, after User Story 5 (the grid) and a `/speckit-clarify` session were added: (§8) no query existed to enumerate which Teachers a caller can see, and (§10) no shared non-working-day calendar existed anywhere in the system. §8 follows the same borrow-a-narrow-stand-in pattern; §10 doesn't — the calendar is genuinely new data, so `attendance` owns it outright rather than borrowing or narrowing.

## 1. `schoolId` on an Attendance Mark is an opaque, unvalidated reference — not derived from a "current assignment" lookup

**Finding**: `school.api` currently exposes `ZoneQueries` (Zone ↔ School-Zone assignment) and `PlaceQueries`, but no School master-data table or `exists(schoolId)` query — School Master Data (profile, branch, assigned manager reference) is still pending per Constitution Principle V's `school` bullet. `organization.api.AccountabilityQueries` derives a Teacher's accountable *Manager*, but there is no Teacher→School assignment anywhere: the Teacher–School–Manager Contract that would define "this Teacher's current school assignment" is `schoolbilling`'s job (Constitution Principle II/V), and `schoolbilling` hasn't been built yet either.

**Decision**: `AttendanceMark.schoolId` is stored as a plain `UUID` supplied by the caller at mark time (Teacher or Manager), validated only for well-formedness — never checked against a School master table or a "this Teacher is currently assigned there" rule, since neither exists yet to check against.

**Rationale**: This is the same narrowing `organization.api.dto.AccountabilityAnswer`'s Javadoc already documents for `UNKNOWN_IDENTIFIER` ("Teacher/School Master Data don't exist as validated master tables... every syntactically valid identifier resolves to UNASSIGNED at worst") — building Attendance ahead of School Master Data and `schoolbilling` means it accepts the same limitation, documented rather than silently assumed. Once `schoolbilling`'s Contract exists, a follow-up spec (mirroring specs/006/007's zone-scoping correction) can add a real "is this School currently valid for this Teacher" check without changing `AttendanceMark`'s shape.

**Alternatives considered**:
- *Block this feature until `schoolbilling` ships*: rejected — Constitution Principle II already establishes the precedent of shipping Attendance-adjacent capability against an interim stand-in rather than blocking on a module several steps down the build order.
- *Have Attendance own a minimal Teacher↔School assignment table itself*: rejected — that data rightfully belongs to `schoolbilling`/`school` per Principle V; Attendance owning it would create exactly the kind of ownership ambiguity Principle V exists to prevent.

## 2. Locking a teacher-month is Attendance's own explicit command, called by a Director — not an automatic `payroll` trigger

**Finding**: Constitution Principle VI describes payroll runs as on-demand, Admin/Director-triggered batch jobs, but the `payroll` module itself is later in the build order than `attendance` (Principle V's package list) — it doesn't exist yet for Attendance to react to.

**Decision**: `attendance.api.AttendanceLockCommands.lockMonth(teacherId, period, actingUserId)` is a directly-callable command, authorized for **Director-only**, that Attendance itself exposes as "payroll has run for this teacher-month" — invoked manually by a Director today, and available for a future `payroll` module to call the same way once it exists (it would simply become another caller of the same public interface, no shape change needed).

**Rationale**: Matches this codebase's established pattern of building the owning module's real capability now and leaving an explicit, narrow seam for the not-yet-built caller to plug into later (the same shape `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` gave `organization` before `organization` existed to answer them for real).

**Amendment (2026-09-22, `/speckit-analyze` finding C1/I1)**: originally scoped to Director **or** Admin, matching Principle VI's "Admin/Director-triggered" framing of payroll runs in general. Narrowed to Director-only for consistency with FR-014's reopen authority — both ends of the lock/reopen cycle are payroll-approval-adjacent under Principle II, and reopening was already correctly Director-only; there was no articulated reason for locking to be looser. A Manager or Admin may still *request* a lock, but only the Director triggers it (spec.md's "Lock and reopen authority" Assumption).

**Alternatives considered**:
- *Add a scheduled/automatic lock (e.g., first of next month)*: rejected — Constitution Principle VI is explicit that there is no fixed monthly schedule, since late corrections can arrive; locking must stay tied to an actual payroll-run event (today, its manual stand-in), not a calendar trigger.
- *Keep Director-or-Admin (the original scoping)*: rejected on reconsideration — Principle VI's "Admin/Director-triggered" language describes payroll runs generically, but Principle II is more specific and controlling for *this particular* authority (payroll approval/correction), and that more specific principle already settled reopen as Director-only.

## 3. "Training days total vs. attended" is derived entirely from Attendance's own Training-day marks — not from a training calendar

**Finding**: spec.md's Key Entities describe "training days total on the calendar" vs. "attended," but Constitution Principle IV's training calendar lives in the `training` module, which is later in the build order than `attendance` — there is no calendar of *expected* training days for Attendance to compare against yet.

**Decision**: Within a teacher-month, `trainingDaysTotal` = the count of days marked with a Training-day-category status code that month, and `trainingDaysAttended` = the sum of those same marks' fractional values (so a half-day training mark counts as 0.5 attended out of that day's 1.0). "Total" and "attended" diverge only through partial (fractional) attendance on a day already marked as training — not against an external schedule, since none exists yet to diverge from.

**Rationale**: This keeps both rollup figures honestly derived only from data Attendance actually owns (its own marks), rather than fabricating a calendar Attendance has no authority over. It's a real, useful number today (it already tells a Director "how many full training-day equivalents did this Teacher log this month"), and once the `training` module's calendar exists, a follow-up spec can widen `trainingDaysTotal` to mean "scheduled" instead of "marked," the same kind of widening specs/006/007 already did for Zone ownership.

**Alternatives considered**:
- *Leave `trainingDaysTotal` unimplemented / hardcoded to equal `trainingDaysAttended` until `training` ships*: rejected — makes the field meaningless (always equal to itself) rather than honestly narrower; the fractional-attendance interpretation above gives it real, if narrower, meaning today.

## 4. History via the `audit` module, not a second `AttendanceMarkHistory` table

**Finding**: FR-006 requires every mark and edit to be attributable and every prior value retrievable, but — unlike specs/009's salary history — nothing in spec.md requires an indexed "what was marked as of date X" query; a mark simply has one current value at a time, replaced (not appended) on edit, until the teacher-month locks.

**Decision**: `AttendanceMark` is a single mutable row per `(teacherId, markDate)`, updated in place on edit; every create/edit calls `audit.api.AuditWriter.record(...)` with the full before/after description, exactly like specs/005's `TeacherProfile` field edits — Audit is where "every prior value" is retrievable, not a bespoke second table.

**Rationale**: Mirrors `teacher`'s own precedent directly (`TeacherService.updateProfile` records before/after via Audit rather than a parallel history table) rather than specs/009's salary-history exception, which only exists there because salary specifically needed a date-indexed "as of" query attendance marks don't need. Simpler schema, one fewer table to keep in sync, and it's the majority pattern in this codebase already.

**Alternatives considered**:
- *A dedicated `AttendanceMarkHistory` table mirroring `TeacherSalaryHistory`*: rejected — no requirement here needs a structured "as of" query over past mark values; Audit's opaque before/after already satisfies FR-006 exactly as it already does for `TeacherProfile`.

## 5. Monthly rollup is computed at read time — never persisted while a teacher-month is unlocked

**Finding**: FR-008 requires the rollup to reflect any add/change/remove immediately while the teacher-month remains open; a persisted, incrementally-maintained rollup row risks drifting out of sync with the marks it summarizes.

**Decision**: `AttendanceRollupQueries.rollupForMonth(teacherId, period)` computes `MonthlyAttendanceRollupView` fresh from `AttendanceMark` rows every call — there is no `MonthlyAttendanceRollup` table. Once a teacher-month locks, its underlying marks stop changing (FR-012), so the computed rollup is naturally stable without needing a frozen snapshot; a reopen makes marks editable again, and the next computed rollup simply reflects whatever correction was made, which is exactly FR-013's "the rollup is marked as having been corrected post-payroll" (surfaced via the lock's own reopen history, not a second rollup-versioning mechanism).

**Rationale**: The same "derive, don't duplicate" reasoning specs/009 research.md §2 already applied to `TeacherProfileView.hlsOfferedSalary` — a live query costs nothing at this system's scale (Additional Constraints: under 100 users, single EC2) and eliminates an entire class of "rollup didn't get recomputed" bugs.

**Alternatives considered**:
- *Persist and incrementally update a rollup row per teacher-month*: rejected — adds a second source of truth for figures fully derivable from `AttendanceMark`, for no performance benefit at this scale.

## 6. Weighted attendance total formula

**Decision**: `weightedAttendanceTotal = Σ (mark.fractionalValue × statusCode.weight)` over every mark in the month whose status code's category is not `NON_WORKING`. Default seeded weights: `PRESENT` (category `WORKED`) = 1.00, `TRAINING` (category `TRAINING`) = 1.00, `LEAVE` (category `LEAVE`) = 0.00, `NON_WORKING` = 0.00 (and excluded from the denominator regardless). An Admin can adjust any code's weight going forward (new marks only — not retroactively reweighting already-computed history, since the rollup is always computed live against whatever weight is current).

**Rationale**: Directly implements spec.md's Assumptions section ("each Attendance Status Code carries a configurable weight... final weighted attendance total is the sum of (fractional value × code weight)"), with concrete default values chosen so Training-day counts fully toward pay-relevant attendance (matching Constitution Principle IV's "training is part of the same operating model," not a lesser category) while Leave and non-working days do not.

## 7. New bounded-context module `attendance`, reusing `identity.api` scoping — no new dependency on `organization.api` or `school.api`

**Decision**: `com.hls.attendance` (api/internal) depends only on `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` (for FR-002/FR-015 scoping — both already compose `organization.api.AccountabilityQueries` internally, per `ManagerScopeGuard`), `audit.api.AuditWriter` (FR-006), and `teacher.api.TeacherQueries.exists(...)` (validating `teacherId` is a real Teacher Profile before accepting a mark). No direct dependency on `organization.api` or `school.api` is introduced, following `teacher`'s and specs/009's own precedent of depending on `identity.api`'s already-composed scoping rather than reaching past it.

**New ArchUnit coverage**: `ArchitectureTest` gains `attendanceInternalsAreOnlyAccessedFromWithinAttendance` (same pattern as every prior module) and the existing "no dependency on other bounded contexts" list gains `com.hls.attendance..`. `AttendanceModuleTest` (`@ApplicationModuleTest(BootstrapMode.ALL_DEPENDENCIES)`, per `TeacherModuleTest`'s discovered-during-implementation note) verifies the module boots in isolation.

**Migration**: `V9__create_attendance_tables.sql` (Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6, Teacher Salary History=V7, Zone-Manager Assignment=V8).

> **Amendment (2026-09-22, User Story 5 added)**: §8 below adds a real, direct dependency on `organization.api.AccountabilityQueries` for the grid's Manager-portfolio listing — narrower than "no dependency at all" stated above, which held only through User Stories 1-4.

## 8. The attendance grid (User Story 5) sources its Teacher rows from `organization.api`'s existing portfolio query, plus one small, additive `teacher.api` extension — no new Attendance-owned listing data

**Finding**: FR-019/020/021 need to enumerate *which Teachers* appear as rows — something no prior story needed, since US1-4 all operate on one already-known `teacherId` at a time. Two different "give me a list" queries are needed: a Manager's (or a Director's Manager-filtered) portfolio, and a Director's unfiltered "every Teacher." `organization.api.AccountabilityQueries.portfolioForManager(managerId)` (specs/003) already answers the first exactly — it returns every `TEACHER`-type `PortfolioItem` currently accountable to a Manager. Nothing anywhere answers the second: `teacher.api.TeacherQueries` only exposes `findById`/`exists` (specs/005), no `findAll`.

**Decision**:
- Attendance depends directly on `organization.api.AccountabilityQueries.portfolioForManager(...)` for both "a Manager's own grid" and "a Director's grid filtered to one Manager" (FR-021) — the same read-only, public-interface dependency `identity.internal.ManagerScopeGuard` already has, just consumed one layer up instead of only through `identity`'s pre-composed yes/no check. This is a direct dependency Attendance didn't have before (research.md §7's original "no dependency on organization.api" is narrowed by this addition, not overturned — Attendance still never depends on `organization.internal` or `school.api`).
- `teacher.api.TeacherQueries` gains one new, additive method: `List<TeacherProfileView> findAll()`, backed by `TeacherProfileRepository.findAll()` (already exists at the repository layer, just not exposed publicly yet). Attendance calls this for a Director's unfiltered grid (FR-020's "a Director sees any Teacher").

**Rationale**: Both are read-only, public-API dependencies on data that already exists and is already owned by the correct module (Principle V) — no new cross-module write path, no reach into another module's `internal` package, and no duplication of Manager-accountability or Teacher-identity data inside `attendance`. Adding `findAll()` to `teacher.api` is the same kind of small, additive, non-breaking extension specs/009 already made to `teacher.api` (new methods alongside existing ones, nothing removed or changed in shape).

**Alternatives considered**:
- *Attendance maintains its own cached "all teacher IDs" list, populated by listening to Teacher-created events*: rejected — pure duplication of data `teacher` already owns and can serve directly; adds a sync-drift risk for no benefit at this system's scale (<100 teachers).
- *Route the Director's "all teachers" listing through `identity.api` instead of adding to `teacher.api` directly*: rejected — `identity.api.ManagerScopeQueries`/`TeacherScopeQueries` are yes/no scope checks by design (research.md §7's original reasoning), not listing queries; forcing a listing shape into that interface would blur what it's for. A plain `teacher.api.TeacherQueries.findAll()` is the more honest home for "give me every Teacher."

**Module-graph impact**: `attendance` now depends on `organization.api` (new) in addition to `identity.api`, `audit.api`, and `teacher.api`. `AttendanceModuleTest`'s `ALL_DEPENDENCIES` bootstrap mode (T034) already accommodates this without change — it was chosen up front specifically to avoid the `DIRECT_DEPENDENCIES`-insufficiency surprise specs/005 hit.

## 9. The grid becomes editable (FR-024/FR-025) by reusing the existing write path, plus one new unscoped writer role — no second write path, no new persistence

**Finding**: A direct user request ("edit by manager, admin... reflect in the backend DB... single page") asked whether the grid (read-only through User Story 5 as originally planned) could also be edited in place. Two sub-questions: (a) does editing from the grid need new backend capability, and (b) should Admin — who currently has no attendance-marking capability at all, only Manager (FR-002) and Teacher-self (FR-001) do — be able to correct any Teacher's attendance the way the request describes.

**Decision**:
- (a) No new write path. `AttendanceMarkCommands.markAttendance(teacherId, request, actingUserId, actingRole)` (T036) already is the single write path every mark goes through, self or on-behalf. Editing a grid cell is exactly the same call with `actingRole = MANAGER` or `ADMIN`, keyed by that cell's `(teacherId, markDate)` — the grid introduces a request shape (`GridCellEditRequest`, data-model.md) but not a second service method or table.
- (b) A new `MarkedByRole.ADMIN` is added alongside `TEACHER`/`MANAGER` (data-model.md). Unlike Manager (scoped to `identity.api.ManagerScopeQueries.isAllowedForTeacher`, FR-002), Admin's on-behalf marking is **unscoped** — any Teacher, no accountability check — mirroring how Admin already has unscoped write authority over Teacher master data itself (specs/005 FR-010). Director is deliberately **not** given a new marking capability here (spec.md Assumptions) — Director's role stays what FR-014/FR-015 already established (reopen authority, unscoped viewing), since the request specifically named Manager and Admin, not Director, and Constitution Principle II is already careful about who gets payroll-adjacent write authority.
- The grid's `editable` flag per cell (data-model.md's `GridCell.editable`) is computed **server-side**, inside `AttendanceGridQueries`'s existing per-Teacher/per-day loop (T075) — `false` whenever that cell's teacher-month is `LOCKED`, or the caller has no write permission for that Teacher (a Director viewing without Manager-filter sees everyone but can edit no one, since Director isn't a writer); `true` otherwise. The actual edit call still re-checks lock state and permission independently (defense in depth) — `editable` is a UI hint, never the authorization boundary itself.

**Rationale**: This is the smallest change that satisfies the request — no new tables, no new module dependency, no new audit mechanism (the existing `AuditWriter.record(...)` call in `markAttendance` already covers a grid-originated edit identically to a form-originated one). Giving Admin unscoped on-behalf authority (rather than, say, requiring Admin to also have an accountability relationship) matches Admin's existing unscoped authority over Teacher master data elsewhere in this codebase — a new, narrower rule here would be an inconsistent one-off.

**Alternatives considered**:
- *A dedicated bulk "grid save" endpoint accepting many cell edits in one request*: rejected for this pass — the request didn't ask for bulk/multi-cell save, and one-call-per-edited-cell is simplest to reason about (each edit is independently attributed/audited/lock-checked, exactly like editing one mark ever was). A batch endpoint can be added later without changing this design if bulk editing turns out to matter.
- *Let the frontend decide editability from `lockStatus` alone, without a server-computed `editable` field*: rejected — the frontend would need to re-derive Manager-portfolio membership itself (duplicating `organization.api` logic that already lives server-side for §8's row-listing), and get it right for both roles; a server-computed flag is one less place that logic can drift out of sync with the real authorization check.

## 10. The Non-Working Calendar (FR-022/FR-023) is a small, separate, Admin-owned table — every rollup/grid computation now walks the full month, not just its marks

**Finding**: A `/speckit-clarify` session (2026-09-22) resolved a real, previously-unmodeled gap: a "non-working day" (declared holiday) was going to require an individual `AttendanceMark` per Teacher per holiday — tedious and easy to miss for one Teacher out of many. The resolved answer is a shared, org-wide calendar that applies automatically, with an individual mark still able to override it for one Teacher on one day (e.g., a school stays open on a declared holiday).

**Decision**:
- New table/entity `AttendanceNonWorkingDate`: `id`, `date` (`LocalDate`, `UNIQUE`), `label`, `active` (boolean, default `true` — mirrors `AttendanceStatusCode`'s never-delete-just-deactivate pattern, research.md's own established style), `createdAt`, `createdBy`. Owned entirely inside `attendance` — no cross-module dependency, unlike §1/§8's gaps, since this data belongs to Attendance itself (nothing else already owns an org-wide holiday calendar).
- `AttendanceNonWorkingCalendarCommands.addNonWorkingDate(date, label, actingUserId)` — **Admin-only**, per FR-022's literal wording (unlike FR-005's status-code authority, which the same clarify session explicitly widened to Admin-or-Director — these are two different authorities, not accidentally inconsistent). `AttendanceNonWorkingCalendarQueries.datesForMonth(period)` — available to any authenticated caller (needed to render the grid/rollup UI's calendar indicators).
- **Rollup/grid computation changes** (data-model.md): every computation now iterates every calendar day in `period` (not only days with a mark) and resolves each day's *effective category* as: the day's `AttendanceMark`'s category if one exists (explicit always wins, per the clarify session's answer) → else `NON_WORKING` if the date is on the active calendar → else `UNMARKED`. `overallWorkingDays`/`daysWorked`/`daysLeave`/`trainingDaysTotal`/`trainingDaysAttended`/`weightedAttendanceTotal`/`unmarkedDays` are all redefined in terms of this per-day effective category rather than only scanning existing mark rows (data-model.md's formulas section, updated).
- **Grid representation**: a day with no mark but on the calendar shows `GridCell(statusCode=null, category=NON_WORKING, fractionalValue=null, editable=...)` — distinguishable from a truly unmarked day (`category=null` too) without adding a new field to `GridCell`, since `category` was already nullable and independent of `statusCode`.

**Rationale**: A separate table (not folding "non-working" into some other existing structure) is the smallest correct model — it's genuinely new information (a calendar) that no other module owns, so it belongs to `attendance` outright, unlike §1/§8's cases where the missing data rightfully belongs elsewhere. Reusing `category`'s existing nullability for the grid, rather than adding an `isCalendarNonWorking` boolean, keeps `GridCell`'s shape from growing for a distinction its existing fields can already express.

**Alternatives considered**:
- *Model non-working dates as a special, always-present `AttendanceMark` row per Teacher, auto-inserted by a batch job when the calendar is configured*: rejected — reintroduces exactly the "mark per Teacher per holiday" tedium the clarify session resolved away from, just automated instead of manual; also means adding a Teacher after a holiday was configured requires a backfill job, whereas the calendar-as-a-separate-table approach applies to every Teacher (present and future) with no backfill needed.
- *Store the calendar as a date range/recurrence rule (e.g., "every Sunday") instead of individual dates*: rejected — Indian school holidays (the actual domain here) are irregular declared dates, not a recurring rule; a flat table of individual dates is simpler and matches how an Admin would actually enter them (one holiday at a time, as the school calendar is announced).
