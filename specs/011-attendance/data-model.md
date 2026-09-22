# Data Model: Daily Attendance Capture & Monthly Rollup

Derived from spec.md's Key Entities and Functional Requirements FR-001 through FR-025. See research.md for the rationale behind each design choice referenced below.

## AttendanceStatusCode

The configurable set of statuses a mark can carry (FR-005). Seeded with the four required minimums; an Admin can add more.

| Field | Type | Notes |
|---|---|---|
| `code` | String (PK), e.g. `PRESENT` | Stable identifier, uppercase snake-case |
| `label` | String, not null | Display label, e.g. "Present" |
| `category` | Enum: `WORKED`, `LEAVE`, `TRAINING`, `NON_WORKING` — not null | Drives rollup bucketing (FR-007/009) |
| `weight` | Numeric(3,2), not null, default `1.00` | Used in the weighted attendance total (research.md §6) |
| `active` | Boolean, not null, default `true` | Deactivated codes stay valid on historical marks but can't be chosen for new ones |
| `createdAt` | Instant, not null | |
| `createdBy` | UUID, not null | |

**Seeded rows** (migration): `PRESENT`/Present/`WORKED`/1.00, `LEAVE`/Leave/`LEAVE`/0.00, `TRAINING`/Training Day/`TRAINING`/1.00, `NON_WORKING`/Non-Working Day/`NON_WORKING`/0.00.

**Invariants**: `code` is immutable once created; only `label`/`weight`/`active` can be changed after creation, each change audited (FR-006).

## AttendanceMark

One Teacher's current recorded status for one calendar day (FR-001–FR-004, FR-006). A single mutable row per `(teacherId, markDate)` — edits update the same row in place; every prior value is retrievable through the `audit` module, not a second table (research.md §4).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `teacherId` | UUID, not null | No FK — opaque reference, consistent with this codebase's established style |
| `markDate` | LocalDate, not null | Calendar date the mark is for |
| `schoolId` | UUID, not null | The school assignment this mark is tagged to (research.md §1 — unvalidated reference) |
| `statusCode` | String, not null, FK → `AttendanceStatusCode.code` | |
| `fractionalValue` | Numeric(3,2), not null, default `1.00`, `CHECK (0 <= fractionalValue <= 1)` | Half-day = `0.50` |
| `evidenceGeoLat` / `evidenceGeoLng` | Numeric(9,6), nullable | Optional geo-tag (FR-003) |
| `evidencePhotoUrl` | String, nullable | Optional photo reference (FR-003) |
| `evidenceCheckinCode` | String, nullable | Optional check-in code (FR-003) |
| `markedBy` | UUID, not null | Who created/last edited this mark — the Teacher or the Manager |
| `markedByRole` | Enum: `TEACHER`, `MANAGER`, `ADMIN` — not null | Distinguishes self-marked from marked-on-behalf; `ADMIN` added for FR-024's unscoped on-behalf marking (FR-002/FR-006/FR-024) |
| `markedAt` | Instant, not null | When the current value was set |

**Invariants**:

- `UNIQUE (teacherId, markDate)` — a second mark for the same Teacher/day edits the existing row (US1 AC4), never creates a duplicate.
- Every insert/update calls `audit.api.AuditWriter.record(...)` with the acting user, role, and full before/after description (FR-006).
- No delete method is exposed on the repository — a mark is corrected by editing its value, never removed, mirroring `TeacherProfileRepository`'s never-delete precedent.
- Rejected (service-level, not a DB constraint) when the mark's `(teacherId, markDate)`'s teacher-month is currently `LOCKED` (FR-012) — see `AttendanceTeacherMonthLock` below.
- Rejected (service-level) when `markDate` is after the current teacher-month (FR-023) — there is otherwise no lower bound on `markDate`; any past date is accepted as long as its teacher-month isn't `LOCKED` (FR-023, research.md's Clarifications session).

## AttendanceNonWorkingDate

An Admin-configured, organization-wide calendar of non-working dates (FR-022), applied automatically to every Teacher's rollup/grid unless overridden by an explicit mark (research.md §10). Owned entirely by `attendance` — no cross-module dependency.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `date` | LocalDate, not null | `UNIQUE` |
| `label` | String, not null | e.g. "Diwali", "Republic Day" |
| `active` | Boolean, not null, default `true` | Deactivated dates stop applying going forward but are never deleted (mirrors `AttendanceStatusCode`'s pattern) |
| `createdAt` | Instant, not null | |
| `createdBy` | UUID, not null | Admin (FR-022 is Admin-only, distinct from FR-005's Admin-or-Director) |

**Invariants**: `date` is immutable once created (add a new, corrected entry and deactivate the old one rather than editing `date` in place); only `active` can be toggled after creation, each change audited (FR-006's attribution discipline applied here too, even though FR-022 itself doesn't name history explicitly).

## AttendanceTeacherMonthLock

The locked/unlocked state of one Teacher's attendance for one calendar month (FR-011–FR-014).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `teacherId` | UUID, not null | |
| `period` | String(7), e.g. `2026-09`, not null | `UNIQUE (teacherId, period)` |
| `status` | Enum: `LOCKED`, `REOPENED` — not null | Absence of a row for a `(teacherId, period)` means "unlocked" (never locked) |
| `lockedAt` | Instant, not null | Set on lock and again on every re-lock |
| `lockedBy` | UUID, not null | Director who locked it (research.md §2 — stands in for a future `payroll` run; Director-only, matching `AttendanceReopenRecord.reopenedBy`'s authority boundary) |

**Invariants**: created by `lockMonth` (FR-011); flips to `REOPENED` via `reopenMonth` (FR-013, Director-only per FR-014); flips back to `LOCKED` via a subsequent `lockMonth` call, which also appends an `AttendanceReopenRecord` row's `relockedAt`/`relockedBy`.

## AttendanceReopenRecord

One append-only row per reopen-and-correct cycle for a teacher-month (FR-013). Never edited or deleted.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `lockId` | UUID, not null, FK → `AttendanceTeacherMonthLock.id` | |
| `reason` | String, not null | Stated reason required to reopen |
| `reopenedAt` | Instant, not null | |
| `reopenedBy` | UUID, not null | Director (FR-014) |
| `relockedAt` | Instant, nullable | Null while still reopened |
| `relockedBy` | UUID, nullable | |

## Derived / query-only shapes (not persisted)

- **MonthlyAttendanceRollupView** — `(teacherId, period, trainingDaysTotal, trainingDaysAttended, daysWorked, daysLeave, overallWorkingDays, unmarkedDays, weightedAttendanceTotal, lockStatus)`, computed live from `AttendanceMark` rows **and** `AttendanceNonWorkingDate` rows for the month (research.md §5/§10). Every day `d` in `period` gets one **effective category**, resolved in this order: (1) `d`'s `AttendanceMark.statusCode.category` if a mark exists for `d` (an explicit mark always wins — research.md §10); else (2) `NON_WORKING` if `d` is an active `AttendanceNonWorkingDate`; else (3) `UNMARKED` (no mark, no calendar coverage). The rollup figures are then:
  - `overallWorkingDays` = count of days in `period` whose effective category ≠ `NON_WORKING` (FR-009).
  - `daysWorked` = Σ `fractionalValue` over marks with category `WORKED`.
  - `daysLeave` = Σ `fractionalValue` over marks with category `LEAVE`.
  - `trainingDaysTotal` = count of marks with category `TRAINING`; `trainingDaysAttended` = Σ `fractionalValue` over those same marks (research.md §3).
  - `unmarkedDays` = count of days in `period` whose effective category is `UNMARKED` (FR-010) — a calendar-covered day is never counted here, even without an individual mark.
  - `weightedAttendanceTotal` = Σ (`fractionalValue` × `statusCode.weight`) over marks whose category ≠ `NON_WORKING` (research.md §6) — a calendar-implied non-working day (no mark) contributes `0`, same as an explicit `NON_WORKING` mark would.
  - `lockStatus` = `UNLOCKED` (no lock row), `LOCKED`, or `REOPENED`, read from `AttendanceTeacherMonthLock`.
- **LockStatusView** — `(teacherId, period, status, lockedAt, lockedBy, reopenHistory: List<ReopenEntry>)`.
- **ReopenEntry** — `(reason, reopenedAt, reopenedBy, relockedAt, relockedBy)`, one per `AttendanceReopenRecord` row.
- **AttendanceMarkView** — the read-side shape of one `AttendanceMark`, including evidence fields and `markedBy`/`markedByRole`/`markedAt`.
- **MarkAttendanceRequest** — `(markDate, schoolId, statusCode, fractionalValue, evidence: EvidenceInput?)`; `teacherId` comes from the JWT (`/attendance/me/marks`) or the path (`/attendance/teachers/{teacherId}/marks`), never from the body, so a caller can't spoof whose attendance they're marking.
- **EvidenceInput** — `(geoLat, geoLng, photoUrl, checkinCode)`, all nullable/optional (FR-003).
- **AttendanceGridView** *(User Story 5)* — `(period, days: List<LocalDate>, rows: List<AttendanceGridRow>)`; `days` is every calendar day in `period`, in order (Edge Cases — column count matches the month's actual day count).
- **AttendanceGridRow** — `(teacherId, teacherName, cells: Map<LocalDate, GridCell>)`; one entry per Teacher visible to the caller (FR-020). `teacherName` is composed from `teacher.api.TeacherQueries` so the grid doesn't require a second lookup per row.
- **GridCell** — `(statusCode: String?, category: AttendanceCategory?, fractionalValue: BigDecimal?, schoolId: UUID?, editable: boolean)`. Three states (research.md §10): **explicit mark** — `statusCode`/`category`/`fractionalValue`/`schoolId` all set, from that day's `AttendanceMark`; **calendar non-working, no mark** — `category = NON_WORKING`, the other three null; **truly unmarked** — all four null (FR-019's "clear unmarked indicator" — never a default status). `editable` is computed server-side per cell (research.md §9): `false` when that cell's teacher-month is `LOCKED`, or when the caller has no write permission for that Teacher (a Director viewing, or a Manager viewing a Teacher outside their own portfolio); `true` otherwise — including a calendar-non-working cell, since an explicit mark can still override the calendar for one Teacher on one day. The frontend never re-derives this itself (FR-025 — no silent bypass of the lock via client-side logic alone). `schoolId` is an implementation-time addition (discovered building the grid's inline edit): it lets editing an already-marked cell resubmit the same school assignment through `MarkAttendanceRequest` without the caller re-typing it.
- **GridCellEditRequest** *(User Story 5's inline edit)* — `(teacherId, markDate, statusCode, fractionalValue)`; no evidence fields (grid edits are Manager/Admin corrections from a desktop view, not a Teacher's own mobile self-mark — evidence stays specific to FR-001/FR-003's self-marking and on-behalf forms). Handled by the same `AttendanceMarkCommands.markAttendance(...)` (FR-025) as US1/US2/FR-024 — the grid introduces no second write path.
- **NonWorkingDateView** — `(id, date, label, active)`, the read-side shape of one `AttendanceNonWorkingDate`.
- **AddNonWorkingDateRequest** — `(date, label)`; Admin-only (FR-022).

## State transitions

- **AttendanceMark**: non-existent → recorded (create) → recorded (edit, repeatable) while unlocked; frozen once the teacher-month locks; edits resume only after an explicit reopen.
- **AttendanceTeacherMonthLock**: no row (unlocked) → `LOCKED` → `REOPENED` → `LOCKED` (repeatable for further corrections), each transition producing/closing an `AttendanceReopenRecord` row.
- **AttendanceNonWorkingDate**: non-existent → `active = true` (create) → `active = false` (deactivate, if a wrongly-added date needs correcting) — never edited (`date`/`label` are fixed at creation) or deleted.
