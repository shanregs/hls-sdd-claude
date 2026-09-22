# Research: Teacher Salary History

No `[NEEDS CLARIFICATION]` markers remain — the user's own message resolved both open design questions before spec.md was written ("by default take latest value, but we can also compute on any past date").

## 1. A dedicated, structured history table — not parsed out of the generic Audit trail

**Finding**: specs/005-teacher already writes an Audit entry for every salary change (FR-005), but `AuditRecordRequest.beforeValue`/`afterValue` are opaque, whole-profile description strings (specs/005 research.md, by design — `audit` never parses entity-specific structure, per Constitution Principle V's module-boundary reasoning applied to data shape too). Answering "what was the salary on date X" by parsing those strings would be fragile (a format change anywhere in `TeacherService.describe(...)` silently breaks every past query) and slow (a full history scan + string-parse per query, not an indexed lookup).

**Decision**: Add `teacher_salary_history`, a small structured table inside `teacher.internal`, purpose-built for the "effective-dated value" query. Audit continues to receive an entry for every salary change too (FR-009 in specs/005 spec.md — "before/after/when" for the general cross-entity trail) — the two are complementary, not a replacement of one by the other: Audit answers "what changed and who did it," the new table answers "what was the value on this date."

**Rationale**: This mirrors a pattern this codebase has already proven twice for a different kind of "the current answer plus a queryable past" need — `organization`'s Manager/School assignment tables and `school`'s `SchoolZoneAssignment` — except those use `effective_from`/`effective_to` pairs for a *current-vs-not* boolean question ("is this assignment active right now"), while salary needs a genuinely date-indexed *point-in-time* question ("what value applied on this specific date"), so the table shape here is simpler: append-only rows with only `effective_from`, no `effective_to` — the row with the latest `effective_from <= date` is definitionally the answer, no explicit "closing" of a prior row is needed the way ending an assignment requires.

**Alternatives considered**:
- *Parse the existing Audit history*: rejected — fragile and not what `audit` is designed for (research.md above).
- *Add an `effectiveTo` column and explicitly close each prior entry when a new one is recorded (mirroring the assignment-table pattern exactly)*: rejected — unnecessary for this query shape. "Latest effective_from not after the requested date" already answers the question correctly without maintaining a second column that would only ever be redundant with the next row's `effective_from`. Simpler schema, one less thing that could get out of sync.

## 2. `TeacherProfile.hlsOfferedSalary` is removed, not kept as a duplicate

**Finding**: specs/005-teacher's `TeacherProfile` entity currently stores `hlsOfferedSalary` as a plain, directly-overwritable column, with `TeacherService.updateProfile(...)` freely changing it via `UpdateTeacherProfileRequest`. specs/005 has not been merged to `main` yet (still on a local feature branch) — this codebase's own established practice (Zone→Places, Organization→Zone-scoping) is to correct an unmerged or not-yet-final design in place rather than build a permanent workaround around it, reserving "keep the old thing as a documented interim mechanism" specifically for already-shipped, merged code (e.g., the Teacher→Manager direct-assignment interim, kept only because Organization was already merged when the correction was made).

**Decision**: Remove the `hlsOfferedSalary` column/field from `TeacherProfile`/the entity entirely. The "current salary" shown on `TeacherProfileView` (User Story 3, unchanged default behavior) is computed by `TeacherService` querying `teacher_salary_history` for the latest effective entry, not stored redundantly on the profile row.

**Rationale**: Keeping both a cached column on `TeacherProfile` *and* the history table would create two sources of truth that could drift (e.g., a bug that updates one but not the other). A single source of truth (the history table) with a cheap, indexed "latest row" query for the common case costs nothing in practice — this is the same "derive, don't duplicate" reasoning `school.api.ZoneQueries.currentZoneForSchool` already applied (derives the current Zone from `SchoolZoneAssignment`'s latest row rather than caching it on a School record).

**Alternatives considered**:
- *Keep `hlsOfferedSalary` on `TeacherProfile` as a cache, updated whenever a new history entry is recorded*: rejected — the duplicate-source-of-truth risk outweighs the marginal query cost saved, especially since "current salary" is already a single indexed lookup either way.

## 3. Recording a salary change is a new, separate command — not folded into `updateProfile`

**Finding**: specs/005's `UpdateTeacherProfileRequest`/`TeacherCommands.updateProfile(...)` currently bundle salary in with name/phone/email as one undifferentiated "change some fields" call. A salary change now carries an `effectiveFrom` date that a contact-detail change has no use for.

**Decision**: `UpdateTeacherProfileRequest` drops `hlsOfferedSalary`; a new `TeacherSalaryCommands.recordSalaryChange(teacherId, amount, effectiveFrom, actingUserId)` handles salary changes on its own endpoint (`POST /teachers/{teacherId}/salary`).

**Rationale**: Matches spec.md's own Assumptions section — a salary change and a contact-detail change are different operations with different shapes (one needs a date, the other doesn't) and different reasons to happen (an increment vs. a phone number update), so conflating them into one endpoint/request type would force every contact-detail-only update to also reason about an irrelevant field.

**Alternatives considered**:
- *Add an optional `effectiveFrom` to the existing `UpdateTeacherProfileRequest` and keep salary in that one call*: rejected — makes the request shape's meaning conditional on which fields are present, a worse API than two clearly-named, single-purpose operations.

## 4. Effective dates are `LocalDate`, not `Instant`

**Decision**: `effectiveFrom` on `TeacherSalaryHistory` is a `java.time.LocalDate` (calendar date), not an `Instant`/timestamp.

**Rationale**: spec.md's Assumptions section is explicit — salary changes are a daily-granularity concept (matching how attendance/payroll are expected to operate elsewhere in this system), so a calendar date is the right precision; adding time-of-day precision would invite an ambiguous "which salary applied for the first three hours of the day" question that has no real-world meaning here.

## 5. "As of" a future date, and a same-day tie, both need an explicit rule — spec.md's Edge Cases already settled both

**Decision**: A same-day duplicate entry: the most-recently-*recorded* one wins for that date (`ORDER BY effective_from DESC, created_at DESC LIMIT 1`, effectively). A future "as of" date (after the latest entry): returns the current (latest) salary, the same answer as no date at all.

**Rationale**: Both are named explicitly in spec.md's Edge Cases, so no invention is needed here — this section just records the query shape that satisfies them: sorting by `(effective_from DESC, created_at DESC)` and taking the first row with `effective_from <= requested date` naturally produces both behaviors without a special case in code.

## 6. No new Spring Modulith / ArchUnit concerns

**Decision**: `TeacherSalaryHistory`/`TeacherSalaryHistoryRepository`/the salary-related service methods all live inside the already-established `com.hls.teacher` package (`internal` and `api` respectively) — no new package, no new `ArchitectureTest` rule, no `TeacherModuleTest` bootstrap-mode change.

**Rationale**: This is a pure extension of the already-covered `com.hls.teacher` boundary (the same reasoning specs/008 applied when extending `school` with `Place`) — the existing `teacherInternalsAreOnlyAccessedFromWithinTeacher` ArchUnit rule and `TeacherModuleTest`'s `ALL_DEPENDENCIES` bootstrap mode (specs/005 research.md §6, corrected during implementation) already cover it.
